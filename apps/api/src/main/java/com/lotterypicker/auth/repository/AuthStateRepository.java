package com.lotterypicker.auth.repository;

import com.lotterypicker.auth.entity.AuthState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AuthStateRepository extends JpaRepository<AuthState, Integer> {

	/** 동시 로그인 요청이 실패 횟수나 사용 구간을 덮어쓰지 않도록 행을 잠그고 읽는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from AuthState s")
	Optional<AuthState> findForUpdate();

}
