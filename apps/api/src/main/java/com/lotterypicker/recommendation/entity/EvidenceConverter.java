package com.lotterypicker.recommendation.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 근거 문구 목록을 JSON 배열 문자열로 저장한다. 읽은 목록은 수정할 수 없다. */
@Converter
class EvidenceConverter implements AttributeConverter<List<String>, String> {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<String> evidence) {
		return MAPPER.writeValueAsString(evidence);
	}

	@Override
	public List<String> convertToEntityAttribute(String json) {
		return List.copyOf(MAPPER.readValue(json, TYPE));
	}

}
