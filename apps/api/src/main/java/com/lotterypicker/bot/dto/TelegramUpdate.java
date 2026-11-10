package com.lotterypicker.bot.dto;

import java.time.Instant;
import org.springframework.lang.Nullable;

/** 텔레그램에서 받은 업데이트 하나. 메시지가 아닌 업데이트는 {@code chatId}·{@code text}·{@code sentAt}이 null이다. */
public record TelegramUpdate(long updateId, @Nullable Long chatId, @Nullable String text, @Nullable Instant sentAt) {
}
