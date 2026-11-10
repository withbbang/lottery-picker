package com.lotterypicker.bot.service;

import com.lotterypicker.bot.BotCommand;
import com.lotterypicker.bot.dto.TelegramUpdate;
import com.lotterypicker.notification.TelegramClient;
import com.lotterypicker.notification.TelegramException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 롱 폴링으로 명령을 받아 답한다. 스케줄러 풀이 아닌 전용 데몬 스레드 하나에서 돈다.
 * 토큰·채팅 id가 없거나 스케줄링이 꺼져 있으면 시작하지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty("app.scheduling.enabled")
public class TelegramPoller implements SmartLifecycle {

	/** RestClient 읽기 타임아웃(30초)보다 짧아야 한다. */
	static final int POLL_TIMEOUT_SECONDS = 20;

	/** 이보다 오래된 명령은 답하지 않는다. 재시작 직후 밀린 명령에 한꺼번에 답하지 않게. */
	private static final Duration STALE_AFTER = Duration.ofMinutes(10);

	private final TelegramClient telegram;
	private final BotCommandService commands;
	private final Clock clock;
	private final PollBackoff backoff = new PollBackoff();

	private long offset;
	private volatile boolean running;
	private Thread thread;

	public TelegramPoller(TelegramClient telegram, BotCommandService commands, Clock clock) {
		this.telegram = telegram;
		this.commands = commands;
		this.clock = clock;
	}

	@Override
	public void start() {
		if (!telegram.isConfigured()) {
			log.info("텔레그램 설정이 없어 명령 폴링을 시작하지 않습니다.");
			return;
		}
		try {
			telegram.setMyCommands(BotCommand.menu());
		} catch (TelegramException e) {
			log.warn("텔레그램 명령 메뉴 등록 실패: {}", e.getMessage());
		}
		running = true;
		thread = new Thread(this::loop, "telegram-poller");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void loop() {
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				pollOnce();
				backoff.reset();
			} catch (RuntimeException e) {
				Duration delay = backoff.nextDelay();
				log.warn("텔레그램 폴링 실패, {}초 뒤 다시 시도: {}", delay.toSeconds(), e.getMessage());
				try {
					Thread.sleep(delay.toMillis());
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	void pollOnce() {
		for (TelegramUpdate update : telegram.getUpdates(offset, POLL_TIMEOUT_SECONDS)) {
			offset = Math.max(offset, update.updateId() + 1);
			handle(update);
		}
	}

	private void handle(TelegramUpdate update) {
		if (update.chatId() == null || update.text() == null || update.sentAt() == null) {
			return;
		}
		if (!telegram.isAllowedChat(update.chatId())) {
			log.info("허용되지 않은 채팅의 메시지를 무시합니다: chat={}", update.chatId());
			return;
		}
		if (update.sentAt().isBefore(Instant.now(clock).minus(STALE_AFTER))) {
			log.info("오래된 명령이라 답하지 않습니다: update={}", update.updateId());
			return;
		}
		try {
			telegram.send(commands.reply(update.text()));
		} catch (TelegramException e) {
			log.warn("텔레그램 명령 응답 전송 실패: {}", e.getMessage());
		}
	}

}
