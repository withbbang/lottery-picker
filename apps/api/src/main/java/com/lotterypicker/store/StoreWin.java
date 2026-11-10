package com.lotterypicker.store;

import org.springframework.lang.Nullable;

/** 판매점 당첨 한 건. 집계에 필요한 열만 읽는다. */
public record StoreWin(long id, int roundNo, String region, String name, String address,
	@Nullable Double latitude, @Nullable Double longitude, String status) {
}
