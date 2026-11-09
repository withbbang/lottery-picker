package com.lotterypicker.notification.service;

import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.NotificationStatus;
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
 */
@Slf4j
@Service
public class NotificationService {

	private static final int MAX_ATTEMPTS = 3;

	/** 재시도 3회의 최악 시간(약 2분)보다 길게 잡는다. 이보다 오래된 PENDING은 발송 중 서버가 멈춘 기록이다. */
	private static final Duration STALE_PENDING = Duration.ofMinutes(5);

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

	/** 다른 호출이 보내는 중이면 PENDING, 이미 보냈으면 SENT를 돌려주고 보내지 않는다. */
	public NotificationStatus sendOnce(NotificationKind kind, String targetKey, String text) {
		if (!telegram.isConfigured()) {
			return NotificationStatus.SKIPPED;
		}
		if (!claim(kind, targetKey)) {
			return statusOf(kind, targetKey);
		}
		return deliver(kind, targetKey, text);
	}

	/** 텔레그램 설정이 있어 실제로 보낼 수 있는지. 없으면 sendOnce는 기록 없이 SKIPPED다. */
	public boolean isEnabled() {
		return telegram.isConfigured();
	}

	public NotificationStatus statusOf(NotificationKind kind, String targetKey) {
		return logs.findByKindAndTargetKey(kind, targetKey)
			.map(NotificationLog::getStatus)
			.orElse(NotificationStatus.SKIPPED);
	}

	private boolean claim(NotificationKind kind, String targetKey) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (logs.findByKindAndTargetKey(kind, targetKey).isPresent()) {
			return logs.reclaim(kind, targetKey, now, now.minus(STALE_PENDING)) == 1;
		}
		try {
			logs.saveAndFlush(NotificationLog.pending(kind, targetKey, now));
			return true;
		} catch (DataIntegrityViolationException e) {
			return false;
		}
	}

	private NotificationStatus deliver(NotificationKind kind, String targetKey, String text) {
		int attempts = 0;
		NotificationStatus result = NotificationStatus.FAILED;
		while (attempts < MAX_ATTEMPTS && result != NotificationStatus.SENT) {
			attempts++;
			try {
				telegram.send(text);
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
