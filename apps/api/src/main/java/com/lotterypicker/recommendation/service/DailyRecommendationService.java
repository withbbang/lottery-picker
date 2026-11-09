package com.lotterypicker.recommendation.service;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.service.NotificationService;
import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.RoundCalendar;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.dto.RecommendationLineResponse;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.engine.DrawHistory;
import com.lotterypicker.recommendation.engine.DrawnLine;
import com.lotterypicker.recommendation.engine.GeneratedLine;
import com.lotterypicker.recommendation.engine.GeneratedSet;
import com.lotterypicker.recommendation.engine.HistoricalDraw;
import com.lotterypicker.recommendation.engine.RecommendationConstraint;
import com.lotterypicker.recommendation.engine.RecommendationEngine;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 날짜별 추천 한 벌을 만들고 읽는다. 번호 생성은 최악 수 초가 걸리므로 클래스에 트랜잭션을 걸지 않고,
 * 저장만 짧은 트랜잭션으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class DailyRecommendationService {

	private static final Comparator<DailyRecommendation> UNIQUE_KEY_ORDER = Comparator
		.comparing((DailyRecommendation line) -> line.getStrategy().name())
		.thenComparingInt(DailyRecommendation::getLineIndex);

	private final RecommendationEngine engine;
	private final RecommendationSettingsService settings;
	private final LottoRoundRepository rounds;
	private final DailyRecommendationRepository repository;
	private final Random random;
	private final Clock clock;
	private final TransactionOperations transactions;
	private final NotificationService notifications;

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	public boolean existsFor(LocalDate date) {
		return repository.existsByDate(date);
	}

	/** 새로 만들면 값, 이미 있으면 빈 값. */
	public Optional<RecommendationSet> generateIfAbsent(LocalDate date) {
		if (repository.existsByDate(date)) {
			return Optional.empty();
		}
		List<LottoRound> stored = rounds.findAllByOrderByRoundNoAsc();
		if (stored.isEmpty()) {
			throw new BusinessException(ErrorCode.RECOMMENDATION_NO_HISTORY);
		}
		LottoRound latest = stored.get(stored.size() - 1);
		int round = RoundCalendar.roundFor(date, latest.getRoundNo(), latest.getDrawDate());
		RecommendationConstraint constraint = settings.currentConstraint();

		GeneratedSet generated = engine.generate(toHistory(stored), constraint, random);

		List<DailyRecommendation> rows = toRows(generated, date, round, constraint, stored.get(0).getRoundNo(),
				latest.getRoundNo());
		try {
			transactions.executeWithoutResult(status -> repository.saveAll(rows));
		} catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
			// 유니크 위반이나 잠금 실패는 다른 쪽이 먼저 저장했을 수 있다. 그날 추천이 없으면 다른 저장 실패이므로 숨기지 않는다.
			if (repository.existsByDate(date)) {
				return Optional.empty();
			}
			throw e;
		}
		return Optional.of(new RecommendationSet(date, round, generated.lines().stream()
			.map(line -> new StoredLine(line.line().strategy(), line.line().numbers(), line.line().evidence(),
					line.relaxation()))
			.toList()));
	}

	/** 화면용 조회. 대조군은 성적표에만 쓰므로 뺀다. */
	public Optional<RecommendationSetResponse> find(LocalDate date) {
		List<DailyRecommendation> lines = repository.findByDateOrderByLineIndexAsc(date).stream()
			.filter(line -> !line.isControl())
			.toList();
		if (lines.isEmpty()) {
			return Optional.empty();
		}
		DailyRecommendation first = lines.get(0);
		return Optional.of(new RecommendationSetResponse(first.getDate(), first.getRoundNo(),
				lines.stream().map(RecommendationLineResponse::from).toList(), first.getHistoryFromRound(),
				first.getHistoryToRound(), RecommendationSetResponse.DISCLAIMER,
				notifications.statusOf(NotificationKind.DAILY_RECOMMENDATION, date.toString())));
	}

	/** 알림 재발송용 조회. 대조군은 알림에 보내지 않으므로 뺀다. */
	public Optional<RecommendationSet> findSet(LocalDate date) {
		List<DailyRecommendation> lines = repository.findByDateOrderByLineIndexAsc(date).stream()
			.filter(line -> !line.isControl())
			.toList();
		if (lines.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new RecommendationSet(date, lines.get(0).getRoundNo(), lines.stream()
			.map(line -> new StoredLine(line.getStrategy(), line.numbers(), line.getEvidence(), line.getRelaxation()))
			.toList()));
	}

	/** 저장된 회차를 엔진 입력으로 바꾼다. 시뮬레이션도 같은 변환을 쓴다. */
	public static DrawHistory toHistory(List<LottoRound> stored) {
		return new DrawHistory(
				stored.stream().map(r -> new HistoricalDraw(r.getRoundNo(), r.numbers(), r.getBonus())).toList());
	}

	private List<DailyRecommendation> toRows(GeneratedSet generated, LocalDate date, int round,
			RecommendationConstraint constraint, int historyFrom, int historyTo) {
		DailyRecommendation.DailyRecommendationBuilder base = DailyRecommendation.builder()
			.date(date)
			.roundNo(round)
			.fixedSnapshot(snapshot(constraint.fixed()))
			.excludedSnapshot(snapshot(constraint.excluded()))
			.historyFromRound(historyFrom)
			.historyToRound(historyTo)
			.createdAt(LocalDateTime.now(clock));

		List<DailyRecommendation> rows = new ArrayList<>();
		List<GeneratedLine> lines = generated.lines();
		for (int i = 0; i < lines.size(); i++) {
			rows.add(row(base, lines.get(i).line(), i, lines.get(i).relaxation()));
		}
		List<DrawnLine> control = generated.control();
		for (int i = 0; i < control.size(); i++) {
			rows.add(row(base, control.get(i), i, Relaxation.NONE));
		}
		// 유니크 인덱스 순서로 넣는다. 작은 키를 나중에 넣으면 동시 생성의 대기 잠금과 맞물려 MySQL에서 데드락이 난다.
		rows.sort(UNIQUE_KEY_ORDER);
		return rows;
	}

	private static DailyRecommendation row(DailyRecommendation.DailyRecommendationBuilder base, DrawnLine line,
			int lineIndex, Relaxation relaxation) {
		return base.strategy(line.strategy())
			.lineIndex(lineIndex)
			.numbers(line.numbers())
			.evidence(line.evidence())
			.relaxation(relaxation)
			.build();
	}

	private static String snapshot(Set<Integer> numbers) {
		return numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

}
