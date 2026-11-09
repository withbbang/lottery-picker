package com.lotterypicker.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfidenceIntervalTest {

	@Test
	void 신뢰구간은_회차_단위_분산으로_계산한다() {
		// given: 회차 3개, 회차마다 200줄, 5등 이상 10·0·2줄
		List<ConfidenceInterval.RoundCount> rounds = List.of(round(10, 200), round(0, 200), round(2, 200));

		// when
		ConfidenceInterval interval = ConfidenceInterval.clustered(rounds);

		// then: 손 계산
		// p = 12 / 600 = 0.02, p·m = 4
		// Σ(h − p·m)² = 6² + (−4)² + (−2)² = 36 + 16 + 4 = 56
		// Var = 3/2 · 56 / 600² = 84 / 360000 = 0.000233333…
		// √Var = 0.0152752523…, 1.96·√Var = 0.0299394945…
		assertThat(interval.low()).isCloseTo(-0.0099394945403782, within(1e-12));
		assertThat(interval.high()).isCloseTo(0.0499394945403782, within(1e-12));
	}

	@Test
	void 회차마다_적중이_고르면_이항_근사와_가깝다() {
		// given: 회차마다 4줄씩, p = 12 / 600 = 0.02
		List<ConfidenceInterval.RoundCount> rounds = List.of(round(4, 200), round(4, 200), round(4, 200));

		// when
		ConfidenceInterval clustered = ConfidenceInterval.clustered(rounds);
		ConfidenceInterval binomial = ConfidenceInterval.normal(12, 600);

		// then: 모든 회차가 p·m = 4와 같아 Σ(h − p·m)² = 0, 구간은 0.02 한 점이고 이항 근사(±0.0112)보다 좁다
		assertThat(clustered.low()).isCloseTo(0.02, within(1e-15));
		assertThat(clustered.high()).isCloseTo(0.02, within(1e-15));
		assertThat(clustered.high() - clustered.low()).isLessThanOrEqualTo(binomial.high() - binomial.low());
	}

	@Test
	void 적중이_한_회차에_몰리면_구간이_넓어진다() {
		// given: 같은 p = 12 / 600 = 0.02인데 한 회차에 12줄이 몰렸다
		List<ConfidenceInterval.RoundCount> rounds = List.of(round(12, 200), round(0, 200), round(0, 200));

		// when
		ConfidenceInterval clustered = ConfidenceInterval.clustered(rounds);

		// then: 손 계산
		// Σ(h − p·m)² = 8² + (−4)² + (−4)² = 96, Var = 3/2 · 96 / 600² = 0.0004, 1.96·√Var = 1.96 · 0.02 = 0.0392
		// 이항 근사는 1.96·√(0.02·0.98/600) = 0.0112023…
		assertThat(clustered.low()).isCloseTo(0.02 - 0.0392, within(1e-12));
		assertThat(clustered.high()).isCloseTo(0.02 + 0.0392, within(1e-12));
		assertThat(clustered.high() - clustered.low()).isGreaterThan(2 * 0.0112023330903284);
	}

	@Test
	void 회차가_하나면_이항_근사를_쓴다() {
		// given: 200줄 중 10줄
		List<ConfidenceInterval.RoundCount> rounds = List.of(round(10, 200));

		// when
		ConfidenceInterval interval = ConfidenceInterval.clustered(rounds);

		// then: p = 0.05, 1.96·√(0.05·0.95/200) = 1.96·√0.0002375 = 1.96·0.0154110… = 0.0302056…
		assertThat(interval.low()).isCloseTo(0.05 - 0.030205628614548, within(1e-12));
		assertThat(interval.high()).isCloseTo(0.05 + 0.030205628614548, within(1e-12));
	}

	@Test
	void 줄이_없으면_0이다() {
		// when
		ConfidenceInterval none = ConfidenceInterval.clustered(List.of());
		ConfidenceInterval empty = ConfidenceInterval.normal(0, 0);

		// then
		assertThat(none).isEqualTo(new ConfidenceInterval(0, 0));
		assertThat(empty).isEqualTo(new ConfidenceInterval(0, 0));
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

	private static ConfidenceInterval.RoundCount round(long hits, long lines) {
		return new ConfidenceInterval.RoundCount(hits, lines);
	}

}
