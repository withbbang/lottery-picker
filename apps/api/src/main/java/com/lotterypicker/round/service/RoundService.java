package com.lotterypicker.round.service;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.common.PageResponse;
import com.lotterypicker.round.dto.RoundDetailResponse;
import com.lotterypicker.round.dto.RoundSummaryResponse;
import com.lotterypicker.round.dto.WinningStoreResponse;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.round.repository.WinningStoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoundService {

	private final LottoRoundRepository roundRepository;
	private final WinningStoreRepository storeRepository;

	public PageResponse<RoundSummaryResponse> list(int page, int size) {
		PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "roundNo"));
		return PageResponse.from(roundRepository.findAll(request).map(RoundSummaryResponse::from));
	}

	public RoundDetailResponse detail(int round) {
		return roundRepository.findById(round)
			.map(RoundDetailResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}

	public List<WinningStoreResponse> winningStores(int round, Integer rank, String region) {
		if (!roundRepository.existsById(round)) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		// 지역은 응답 값(온라인 보정 후)으로 거른다. 회차당 판매점이 백 건대라 메모리에서 거른다.
		return storeRepository.findByRoundRoundNoOrderByWinRankAscIdAsc(round).stream()
			.filter(s -> rank == null || s.getWinRank() == rank)
			.map(WinningStoreResponse::from)
			.filter(s -> region == null || region.equals(s.region()))
			.toList();
	}

}
