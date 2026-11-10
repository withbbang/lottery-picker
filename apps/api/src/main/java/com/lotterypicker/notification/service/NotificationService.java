package com.lotterypicker.notification.service;

import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.NotificationStatus;
import com.lotterypicker.notification.TelegramChat;
import com.lotterypicker.notification.TelegramClient;
import com.lotterypicker.notification.TelegramException;
import com.lotterypicker.notification.entity.NotificationLog;
import com.lotterypicker.notification.repository.NotificationLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 같은 알림을 한 번만 보낸다. 재시도 대기 동안 DB 연결을 잡지 않도록 트랜잭션은 리포지토리 호출 단위로만 쓴다.
 * 발송 실패는 예외 없이 FAILED로 돌려준다. 알림이 실패해도 추천과 수집은 계속되어야 한다.
 * 가족방에도 보내는 알림은 방마다 따로 기록한다. 한 방의 실패가 다른 방의 중복 발송으로 이어지지 않고, 실패한 방만 다시 보낸다.
 */
@Slf4j
@Service
public class NotificationService {

	private static final int MAX_ATTEMPTS = 3;

	/**
	 * 한 알림을 보내려는 발송(각 {@link #MAX_ATTEMPTS}회 시도)의 최대 횟수. 처음 발송과 재발송을 합친다.
	 * 이만큼 모두 실패한 FAILED 기록은 다시 선점하지 않는다. 5분 재발송이 텔레그램 장애 동안 끝없이 시도하지 않게 하기 위해서다.
	 */
	private static final int MAX_DELIVERIES = 3;

	/** 재시도 3회의 최악 시간(약 2분)보다 길게 잡는다. 이보다 오래된 PENDING은 발송 중 서버가 멈춘 기록이다. */
	private static final Duration STALE_PENDING = Duration.ofMinutes(5);

	/** 가족방 발송 기록의 키 접두사. 운영자 방 기록은 키를 그대로 쓴다. */
	private static final String FAMILY_KEY_PREFIX = "family:";

	private final TelegramClient telegram;
	private final NotificationLogRepository logs;
	private final Clock clock;
	private final long retryDelayMs;

	public NotificationService(TelegramClient telegram, NotificationLogRepository logs, Clock clock,
		@Value("${app.telegram.retry-delay-ms}") long retryDelayMs) {
		this.telegram = telegram;
		this.logs = logs;
		this.clock = clock;
		this.retryDelayMs = retryDelayMs;
	}

	/**
	 * 다른 호출이 보내는 중이면 PENDING, 이미 보냈으면 SENT를 돌려주고 보내지 않는다.
	 * 가족방에도 보내는 알림이면 가족방에도 같은 방식으로 보낸다. 돌려주는 상태는 운영자 방 것이다.
	 */
	public NotificationStatus sendOnce(NotificationKind kind, String targetKey, String text) {
		if (!telegram.isConfigured()) {
			return NotificationStatus.SKIPPED;
		}
		NotificationStatus result = sendOnce(TelegramChat.OWNER, kind, targetKey, text);
		if (sendsToFamily(kind)) {
			sendToFamily(kind, targetKey, text);
		}
		return result;
	}

	/** 보내야 할 모든 방에 보냈는지. 재발송 대상을 고를 때 쓴다. */
	public boolean isDelivered(NotificationKind kind, String targetKey) {
		if (statusOf(kind, targetKey) != NotificationStatus.SENT) {
			return false;
		}
		return !sendsToFamily(kind) || statusOf(kind, familyKey(targetKey)) == NotificationStatus.SENT;
	}

	private NotificationStatus sendOnce(TelegramChat chat, NotificationKind kind, String key, String text) {
		if (!claim(kind, key)) {
			return statusOf(kind, key);
		}
		return deliver(chat, kind, key, text);
	}

	/** 가족방 발송은 부가 기능이다. 기록 중 DB 오류가 나도 이미 끝난 운영자 방 발송과 호출한 잡을 깨뜨리지 않는다. */
	private void sendToFamily(NotificationKind kind, String targetKey, String text) {
		try {
			sendOnce(TelegramChat.FAMILY, kind, familyKey(targetKey), text);
		} catch (RuntimeException e) {
			log.warn("가족방 알림 처리 실패: {} {} - {}", kind, targetKey, e.getMessage());
		}
	}

	private boolean sendsToFamily(NotificationKind kind) {
		return kind.sharedWithFamily() && telegram.hasFamilyChat();
	}

	private static String familyKey(String targetKey) {
		return FAMILY_KEY_PREFIX + targetKey;
	}

	/** 텔레그램 설정이 있어 실제로 보낼 수 있는지. 없으면 sendOnce는 기록 없이 SKIPPED다. */
	public boolean isEnabled() {
		return telegram.isConfigured();
	}

	/** 운영자 방 발송 상태. */
	public NotificationStatus statusOf(NotificationKind kind, String targetKey) {
		return logs.findByKindAndTargetKey(kind, targetKey)
			.map(NotificationLog::getStatus)
			.orElse(NotificationStatus.SKIPPED);
	}

	private boolean claim(NotificationKind kind, String targetKey) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (logs.findByKindAndTargetKey(kind, targetKey).isPresent()) {
			return logs.reclaim(kind, targetKey, now, now.minus(STALE_PENDING), MAX_ATTEMPTS * MAX_DELIVERIES) == 1;
		}
		try {
			logs.saveAndFlush(NotificationLog.pending(kind, targetKey, now));
			return true;
		} catch (DataIntegrityViolationException e) {
			return false;
		}
	}

	private NotificationStatus deliver(TelegramChat chat, NotificationKind kind, String targetKey, String text) {
		int attempts = 0;
		NotificationStatus result = NotificationStatus.FAILED;
		while (attempts < MAX_ATTEMPTS && result != NotificationStatus.SENT) {
			attempts++;
			try {
				telegram.send(chat, text);
				result = NotificationStatus.SENT;
			} catch (TelegramException e) {
				log.warn("텔레그램 발송 실패 {}/{}: {} {} - {}", attempts, MAX_ATTEMPTS, kind, targetKey, e.getMessage());
				if (attempts < MAX_ATTEMPTS && !waitForRetry()) {
					break;
				}
			}
		}
		logs.finish(kind, targetKey, result, attempts, LocalDateTime.now(clock));
		return result;
	}

	private boolean waitForRetry() {
		try {
			Thread.sleep(retryDelayMs);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
