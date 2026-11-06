package com.lotterypicker.collector;

public record StorePayload(int rank, String region, String name, String address, String purchaseType,
	Double latitude, Double longitude, String status) {
}
