package com.lotterypicker.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ConfidenceIntervalTest {

	@Test
	void 신뢰구간은_정규근사이다() {
		// given
		long hits = 50;
		long trials = 1_000;
		double p = 0.05;
		double margin = 1.96 * Math.sqrt(p * (1 - p) / trials);

		// when
		ConfidenceInterval interval = ConfidenceInterval.normal(hits, trials);

		// then: p ± 1.96√(p(1-p)/n)
		assertThat(interval.low()).isCloseTo(p - margin, within(1e-15));
		assertThat(interval.high()).isCloseTo(p + margin, within(1e-15));
	}

	@Test
	void 줄이_없으면_구간은_0이다() {
		// when
		ConfidenceInterval interval = ConfidenceInterval.normal(0, 0);

		// then
		assertThat(interval.low()).isZero();
		assertThat(interval.high()).isZero();
	}

	@Test
	void 구간이_겹치면_유의한_차이가_없다() {
		// given
		ConfidenceInterval control = new ConfidenceInterval(0.02, 0.03);

		// when / then: 겹치면 false, 안 겹치면 true, 경계가 닿으면 겹침으로 본다
		assertThat(control.differsFrom(new ConfidenceInterval(0.025, 0.035))).isFalse();
		assertThat(control.differsFrom(new ConfidenceInterval(0.031, 0.04))).isTrue();
		assertThat(control.differsFrom(new ConfidenceInterval(0.005, 0.019))).isTrue();
		assertThat(control.differsFrom(new ConfidenceInterval(0.03, 0.04))).isFalse();
		assertThat(control.differsFrom(new ConfidenceInterval(0.01, 0.02))).isFalse();
	}

}
