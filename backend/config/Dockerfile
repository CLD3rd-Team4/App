# 멀티 스테이지 빌드를 사용하여 이미지 크기 최적화
FROM maven:3.9.6-eclipse-temurin-17 AS build

# 작업 디렉토리 설정
WORKDIR /app

# Maven 파일들을 먼저 복사 (캐시 최적화)
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./

# 의존성 다운로드 (캐시 레이어)
RUN ./mvnw dependency:go-offline -B

# 소스 코드 복사
COPY src/ src/

# 애플리케이션 빌드
RUN ./mvnw clean package -DskipTests -B


FROM eclipse-temurin:17-jre

# curl 설치 (헬스체크용)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*


# 애플리케이션 실행을 위한 사용자 생성 (보안)
RUN addgroup --system spring && adduser --system spring --ingroup spring

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/target/config-0.0.1-SNAPSHOT.jar app.jar

# 소유권 변경
RUN chown spring:spring app.jar

# 사용자 변경
USER spring:spring

# 포트 노출
EXPOSE 8888

# 헬스체크 추가
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8888/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
