package com.lotterypicker.recommendation.entity;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** 추천 또는 대조군 한 줄. 지난 추천이 바뀌면 성적표를 믿을 수 없으므로 생성 후 바꾸지 않는다. */
@Getter
@Entity
@Immutable
@Table(name = "daily_recommendation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "rec_date")
	private LocalDate date;

	@Column(name = "round_no")
	private int roundNo;

	@Enumerated(EnumType.STRING)
	private StrategyType strategy;

	@Column(name = "line_index")
	private int lineIndex;

	private int n1;
	private int n2;
	private int n3;
	private int n4;
	private int n5;
	private int n6;

	@Convert(converter = EvidenceConverter.class)
	@Column(columnDefinition = "TEXT")
	private List<String> evidence;

	/** 근거 문구에 적힌 번호. 통계 지표가 있는 전략에만 있고, 나머지는 빈 목록. */
	@Convert(converter = EvidenceNumbersConverter.class)
	@Column(name = "evidence_numbers")
	private List<Integer> evidenceNumbers;

	@Enumerated(EnumType.STRING)
	private Relaxation relaxation;

	@Column(name = "fixed_snapshot")
	private String fixedSnapshot;

	@Column(name = "excluded_snapshot")
	private String excludedSnapshot;

	@Column(name = "history_from_round")
	private int historyFromRound;

	@Column(name = "history_to_round")
	private int historyToRound;

	@Column(name = "is_control")
	private boolean control;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Builder
	private DailyRecommendation(LocalDate date, int roundNo, StrategyType strategy, int lineIndex, LottoNumbers numbers,
			List<String> evidence, List<Integer> evidenceNumbers, Relaxation relaxation, String fixedSnapshot,
			String excludedSnapshot, int historyFromRound, int historyToRound, LocalDateTime createdAt) {
		this.date = date;
		this.roundNo = roundNo;
		this.strategy = strategy;
		this.lineIndex = lineIndex;
		List<Integer> v = numbers.values();
		this.n1 = v.get(0);
		this.n2 = v.get(1);
		this.n3 = v.get(2);
		this.n4 = v.get(3);
		this.n5 = v.get(4);
		this.n6 = v.get(5);
		this.evidence = List.copyOf(evidence);
		this.evidenceNumbers = evidenceNumbers == null ? List.of() : List.copyOf(evidenceNumbers);
		this.relaxation = relaxation;
		this.fixedSnapshot = fixedSnapshot;
		this.excludedSnapshot = excludedSnapshot;
		this.historyFromRound = historyFromRound;
		this.historyToRound = historyToRound;
		this.control = strategy == StrategyType.CONTROL;
		this.createdAt = createdAt;
	}

	public LottoNumbers numbers() {
		return new LottoNumbers(List.of(n1, n2, n3, n4, n5, n6));
	}

	/** 고정 또는 제외 번호가 적용된 줄인지. 성적표의 이론값 비교에서 뺀다. */
	public boolean hasSettings() {
		return !fixedSnapshot.isEmpty() || !excludedSnapshot.isEmpty();
	}

}
