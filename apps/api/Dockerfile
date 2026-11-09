# 운영 이미지. 테스트는 커밋 전에 돌리므로 이미지 빌드에서는 건너뛴다.
# BuildKit 전용 문법(--mount, --chmod)은 쓰지 않는다. NAS의 빌더가 구형일 수 있다.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해 소스만 바뀌면 다운로드를 다시 하지 않는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
	&& cp build/libs/api-*.jar app.jar \
	&& java -Djarmode=tools -jar app.jar extract --destination extracted

FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
# extracted/app.jar 의 매니페스트가 lib/ 를 클래스패스로 가리킨다.
COPY --from=build /workspace/extracted/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
