package com.lotterypicker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TotpVerifierTest {

	/** RFC 6238 SHA-1 시크릿 12345678901234567890의 Base32 */
	private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

	@Test
	void 시각_59초의_코드는_287082이다() {
		// when
		long step = TotpVerifier.matchStep(SECRET, "287082", 59);

		// then
		assertThat(step).isEqualTo(1);
	}

	@Test
	void 시각_1111111109초의_코드는_081804이다() {
		// when
		long step = TotpVerifier.matchStep(SECRET, "081804", 1111111109L);

		// then
		assertThat(step).isEqualTo(37037036L);
	}

	@Test
	void 앞뒤_30초_구간까지_허용한다() {
		// given
		long codeTime = 1111111109L;

		// when
		long before = TotpVerifier.matchStep(SECRET, "081804", codeTime - 30);
		long after = TotpVerifier.matchStep(SECRET, "081804", codeTime + 30);

		// then
		assertThat(before).isEqualTo(37037036L);
		assertThat(after).isEqualTo(37037036L);
	}

	@Test
	void 시각_60초_벗어나면_거부한다() {
		// given
		long codeTime = 1111111109L;

		// when
		long before = TotpVerifier.matchStep(SECRET, "081804", codeTime - 60);
		long after = TotpVerifier.matchStep(SECRET, "081804", codeTime + 60);

		// then
		assertThat(before).isEqualTo(-1);
		assertThat(after).isEqualTo(-1);
	}

	@Test
	void 구간_번호로_코드를_만들면_앞자리_0을_유지한다() {
		// when
		String code = TotpVerifier.codeAt(SECRET, 37037036L);

		// then
		assertThat(code).isEqualTo("081804");
	}

}
