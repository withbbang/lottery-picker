package com.lotterypicker.bot;

import com.lotterypicker.bot.dto.BotMenuItem;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.lang.Nullable;

/** 텔레그램 봇 명령. 명령어는 이름의 소문자이고, 메뉴 순서는 선언 순서다. */
public enum BotCommand {

	TODAY("오늘 추천"),
	RESULT("최근 회차 채점 결과"),
	PERFORMANCE("전략별 성적표"),
	ROUND("회차 당첨 정보 (예: /round 1249)"),
	STATS("번호 통계 상위 5개"),
	SETTINGS("고정·제외 번호 보기"),
	HELP("명령 목록");

	/** 텔레그램이 대화를 시작할 때 보내는 명령. 도움말로 답한다. */
	private static final String START = "start";

	private final String description;

	BotCommand(String description) {
		this.description = description;
	}

	public String command() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String description() {
		return description;
	}

	public record Parsed(BotCommand command, @Nullable String argument) {
	}

	/** {@code /명령[@봇이름] [인자]}를 읽는다. 명령이 아니거나 모르는 명령이면 빈 값. */
	public static Optional<Parsed> parse(String text) {
		String trimmed = text.strip();
		if (!trimmed.startsWith("/")) {
			return Optional.empty();
		}
		String[] parts = trimmed.substring(1).split("\\s+", 2);
		String name = parts[0].split("@", 2)[0].toLowerCase(Locale.ROOT);
		String argument = parts.length > 1 && !parts[1].isBlank() ? parts[1].strip() : null;
		if (name.equals(START)) {
			return Optional.of(new Parsed(HELP, argument));
		}
		return Arrays.stream(values())
			.filter(command -> command.command().equals(name))
			.findFirst()
			.map(command -> new Parsed(command, argument));
	}

	public static List<BotMenuItem> menu() {
		return Arrays.stream(values()).map(command -> new BotMenuItem(command.command(), command.description)).toList();
	}

}
