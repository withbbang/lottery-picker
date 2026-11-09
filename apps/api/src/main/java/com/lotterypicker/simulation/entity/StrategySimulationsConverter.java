package com.lotterypicker.simulation.entity;

import com.lotterypicker.simulation.StrategySimulation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 전략별 시뮬레이션 집계 목록을 JSON 배열 문자열로 저장한다. */
@Converter
class StrategySimulationsConverter implements AttributeConverter<List<StrategySimulation>, String> {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<StrategySimulation>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<StrategySimulation> strategies) {
		return MAPPER.writeValueAsString(strategies);
	}

	@Override
	public List<StrategySimulation> convertToEntityAttribute(String json) {
		return List.copyOf(MAPPER.readValue(json, TYPE));
	}

}
