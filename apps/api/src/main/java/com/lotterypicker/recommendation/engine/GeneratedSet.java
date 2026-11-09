package com.lotterypicker.recommendation.engine;

import java.util.List;

/**
 * @param lines 전략 5줄
 * @param combined 그 5줄의 근거로 만든 근거 조합(0~5줄)
 * @param control 대조군 5줄
 */
public record GeneratedSet(List<GeneratedLine> lines, List<DrawnLine> combined, List<DrawnLine> control) {
}
