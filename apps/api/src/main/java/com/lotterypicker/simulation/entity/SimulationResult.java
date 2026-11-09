package com.lotterypicker.simulation.entity;

import com.lotterypicker.simulation.SimulationReport;
import com.lotterypicker.simulation.StrategySimulation;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마지막 시뮬레이션 결과. 항상 같은 id로 저장해 한 행만 남긴다. 시각은 Asia/Seoul 기준이다.
 * {@code methodVersion}은 신뢰구간 계산 방식 버전이며 코드의 버전과 다르면 다시 계산한다.
 */
@Getter
@Entity
@Table(name = "simulation_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationResult {

	public static final int SINGLE_ROW_ID = 1;

	@Id
	private Integer id;

	@Column(name = "from_round")
	private int fromRound;

	@Column(name = "to_round")
	private int toRound;

	private int repetitions;

	@Convert(converter = StrategySimulationsConverter.class)
	@Column(columnDefinition = "TEXT")
	private List<StrategySimulation> strategies;

	@Column(name = "method_version")
	private int methodVersion;

	@Column(name = "computed_at")
	private LocalDateTime computedAt;

	public static SimulationResult latest(SimulationReport report, int methodVersion, LocalDateTime computedAt) {
		SimulationResult result = new SimulationResult();
		result.id = SINGLE_ROW_ID;
		result.fromRound = report.fromRound();
		result.toRound = report.toRound();
		result.repetitions = report.repetitions();
		result.strategies = report.strategies();
		result.methodVersion = methodVersion;
		result.computedAt = computedAt;
		return result;
	}

}
