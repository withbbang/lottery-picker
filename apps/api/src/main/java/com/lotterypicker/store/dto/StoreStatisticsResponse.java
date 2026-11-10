package com.lotterypicker.store.dto;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * 당첨 판매점 지역 통계.
 *
 * @param regions 전국 시·도 순위(온라인 포함). 선택과 무관하게 늘 전국 기준
 * @param districts 고른 시·도의 시·군·구 순위. 시·도를 고르지 않았으면 null
 * @param repeatStores 범위 안에서 두 번 이상 당첨된 판매점, 상위 20개
 * @param pins 지도에 찍을 판매점. 시·도를 골랐을 때만, 온라인과 좌표 없는 판매점 제외
 */
public record StoreStatisticsResponse(int rank, int fromRound, int toRound, List<AreaCountResponse> regions,
	@Nullable List<AreaCountResponse> districts, List<StoreResponse> repeatStores, List<StoreResponse> pins) {
}
