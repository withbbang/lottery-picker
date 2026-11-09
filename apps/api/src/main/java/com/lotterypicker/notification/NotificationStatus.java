package com.lotterypicker.notification;

/** SKIPPED는 기록하지 않는다. 설정이 비어 있었거나 아직 보낸 적 없는 알림의 상태다. */
public enum NotificationStatus {
	PENDING, SENT, FAILED, SKIPPED
}
