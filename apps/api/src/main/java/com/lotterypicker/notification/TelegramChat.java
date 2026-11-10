package com.lotterypicker.notification;

/** 봇이 답하고 알림을 보내는 채팅방. 운영자 방이 기본이고, 가족방은 설정했을 때만 쓴다. */
public enum TelegramChat {

	/** {@code TELEGRAM_CHAT_ID}. 모든 명령과 모든 알림. */
	OWNER,

	/** {@code TELEGRAM_FAMILY_CHAT_ID}. 설정 보기 명령과 운영 오류 알림은 받지 않는다. */
	FAMILY

}
