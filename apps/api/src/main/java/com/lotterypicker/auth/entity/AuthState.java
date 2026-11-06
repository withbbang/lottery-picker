package com.lotterypicker.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 시도 상태. 사용자가 한 명이라 행도 하나이며 V2 마이그레이션이 만든다. */
@Getter
@Entity
@Table(name = "auth_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthState {

	private static final int SINGLE_ROW_ID = 1;
	private static final int MAX_FAILURES = 5;
	private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
	private static final long NO_STEP = -1;

	@Id
	private Integer id;

	@Column(name = "failed_count")
	private int failedCount;

	@Column(name = "locked_until")
	private LocalDateTime lockedUntil;

	@Column(name = "last_used_step")
	private long lastUsedStep;

	@Column(name = "secret_fingerprint")
	private String secretFingerprint;

	public static AuthState initial() {
		AuthState state = new AuthState();
		state.id = SINGLE_ROW_ID;
		state.lastUsedStep = NO_STEP;
		return state;
	}

	public boolean isLockedAt(LocalDateTime now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	public boolean isStepAlreadyUsed(long step) {
		return step <= lastUsedStep;
	}

	public void recordFailure(LocalDateTime now) {
		failedCount++;
		if (failedCount >= MAX_FAILURES) {
			failedCount = 0;
			lockedUntil = now.plus(LOCK_DURATION);
		}
	}

	public void recordSuccess(long step) {
		failedCount = 0;
		lockedUntil = null;
		lastUsedStep = step;
	}

	public void replaceSecret(String fingerprint) {
		failedCount = 0;
		lockedUntil = null;
		lastUsedStep = NO_STEP;
		secretFingerprint = fingerprint;
	}

}
