package com.lotterypicker.round.repository;

import com.lotterypicker.round.entity.WinningStore;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WinningStoreRepository extends JpaRepository<WinningStore, Long> {

	List<WinningStore> findByRoundRoundNoOrderByWinRankAscIdAsc(int roundNo);

}
