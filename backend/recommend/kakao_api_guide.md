# Kakao API gRPC 서비스 이전 및 구축 가이드

## 1. 개요

이 문서는 **Kakao API 호출 기능을 `recommend` 서비스로 이전**하고, 두 서비스가 **gRPC로 통신**하는 아키텍처로 전환하는 방법을 안내합니다.

-   **`recommend` 서비스 (gRPC 서버):**
    -   Kakao Local API 호출 로직을 소유하고, 이 기능을 gRPC 서비스(`KakaoApiService`)로 외부에 제공합니다.
-   **`schedule` 서비스 (gRPC 클라이언트):**
    -   기존의 자체 Kakao API 호출 로직을 제거하고, `recommend` 서비스가 제공하는 gRPC 서비스를 호출하여 필요할 때마다 음식점 정보를 요청합니다.

## 2. `recommend` 서비스: gRPC 서버 구축

### 2.1. 이전 대상 파일 목록

`schedule` 서비스에서 아래 파일들을 `recommend` 서비스의 적절한 패키지 경로로 **모두 이전**합니다.

-   **Proto 파일 (gRPC 계약):**
    -   `schedule/src/main/proto/schedule.proto`
-   **gRPC 서비스 구현체:**
    -   `schedule/src/main/java/com/mapzip/schedule/service/KakaoApiGrpcService.java`
-   **핵심 비즈니스 로직:**
    -   `schedule/src/main/java/com/mapzip/schedule/service/KakaoApiService.java`
-   **Kakao API REST 클라이언트:**
    -   `schedule/src/main/java/com/mapzip/schedule/client/KakaoClient.java`
-   **DTO (Data Transfer Objects):**
    -   `schedule/src/main/java/com/mapzip/schedule/dto/kakao/` (패키지 전체)

### 2.2. `pom.xml` 의존성 및 플러그인 추가

`recommend` 서비스의 `pom.xml`에 gRPC **서버** 및 관련 의존성을 추가합니다.

-   **필수 플러그인 (`protobuf-maven-plugin`):** `.proto` 파일로부터 gRPC 코드 자동 생성을 위해 반드시 필요합니다.
-   **필수 의존성:**

```xml
<!-- gRPC Server Starter -->
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-server-spring-boot-starter</artifactId>
    <version>2.15.0.RELEASE</version>
</dependency>

<!-- WebClient for Kakao REST API call -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- Protobuf & gRPC Stubs -->
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>3.25.3</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-stub</artifactId>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
</dependency>
<dependency>
    <groupId>javax.annotation</groupId>
    <artifactId>javax.annotation-api</artifactId>
    <version>1.3.2</version>
</dependency>
```

### 2.3. `application.properties` 설정

`recommend` 서비스의 `application.properties`에 gRPC 서버 포트와 Kakao API 키를 설정합니다.

```properties
# gRPC 서버 포트 설정
grpc.server.port=9091

# Kakao Local API 설정
external.api.kakao.url=https://dapi.kakao.com
external.api.kakao.key=KakaoAK {여기에_카카오_REST_API_키를_입력하세요}
```

## 3. `schedule` 서비스: gRPC 클라이언트 전환

### 3.1. 코드 수정 및 삭제

`schedule` 서비스는 더 이상 Kakao API를 직접 호출하지 않으므로, 아래 파일들을 **삭제**합니다.

-   `src/main/java/com/mapzip/schedule/service/KakaoApiGrpcService.java`
-   `src/main/java/com/mapzip/schedule/service/KakaoApiService.java`
-   `src/main/java/com/mapzip/schedule/client/KakaoClient.java`
-   `src/main/java/com/mapzip/schedule/dto/kakao/` (패키지 전체)

### 3.2. `application.properties` 설정 수정

`schedule` 서비스의 `application.properties`에서 gRPC 클라이언트(`kakao-api-service`)가 `recommend` 서비스를 바라보도록 주소를 변경합니다.

```properties
# gRPC 클라이언트 설정: recommend 서비스의 KakaoApiService 호출
grpc.client.kakao-api-service.address=static://localhost:9091
grpc.client.kakao-api-service.negotiation-type=plaintext
```

### 3.3. gRPC 호출 코드 확인

`ScheduleGrpcService.java`의 gRPC 호출 코드는 그대로 유지됩니다. `@GrpcClient("kakao-api-service")` 어노테이션이 `application.properties`에 설정된 새로운 주소로 요청을 보내주므로 코드 변경은 필요 없습니다.

```java
// ScheduleGrpcService.java
@GrpcClient("kakao-api-service")
private KakaoApiServiceGrpc.KakaoApiServiceBlockingStub kakaoApiServiceBlockingStub;

// ...

private void executeTmapAndKakaoProcess(Schedule schedule, GeneratedMessageV3 request) {
    // ...
    // 이 코드는 이제 recommend 서비스의 gRPC를 호출하게 됩니다.
    kakaoApiServiceBlockingStub.searchRestaurants(searchRequest);
    // ...
}
```