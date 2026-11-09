package com.lotterypicker.notification.entity;

import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알림 한 건의 발송 기록. 상태 변경은 동시 실행을 막기 위해 리포지토리의 조건부 갱신으로만 한다. */
@Getter
@Entity
@Table(name = "notification_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private NotificationKind kind;

	@Column(name = "target_key")
	private String targetKey;

	@Enumerated(EnumType.STRING)
	private NotificationStatus status;

	private int attempts;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public static NotificationLog pending(NotificationKind kind, String targetKey, LocalDateTime now) {
		NotificationLog log = new NotificationLog();
		log.kind = kind;
		log.targetKey = targetKey;
		log.status = NotificationStatus.PENDING;
		log.attempts = 0;
		log.updatedAt = now;
		return log;
	}

}
