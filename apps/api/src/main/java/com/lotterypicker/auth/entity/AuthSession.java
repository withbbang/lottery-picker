package com.lotterypicker.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "auth_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

	@Id
	@Column(name = "token_hash")
	private String tokenHash;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	public AuthSession(String tokenHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isActiveAt(LocalDateTime now) {
		return now.isBefore(expiresAt);
	}

}
