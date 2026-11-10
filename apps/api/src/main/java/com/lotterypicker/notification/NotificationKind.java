package com.lotterypicker.notification;

public enum NotificationKind {

	DAILY_RECOMMENDATION(true),
	RECOMMENDATION_FAILED(false),
	DRAW_FINISHED(true),
	COLLECTION_FAILED(false),
	COLLECTION_ERROR(false);

	private final boolean sharedWithFamily;

	NotificationKind(boolean sharedWithFamily) {
		this.sharedWithFamily = sharedWithFamily;
	}

	/** 가족방에도 보내는 알림인지. 운영 오류 알림은 운영자 방에만 보낸다. */
	public boolean sharedWithFamily() {
		return sharedWithFamily;
	}

}
