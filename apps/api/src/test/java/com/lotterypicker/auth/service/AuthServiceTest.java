package com.lotterypicker.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.auth.TotpVerifier;
import com.lotterypicker.auth.entity.AuthSession;
import com.lotterypicker.auth.entity.AuthState;
import com.lotterypicker.auth.repository.AuthSessionRepository;
import com.lotterypicker.auth.repository.AuthStateRepository;
import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

	private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
	private static final String OTHER_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T04:00:00Z"));
	private final AuthSessionRepository sessionRepository = mock(AuthSessionRepository.class);
	private final AuthStateRepository stateRepository = mock(AuthStateRepository.class);
	private final Map<String, AuthSession> sessions = new HashMap<>();
	private final AuthState state = AuthState.initial();

	private AuthService service;

	@BeforeEach
	void setUp() {
		when(stateRepository.findForUpdate()).thenReturn(Optional.of(state));
		when(sessionRepository.save(any())).thenAnswer(call -> {
			AuthSession session = call.getArgument(0);
			sessions.put(session.getTokenHash(), session);
			return session;
		});
		when(sessionRepository.findById(anyString()))
				.thenAnswer(call -> Optional.ofNullable(sessions.get(call.<String>getArgument(0))));
		doAnswer(call -> sessions.remove(call.<String>getArgument(0)))
				.when(sessionRepository).deleteById(anyString());
		service = serviceWith(SECRET);
	}

	@Test
	void 올바른_코드는_세션을_만들고_해시만_저장한다() {
		// when
		String token = service.login(currentCode());

		// then
		assertThat(sessions).hasSize(1);
		String storedHash = sessions.keySet().iterator().next();
		assertThat(storedHash).isNotEqualTo(token).isEqualTo(sha256(token));
	}

	@Test
	void 틀린_코드는_AUTH_INVALID_CODE이다() {
		// when, then
		assertLoginFails(wrongCode(), ErrorCode.AUTH_INVALID_CODE);
	}

	@Test
	void 연속_5회_실패하면_AUTH_LOCKED이다() {
		// given
		String wrong = wrongCode();
		for (int i = 0; i < 4; i++) {
			assertLoginFails(wrong, ErrorCode.AUTH_INVALID_CODE);
		}

		// when, then
		assertLoginFails(wrong, ErrorCode.AUTH_LOCKED);
	}

	@Test
	void 잠금_중에는_올바른_코드도_거부한다() {
		// given
		lockOut();

		// when, then
		assertLoginFails(currentCode(), ErrorCode.AUTH_LOCKED);
		assertThat(sessions).isEmpty();
	}

	@Test
	void 잠금_15분_후에는_다시_시도할_수_있다() {
		// given
		lockOut();

		// when, then
		clock.advance(Duration.ofMinutes(14).plusSeconds(59));
		assertLoginFails(currentCode(), ErrorCode.AUTH_LOCKED);

		clock.advance(Duration.ofSeconds(1));
		assertThat(service.login(currentCode())).isNotBlank();
	}

	@Test
	void 성공하면_실패_횟수를_초기화한다() {
		// given
		String wrong = wrongCode();
		for (int i = 0; i < 4; i++) {
			assertLoginFails(wrong, ErrorCode.AUTH_INVALID_CODE);
		}

		// when
		service.login(currentCode());

		// then
		assertThat(state.getFailedCount()).isZero();
	}

	@Test
	void 이미_사용한_코드는_거부한다() {
		// given
		String code = currentCode();
		service.login(code);

		// when, then
		assertLoginFails(code, ErrorCode.AUTH_INVALID_CODE);
	}

	@Test
	void 사용한_구간보다_이전_구간의_코드도_거부한다() {
		// given
		long step = clock.instant().getEpochSecond() / 30;
		service.login(TotpVerifier.codeAt(SECRET, step));

		// when, then
		assertLoginFails(TotpVerifier.codeAt(SECRET, step - 1), ErrorCode.AUTH_INVALID_CODE);
	}

	@Test
	void 만든지_30일이_지난_세션은_무효이다() {
		// given
		String token = service.login(currentCode());

		// when, then
		clock.advance(Duration.ofDays(30).minusMinutes(1));
		assertThat(service.isValid(token)).isTrue();

		clock.advance(Duration.ofMinutes(1));
		assertThat(service.isValid(token)).isFalse();
	}

	@Test
	void 없는_토큰은_무효이다() {
		// when, then
		assertThat(service.isValid("unknown")).isFalse();
		assertThat(service.isValid(null)).isFalse();
	}

	@Test
	void 로그아웃한_세션은_무효이다() {
		// given
		String token = service.login(currentCode());

		// when
		service.logout(token);

		// then
		assertThat(service.isValid(token)).isFalse();
	}

	@Test
	void 시크릿이_바뀌면_기존_세션을_모두_폐기한다() {
		// given
		service.revokeSessionsIfSecretChanged();

		// when
		serviceWith(OTHER_SECRET).revokeSessionsIfSecretChanged();

		// then
		verify(sessionRepository, times(2)).deleteAllInBatch();
		assertThat(state.getSecretFingerprint()).isEqualTo(sha256(OTHER_SECRET));
	}

	@Test
	void 시크릿이_같으면_세션을_유지한다() {
		// given
		state.replaceSecret(sha256(SECRET));

		// when
		service.revokeSessionsIfSecretChanged();

		// then
		verify(sessionRepository, never()).deleteAllInBatch();
	}

	@Test
	void 시크릿이_바뀌면_잠금과_사용_기록을_초기화한다() {
		// given
		service.login(currentCode());
		lockOut();

		// when
		serviceWith(OTHER_SECRET).revokeSessionsIfSecretChanged();

		// then
		assertThat(state.getFailedCount()).isZero();
		assertThat(state.getLockedUntil()).isNull();
		assertThat(state.getLastUsedStep()).isEqualTo(-1);
	}

	@Test
	void 시크릿이_비어_있으면_생성에_실패한다() {
		// when, then
		assertThatThrownBy(() -> serviceWith(" "))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TOTP_SECRET");
	}

	@Test
	void 시크릿_형식이_틀리면_값을_드러내지_않고_생성에_실패한다() {
		// when, then
		assertThatThrownBy(() -> serviceWith("not-base32!"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TOTP_SECRET")
				.hasMessageNotContaining("not-base32!");
	}

	private AuthService serviceWith(String secret) {
		return new AuthService(sessionRepository, stateRepository, clock, new Random(1), secret);
	}

	private void lockOut() {
		String wrong = wrongCode();
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> service.login(wrong)).isInstanceOf(BusinessException.class);
		}
	}

	private void assertLoginFails(String code, ErrorCode expected) {
		assertThatThrownBy(() -> service.login(code))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}

	private String currentCode() {
		return TotpVerifier.codeAt(SECRET, clock.instant().getEpochSecond() / 30);
	}

	private String wrongCode() {
		long now = clock.instant().getEpochSecond();
		for (int candidate = 0; candidate < 1_000_000; candidate++) {
			String code = "%06d".formatted(candidate);
			if (TotpVerifier.matchStep(SECRET, code, now) == -1) {
				return code;
			}
		}
		throw new IllegalStateException("틀린 코드를 찾지 못했습니다.");
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		private MutableClock(Instant now) {
			this.now = now;
		}

		private void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("Asia/Seoul");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
