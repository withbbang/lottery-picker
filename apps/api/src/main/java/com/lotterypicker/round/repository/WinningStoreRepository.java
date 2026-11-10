package com.lotterypicker.round.repository;

import com.lotterypicker.round.entity.WinningStore;
import com.lotterypicker.store.StoreWin;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WinningStoreRepository extends JpaRepository<WinningStore, Long> {

	List<WinningStore> findByRoundRoundNoOrderByWinRankAscIdAsc(int roundNo);

	@Query("""
		select new com.lotterypicker.store.StoreWin(
			s.id, s.round.roundNo, s.region, s.name, s.address, s.latitude, s.longitude, s.status)
		from WinningStore s
		where s.winRank = :rank
		order by s.id""")
	List<StoreWin> findWinsByRank(@Param("rank") int rank);

	long countByWinRank(int rank);

	@Query("select max(s.id) from WinningStore s where s.winRank = :rank")
	Optional<Long> findMaxIdByWinRank(@Param("rank") int rank);

}
