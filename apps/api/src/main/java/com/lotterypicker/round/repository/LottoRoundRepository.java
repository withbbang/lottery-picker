package com.lotterypicker.round.repository;

import com.lotterypicker.round.entity.LottoRound;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LottoRoundRepository extends JpaRepository<LottoRound, Integer> {

	Optional<LottoRound> findTopByOrderByRoundNoDesc();

	List<LottoRound> findAllByOrderByRoundNoAsc();

	/** 양끝 포함. 지난 추천의 통계를 그 추천이 쓴 회차 범위로 다시 계산할 때 쓴다. */
	/** 회차 범위에서 기준 시각까지 수집된 회차만. 지난 추천이 실제로 본 회차를 다시 읽을 때 쓴다. */
	List<LottoRound> findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(int from, int to,
			LocalDateTime fetchedAt);

	@Query("select r.roundNo from LottoRound r")
	List<Integer> findAllRoundNos();

	List<LottoRound> findByStoreFetchedAtIsNullOrderByRoundNoAsc();

}
