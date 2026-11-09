package com.lotterypicker.notification;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** 보내기 전용 텔레그램 Bot API 클라이언트. 실패는 토큰이 없는 {@link TelegramException}으로만 알린다. */
@Component
public class TelegramClient {

	private final RestClient restClient;
	private final String baseUrl;
	private final String botToken;
	private final String chatId;

	public TelegramClient(RestClient.Builder builder,
		@Value("${app.telegram.base-url}") String baseUrl,
		@Value("${app.telegram.bot-token}") String botToken,
		@Value("${app.telegram.chat-id}") String chatId) {
		this.restClient = builder.build();
		this.baseUrl = baseUrl;
		this.botToken = botToken;
		this.chatId = chatId;
	}

	public boolean isConfigured() {
		return !botToken.isBlank() && !chatId.isBlank();
	}

	public void send(String text) {
		try {
			restClient.post()
				.uri(sendMessageUri())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("chat_id", chatId, "text", text))
				.retrieve()
				.toBodilessEntity();
		} catch (RestClientResponseException e) {
			throw new TelegramException("텔레그램 응답 오류: HTTP " + e.getStatusCode().value());
		} catch (RuntimeException e) {
			// 연결 실패나 잘못된 URL의 원본 메시지에는 토큰이 포함되므로 종류만 남긴다.
			throw new TelegramException("텔레그램 요청 실패: " + e.getClass().getSimpleName());
		}
	}

	private URI sendMessageUri() {
		return UriComponentsBuilder.fromUriString(baseUrl)
			.pathSegment("bot" + botToken, "sendMessage")
			.build()
			.toUri();
	}

}
