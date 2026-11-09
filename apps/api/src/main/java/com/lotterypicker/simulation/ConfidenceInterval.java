package com.lotterypicker.simulation;

/** 비율의 95% 신뢰구간. 정규근사라 비율이 0이나 1에 가까우면 구간이 0~1 밖으로 나갈 수 있으며 자르지 않는다. */
public record ConfidenceInterval(double low, double high) {

	private static final double Z_95 = 1.96;

	/** {@code p ± 1.96√(p(1-p)/n)}. 시행이 없으면 0~0. */
	public static ConfidenceInterval normal(long successes, long trials) {
		if (trials == 0) {
			return new ConfidenceInterval(0, 0);
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
