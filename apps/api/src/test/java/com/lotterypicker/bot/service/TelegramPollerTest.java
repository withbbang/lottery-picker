package com.lotterypicker.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.bot.dto.TelegramUpdate;
import com.lotterypicker.notification.TelegramClient;
import com.lotterypicker.notification.TelegramException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramPollerTest {

	private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

	private final TelegramClient telegram = mock(TelegramClient.class);
	private final BotCommandService commands = mock(BotCommandService.class);
	private final TelegramPoller poller = new TelegramPoller(telegram, commands,
			Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

	@BeforeEach
	void setUp() {
		when(telegram.isConfigured()).thenReturn(true);
		when(telegram.isAllowedChat(42)).thenReturn(true);
		when(commands.reply(anyString())).thenReturn("응답");
	}

	private static TelegramUpdate update(long id, long chat, Instant sentAt) {
		return new TelegramUpdate(id, chat, "/today", sentAt);
	}

	@Test
	void 허용된_채팅의_명령에_답한다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(update(7, 42, NOW)));

		// when
		poller.pollOnce();

		// then
		verify(commands).reply("/today");
		verify(telegram).send("응답");
	}

	@Test
	void 다음_폴링은_마지막_업데이트_다음부터다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(update(7, 42, NOW), update(9, 42, NOW)));

		// when
		poller.pollOnce();
		poller.pollOnce();

		// then
		verify(telegram).getUpdates(10, 20);
	}

	@Test
	void 다른_채팅은_답하지_않고_넘긴다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(update(7, 43, NOW)));

		// when
		poller.pollOnce();
		poller.pollOnce();

		// then
		verify(commands, never()).reply(anyString());
		verify(telegram, never()).send(anyString());
		verify(telegram).getUpdates(8, 20);
	}

	@Test
	void 십분_넘은_명령은_답하지_않는다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(update(7, 42, NOW.minus(Duration.ofMinutes(11)))));
		when(telegram.getUpdates(8, 20)).thenReturn(List.of(update(8, 42, NOW.minus(Duration.ofMinutes(9)))));

		// when
		poller.pollOnce();
		verify(telegram, never()).send(anyString());
		poller.pollOnce();

		// then
		verify(telegram).send("응답");
	}

	@Test
	void 본문_없는_업데이트는_건너뛴다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(new TelegramUpdate(7, null, null, null)));

		// when
		poller.pollOnce();

		// then
		verify(commands, never()).reply(anyString());
		verify(telegram, never()).send(anyString());
	}

	@Test
	void 보내기_실패해도_다음_업데이트를_처리한다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(update(7, 42, NOW),
				new TelegramUpdate(8, 42L, "/help", NOW)));
		when(commands.reply("/help")).thenReturn("도움말");
		doThrow(new TelegramException("실패")).when(telegram).send("응답");

		// when
		poller.pollOnce();

		// then
		verify(telegram).send("도움말");
	}

	@Test
	void 설정이_없으면_시작하지_않는다() {
		// given
		when(telegram.isConfigured()).thenReturn(false);

		// when
		poller.start();

		// then
		assertThat(poller.isRunning()).isFalse();
		verify(telegram, never()).getUpdates(anyLong(), anyInt());
		verify(telegram, never()).setMyCommands(any());
	}

}
