package com.lotterypicker.round.dto;

import com.lotterypicker.round.entity.WinningStore;
import com.lotterypicker.store.StoreArea;

public record WinningStoreResponse(int rank, String region, String name, String address, String purchaseType,
		String status) {

	public static final String ONLINE_REGION = StoreArea.ONLINE_REGION;

	// DB에는 원본을 두고 응답에서만 바꾼다.

	public static WinningStoreResponse from(WinningStore s) {
		String region = StoreArea.ONLINE_ADDRESS.equals(s.getAddress()) ? ONLINE_REGION : s.getRegion();
		return new WinningStoreResponse(s.getWinRank(), region, s.getName(), s.getAddress(),
			s.getPurchaseType(), s.getStatus());
	}

}
