package com.lotterypicker.collector.service;

import com.lotterypicker.collector.CollectorException;
import com.lotterypicker.collector.DhLotteryClient;
import com.lotterypicker.collector.PrizeTier;
import com.lotterypicker.collector.RoundPayload;
import com.lotterypicker.collector.RoundPeriod;
import com.lotterypicker.collector.StorePayload;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.entity.WinningStore;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.round.repository.WinningStoreRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/** 회차와 판매점을 수집해 저장한다. 회차 단위로 저장하므로 중간에 끊겨도 이어서 수집할 수 있다. */
@Slf4j
@Service
public class CollectorService {

	/** 추첨 후 이 일수가 지나도 판매점이 비어 있으면 없는 것으로 보고 완료 처리한다. */
	private static final int STORE_RETRY_DAYS = 7;

	private final DhLotteryClient client;
	private final LottoRoundRepository roundRepository;
	private final WinningStoreRepository storeRepository;
	private final TransactionOperations transactions;
	private final Clock clock;
	private final ReentrantLock roundLock = new ReentrantLock();

	public CollectorService(DhLotteryClient client, LottoRoundRepository roundRepository,
		WinningStoreRepository storeRepository, TransactionOperations transactions, Clock clock) {
		this.client = client;
		this.roundRepository = roundRepository;
		this.storeRepository = storeRepository;
		this.transactions = transactions;
		this.clock = clock;
	}

	/** 이번에 저장한 회차를 오름차순으로 돌려준다. 이미 실행 중이면 아무것도 하지 않고 빈 목록을 돌려준다. */
	public List<Integer> syncMissingRounds() {
		if (!roundLock.tryLock()) {
			log.info("회차 수집이 이미 실행 중이라 건너뜁니다.");
			return List.of();
		}
		try {
			TreeSet<Integer> missing = new TreeSet<>();
			for (RoundPeriod period : client.fetchRoundList()) {
				missing.add(period.round());
			}
			missing.removeAll(roundRepository.findAllRoundNos());

			TreeSet<Integer> saved = new TreeSet<>();
			while (!missing.isEmpty()) {
				int target = missing.last();
				List<RoundPayload> page = client.fetchRoundPage(target);
				if (page.stream().noneMatch(p -> p.round() == target)) {
					throw new CollectorException("요청한 " + target + "회가 응답 페이지에 없습니다.");
				}
				for (RoundPayload payload : page) {
					if (missing.remove(payload.round())) {
						roundRepository.save(toEntity(payload));
						saved.add(payload.round());
					}
				}
				log.info("회차 수집: 남은 {}건", missing.size());
			}
			return new ArrayList<>(saved);
		} finally {
			roundLock.unlock();
		}
	}

	/** 판매점을 아직 받지 못한 회차의 판매점을 수집한다. 빈 결과는 추첨 후 7일이 지나야 완료로 본다. */
	public void syncStores() {
		LocalDate today = LocalDate.now(clock);
		for (LottoRound round : roundRepository.findByStoreFetchedAtIsNullOrderByRoundNoAsc()) {
			List<StorePayload> payloads = client.fetchStores(round.getRoundNo());
			boolean giveUp = today.isAfter(round.getDrawDate().plusDays(STORE_RETRY_DAYS));
			if (payloads.isEmpty() && !giveUp) {
				continue;
			}
			transactions.executeWithoutResult(status -> {
				storeRepository.saveAll(payloads.stream().map(p -> toEntity(round, p)).toList());
				round.markStoresFetched(LocalDateTime.now(clock));
				roundRepository.save(round);
			});
		}
	}

	private LottoRound toEntity(RoundPayload payload) {
		List<PrizeTier> tiers = payload.tiers();
		return LottoRound.builder()
			.roundNo(payload.round())
			.numbers(payload.numbers())
			.bonus(payload.bonus())
			.drawDate(payload.drawDate())
			.rank1Count(count(tiers, 1)).rank1Amount(tiers.get(0).amountPerWinner())
			.rank2Count(count(tiers, 2)).rank2Amount(tiers.get(1).amountPerWinner())
			.rank3Count(count(tiers, 3)).rank3Amount(tiers.get(2).amountPerWinner())
			.rank4Count(count(tiers, 4)).rank4Amount(tiers.get(3).amountPerWinner())
			.rank5Count(count(tiers, 5)).rank5Amount(tiers.get(4).amountPerWinner())
			.source(client.source())
			.fetchedAt(LocalDateTime.now(clock))
			.build();
	}

	private static int count(List<PrizeTier> tiers, int rank) {
		return Math.toIntExact(tiers.get(rank - 1).winnerCount());
	}

	private static WinningStore toEntity(LottoRound round, StorePayload p) {
		return WinningStore.builder()
			.round(round)
			.winRank(p.rank())
			.region(p.region())
			.name(p.name())
			.address(p.address())
			.purchaseType(p.purchaseType())
			.latitude(p.latitude())
			.longitude(p.longitude())
			.status(p.status())
			.build();
	}

}
