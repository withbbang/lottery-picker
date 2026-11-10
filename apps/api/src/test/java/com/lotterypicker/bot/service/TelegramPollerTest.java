package com.lotterypicker.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.bot.BotCommand;
import com.lotterypicker.bot.dto.TelegramUpdate;
import com.lotterypicker.notification.TelegramChat;
import com.lotterypicker.notification.TelegramClient;
import com.lotterypicker.notification.TelegramException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
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
		when(telegram.chatOf(42)).thenReturn(Optional.of(TelegramChat.OWNER));
		when(telegram.chatOf(-1007)).thenReturn(Optional.of(TelegramChat.FAMILY));
		when(commands.reply(anyString(), any())).thenReturn("응답");
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
		verify(commands).reply("/today", TelegramChat.OWNER);
		verify(telegram).send(TelegramChat.OWNER, "응답");
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
		verify(commands, never()).reply(anyString(), any());
		verify(telegram, never()).send(any(), anyString());
		verify(telegram).getUpdates(8, 20);
	}

	@Test
	void 십분_넘은_명령은_답하지_않는다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(update(7, 42, NOW.minus(Duration.ofMinutes(11)))));
		when(telegram.getUpdates(8, 20)).thenReturn(List.of(update(8, 42, NOW.minus(Duration.ofMinutes(9)))));

		// when
		poller.pollOnce();
		verify(telegram, never()).send(any(), anyString());
		poller.pollOnce();

		// then
		verify(telegram).send(TelegramChat.OWNER, "응답");
	}

	@Test
	void 본문_없는_업데이트는_건너뛴다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(new TelegramUpdate(7, null, null, null)));

		// when
		poller.pollOnce();

		// then
		verify(commands, never()).reply(anyString(), any());
		verify(telegram, never()).send(any(), anyString());
	}

	@Test
	void 보내기_실패해도_다음_업데이트를_처리한다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(update(7, 42, NOW),
				new TelegramUpdate(8, 42L, "/help", NOW)));
		when(commands.reply("/help", TelegramChat.OWNER)).thenReturn("도움말");
		doThrow(new TelegramException("실패")).when(telegram).send(TelegramChat.OWNER, "응답");

		// when
		poller.pollOnce();

		// then
		verify(telegram).send(TelegramChat.OWNER, "도움말");
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

	@Test
	void 메뉴_등록이_느려도_앱_시작을_막지_않는다() {
		// given: 텔레그램이 메뉴 등록 요청에 응답하지 않는다
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(invocation -> {
			release.await();
			return null;
		}).when(telegram).setMyCommands(any());

		// when
		assertTimeoutPreemptively(Duration.ofSeconds(2), poller::start);

		// then: 메뉴 등록은 폴링 스레드에서 한다
		verify(telegram, timeout(1000)).setMyCommands(any());
		assertThat(poller.isRunning()).isTrue();
		release.countDown();
		poller.stop();
	}

	@Test
	void 가족방_명령에는_가족방_기준으로_가족방에_답한다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(update(7, -1007, NOW)));
		when(commands.reply("/today", TelegramChat.FAMILY)).thenReturn("가족 응답");

		// when
		poller.pollOnce();

		// then
		verify(telegram).send(TelegramChat.FAMILY, "가족 응답");
		verify(telegram, never()).send(TelegramChat.OWNER, "가족 응답");
	}

	@Test
	void 가족방의_명령이_아닌_글에는_답하지_않는다() {
		// given
		when(telegram.getUpdates(0, 20)).thenReturn(List.of(new TelegramUpdate(7, -1007L, "오늘 저녁 뭐 먹지", NOW)));

		// when
		poller.pollOnce();

		// then
		verify(commands, never()).reply(anyString(), any());
		verify(telegram, never()).send(any(), anyString());
	}

	@Test
	void 가족방이_있으면_설정_명령을_뺀_메뉴를_가족방에_등록한다() {
		// given
		when(telegram.hasFamilyChat()).thenReturn(true);

		// when
		poller.start();

		// then
		verify(telegram, timeout(1000)).setMyCommands(BotCommand.menu(TelegramChat.FAMILY), TelegramChat.FAMILY);
		verify(telegram).setMyCommands(BotCommand.menu(TelegramChat.OWNER));
		poller.stop();
	}

	@Test
	void 가족방이_없으면_가족방_메뉴를_등록하지_않는다() {
		// when
		poller.start();

		// then
		verify(telegram, timeout(1000)).setMyCommands(BotCommand.menu(TelegramChat.OWNER));
		poller.stop();
		verify(telegram, never()).setMyCommands(any(), any());
	}

}
