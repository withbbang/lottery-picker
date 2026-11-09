package com.lotterypicker.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.engine.DrawHistory;
import com.lotterypicker.recommendation.engine.HistoricalDraw;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.simulation.SimulationReport;
import com.lotterypicker.simulation.repository.SimulationResultRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 회차로 계산 시간을 재는 수동 확인. 기본 테스트에서는 돌지 않는다. 원본 {@code .data}가 아니라 복사본을 읽기 전용으로 연다.
 * 실행: {@code SIMULATION_TIMING_DB=jdbc:h2:file:/복사본/lottery;MODE=MySQL;ACCESS_MODE_DATA=r ./gradlew test --tests '*SimulationTimingManualTest'}
 */
@EnabledIfEnvironmentVariable(named = "SIMULATION_TIMING_DB", matches = ".+")
class SimulationTimingManualTest {

	@Test
	void 실제_전체_회차를_5분_안에_계산한다() throws Exception {
		// given
		DrawHistory history = load(System.getenv("SIMULATION_TIMING_DB"));
		SimulationService service = new SimulationService(mock(LottoRoundRepository.class),
				mock(SimulationResultRepository.class), Clock.systemUTC());

		// when
		long started = System.nanoTime();
		SimulationReport report = service.run(history, SimulationService.START_ROUND, SimulationService.REPETITIONS,
				SimulationService.BASE_SEED);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

		// then
		System.out.printf("SIMULATION_TIMING rounds=%d range=%d~%d elapsed=%dms%n", history.draws().size(),
				report.fromRound(), report.toRound(), elapsed.toMillis());
		report.strategies()
			.forEach(s -> System.out.printf("SIMULATION_TIMING %s lines=%d avg=%.4f rate=%.5f ci=[%.5f, %.5f] differs=%s%n",
					s.strategy(), s.lineCount(), s.averageMatches(), s.atLeastFifthRate(), s.ciLow(), s.ciHigh(),
					s.differsFromControl()));
		assertThat(elapsed).isLessThan(Duration.ofMinutes(5));
	}

	private static DrawHistory load(String url) throws Exception {
		List<HistoricalDraw> draws = new ArrayList<>();
		try (Connection connection = DriverManager.getConnection(url, "sa", "");
				Statement statement = connection.createStatement();
				ResultSet rs = statement
					.executeQuery("select round_no, n1, n2, n3, n4, n5, n6, bonus from lotto_round order by round_no")) {
			while (rs.next()) {
				draws.add(new HistoricalDraw(rs.getInt(1), new LottoNumbers(List.of(rs.getInt(2), rs.getInt(3),
						rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7))), rs.getInt(8)));
			}
		}
		return new DrawHistory(draws);
	}

}
