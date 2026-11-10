package com.lotterypicker.bot;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.bot.BotCommand.Parsed;
import com.lotterypicker.bot.dto.BotMenuItem;
import org.junit.jupiter.api.Test;

class BotCommandTest {

	@Test
	void 명령과_인자를_나눈다() {
		// given / when / then
		assertThat(BotCommand.parse("/round 1249")).contains(new Parsed(BotCommand.ROUND, "1249"));
		assertThat(BotCommand.parse("/today")).contains(new Parsed(BotCommand.TODAY, null));
	}

	@Test
	void 봇_이름이_붙은_명령도_읽는다() {
		// given / when / then
		assertThat(BotCommand.parse("/round@lotto_bot 1249")).contains(new Parsed(BotCommand.ROUND, "1249"));
	}

	@Test
	void 대소문자와_공백을_가리지_않는다() {
		// given / when / then
		assertThat(BotCommand.parse("  /TODAY  ")).contains(new Parsed(BotCommand.TODAY, null));
		assertThat(BotCommand.parse("/round   1249  ")).contains(new Parsed(BotCommand.ROUND, "1249"));
	}

	@Test
	void start는_help다() {
		// given / when / then
		assertThat(BotCommand.parse("/start")).contains(new Parsed(BotCommand.HELP, null));
	}

	@Test
	void 명령이_아니거나_모르는_명령은_빈_값() {
		// given / when / then
		assertThat(BotCommand.parse("안녕")).isEmpty();
		assertThat(BotCommand.parse("/foo")).isEmpty();
		assertThat(BotCommand.parse("/")).isEmpty();
		assertThat(BotCommand.parse("")).isEmpty();
	}

	@Test
	void 메뉴는_표_순서다() {
		// given / when / then
		assertThat(BotCommand.menu()).extracting(BotMenuItem::command)
			.containsExactly("today", "result", "performance", "round", "stats", "settings", "help");
		assertThat(BotCommand.menu().get(0).description()).isEqualTo("오늘 추천");
	}

}
