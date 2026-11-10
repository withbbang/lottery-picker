package com.lotterypicker.bot.dto;

/** 텔레그램 `/` 메뉴에 등록하는 명령 하나. {@code command}는 앞의 `/` 없이 쓴다. */
public record BotMenuItem(String command, String description) {
}
