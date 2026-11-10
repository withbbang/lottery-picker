package com.lotterypicker.recommendation.engine;

/**
 * 한 지표에서 번호 하나의 값과 순위.
 * @param rank 공동 순위(1, 2, 2, 4…)
 * @param tied 같은 값의 번호가 또 있는지
 */
public record RankedNumber(int number, int value, int rank, boolean tied) {
}
