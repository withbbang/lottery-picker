package com.lotterypicker.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(value = DhLotteryClient.class, properties = "app.dhlottery.min-interval-ms=0")
class DhLotteryClientTest {

	private static final String BASE = "https://www.dhlottery.co.kr";
	private static final String ROUND_URL = BASE + "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=1248";
	private static final String STORE_URL =
		BASE + "/wnprchsplcsrch/selectLtWnShp.do?srchWnShpRnk=all&srchLtEpsd=1248&srchShpLctn=";

	@Autowired
	private DhLotteryClient client;

	@Autowired
	private MockRestServiceServer server;

	private static String fixture(String name) throws IOException {
		return new ClassPathResource("fixtures/dhlottery/" + name).getContentAsString(StandardCharsets.UTF_8);
	}

	private void respondJson(String url, String body) {
		server.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	@Test
	void 회차_목록을_읽는다() throws IOException {
		// given
		respondJson(BASE + "/lt645/selectLtEpsdInfo.do", fixture("round-list.json"));

		// when
		List<RoundPeriod> periods = client.fetchRoundList();

		// then
		assertThat(periods).hasSize(5);
		assertThat(periods.get(0).round()).isEqualTo(1248);
		assertThat(periods.get(0).drawDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(periods.get(0).salesStart()).isEqualTo(LocalDate.of(2026, 10, 25));
		assertThat(periods.get(0).salesEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	@Test
	void 회차_상세를_읽는다() throws IOException {
		// given
		respondJson(ROUND_URL, fixture("round-page-1248.json"));

		// when
		RoundPayload payload = client.fetchRoundPage(1248).stream()
			.filter(p -> p.round() == 1248).findFirst().orElseThrow();

		// then
		assertThat(payload.numbers().values()).containsExactly(16, 18, 27, 36, 38, 42);
		assertThat(payload.bonus()).isEqualTo(22);
		assertThat(payload.drawDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(payload.tiers()).hasSize(5);
		assertThat(payload.tiers().get(0)).isEqualTo(new PrizeTier(1, 15, 1_933_658_450L));
		assertThat(payload.tiers().get(1).winnerCount()).isEqualTo(91);
		assertThat(payload.tiers().get(4).amountPerWinner()).isEqualTo(5000);
	}

	@Test
	void 요청_경로와_파라미터가_맞다() throws IOException {
		// given
		server.expect(requestTo(ROUND_URL))
			.andExpect(header("User-Agent", "lottery-picker/1.0 (personal use)"))
			.andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
			.andRespond(withSuccess(fixture("round-page-1248.json"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(STORE_URL))
			.andExpect(header("User-Agent", "lottery-picker/1.0 (personal use)"))
			.andRespond(withSuccess(fixture("stores-1248.json"), MediaType.APPLICATION_JSON));

		// when
		client.fetchRoundPage(1248);
		client.fetchStores(1248);

		// then
		server.verify();
	}

	@Test
	void 판매점의_등수는_응답의_wnShpRnk로_판단한다() throws IOException {
		// given
		respondJson(STORE_URL, fixture("stores-1248.json"));

		// when
		List<StorePayload> stores = client.fetchStores(1248);

		// then
		assertThat(stores).extracting(StorePayload::rank).containsExactly(1, 1, 2, 2);
		StorePayload first = stores.get(0);
		assertThat(first.region()).isEqualTo("서울");
		assertThat(first.name()).isEqualTo("로또킹");
		assertThat(first.address()).isEqualTo("서울 영등포구 영중로 2 1층(영등포동3가)");
		assertThat(first.purchaseType()).isEqualTo("자동");
		assertThat(first.latitude()).isEqualTo(37.516741);
		assertThat(first.longitude()).isEqualTo(126.90673);
		assertThat(first.status()).isEqualTo("정상");
	}

	@Test
	void 판매점이_없으면_빈_목록을_돌려준다() throws IOException {
		// given
		respondJson(BASE + "/wnprchsplcsrch/selectLtWnShp.do?srchWnShpRnk=all&srchLtEpsd=1&srchShpLctn=",
			fixture("stores-empty.json"));

		// when
		List<StorePayload> stores = client.fetchStores(1);

		// then
		assertThat(stores).isEmpty();
	}

	@Test
	void HTML_응답은_CollectorException이다() {
		// given
		server.expect(requestTo(ROUND_URL))
			.andRespond(withSuccess("<html><body>점검 중</body></html>", MediaType.TEXT_HTML));

		// when & then
		assertThatThrownBy(() -> client.fetchRoundPage(1248)).isInstanceOf(CollectorException.class);
	}

	@Test
	void 범위_밖_번호는_CollectorException이다() throws IOException {
		// given
		respondJson(ROUND_URL, fixture("round-page-1248.json").replaceFirst("\"tm1WnNo\":16", "\"tm1WnNo\":46"));

		// when & then
		assertThatThrownBy(() -> client.fetchRoundPage(1248)).isInstanceOf(CollectorException.class);
	}

	@Test
	void 본번호와_겹치는_보너스는_CollectorException이다() throws IOException {
		// given
		respondJson(ROUND_URL, fixture("round-page-1248.json").replaceFirst("\"bnsWnNo\":22", "\"bnsWnNo\":16"));

		// when & then
		assertThatThrownBy(() -> client.fetchRoundPage(1248)).isInstanceOf(CollectorException.class);
	}

	@Test
	void 범위_밖_보너스는_CollectorException이다() throws IOException {
		// given
		respondJson(ROUND_URL, fixture("round-page-1248.json").replaceFirst("\"bnsWnNo\":22", "\"bnsWnNo\":46"));

		// when & then
		assertThatThrownBy(() -> client.fetchRoundPage(1248)).isInstanceOf(CollectorException.class);
	}

	@Test
	void 필수_필드가_없으면_CollectorException이다() throws IOException {
		// given
		respondJson(ROUND_URL, fixture("round-page-1248.json").replaceFirst("\"tm6WnNo\"", "\"tm6WnNoX\""));

		// when & then
		assertThatThrownBy(() -> client.fetchRoundPage(1248)).isInstanceOf(CollectorException.class);
	}

	@Test
	void 서버_오류_응답은_CollectorException이다() {
		// given
		server.expect(requestTo(ROUND_URL))
			.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

		// when & then
		assertThatThrownBy(() -> client.fetchRoundPage(1248)).isInstanceOf(CollectorException.class);
	}

}
