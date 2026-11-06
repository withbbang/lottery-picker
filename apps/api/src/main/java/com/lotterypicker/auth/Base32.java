package com.lotterypicker.auth;

import java.io.ByteArrayOutputStream;

/** RFC 4648 Base32. 패딩 없이 인코딩하고, 디코딩은 대문자 알파벳만 받는다. */
public final class Base32 {

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private Base32() {
	}

	public static String encode(byte[] bytes) {
		StringBuilder encoded = new StringBuilder();
		int buffer = 0;
		int bits = 0;
		for (byte b : bytes) {
			buffer = (buffer << 8) | (b & 0xFF);
			bits += 8;
			while (bits >= 5) {
				encoded.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
				bits -= 5;
			}
		}
		if (bits > 0) {
			encoded.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
		}
		return encoded.toString();
	}

	public static byte[] decode(String base32) {
		ByteArrayOutputStream decoded = new ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (char c : base32.toCharArray()) {
			int value = ALPHABET.indexOf(c);
			if (value < 0) {
				throw new IllegalArgumentException("Base32 형식이 아닙니다.");
			}
			buffer = (buffer << 5) | value;
			bits += 5;
			if (bits >= 8) {
				decoded.write((buffer >> (bits - 8)) & 0xFF);
				bits -= 8;
			}
		}
		return decoded.toByteArray();
	}

}
