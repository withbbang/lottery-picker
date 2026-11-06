package com.lotterypicker.round.dto;

import com.lotterypicker.round.entity.WinningStore;

public record WinningStoreResponse(int rank, String region, String name, String address, String purchaseType,
		String status) {

	public static final String ONLINE_REGION = "온라인";

	// 사이트는 온라인 판매에 지역 "서울", 주소 "동행복권(dhlottery.co.kr)"을 준다. DB에는 원본을 두고 응답에서만 바꾼다.
	private static final String ONLINE_ADDRESS = "동행복권(dhlottery.co.kr)";

	public static WinningStoreResponse from(WinningStore s) {
		String region = ONLINE_ADDRESS.equals(s.getAddress()) ? ONLINE_REGION : s.getRegion();
		return new WinningStoreResponse(s.getWinRank(), region, s.getName(), s.getAddress(),
			s.getPurchaseType(), s.getStatus());
	}

}
