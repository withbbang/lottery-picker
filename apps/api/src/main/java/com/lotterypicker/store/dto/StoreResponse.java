package com.lotterypicker.store.dto;

import org.springframework.lang.Nullable;

/** 판매점 하나. 좌표·상태는 가장 최근 당첨 행 값, wins는 당첨 건수. */
public record StoreResponse(String name, String address, String region, @Nullable String district, int wins,
	int lastRound, @Nullable Double latitude, @Nullable Double longitude, String status) {
}
