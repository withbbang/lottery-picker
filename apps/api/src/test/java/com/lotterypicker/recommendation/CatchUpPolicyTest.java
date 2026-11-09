package com.lotterypicker.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class CatchUpPolicyTest {

	private static ZonedDateTime at(String isoWithZone) {
		return ZonedDateTime.parse(isoWithZone);
	}

	@Test
	void _16시59분은_false() {
		// when
		boolean result = CatchUpPolicy.shouldGenerate(at("2026-11-06T16:59:00+09:00[Asia/Seoul]"), false);

		// then
		assertThat(result).isFalse();
	}

	@Test
	void _17시는_true() {
		// when
		boolean result = CatchUpPolicy.shouldGenerate(at("2026-11-06T17:00:00+09:00[Asia/Seoul]"), false);

		// then
		assertThat(result).isTrue();
	}

	@Test
	void 이미_있으면_false() {
		// when
		boolean result = CatchUpPolicy.shouldGenerate(at("2026-11-06T18:00:00+09:00[Asia/Seoul]"), true);

		// then
		assertThat(result).isFalse();
	}

	@Test
	void 토요일_19시59분은_true() {
		// when
		boolean result = CatchUpPolicy.shouldGenerate(at("2026-11-07T19:59:00+09:00[Asia/Seoul]"), false);

		// then
		assertThat(result).isTrue();
	}

	@Test
	void 토요일_20시는_false() {
		// when
		boolean result = CatchUpPolicy.shouldGenerate(at("2026-11-07T20:00:00+09:00[Asia/Seoul]"), false);

		// then
		assertThat(result).isFalse();
	}

	@Test
	void 일요일_20시는_true() {
		// when
		boolean result = CatchUpPolicy.shouldGenerate(at("2026-11-08T20:00:00+09:00[Asia/Seoul]"), false);

		// then
		assertThat(result).isTrue();
	}

	@Test
	void UTC_시각으로_받아도_한국_시각으로_판단한다() {
		// when, then: 08:00Z는 한국 17:00, 11:00Z 토요일은 한국 20:00이다
		assertThat(CatchUpPolicy.shouldGenerate(at("2026-11-06T07:59:00Z"), false)).isFalse();
		assertThat(CatchUpPolicy.shouldGenerate(at("2026-11-06T08:00:00Z"), false)).isTrue();
		assertThat(CatchUpPolicy.shouldGenerate(at("2026-11-07T11:00:00Z"), false)).isFalse();
	}

	@Test
	void 토요일_20시부터_판매_마감이다() {
		// when, then
		assertThat(CatchUpPolicy.salesOpen(at("2026-11-07T19:59:00+09:00[Asia/Seoul]"))).isTrue();
		assertThat(CatchUpPolicy.salesOpen(at("2026-11-07T20:00:00+09:00[Asia/Seoul]"))).isFalse();
		assertThat(CatchUpPolicy.salesOpen(at("2026-11-08T20:00:00+09:00[Asia/Seoul]"))).isTrue();
	}

}
