package com.lotterypicker.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP (SHA-1, 6자리, 30초). */
public final class TotpVerifier {

	private static final int STEP_SECONDS = 30;
	private static final int ALLOWED_DRIFT_STEPS = 1;
	private static final int DIGITS_MODULUS = 1_000_000;

	private TotpVerifier() {
	}

	/** 코드가 일치한 30초 구간 번호를 돌려준다. 앞뒤 1구간까지 허용하고, 없으면 -1이다. */
	public static long matchStep(String base32Secret, String code, long epochSeconds) {
		long currentStep = epochSeconds / STEP_SECONDS;
		byte[] expected = code.getBytes(StandardCharsets.US_ASCII);
		long matched = -1;
		for (long step = currentStep - ALLOWED_DRIFT_STEPS; step <= currentStep + ALLOWED_DRIFT_STEPS; step++) {
			byte[] candidate = codeAt(base32Secret, step).getBytes(StandardCharsets.US_ASCII);
			if (MessageDigest.isEqual(candidate, expected)) {
				matched = step;
			}
		}
		return matched;
	}

	public static String codeAt(String base32Secret, long step) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(Base32.decode(base32Secret), "HmacSHA1"));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
			int offset = hash[hash.length - 1] & 0x0F;
			int truncated = ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & 0x7FFFFFFF;
			return "%06d".formatted(truncated % DIGITS_MODULUS);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("TOTP 계산에 실패했습니다.");
		}
	}

}
