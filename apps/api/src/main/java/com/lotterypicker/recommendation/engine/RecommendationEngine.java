package com.lotterypicker.recommendation.engine;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.lotto.LottoNumbers;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 전략 5줄, 그 근거로 만든 근거 조합(최대 5줄), 대조군 5줄을 만든다. 추천 줄은 제약을 못 지키면 겹침, 인기 조합 순으로 완화한다. */
@Component
public class RecommendationEngine {

	private static final int MAX_ATTEMPTS = 1_000;
	private static final int MAX_OVERLAP = 2;
	private static final int CONTROL_LINES = 5;
	private static final String OVERLAP_RELAXED = "중복 제한 완화";
	private static final String ALL_RELAXED = "중복·인기 조합 제한 완화";

	private final List<LineStrategy> strategies = List.of(new RecentHotStrategy(), new LongColdStrategy(),
			new AllTimeStrategy(), new PairStrategy(), new BalancedStrategy());
	private final LineStrategy control = new ControlStrategy();

	public GeneratedSet generate(DrawHistory history, RecommendationConstraint c, Random random) {
		if (history.draws().isEmpty()) {
			throw new BusinessException(ErrorCode.RECOMMENDATION_NO_HISTORY);
		}
		NumberStatistics stats = NumberStatistics.from(history);
		Set<LottoNumbers> winners = history.draws().stream().map(HistoricalDraw::numbers).collect(Collectors.toSet());

		List<GeneratedLine> lines = new ArrayList<>();
		for (LineStrategy strategy : strategies) {
			lines.add(generateLine(strategy, stats, c, random, winners, lines));
		}

		List<DrawnLine> combined = CombinedLines.combine(lines.stream().map(GeneratedLine::line).toList(), c.fixed(),
				numbers -> PopularityFilter.isPopularExceptBirthday(numbers, winners));

		List<DrawnLine> controlLines = new ArrayList<>();
		for (int i = 0; i < CONTROL_LINES; i++) {
			controlLines.add(control.draw(stats, c, random));
		}
		return new GeneratedSet(List.copyOf(lines), combined, List.copyOf(controlLines));
	}

	private static GeneratedLine generateLine(LineStrategy strategy, NumberStatistics stats,
			RecommendationConstraint c, Random random, Set<LottoNumbers> winners, List<GeneratedLine> previous) {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			DrawnLine line = strategy.draw(stats, c, random);
			if (!PopularityFilter.isPopular(line.numbers(), winners) && overlapAllowed(line, previous, c)) {
				return new GeneratedLine(line, Relaxation.NONE);
			}
		}
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			DrawnLine line = strategy.draw(stats, c, random);
			if (!PopularityFilter.isPopular(line.numbers(), winners)) {
				return new GeneratedLine(withNote(line, OVERLAP_RELAXED), Relaxation.OVERLAP);
			}
		}
		DrawnLine line = strategy.draw(stats, c, random);
		return new GeneratedLine(withNote(line, ALL_RELAXED), Relaxation.OVERLAP_AND_POPULARITY);
	}

	private static boolean overlapAllowed(DrawnLine line, List<GeneratedLine> previous, RecommendationConstraint c) {
		return previous.stream().allMatch(other -> overlap(line.numbers(), other.line().numbers(), c) <= MAX_OVERLAP);
	}

	private static int overlap(LottoNumbers a, LottoNumbers b, RecommendationConstraint c) {
		return (int) a.values().stream().filter(b::contains).filter(n -> !c.fixed().contains(n)).count();
	}

	private static DrawnLine withNote(DrawnLine line, String note) {
		List<String> evidence = new ArrayList<>(line.evidence());
		evidence.add(note);
		return new DrawnLine(line.strategy(), line.numbers(), List.copyOf(evidence), line.ingredients());
	}

}
