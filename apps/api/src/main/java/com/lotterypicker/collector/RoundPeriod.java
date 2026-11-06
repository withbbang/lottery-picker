package com.lotterypicker.collector;

import java.time.LocalDate;

public record RoundPeriod(int round, LocalDate drawDate, LocalDate salesStart, LocalDate salesEnd) {
}
