package com.lotterypicker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@RestClientTest(value = TelegramClient.class,
		properties = { "app.telegram.bot-token=123:secret", "app.telegram.chat-id=42" })
class TelegramClientTest {

	private static final String TOKEN = "123:secret";
	private static final String URL = "https://api.telegram.org/bot" + TOKEN + "/sendMessage";

	@Autowired
	private TelegramClient client;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void sendMessage로_chat_id와_text를_보낸다() {
		// given
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.chat_id").value("42"))
			.andExpect(jsonPath("$.text").value("안녕"))
			.andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

		// when
		client.send("안녕");

		// then
		server.verify();
	}

	@Test
	void 실패_예외에_토큰이_포함되지_않는다() {
		// given
		server.expect(requestTo(URL)).andRespond(withServerError());

		// when, then
		assertThatThrownBy(() -> client.send("안녕")).isInstanceOf(TelegramException.class)
			.hasMessageContaining("500")
			.hasMessageNotContaining(TOKEN)
			.hasNoCause();
	}

	@Test
	void 연결_실패_예외에_토큰이_포함되지_않는다() {
		// given: 연결 실패 예외의 원본 메시지에는 요청 URL이 들어간다
		server.expect(requestTo(URL)).andRespond(withException(new IOException("connection refused")));

		// when, then
		assertThatThrownBy(() -> client.send("안녕")).isInstanceOf(TelegramException.class)
			.hasMessageNotContaining(TOKEN)
			.hasNoCause();
	}

	@Test
	void 토큰과_채팅_ID가_모두_있어야_설정된_것이다() {
		// given
		String base = "https://api.telegram.org";

		// when, then
		assertThat(new TelegramClient(RestClient.builder(), base, TOKEN, "42").isConfigured()).isTrue();
		assertThat(new TelegramClient(RestClient.builder(), base, "", "42").isConfigured()).isFalse();
		assertThat(new TelegramClient(RestClient.builder(), base, TOKEN, "").isConfigured()).isFalse();
	}

}
