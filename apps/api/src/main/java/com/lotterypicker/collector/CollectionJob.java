package com.lotterypicker.collector;

import com.lotterypicker.collector.service.CollectorService;
import com.lotterypicker.notification.MessageFormatter;
import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.service.NotificationService;
import com.lotterypicker.recommendation.RoundCalendar;
import com.lotterypicker.result.service.PostDrawService;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 새 회차를 확인해 수집하고, 새 회차와 채점 대기 회차를 처리한다. 5분마다 깨지만 {@link PollPolicy}가 정한 시각에만
 * 사이트를 부른다. 06:00에는 판매점을 수집한다. 기동 수집과 같은 {@link CollectionLock}으로 서로 겹치지 않게 한다.
 * 수집기 예외는 기대 추첨일을 키로 COLLECTION_ERROR를 알린다. 수집기는 파싱에 성공한 회차만 저장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("app.scheduling.enabled")
public class CollectionJob {

	private static final int DAYS_PER_ROUND = 7;

	private final CollectorService collector;
	private final PostDrawService postDraw;
	private final NotificationService notifications;
	private final LottoRoundRepository rounds;
	private final CollectionLock lock;
	private final Clock clock;

	/**
	 * 확인 시각이면 수집하고, 매번 보내지 못한 최근 종료 알림을 다시 보낸다. 재발송은 수집이 실패해도 한다.
	 * 재발송은 최근 두 회차만 보고 이미 보낸 회차는 건너뛰므로 5분마다 돌아도 가볍다.
	 */
	@Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
	public void poll() {
		// 초기 적재 전에는 기동 수집이 맡는다.
		Optional<LocalDate> latest = rounds.findTopByOrderByRoundNoDesc().map(LottoRound::getDrawDate);
		if (latest.isEmpty()) {
			return;
		}
		ZonedDateTime now = ZonedDateTime.now(clock);
		lock.tryRun("5분 확인", () -> {
			try {
				if (PollPolicy.isDue(now, latest.get())) {
					collectDue(now, latest.get());
				}
			} finally {
				postDraw.resendUnsent();
			}
		});
	}

	private void collectDue(ZonedDateTime now, LocalDate latest) {
		if (PollPolicy.isOverdue(now, latest)) {
			LocalDate expected = latest.plusDays(DAYS_PER_ROUND);
			notifications.sendOnce(NotificationKind.COLLECTION_FAILED, expected.toString(),
					MessageFormatter.collectionFailed(expected));
		}
		collectRounds();
	}

	@Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
	public void syncStoresDaily() {
		lock.tryRun("판매점 수집", this::collectStores);
	}

	/** 잠금 없이 회차를 수집하고 새 회차와 대기 회차를 처리한다. 호출하는 쪽이 잠금을 잡는다. */
	public void collectRounds() {
		List<Integer> collected;
		try {
			collected = collector.syncMissingRounds();
		} catch (CollectorException e) {
			notifyError(e);
			throw e;
		}
		TreeSet<Integer> targets = new TreeSet<>(collected);
		targets.addAll(postDraw.pendingRounds());
		targets.forEach(postDraw::process);
	}

	/** 잠금 없이 판매점을 수집한다. 호출하는 쪽이 잠금을 잡는다. */
	public void collectStores() {
		try {
			collector.syncStores();
		} catch (CollectorException e) {
			notifyError(e);
			throw e;
		}
	}

	/** 수집 범위 밖(기동, 06시) 오류도 주 단위로 한 번만 알리도록 키는 기대 추첨일로 한다. */
	private void notifyError(CollectorException cause) {
		LocalDate expected = rounds.findTopByOrderByRoundNoDesc()
			.map(round -> round.getDrawDate().plusDays(DAYS_PER_ROUND))
			.orElseGet(() -> RoundCalendar.targetSaturday(LocalDate.now(clock)));
		try {
			notifications.sendOnce(NotificationKind.COLLECTION_ERROR, expected.toString(),
					MessageFormatter.collectionError(expected));
		} catch (RuntimeException e) {
			cause.addSuppressed(e);
		}
	}

}
