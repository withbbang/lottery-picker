package com.lotterypicker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.lotterypicker.bot.dto.BotMenuItem;
import com.lotterypicker.bot.dto.TelegramUpdate;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@RestClientTest(value = TelegramClient.class,
		properties = { "app.telegram.bot-token=123:secret", "app.telegram.chat-id=42",
				"app.telegram.family-chat-id=-1007" })
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
		client.send(TelegramChat.OWNER, "안녕");

		// then
		server.verify();
	}

	@Test
	void 본문을_HTML로_해석하도록_보낸다() {
		// given
		server.expect(requestTo(URL))
			.andExpect(jsonPath("$.parse_mode").value("HTML"))
			.andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

		// when
		client.send(TelegramChat.OWNER, "<pre>1</pre>");

		// then
		server.verify();
	}

	@Test
	void 실패_예외에_토큰이_포함되지_않는다() {
		// given
		server.expect(requestTo(URL)).andRespond(withServerError());

		// when, then
		assertThatThrownBy(() -> client.send(TelegramChat.OWNER, "안녕")).isInstanceOf(TelegramException.class)
			.hasMessageContaining("500")
			.hasMessageNotContaining(TOKEN)
			.hasNoCause();
	}

	@Test
	void 연결_실패_예외에_토큰이_포함되지_않는다() {
		// given: 연결 실패 예외의 원본 메시지에는 요청 URL이 들어간다
		server.expect(requestTo(URL)).andRespond(withException(new IOException("connection refused")));

		// when, then
		assertThatThrownBy(() -> client.send(TelegramChat.OWNER, "안녕")).isInstanceOf(TelegramException.class)
			.hasMessageNotContaining(TOKEN)
			.hasNoCause();
	}

	@Test
	void 토큰과_채팅_ID가_모두_있어야_설정된_것이다() {
		// given
		String base = "https://api.telegram.org";

		// when, then
		assertThat(new TelegramClient(RestClient.builder(), base, TOKEN, "42", "").isConfigured()).isTrue();
		assertThat(new TelegramClient(RestClient.builder(), base, "", "42", "").isConfigured()).isFalse();
		assertThat(new TelegramClient(RestClient.builder(), base, TOKEN, "", "-1007").isConfigured()).isFalse();
	}

	private static final String UPDATES_URL = "https://api.telegram.org/bot" + TOKEN + "/getUpdates";

	@Test
	void getUpdates로_offset과_대기시간을_보내고_메시지를_읽는다() {
		// given
		server.expect(requestTo(UPDATES_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.offset").value(7))
			.andExpect(jsonPath("$.timeout").value(20))
			.andExpect(jsonPath("$.allowed_updates[0]").value("message"))
			.andRespond(withSuccess("""
				{"ok":true,"result":[
				  {"update_id":7,"message":{"message_id":1,"date":1762750000,"chat":{"id":42},"text":"/today"}},
				  {"update_id":8,"message":{"message_id":2,"date":1762750060,"chat":{"id":43},"text":"/help"}}
				]}""", MediaType.APPLICATION_JSON));

		// when
		List<TelegramUpdate> updates = client.getUpdates(7, 20);

		// then
		assertThat(updates).containsExactly(
			new TelegramUpdate(7, 42L, "/today", Instant.ofEpochSecond(1762750000)),
			new TelegramUpdate(8, 43L, "/help", Instant.ofEpochSecond(1762750060)));
	}

	@Test
	void 메시지가_없는_업데이트는_본문이_비어_있다() {
		// given
		server.expect(requestTo(UPDATES_URL))
			.andRespond(withSuccess("{\"ok\":true,\"result\":[{\"update_id\":8}]}", MediaType.APPLICATION_JSON));

		// when
		List<TelegramUpdate> updates = client.getUpdates(0, 20);

		// then
		assertThat(updates).containsExactly(new TelegramUpdate(8, null, null, null));
	}

	@Test
	void getUpdates_실패는_토큰_없는_예외다() {
		// given
		server.expect(requestTo(UPDATES_URL)).andRespond(withServerError());

		// when, then
		assertThatThrownBy(() -> client.getUpdates(0, 20)).isInstanceOf(TelegramException.class)
			.hasMessageNotContaining(TOKEN)
			.hasNoCause();
	}

	@Test
	void 명령_메뉴를_등록한다() {
		// given
		server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/setMyCommands"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.commands[0].command").value("today"))
			.andExpect(jsonPath("$.commands[0].description").value("오늘 추천"))
			.andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));

		// when
		client.setMyCommands(List.of(new BotMenuItem("today", "오늘 추천")));

		// then
		server.verify();
	}

	@Test
	void 설정된_채팅만_어느_방인지_알려_준다() {
		// given / when / then
		assertThat(client.chatOf(42)).contains(TelegramChat.OWNER);
		assertThat(client.chatOf(-1007)).contains(TelegramChat.FAMILY);
		assertThat(client.chatOf(43)).isEmpty();
	}

	@Test
	void 가족방_id가_비어_있으면_가족방이_없다() {
		// given
		TelegramClient ownerOnly = new TelegramClient(RestClient.builder(), "https://api.telegram.org", TOKEN, "42", "");

		// when / then
		assertThat(ownerOnly.hasFamilyChat()).isFalse();
		assertThat(ownerOnly.chatOf(-1007)).isEmpty();
		assertThat(client.hasFamilyChat()).isTrue();
	}

	@Test
	void 운영자_방이_없으면_가족방도_쓰지_않는다() {
		// given
		TelegramClient familyOnly = new TelegramClient(RestClient.builder(), "https://api.telegram.org", TOKEN, "",
				"-1007");

		// when / then
		assertThat(familyOnly.hasFamilyChat()).isFalse();
		assertThat(familyOnly.chatOf(-1007)).isEmpty();
	}

	@Test
	void 가족방으로_보내면_가족방_id로_보낸다() {
		// given
		server.expect(requestTo(URL))
			.andExpect(jsonPath("$.chat_id").value("-1007"))
			.andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

		// when
		client.send(TelegramChat.FAMILY, "안녕");

		// then
		server.verify();
	}

	@Test
	void 가족방_메뉴는_그_방_범위로_등록한다() {
		// given
		server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/setMyCommands"))
			.andExpect(jsonPath("$.commands[0].command").value("today"))
			.andExpect(jsonPath("$.scope.type").value("chat"))
			.andExpect(jsonPath("$.scope.chat_id").value("-1007"))
			.andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));

		// when
		client.setMyCommands(List.of(new BotMenuItem("today", "오늘 추천")), TelegramChat.FAMILY);

		// then
		server.verify();
	}

	@Test
	void 기본_메뉴는_범위_없이_등록한다() {
		// given
		server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/setMyCommands"))
			.andExpect(jsonPath("$.scope").doesNotExist())
			.andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));

		// when
		client.setMyCommands(List.of(new BotMenuItem("today", "오늘 추천")));

		// then
		server.verify();
	}

	@Test
	void 가족방_id가_운영자_방과_같으면_가족방이_없다() {
		// given
		TelegramClient same = new TelegramClient(RestClient.builder(), "https://api.telegram.org", TOKEN, "42", "42");

		// when / then
		assertThat(same.hasFamilyChat()).isFalse();
		assertThat(same.chatOf(42)).contains(TelegramChat.OWNER);
	}

}
