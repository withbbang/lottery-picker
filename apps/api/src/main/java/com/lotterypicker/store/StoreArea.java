package com.lotterypicker.store;

import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;

/** 판매점의 시·도와 시·군·구. 원본 지역·주소에서 뽑는다. */
public record StoreArea(String region, @Nullable String district) {

	// 사이트는 온라인 판매에 지역 "서울", 주소 "동행복권(dhlottery.co.kr)"을 준다.
	public static final String ONLINE_ADDRESS = "동행복권(dhlottery.co.kr)";
	public static final String ONLINE_REGION = "온라인";

	// 사이트가 지금은 광주·전남을 "전남광주"로 준다. 예전에 수집한 "광주"·"전남"도 여기로 합친다.
	public static final String MERGED_REGION = "전남광주";
	public static final String UNKNOWN_DISTRICT = "기타";

	private static final Set<String> MERGED_SOURCES = Set.of("광주", "전남");
	private static final String SEJONG = "세종";
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern DISTRICT = Pattern.compile(".+[시군구]");

	public static StoreArea of(String region, String address) {
		String trimmed = address.strip();
		if (ONLINE_ADDRESS.equals(trimmed)) {
			return new StoreArea(ONLINE_REGION, null);
		}
		String normalized = MERGED_SOURCES.contains(region) ? MERGED_REGION : region;
		if (SEJONG.equals(normalized)) {
			return new StoreArea(SEJONG, SEJONG);
		}
		String[] tokens = WHITESPACE.split(trimmed);
		String district = tokens.length > 1 && DISTRICT.matcher(tokens[1]).matches() ? tokens[1] : UNKNOWN_DISTRICT;
		return new StoreArea(normalized, district);
	}

}
