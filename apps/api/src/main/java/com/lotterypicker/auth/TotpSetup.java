package com.lotterypicker.auth;

import java.security.SecureRandom;

/** 첫 등록용 일회성 명령. 시크릿과 OTP 앱 등록 URI를 표준 출력으로만 내보낸다. */
public final class TotpSetup {

	private static final int SECRET_BYTES = 20;

	private TotpSetup() {
	}

	public static void main(String[] args) {
		byte[] bytes = new byte[SECRET_BYTES];
		new SecureRandom().nextBytes(bytes);
		String secret = Base32.encode(bytes);
		System.out.println("TOTP_SECRET=" + secret);
		System.out.println("otpauth://totp/lottery-picker?secret=" + secret + "&issuer=lottery-picker");
	}

}
