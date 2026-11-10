package com.lotterypicker.recommendation.entity;

import com.lotterypicker.recommendation.EvidenceNumbers;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/** 근거 번호 목록을 "7,12" 형식 문자열로 저장한다. 빈 목록은 빈 문자열. */
@Converter
class EvidenceNumbersConverter implements AttributeConverter<List<Integer>, String> {

	@Override
	public String convertToDatabaseColumn(List<Integer> numbers) {
		return EvidenceNumbers.toColumn(numbers);
	}

	@Override
	public List<Integer> convertToEntityAttribute(String column) {
		return EvidenceNumbers.fromColumn(column);
	}

}
