package com.lotterypicker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Base32Test {

	private static final byte[] RFC_BYTES = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
	private static final String RFC_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

	@Test
	void RFC_시크릿을_인코딩한다() {
		// when
		String encoded = Base32.encode(RFC_BYTES);

		// then
		assertThat(encoded).isEqualTo(RFC_BASE32);
	}

	@Test
	void RFC_시크릿을_디코딩한다() {
		// when
		byte[] decoded = Base32.decode(RFC_BASE32);

		// then
		assertThat(decoded).isEqualTo(RFC_BYTES);
	}

	@Test
	void 잘못된_문자는_값을_드러내지_않고_거부한다() {
		// when, then
		assertThatThrownBy(() -> Base32.decode("SECRET1!"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageNotContaining("SECRET1!");
	}

}
