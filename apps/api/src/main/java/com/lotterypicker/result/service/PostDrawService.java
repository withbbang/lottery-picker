package com.lotterypicker.result.service;

import com.lotterypicker.collector.PollPolicy;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.notification.MessageFormatter;
import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.service.NotificationService;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.result.DrawSummary;
import com.lotterypicker.result.ResultTally;
import com.lotterypicker.result.entity.RecommendationResult;
import com.lotterypicker.result.repository.RecommendationResultRepository;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 당첨번호가 저장된 회차의 추천과 대조군을 채점하고 추첨 종료 알림을 한 번 보낸다.
 * 알림 재시도 대기 동안 DB 연결을 잡지 않도록 클래스에 트랜잭션을 걸지 않는다. 채점 저장은 saveAll 한 번이라 원자적이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostDrawService {

	/**
	 * 추천이 없는 회차는 추첨일로부터 이 일수 안일 때만 알린다. 초기 적재한 지난 회차는 알리지 않는다.
	 * 재발송도 이 기간 안의 회차만 한다.
	 */
	private static final int NOTIFY_WINDOW_DAYS = 7;

	private final LottoRoundRepository rounds;
	private final DailyRecommendationRepository recommendations;
	private final RecommendationResultRepository results;
	private final NotificationService notifications;
	private final Clock clock;

	/** 당첨번호는 저장됐는데 채점하지 않은 추천이 있는 회차, 오름차순. */
	public List<Integer> pendingRounds() {
		return results.findPendingRoundNos();
	}

	/** 멱등이다. 당첨번호가 없는 회차는 아무것도 만들지 않는다. 없는 결과를 낙첨으로 만들지 않기 위해서다. */
	public void process(int round) {
		Optional<LottoRound> stored = rounds.findById(round);
		if (stored.isEmpty()) {
			log.warn("{}회 당첨번호가 없어 채점하지 않습니다.", round);
			return;
		}
		process(stored.get());
	}

	/**
	 * 종료 알림을 아직 모든 방에 보내지 못한 최근 회차를 다시 처리한다. 발송 실패(FAILED)나 채점 후 알림 전에 멈춘 경우를 잡는다.
	 * 추첨일로부터 7일 안의 회차만 본다. 지난주 회차를 이번 주에 새 소식처럼 보내지 않기 위해서다.
	 * 그런 회차는 최신 회차와 직전 회차뿐이므로 두 회차만 조회한다. 이미 보냈거나 텔레그램 설정이 없으면 건드리지 않는다.
	 * 늦게 수집한 회차는 수집 경로의 {@link #process(int)}가 기간과 관계없이 채점하고 알린다.
	 */
	public void resendUnsent() {
		if (!notifications.isEnabled()) {
			return;
		}
		LocalDate today = LocalDate.now(clock);
		rounds.findTopByOrderByRoundNoDesc().ifPresent(latest -> {
			for (int round : List.of(latest.getRoundNo() - 1, latest.getRoundNo())) {
				rounds.findById(round)
					.filter(drawn -> withinNotifyWindow(drawn, today))
					.filter(drawn -> !notifications.isDelivered(NotificationKind.DRAW_FINISHED, String.valueOf(round)))
					.ifPresent(this::process);
			}
		});
	}

	/** 채점 결과가 있는 가장 최근 회차를 추첨 종료 알림과 같은 요약으로. 채점도 알림도 하지 않는다. */
	@Transactional(readOnly = true)
	public Optional<DrawSummary> latestSummary() {
		return results.findFirstByOrderByRoundNoDesc()
			.flatMap(latest -> rounds.findById(latest.getRoundNo()))
			.map(drawn -> summarize(drawn, recommendations.findByRoundNoOrderByDateAscLineIndexAsc(drawn.getRoundNo()),
					results.findByRoundNo(drawn.getRoundNo())));
	}

	private static boolean withinNotifyWindow(LottoRound round, LocalDate today) {
		return ChronoUnit.DAYS.between(round.getDrawDate(), today) <= NOTIFY_WINDOW_DAYS;
	}

	private void process(LottoRound drawn) {
		int round = drawn.getRoundNo();
		List<DailyRecommendation> lines = recommendations.findByRoundNoOrderByDateAscLineIndexAsc(round);
		List<RecommendationResult> scored = score(drawn, lines);

		if (shouldNotify(drawn, !lines.isEmpty(), LocalDate.now(clock))) {
			notifications.sendOnce(NotificationKind.DRAW_FINISHED, String.valueOf(round),
					MessageFormatter.drawFinished(summarize(drawn, lines, scored)));
		}
	}

	public static boolean shouldNotify(LottoRound round, boolean hasRecommendations, LocalDate today) {
		if (hasRecommendations) {
			return true;
		}
		return withinNotifyWindow(round, today);
	}

	/** 아직 채점하지 않은 줄만 채점해 저장하고, 그 회차의 결과 전체를 돌려준다. */
	private List<RecommendationResult> score(LottoRound drawn, List<DailyRecommendation> lines) {
		List<RecommendationResult> existing = results.findByRoundNo(drawn.getRoundNo());
		Set<Long> done = existing.stream().map(RecommendationResult::getRecommendationId).collect(Collectors.toSet());
		LottoNumbers winning = drawn.numbers();
		LocalDateTime now = LocalDateTime.now(clock);
		List<RecommendationResult> fresh = lines.stream()
			.filter(line -> !done.contains(line.getId()))
			.map(line -> {
				PrizeRank rank = PrizeRank.of(line.numbers(), winning, drawn.getBonus());
				return RecommendationResult.builder()
					.recommendationId(line.getId())
					.roundNo(drawn.getRoundNo())
					.matchCount(line.numbers().countMatches(winning))
					.prizeRank(rank)
					.prizeAmount(drawn.amountFor(rank))
					.createdAt(now)
					.build();
			})
			.toList();
		if (fresh.isEmpty()) {
			return existing;
		}
		List<RecommendationResult> saved = results.saveAll(fresh);
		return Stream.concat(existing.stream(), saved.stream()).toList();
	}

	private static DrawSummary summarize(LottoRound drawn, List<DailyRecommendation> lines,
			List<RecommendationResult> scored) {
		Set<Long> recommended = lines.stream()
			.filter(line -> !line.isControl())
			.map(DailyRecommendation::getId)
			.collect(Collectors.toSet());
		ResultTally tally = ResultTally.of(scored.stream()
			.filter(result -> recommended.contains(result.getRecommendationId()))
			.toList());
		LocalDateTime fetchedAt = drawn.getFetchedAt();
		boolean late = fetchedAt != null && !fetchedAt.isBefore(PollPolicy.overdueAt(drawn.getDrawDate()));
		return new DrawSummary(drawn.getRoundNo(), drawn.getDrawDate(), drawn.numbers(), drawn.getBonus(),
				late ? fetchedAt : null, tally);
	}

}
