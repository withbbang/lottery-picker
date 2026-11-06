package com.lotterypicker.round.repository;

import com.lotterypicker.round.entity.LottoRound;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LottoRoundRepository extends JpaRepository<LottoRound, Integer> {

	Optional<LottoRound> findTopByOrderByRoundNoDesc();

	@Query("select r.roundNo from LottoRound r")
	List<Integer> findAllRoundNos();

	List<LottoRound> findByStoreFetchedAtIsNullOrderByRoundNoAsc();

}
