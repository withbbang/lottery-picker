package com.lotterypicker.round.entity;

import com.lotterypicker.lotto.LottoNumbers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "lotto_round")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LottoRound {

	@Id
	@Column(name = "round_no")
	private Integer roundNo;

	private int n1;
	private int n2;
	private int n3;
	private int n4;
	private int n5;
	private int n6;

	private int bonus;

	@Column(name = "draw_date")
	private LocalDate drawDate;

	@Column(name = "rank1_count")
	private int rank1Count;
	@Column(name = "rank2_count")
	private int rank2Count;
	@Column(name = "rank3_count")
	private int rank3Count;
	@Column(name = "rank4_count")
	private int rank4Count;
	@Column(name = "rank5_count")
	private int rank5Count;

	@Column(name = "rank1_amount")
	private long rank1Amount;
	@Column(name = "rank2_amount")
	private long rank2Amount;
	@Column(name = "rank3_amount")
	private long rank3Amount;
	@Column(name = "rank4_amount")
	private long rank4Amount;
	@Column(name = "rank5_amount")
	private long rank5Amount;

	private String source;

	@Column(name = "fetched_at")
	private LocalDateTime fetchedAt;

	@Column(name = "store_fetched_at")
	private LocalDateTime storeFetchedAt;

	@Builder
	private LottoRound(int roundNo, LottoNumbers numbers, int bonus, LocalDate drawDate,
		int rank1Count, int rank2Count, int rank3Count, int rank4Count, int rank5Count,
		long rank1Amount, long rank2Amount, long rank3Amount, long rank4Amount, long rank5Amount,
		String source, LocalDateTime fetchedAt, LocalDateTime storeFetchedAt) {
		if (numbers.contains(bonus) || bonus < LottoNumbers.MIN || bonus > LottoNumbers.MAX) {
			throw new IllegalArgumentException("보너스 번호가 올바르지 않습니다.");
		}
		this.roundNo = roundNo;
		List<Integer> v = numbers.values();
		this.n1 = v.get(0);
		this.n2 = v.get(1);
		this.n3 = v.get(2);
		this.n4 = v.get(3);
		this.n5 = v.get(4);
		this.n6 = v.get(5);
		this.bonus = bonus;
		this.drawDate = drawDate;
		this.rank1Count = rank1Count;
		this.rank2Count = rank2Count;
		this.rank3Count = rank3Count;
		this.rank4Count = rank4Count;
		this.rank5Count = rank5Count;
		this.rank1Amount = rank1Amount;
		this.rank2Amount = rank2Amount;
		this.rank3Amount = rank3Amount;
		this.rank4Amount = rank4Amount;
		this.rank5Amount = rank5Amount;
		this.source = source;
		this.fetchedAt = fetchedAt;
		this.storeFetchedAt = storeFetchedAt;
	}

	public void markStoresFetched(LocalDateTime at) {
		this.storeFetchedAt = at;
	}

	public LottoNumbers numbers() {
		return new LottoNumbers(List.of(n1, n2, n3, n4, n5, n6));
	}

}
