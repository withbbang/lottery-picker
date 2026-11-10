package com.lotterypicker.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lotterypicker.bot.dto.BotMenuItem;
import com.lotterypicker.bot.dto.TelegramUpdate;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** 텔레그램 Bot API 클라이언트. 실패는 토큰이 없는 {@link TelegramException}으로만 알린다. */
@Component
public class TelegramClient {

	private final RestClient restClient;
	private final String baseUrl;
	private final String botToken;
	private final String chatId;
	private final String familyChatId;

	public TelegramClient(RestClient.Builder builder,
		@Value("${app.telegram.base-url}") String baseUrl,
		@Value("${app.telegram.bot-token}") String botToken,
		@Value("${app.telegram.chat-id}") String chatId,
		@Value("${app.telegram.family-chat-id:}") String familyChatId) {
		this.restClient = builder.build();
		this.baseUrl = baseUrl;
		this.botToken = botToken;
		this.chatId = chatId;
		this.familyChatId = familyChatId;
	}

	/** 운영자 방까지 설정되어야 봇이 동작한다. 가족방만으로는 동작하지 않는다. */
	public boolean isConfigured() {
		return !botToken.isBlank() && !chatId.isBlank();
	}

	/** 운영자 방과 같은 id면 가족방이 없는 것으로 본다. 같은 방에 알림이 두 번 가거나 메뉴가 덮어써지지 않게. */
	public boolean hasFamilyChat() {
		return isConfigured() && !familyChatId.isBlank() && !familyChatId.equals(chatId);
	}

	/** 메시지가 온 채팅이 설정된 방 중 어느 것인지. 봇은 설정된 방에만 답한다. 두 id가 같으면 운영자 방이다. */
	public Optional<TelegramChat> chatOf(long chatId) {
		String id = Long.toString(chatId);
		if (isConfigured() && this.chatId.equals(id)) {
			return Optional.of(TelegramChat.OWNER);
		}
		if (hasFamilyChat() && familyChatId.equals(id)) {
			return Optional.of(TelegramChat.FAMILY);
		}
		return Optional.empty();
	}

	public void send(TelegramChat chat, String text) {
		call(() -> restClient.post()
			.uri(methodUri("sendMessage"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("chat_id", idOf(chat), "text", text, "parse_mode", "HTML"))
			.retrieve()
			.toBodilessEntity());
	}

	/**
	 * 롱 폴링으로 메시지를 받는다. {@code offset}보다 작은 업데이트는 확인 처리되어 다시 오지 않는다.
	 * {@code timeoutSeconds}는 RestClient 읽기 타임아웃보다 짧아야 한다.
	 */
	public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
		UpdatesResponse response = call(() -> restClient.post()
			.uri(methodUri("getUpdates"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("offset", offset, "timeout", timeoutSeconds, "allowed_updates", List.of("message")))
			.retrieve()
			.body(UpdatesResponse.class));
		if (response == null || response.result() == null) {
			return List.of();
		}
		return response.result().stream().map(Update::toUpdate).toList();
	}

	/** `/` 메뉴의 기본 명령 목록을 등록한다. 따로 등록하지 않은 모든 채팅에 보인다. */
	public void setMyCommands(List<BotMenuItem> commands) {
		registerCommands(Map.of("commands", commands));
	}

	/** 한 채팅방에만 보일 `/` 메뉴를 등록한다. 기본 목록보다 우선한다. */
	public void setMyCommands(List<BotMenuItem> commands, TelegramChat chat) {
		registerCommands(Map.of("commands", commands, "scope", Map.of("type", "chat", "chat_id", idOf(chat))));
	}

	private void registerCommands(Map<String, Object> body) {
		call(() -> restClient.post()
			.uri(methodUri("setMyCommands"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.toBodilessEntity());
	}

	private String idOf(TelegramChat chat) {
		return switch (chat) {
			case OWNER -> chatId;
			case FAMILY -> familyChatId;
		};
	}

	private <T> T call(Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientResponseException e) {
			throw new TelegramException("텔레그램 응답 오류: HTTP " + e.getStatusCode().value());
		} catch (RuntimeException e) {
			// 연결 실패나 잘못된 URL의 원본 메시지에는 토큰이 포함되므로 종류만 남긴다.
			throw new TelegramException("텔레그램 요청 실패: " + e.getClass().getSimpleName());
		}
	}

	private URI methodUri(String method) {
		return UriComponentsBuilder.fromUriString(baseUrl)
			.pathSegment("bot" + botToken, method)
			.build()
			.toUri();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record UpdatesResponse(List<Update> result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Update(@JsonProperty("update_id") long updateId, Message message) {

		TelegramUpdate toUpdate() {
			if (message == null) {
				return new TelegramUpdate(updateId, null, null, null);
			}
			Long chat = message.chat() == null ? null : message.chat().id();
			return new TelegramUpdate(updateId, chat, message.text(), Instant.ofEpochSecond(message.date()));
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(long date, Chat chat, String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Chat(long id) {
	}

}
