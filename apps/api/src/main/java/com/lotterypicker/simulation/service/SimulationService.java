package com.lotterypicker.simulation.service;

import com.lotterypicker.common.config.ClockConfig;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.AllTimeStrategy;
import com.lotterypicker.recommendation.engine.BalancedStrategy;
import com.lotterypicker.recommendation.engine.CombinedLines;
import com.lotterypicker.recommendation.engine.ControlStrategy;
import com.lotterypicker.recommendation.engine.DrawHistory;
import com.lotterypicker.recommendation.engine.DrawnLine;
import com.lotterypicker.recommendation.engine.HistoricalDraw;
import com.lotterypicker.recommendation.engine.LineStrategy;
import com.lotterypicker.recommendation.engine.LongColdStrategy;
import com.lotterypicker.recommendation.engine.NumberStatistics;
import com.lotterypicker.recommendation.engine.PairStrategy;
import com.lotterypicker.recommendation.engine.RecentHotStrategy;
import com.lotterypicker.recommendation.engine.RecommendationConstraint;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.service.DailyRecommendationService;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.simulation.ConfidenceInterval;
import com.lotterypicker.simulation.SimulationReport;
import com.lotterypicker.simulation.StrategySimulation;
import com.lotterypicker.simulation.dto.SimulationResponse;
import com.lotterypicker.simulation.dto.StrategySimulationResponse;
import com.lotterypicker.simulation.entity.SimulationResult;
import com.lotterypicker.simulation.repository.SimulationResultRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 과거 회차 시뮬레이션. 각 회차 r마다 r 이전 회차만으로 통계를 만들고, 전략별로 회차·전략 고정 시드 {@link Random}으로
 * N줄을 뽑아 r의 당첨번호와 비교한다. 고정·제외 번호, 인기 조합 필터, 줄 간 중복 제약은 적용하지 않는다.
 * 계산은 수 분이 걸릴 수 있으므로 클래스에 트랜잭션을 걸지 않고, 읽기와 저장은 각 저장소 호출의 트랜잭션만 쓴다.
 * 결과는 마지막 한 행만 저장하며 조회 요청은 저장된 값만 읽는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

	/** 통계가 안정되도록 앞의 100회는 입력으로만 쓴다. */
	public static final int START_ROUND = 101;

	public static final int REPETITIONS = 200;

	/**
	 * 계산 방식 버전. 1: 줄 단위 이항 근사, 2: 회차 단위(군집) 분산, 3: 근거 조합 전략 추가. 바꾸면 저장된 결과를 다시
	 * 계산한다.
	 */
	public static final int METHOD_VERSION = 3;

	public static final long BASE_SEED = 20_261_106L;

	public static final String SCOPE_NOTE = "실제 추천에서 거르는 고정·제외 번호, 많은 사람이 고르는 조합, 다른 줄과 겹치는 번호는 적용하지 않고 전략이 번호를 고르는 방식만 시험했습니다. 근거 조합도 같은 조건에서 다섯 전략 줄의 근거로 만들었습니다.";

	private static final Map<StrategyType, LineStrategy> STRATEGIES = Stream
		.of(new RecentHotStrategy(), new LongColdStrategy(), new AllTimeStrategy(), new PairStrategy(),
				new BalancedStrategy(), new ControlStrategy())
		.collect(Collectors.toMap(LineStrategy::type, Function.identity(), (a, b) -> a,
				() -> new EnumMap<>(StrategyType.class)));

	/** 근거 조합의 재료가 되는 다섯 전략. 실제 추천과 같은 순서. */
	private static final List<StrategyType> COMBINED_SOURCES = List.of(StrategyType.RECENT_HOT,
			StrategyType.LONG_COLD, StrategyType.ALL_TIME, StrategyType.PAIR, StrategyType.BALANCED);

	private final LottoRoundRepository rounds;
	private final SimulationResultRepository results;
	private final Clock clock;

	/** 계산 중인지. 동시에 두 번 계산하지 않기 위해 쓴다. */
	private final AtomicBoolean running = new AtomicBoolean();

	/** 아직 반영하지 않은 계산 요청이 있는지. 계산 중에 들어온 요청은 끝난 뒤 한 번으로 합쳐 다시 계산한다. */
	private final AtomicBoolean pending = new AtomicBoolean();

	/**
	 * 저장된 결과가 오래됐으면(결과가 없거나, 끝 회차가 최신 회차와 다르거나, 계산 방식 버전이 {@link #METHOD_VERSION}과
	 * 다르면) 다시 계산해 바꾼다.
	 * 확인은 최신 회차와 저장된 결과 조회 두 번뿐이라, 수집을 시도할 때마다와 기동 때 불러도 가볍다.
	 * 수집이 일부 회차를 저장한 뒤 실패해도 다음 호출이 그 회차를 잡는다.
	 * 이미 계산 중이면 새로 돌리지 않고 요청만 남기며, 돌고 있던 계산이 끝난 뒤 한 번 더 확인한다. 그래서 가장 최근 회차가
	 * 빠지지 않고, 그 사이 이미 반영됐으면 다시 계산하지 않는다.
	 */
	@Async
	public void refresh() {
		pending.set(true);
		while (pending.get() && running.compareAndSet(false, true)) {
			try {
				pending.set(false);
				if (isStale()) {
					computeAndSave();
				}
			} catch (RuntimeException e) {
				log.error("시뮬레이션 계산 실패", e);
			} finally {
				running.set(false);
			}
		}
	}

	private boolean isStale() {
		Optional<Integer> latest = rounds.findTopByOrderByRoundNoDesc().map(LottoRound::getRoundNo);
		if (latest.isEmpty() || latest.get() < START_ROUND) {
			return false;
		}
		return results.findById(SimulationResult.SINGLE_ROW_ID)
			.map(saved -> saved.getToRound() != latest.get() || saved.getMethodVersion() != METHOD_VERSION)
			.orElse(true);
	}

	public Optional<SimulationResponse> findLatest() {
		return results.findById(SimulationResult.SINGLE_ROW_ID)
			.map(result -> new SimulationResponse(result.getFromRound(), result.getToRound(), result.getRepetitions(),
					result.getComputedAt().atZone(ClockConfig.SEOUL).toOffsetDateTime(), PrizeRank.AT_LEAST_FIFTH_RATE,
					SCOPE_NOTE, result.getStrategies().stream().map(StrategySimulationResponse::from).toList()));
	}

	private void computeAndSave() {
		long started = System.nanoTime();
		DrawHistory history = DailyRecommendationService.toHistory(rounds.findAllByOrderByRoundNoAsc());
		if (history.draws().stream().noneMatch(draw -> draw.round() >= START_ROUND)) {
			log.info("{}회 이후 회차가 없어 시뮬레이션을 건너뜁니다.", START_ROUND);
			return;
		}
		SimulationReport report = run(history, START_ROUND, REPETITIONS, BASE_SEED);
		results.save(SimulationResult.latest(report, METHOD_VERSION, LocalDateTime.now(clock)));
		log.info("시뮬레이션 완료: {}~{}회, {}ms", report.fromRound(), report.toRound(),
				(System.nanoTime() - started) / 1_000_000);
	}

	/**
	 * 순수 계산. {@code startRound} 이상인 각 회차를 그 회차 이전 데이터만으로 시뮬레이션한다.
	 * @throws IllegalArgumentException 시작 회차 이상인 회차가 없을 때
	 */
	public SimulationReport run(DrawHistory history, int startRound, int repetitions, long baseSeed) {
		List<HistoricalDraw> targets = history.draws().stream().filter(draw -> draw.round() >= startRound).toList();
		if (targets.isEmpty()) {
			throw new IllegalArgumentException(startRound + "회 이상인 회차가 없습니다.");
		}
		Map<StrategyType, Tally> tallies = new EnumMap<>(StrategyType.class);
		for (StrategyType type : StrategyType.values()) {
			tallies.put(type, new Tally());
		}
		for (HistoricalDraw target : targets) {
			NumberStatistics stats = NumberStatistics.from(history.before(target.round()));
			Map<StrategyType, List<DrawnLine>> drawn = new EnumMap<>(StrategyType.class);
			for (StrategyType type : STRATEGIES.keySet()) {
				drawn.put(type, draw(stats, target.round(), type, repetitions, baseSeed));
			}
			for (StrategyType type : StrategyType.values()) {
				List<LottoNumbers> lines = type == StrategyType.COMBINED ? combined(drawn, repetitions)
						: drawn.get(type).stream().map(DrawnLine::numbers).toList();
				Tally tally = tallies.get(type);
				for (LottoNumbers line : lines) {
					tally.add(line.countMatches(target.numbers()), PrizeRank.of(line, target.numbers(), target.bonus()));
				}
				tally.endRound();
			}
		}

		ConfidenceInterval control = tallies.get(StrategyType.CONTROL).interval();
		List<StrategySimulation> strategies = new ArrayList<>();
		for (StrategyType type : StrategyType.values()) {
			strategies.add(tallies.get(type).summarize(type, control));
		}
		return new SimulationReport(targets.get(0).round(), targets.get(targets.size() - 1).round(), repetitions,
				strategies);
	}

	/** 회차 {@code round}에서 전략이 뽑는 줄. 그 회차 이전 데이터만 본다. 근거 조합은 반복마다 0~5줄이다. */
	static List<LottoNumbers> linesFor(DrawHistory history, int round, StrategyType type, int repetitions,
			long baseSeed) {
		NumberStatistics stats = NumberStatistics.from(history.before(round));
		if (type == StrategyType.COMBINED) {
			Map<StrategyType, List<DrawnLine>> drawn = new EnumMap<>(StrategyType.class);
			for (StrategyType source : COMBINED_SOURCES) {
				drawn.put(source, draw(stats, round, source, repetitions, baseSeed));
			}
			return combined(drawn, repetitions);
		}
		return draw(stats, round, type, repetitions, baseSeed).stream().map(DrawnLine::numbers).toList();
	}

	/** 근거 조합을 제외한 전략이 뽑는 줄(근거와 재료 포함). */
	static List<DrawnLine> drawnFor(DrawHistory history, int round, StrategyType type, int repetitions,
			long baseSeed) {
		return draw(NumberStatistics.from(history.before(round)), round, type, repetitions, baseSeed);
	}

	/** 반복 i마다 다섯 전략의 i번째 줄을 재료로 근거 조합을 만든다. 실제 추천과 달리 인기 조합도 거르지 않는다. */
	private static List<LottoNumbers> combined(Map<StrategyType, List<DrawnLine>> drawn, int repetitions) {
		List<LottoNumbers> lines = new ArrayList<>();
		for (int i = 0; i < repetitions; i++) {
			int rep = i;
			List<DrawnLine> sources = COMBINED_SOURCES.stream().map(type -> drawn.get(type).get(rep)).toList();
			CombinedLines.combine(sources, Set.of(), numbers -> false)
				.forEach(line -> lines.add(line.numbers()));
		}
		return lines;
	}

	static long seedFor(long baseSeed, int round, StrategyType type) {
		return baseSeed ^ (round * 1_000_003L) ^ type.ordinal();
	}

	/**
	 * {@link #seedFor} 값을 SplitMix64 마무리 함수로 섞는다. 섞기 전 시드는 한 회차 안에서 전략 순번만큼 하위 비트만 달라
	 * {@link Random}의 첫 출력들이 서로 닮는다. 섞으면 전략별 줄이 서로 독립에 가까워지고, 같은 입력이면 결과는 같다.
	 */
	static long mixedSeedFor(long baseSeed, int round, StrategyType type) {
		long z = seedFor(baseSeed, round, type);
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}

	private static List<DrawnLine> draw(NumberStatistics stats, int round, StrategyType type, int repetitions,
			long baseSeed) {
		LineStrategy strategy = STRATEGIES.get(type);
		Random random = new Random(mixedSeedFor(baseSeed, round, type));
		List<DrawnLine> lines = new ArrayList<>(repetitions);
		for (int i = 0; i < repetitions; i++) {
			lines.add(strategy.draw(stats, RecommendationConstraint.NONE, random));
		}
		return lines;
	}

	private static final class Tally {

		private final Map<PrizeRank, Long> rankCounts = new EnumMap<>(PrizeRank.class);
		/** 회차별 적중 수. 구간 계산에만 쓰고 저장하지 않는다. */
		private final List<ConfidenceInterval.RoundCount> rounds = new ArrayList<>();
		private long lines;
		private long matches;
		private long hits;
		private long roundLines;
		private long roundHits;

		Tally() {
			for (PrizeRank rank : PrizeRank.values()) {
				rankCounts.put(rank, 0L);
			}
		}

		void add(int matchCount, PrizeRank rank) {
			lines++;
			roundLines++;
			matches += matchCount;
			rankCounts.merge(rank, 1L, Long::sum);
			if (rank != PrizeRank.NONE) {
				hits++;
				roundHits++;
			}
		}

		void endRound() {
			rounds.add(new ConfidenceInterval.RoundCount(roundHits, roundLines));
			roundHits = 0;
			roundLines = 0;
		}

		ConfidenceInterval interval() {
			return ConfidenceInterval.clustered(rounds);
		}

		StrategySimulation summarize(StrategyType type, ConfidenceInterval control) {
			ConfidenceInterval interval = interval();
			boolean differs = type != StrategyType.CONTROL && interval.differsFrom(control);
			// 근거 조합은 재료가 모자라면 줄이 없을 수 있다.
			double perLine = lines == 0 ? 0 : 1.0 / lines;
			return new StrategySimulation(type, lines, matches * perLine, rankCounts, hits * perLine, interval.low(),
					interval.high(), differs);
		}

	}

}
