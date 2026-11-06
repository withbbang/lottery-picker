package com.lotterypicker.round.repository;

import com.lotterypicker.round.entity.LottoRound;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LottoRoundRepository extends JpaRepository<LottoRound, Integer> {

	Optional<LottoRound> findTopByOrderByRoundNoDesc();

}
