package com.lotterypicker.collector;

import com.lotterypicker.lotto.LottoNumbers;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 동행복권 내부 조회 경로 클라이언트. 비공식 경로라 형식이 바뀌면 조용히 넘기지 않고 {@link CollectorException}을 던진다.
 * 요청 사이에는 {@code app.dhlottery.min-interval-ms} 이상 간격을 둔다.
 */
@Component
public class DhLotteryClient {

	private static final String USER_AGENT = "lottery-picker/1.0 (personal use)";
	private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
	private static final int TIER_COUNT = 5;

	private final RestClient restClient;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final String baseUrl;
	private final long minIntervalMs;
	private long lastRequestAtNanos;
	private boolean requestedBefore;

	public DhLotteryClient(RestClient.Builder builder,
		@Value("${app.dhlottery.base-url}") String baseUrl,
		@Value("${app.dhlottery.min-interval-ms}") long minIntervalMs) {
		this.restClient = builder
			.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
		this.baseUrl = baseUrl;
		this.minIntervalMs = minIntervalMs;
	}

	public String source() {
		return baseUrl;
	}

	public List<RoundPeriod> fetchRoundList() {
		JsonNode list = fetchList(uri("/lt645/selectLtEpsdInfo.do"));
		List<RoundPeriod> result = new ArrayList<>();
		for (JsonNode node : list) {
			result.add(new RoundPeriod(
				intOf(node, "ltEpsd"),
				dateOf(node, "ltRflYmd"),
				dateOf(node, "ltEpsdBgngYmd"),
				dateOf(node, "ltEpsdEndYmd")));
		}
		return result;
	}

	/** 요청한 회차를 포함한 최대 10개 회차를 돌려준다. */
	public List<RoundPayload> fetchRoundPage(int round) {
		JsonNode list = fetchList(uri("/lt645/selectPstLt645InfoNew.do", "srchDir", "center", "srchLtEpsd", round));
		List<RoundPayload> result = new ArrayList<>();
		for (JsonNode node : list) {
			result.add(toRoundPayload(node));
		}
		return result;
	}

	public List<StorePayload> fetchStores(int round) {
		JsonNode list = fetchList(uri("/wnprchsplcsrch/selectLtWnShp.do",
			"srchWnShpRnk", "all", "srchLtEpsd", round, "srchShpLctn", ""));
		List<StorePayload> result = new ArrayList<>();
		for (JsonNode node : list) {
			int rank = intOf(node, "wnShpRnk");
			if (rank < 1 || rank > TIER_COUNT) {
				throw new CollectorException("판매점 등수가 올바르지 않습니다: " + rank);
			}
			String formerAddress = optionalTextOf(node, "befAddr");
			result.add(new StorePayload(
				rank,
				storeRegion(node, formerAddress),
				textOf(node, "shpNm"),
				storeAddress(node, formerAddress, regionBlank(node)),
				optionalTextOf(node, "atmtPsvYnTxt"),
				optionalDoubleOf(node, "shpLat"),
				optionalDoubleOf(node, "shpLot"),
				optionalTextOf(node, "status")));
		}
		return result;
	}

	/**
	 * 폐점한 오래된 판매점은 region 이 공백이다. 이때만 당첨 당시 주소(befAddr)의 첫 단어로 대신한다.
	 * 대신할 값도 없으면 형식 오류로 본다.
	 */
	private static String storeRegion(JsonNode node, String formerAddress) {
		JsonNode value = node.path("region");
		if (value.isString() && !value.stringValue().isBlank()) {
			return value.stringValue().trim();
		}
		if (formerAddress != null && !formerAddress.isBlank()) {
			return firstWord(formerAddress);
		}
		throw new CollectorException("문자열 필드가 없거나 비어 있습니다: region");
	}

	private static boolean regionBlank(JsonNode node) {
		JsonNode value = node.path("region");
		return !value.isString() || value.stringValue().isBlank();
	}

	/**
	 * 폐점한 오래된 판매점(region 공백)은 shpAddr 가 비었거나 "1층" 같은 상세 문구뿐일 수 있다.
	 * 이때 shpAddr 의 첫 단어가 당시 주소(befAddr)의 첫 단어와 다르면 befAddr 로 대신한다.
	 * region 이 있는 레코드는 그대로 둔다. befAddr 가 없으면 shpAddr 를 유지하고, 둘 다 비면 형식 오류다.
	 */
	private static String storeAddress(JsonNode node, String formerAddress, boolean regionBlank) {
		JsonNode value = node.path("shpAddr");
		String address = value.isString() ? value.stringValue().trim() : "";
		boolean hasFormer = formerAddress != null && !formerAddress.isBlank();
		if (address.isEmpty()) {
			if (hasFormer) {
				return formerAddress.trim();
			}
			throw new CollectorException("문자열 필드가 없거나 비어 있습니다: shpAddr");
		}
		if (regionBlank && hasFormer && !firstWord(address).equals(firstWord(formerAddress))) {
			return formerAddress.trim();
		}
		return address;
	}

	private static String firstWord(String text) {
		return text.trim().split("\\s+")[0];
	}

	private RoundPayload toRoundPayload(JsonNode node) {
		int round = intOf(node, "ltEpsd");
		LottoNumbers numbers;
		try {
			numbers = new LottoNumbers(List.of(
				intOf(node, "tm1WnNo"), intOf(node, "tm2WnNo"), intOf(node, "tm3WnNo"),
				intOf(node, "tm4WnNo"), intOf(node, "tm5WnNo"), intOf(node, "tm6WnNo")));
		} catch (IllegalArgumentException e) {
			throw new CollectorException(round + "회 당첨 번호가 올바르지 않습니다: " + e.getMessage(), e);
		}
		int bonus = intOf(node, "bnsWnNo");
		if (bonus < LottoNumbers.MIN || bonus > LottoNumbers.MAX || numbers.contains(bonus)) {
			throw new CollectorException(round + "회 보너스 번호가 올바르지 않습니다: " + bonus);
		}
		List<PrizeTier> tiers = new ArrayList<>();
		for (int rank = 1; rank <= TIER_COUNT; rank++) {
			tiers.add(new PrizeTier(rank, longOf(node, "rnk" + rank + "WnNope"), longOf(node, "rnk" + rank + "WnAmt")));
		}
		return new RoundPayload(round, numbers, bonus, dateOf(node, "ltRflYmd"), List.copyOf(tiers));
	}

	private URI uri(String path, Object... queryPairs) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).path(path);
		for (int i = 0; i < queryPairs.length; i += 2) {
			builder.queryParam((String) queryPairs[i], queryPairs[i + 1]);
		}
		return builder.build().toUri();
	}

	private JsonNode fetchList(URI uri) {
		throttle();
		ResponseEntity<String> response;
		try {
			response = restClient.get().uri(uri).retrieve().toEntity(String.class);
		} catch (RestClientException e) {
			throw new CollectorUnavailableException("동행복권 요청에 실패했습니다: " + uri.getPath(), e);
		}
		MediaType contentType = response.getHeaders().getContentType();
		if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
			throw new CollectorUnavailableException("JSON이 아닌 응답입니다: " + uri.getPath() + " (" + contentType + ")");
		}
		JsonNode list;
		try {
			list = jsonMapper.readTree(response.getBody()).path("data").path("list");
		} catch (JacksonException | IllegalArgumentException e) {
			throw new CollectorException("JSON 파싱에 실패했습니다: " + uri.getPath(), e);
		}
		if (!list.isArray()) {
			throw new CollectorException("data.list 가 배열이 아닙니다: " + uri.getPath());
		}
		return list;
	}

	private synchronized void throttle() {
		if (requestedBefore && minIntervalMs > 0) {
			long elapsedMs = (System.nanoTime() - lastRequestAtNanos) / 1_000_000;
			long waitMs = minIntervalMs - elapsedMs;
			if (waitMs > 0) {
				try {
					Thread.sleep(waitMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new CollectorException("요청 간격 대기 중 중단되었습니다.", e);
				}
			}
		}
		requestedBefore = true;
		lastRequestAtNanos = System.nanoTime();
	}

	private static int intOf(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new CollectorException("정수 필드가 없거나 형식이 다릅니다: " + field);
		}
		return value.intValue();
	}

	private static long longOf(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isIntegralNumber() || !value.canConvertToLong()) {
			throw new CollectorException("정수 필드가 없거나 형식이 다릅니다: " + field);
		}
		return value.longValue();
	}

	private static String textOf(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isString() || value.stringValue().isBlank()) {
			throw new CollectorException("문자열 필드가 없거나 비어 있습니다: " + field);
		}
		return value.stringValue();
	}

	private static String optionalTextOf(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isString() ? value.stringValue() : null;
	}

	private static Double optionalDoubleOf(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isNumber() ? value.doubleValue() : null;
	}

	private static LocalDate dateOf(JsonNode node, String field) {
		String text = textOf(node, field);
		try {
			return LocalDate.parse(text, YMD);
		} catch (DateTimeParseException e) {
			throw new CollectorException("날짜 형식이 다릅니다: " + field + "=" + text, e);
		}
	}

}
