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
	void 아직_비어_있는_당첨금_필드는_0으로_읽는다() throws IOException {
		// given: 추첨 직후에는 당첨자 수와 금액이 null일 수 있다
		respondJson(ROUND_URL, fixture("round-page-1248.json")
			.replaceFirst("\"rnk4WnAmt\":50000", "\"rnk4WnAmt\":null")
			.replaceFirst("\"rnk5WnNope\":2723467", "\"rnk5WnNopeX\":1"));

		// when
		RoundPayload payload = client.fetchRoundPage(1248).stream()
			.filter(p -> p.round() == 1248).findFirst().orElseThrow();

		// then
		assertThat(payload.tiers().get(3).amountPerWinner()).isZero();
		assertThat(payload.tiers().get(4).winnerCount()).isZero();
		assertThat(payload.prizesPublished()).isFalse();
	}

	@Test
	void 당첨금_필드가_숫자가_아니면_CollectorException이다() throws IOException {
		// given
		respondJson(ROUND_URL, fixture("round-page-1248.json").replaceFirst("\"rnk4WnAmt\":50000", "\"rnk4WnAmt\":\"x\""));

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

	private static String closedStoreJson(String address, String region, String beforeAddress) {
		return "{\"data\":{\"list\":[{\"wnShpRnk\":2,\"shpNm\":\"노다지\",\"status\":\"폐점\","
			+ "\"shpAddr\":\"" + address + "\",\"region\":\"" + region + "\","
			+ "\"befAddr\":" + (beforeAddress == null ? "null" : "\"" + beforeAddress + "\"") + ","
			+ "\"atmtPsvYnTxt\":null,\"shpLat\":37.589488,\"shpLot\":127.091767}]}}";
	}

	@Test
	void 폐점한_판매점은_당첨_당시_주소로_지역과_주소를_채운다() {
		// given
		respondJson(STORE_URL, closedStoreJson("   ", " ", "서울 중랑구 면목동  74-35"));

		// when
		List<StorePayload> stores = client.fetchStores(1248);

		// then
		assertThat(stores).hasSize(1);
		assertThat(stores.get(0).region()).isEqualTo("서울");
		assertThat(stores.get(0).address()).isEqualTo("서울 중랑구 면목동  74-35");
	}

	@Test
	void 폐점_판매점의_주소가_상세_문구뿐이면_당시_주소를_쓴다() {
		// given
		respondJson(STORE_URL, closedStoreJson("    앞도로가로판매", " ", "서울 성동구 성수동2가  235-1호 앞도로가로판매"));

		// when
		List<StorePayload> stores = client.fetchStores(1248);

		// then
		assertThat(stores.get(0).region()).isEqualTo("서울");
		assertThat(stores.get(0).address()).isEqualTo("서울 성동구 성수동2가  235-1호 앞도로가로판매");
	}

	@Test
	void 폐점_판매점의_주소가_공백_있는_상세_문구여도_당시_주소를_쓴다() {
		// given
		respondJson(STORE_URL, closedStoreJson("1층 세븐일레븐", " ", "서울 강남구 역삼동 1-1"));

		// when
		List<StorePayload> stores = client.fetchStores(1248);

		// then
		assertThat(stores.get(0).address()).isEqualTo("서울 강남구 역삼동 1-1");
	}

	@Test
	void 폐점_판매점의_주소가_지역으로_시작하면_그대로_둔다() {
		// given
		respondJson(STORE_URL, closedStoreJson("서울 성동구 성수동2가 235-1", " ", "서울 성동구 성수동2가  235-1호 앞도로가로판매"));

		// when
		List<StorePayload> stores = client.fetchStores(1248);

		// then
		assertThat(stores.get(0).address()).isEqualTo("서울 성동구 성수동2가 235-1");
	}

	@Test
	void 지역이_있는_정상_판매점의_공백_없는_주소는_그대로_둔다() {
		// given
		respondJson(STORE_URL, closedStoreJson("동행복권(dhlottery.co.kr)", "서울", "서울 중구 어딘가 1"));

		// when
		List<StorePayload> stores = client.fetchStores(1248);

		// then
		assertThat(stores.get(0).region()).isEqualTo("서울");
		assertThat(stores.get(0).address()).isEqualTo("동행복권(dhlottery.co.kr)");
	}

	@Test
	void 당시_주소가_없으면_상세_문구를_그대로_둔다() {
		// given
		respondJson(STORE_URL, closedStoreJson("앞도로가로판매", "서울", null));

		// when
		List<StorePayload> stores = client.fetchStores(1248);

		// then
		assertThat(stores.get(0).address()).isEqualTo("앞도로가로판매");
	}

	@Test
	void 대체할_당시_주소도_없으면_CollectorException이다() {
		// given
		respondJson(STORE_URL, closedStoreJson("   ", " ", null));

		// when & then
		assertThatThrownBy(() -> client.fetchStores(1248)).isInstanceOf(CollectorException.class);
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
