package com.lotterypicker.store.service;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.round.repository.WinningStoreRepository;
import com.lotterypicker.store.StoreArea;
import com.lotterypicker.store.StoreWin;
import com.lotterypicker.store.dto.AreaCountResponse;
import com.lotterypicker.store.dto.StoreResponse;
import com.lotterypicker.store.dto.StoreStatisticsResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 당첨 판매점을 시·도 → 시·군·구로 센다. 같은 판매점은 이름+주소로 묶는다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StoreStatisticsService {

	static final int REPEAT_STORE_LIMIT = 20;

	private static final Comparator<AreaCountResponse> AREA_ORDER = Comparator
		.comparingInt(AreaCountResponse::count).reversed()
		.thenComparing(AreaCountResponse::name);

	private static final Comparator<StoreResponse> STORE_ORDER = Comparator
		.comparingInt(StoreResponse::wins).reversed()
		.thenComparing(Comparator.comparingInt(StoreResponse::lastRound).reversed())
		.thenComparing(StoreResponse::name);

	private final WinningStoreRepository repository;

	/** 등수별 원본 행. 수집기는 판매점을 추가만 하므로 (행 수, 최대 id)가 같으면 그대로 쓴다. */
	private final Map<Integer, Snapshot> cache = new ConcurrentHashMap<>();

	private record Snapshot(long count, long maxId, List<Entry> entries) {
	}

	private record Entry(StoreWin win, StoreArea area, String storeKey) {
	}

	/**
	 * @return 그 등수 판매점 행이 하나도 없으면 빈 값
	 * @throws BusinessException 없는 시·도·시·군·구이거나 시·도 없이 시·군·구만 오면 {@link ErrorCode#COMMON_NOT_FOUND}
	 */
	public Optional<StoreStatisticsResponse> find(int rank, @Nullable String region, @Nullable String district) {
		List<Entry> entries = load(rank).entries();
		if (entries.isEmpty()) {
			return Optional.empty();
		}
		Predicate<Entry> inRegion = e -> e.area().region().equals(region);
		Predicate<Entry> inScope = e -> (region == null || inRegion.test(e))
			&& (district == null || district.equals(e.area().district()));
		if ((district != null && region == null) || entries.stream().noneMatch(inScope)) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}

		List<AreaCountResponse> regions = count(entries, e -> e.area().region());
		List<AreaCountResponse> districts = region == null ? null
			: count(entries.stream().filter(inRegion).filter(e -> e.area().district() != null).toList(),
				e -> e.area().district());
		List<StoreResponse> stores = stores(entries.stream().filter(inScope).toList());
		List<StoreResponse> repeatStores = stores.stream()
			.filter(s -> s.wins() >= 2)
			.sorted(STORE_ORDER)
			.limit(REPEAT_STORE_LIMIT)
			.toList();
		List<StoreResponse> pins = region == null ? List.of()
			: stores.stream()
				.filter(s -> !StoreArea.ONLINE_REGION.equals(s.region()))
				.filter(s -> s.latitude() != null && s.longitude() != null)
				.sorted(STORE_ORDER)
				.toList();

		int from = entries.stream().mapToInt(e -> e.win().roundNo()).min().orElseThrow();
		int to = entries.stream().mapToInt(e -> e.win().roundNo()).max().orElseThrow();
		return Optional.of(new StoreStatisticsResponse(rank, from, to, regions, districts, repeatStores, pins));
	}

	private Snapshot load(int rank) {
		long count = repository.countByWinRank(rank);
		long maxId = repository.findMaxIdByWinRank(rank).orElse(-1L);
		Snapshot cached = cache.get(rank);
		if (cached != null && cached.count() == count && cached.maxId() == maxId) {
			return cached;
		}
		List<Entry> entries = repository.findWinsByRank(rank).stream()
			.map(w -> new Entry(w, StoreArea.of(w.region(), w.address()), w.name().strip() + "\n" + w.address().strip()))
			.toList();
		Snapshot loaded = new Snapshot(count, maxId, entries);
		cache.put(rank, loaded);
		return loaded;
	}

	private static List<AreaCountResponse> count(List<Entry> entries, Function<Entry, String> key) {
		return entries.stream()
			.collect(Collectors.groupingBy(key, Collectors.counting()))
			.entrySet().stream()
			.map(e -> new AreaCountResponse(e.getKey(), e.getValue().intValue()))
			.sorted(AREA_ORDER)
			.toList();
	}

	/** 판매점별로 묶는다. 표시 값은 가장 최근 당첨 행(회차가 같으면 나중 행)에서. */
	private static List<StoreResponse> stores(List<Entry> entries) {
		Map<String, List<Entry>> byStore = entries.stream()
			.collect(Collectors.groupingBy(Entry::storeKey, LinkedHashMap::new, Collectors.toList()));
		return byStore.values().stream().map(group -> {
			Entry latest = group.stream()
				.max(Comparator.comparingInt((Entry e) -> e.win().roundNo()).thenComparingLong(e -> e.win().id()))
				.orElseThrow();
			StoreWin w = latest.win();
			return new StoreResponse(w.name().strip(), w.address().strip(), latest.area().region(),
				latest.area().district(), group.size(), w.roundNo(), w.latitude(), w.longitude(), w.status());
		}).toList();
	}

}
