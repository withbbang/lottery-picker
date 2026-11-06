---
paths:
  - "apps/api/**"
---
# apps/api — Spring Boot 4.1 / Java 17 / Gradle

- 패키지: `com.lotterypicker.<domain>` 아래 `controller` / `service` / `repository` / `dto` / `entity`. 도메인 단위 수직 분할, 공통은 `com.lotterypicker.common`.
- 레이어: Controller는 요청 검증·DTO 매핑·응답만. 비즈니스 로직은 Service. Repository는 Spring Data JPA 인터페이스.
- DTO: 요청 `XxxRequest`, 응답 `XxxResponse`, **Java `record`** 로. Entity는 Controller 밖으로 절대 노출하지 않는다.
- 응답 래퍼·에러 코드·페이징은 `api-conventions` 스킬 그대로. 새 포맷 만들지 않는다.
- 검증: `@Valid` + `jakarta.validation`. 도메인 예외는 `BusinessException(ErrorCode)` 하나를 상속, `GlobalExceptionHandler`(`@RestControllerAdvice`) 한 곳에서 변환.
- 설정: `application.yml` + 프로파일 `application-local.yml`(H2) / `application-prod.yml`(MySQL). 비밀값은 `${ENV_VAR}` 참조만, 값 하드코딩 금지.
- Lombok: `@Getter` `@Builder` `@RequiredArgsConstructor` `@NoArgsConstructor(access = PROTECTED)` 허용. **`@Data` `@Setter` 금지** (특히 entity).
- 의존성 주입은 생성자 주입만 (`@RequiredArgsConstructor`). 필드 `@Autowired` 금지.
- 트랜잭션: Service 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`.
- 테스트: Service는 Mockito 단위 테스트, Controller는 `@WebMvcTest`, Repository는 `@DataJpaTest`. `@SpringBootTest`는 꼭 필요할 때만.
- 난수: `java.security.SecureRandom` 을 빈으로 등록해 주입. `new Random()` 직접 생성 금지 → 테스트에서 시드 고정 불가.
