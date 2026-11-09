package com.lotterypicker.simulation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.simulation.SimulationReport;
import com.lotterypicker.simulation.StrategySimulation;
import com.lotterypicker.simulation.entity.SimulationResult;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SimulationResultRepositoryTest {

	@Autowired
	private SimulationResultRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void 다시_저장하면_한_행을_덮어쓴다() {
		// given
		repository.saveAndFlush(SimulationResult.latest(report(101, 1200), 1, LocalDateTime.parse("2026-11-08T10:00")));
		entityManager.clear();

		// when
		repository.saveAndFlush(SimulationResult.latest(report(101, 1249), 2, LocalDateTime.parse("2026-11-09T10:00")));
		entityManager.clear();

		// then
		assertThat(repository.count()).isEqualTo(1);
		SimulationResult stored = repository.findById(SimulationResult.SINGLE_ROW_ID).orElseThrow();
		assertThat(stored.getToRound()).isEqualTo(1249);
		assertThat(stored.getComputedAt()).isEqualTo(LocalDateTime.parse("2026-11-09T10:00"));
		assertThat(stored.getMethodVersion()).isEqualTo(2);
	}

	@Test
	void 전략별_결과를_JSON으로_저장하고_그대로_읽는다() {
		// given
		SimulationReport report = report(101, 1249);

		// when
		repository.saveAndFlush(SimulationResult.latest(report, 2, LocalDateTime.parse("2026-11-09T10:00")));
		entityManager.clear();

		// then
		SimulationResult stored = repository.findById(SimulationResult.SINGLE_ROW_ID).orElseThrow();
		assertThat(stored.getStrategies()).isEqualTo(report.strategies());
		assertThat(stored.getRepetitions()).isEqualTo(200);
	}

	@Test
	void 계산_방식_버전이_없던_행은_옛_버전으로_읽는다() {
		// given: V8 이전에 저장된 행처럼 버전 없이 넣는다
		entityManager.getEntityManager()
			.createNativeQuery("insert into simulation_result (id, from_round, to_round, repetitions, strategies, computed_at)"
					+ " values (1, 101, 1249, 200, '[]', TIMESTAMP '2026-11-08 10:00:00')")
			.executeUpdate();
		entityManager.clear();

		// when
		SimulationResult stored = repository.findById(SimulationResult.SINGLE_ROW_ID).orElseThrow();

		// then
		assertThat(stored.getMethodVersion()).isEqualTo(1);
	}

	private static SimulationReport report(int from, int to) {
		Map<PrizeRank, Long> ranks = new EnumMap<>(PrizeRank.class);
		for (PrizeRank rank : PrizeRank.values()) {
			ranks.put(rank, 0L);
		}
		ranks.put(PrizeRank.FIFTH, 24L);
		ranks.put(PrizeRank.NONE, 976L);
		return new SimulationReport(from, to, 200, List.of(new StrategySimulation(StrategyType.CONTROL, 1000, 0.8,
				ranks, 0.024, 0.0145, 0.0335, false)));
	}

}
