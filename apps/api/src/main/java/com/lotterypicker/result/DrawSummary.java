package com.lotterypicker.result;

import com.lotterypicker.lotto.LottoNumbers;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 추첨 종료 알림에 담는 한 회차 요약. {@code tally}는 대조군을 뺀 추천 줄만 센다.
 * {@code lateCollectedAt}은 늦게 수집했을 때만 값이 있다.
 */
public record DrawSummary(int round, LocalDate drawDate, LottoNumbers numbers, int bonus,
		LocalDateTime lateCollectedAt, ResultTally tally) {
}
