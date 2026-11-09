package com.lotterypicker.simulation.repository;

import com.lotterypicker.simulation.entity.SimulationResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationResultRepository extends JpaRepository<SimulationResult, Integer> {
}
