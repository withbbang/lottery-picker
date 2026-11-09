package com.lotterypicker.simulation;

import java.util.List;

/**
 * 비율의 95% 신뢰구간. 시뮬레이션은 같은 회차의 줄을 같은 당첨번호로 함께 채점하므로, 줄을 서로 독립으로 보지 않고
 * 회차를 묶음(군집)으로 보는 {@link #clustered}를 쓴다. 0~1 밖으로 나가도 자르지 않는다.
 */
public record ConfidenceInterval(double low, double high) {

	private static final double Z_95 = 1.96;
	private static final ConfidenceInterval NONE = new ConfidenceInterval(0, 0);

	/** 회차 하나의 5등 이상 줄 수와 줄 수. */
	public record RoundCount(long hits, long lines) {
	}

	/**
	 * 회차 단위 구간. 회차 r의 적중 h_r, 줄 수 m_r, 회차 수 R일 때 {@code p = Σh/Σm},
	 * {@code Var(p) = R/(R-1) · Σ(h_r − p·m_r)² / (Σm)²}(비율 추정량의 군집 강건 분산), 구간 {@code p ± 1.96√Var}.
	 * 회차가 2개 미만이면 분산을 추정할 수 없어 {@link #normal}을 쓴다. 줄이 없으면 0~0.
	 */
	public static ConfidenceInterval clustered(List<RoundCount> rounds) {
		long hits = 0;
		long lines = 0;
		for (RoundCount round : rounds) {
			hits += round.hits();
			lines += round.lines();
		}
		if (lines == 0) {
			return NONE;
		}
		int r = rounds.size();
		if (r < 2) {
			return normal(hits, lines);
		}
		double p = (double) hits / lines;
		double squares = 0;
		for (RoundCount round : rounds) {
			double residual = round.hits() - p * round.lines();
			squares += residual * residual;
		}
		double variance = (double) r / (r - 1) * squares / ((double) lines * lines);
		double margin = Z_95 * Math.sqrt(variance);
		return new ConfidenceInterval(p - margin, p + margin);
	}

	/** 줄 단위 이항 근사 {@code p ± 1.96√(p(1-p)/n)}. 회차가 하나뿐일 때 쓴다. 시행이 없으면 0~0. */
	public static ConfidenceInterval normal(long successes, long trials) {
		if (trials == 0) {
			return NONE;
		}
		double p = (double) successes / trials;
		double margin = Z_95 * Math.sqrt(p * (1 - p) / trials);
		return new ConfidenceInterval(p - margin, p + margin);
	}

	/** 두 구간이 겹치지 않으면 true. 경계가 맞닿으면 겹침으로 본다. */
	public boolean differsFrom(ConfidenceInterval other) {
		return high < other.low || other.high < low;
	}

}
