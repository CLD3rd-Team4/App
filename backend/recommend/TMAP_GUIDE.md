# `recommend` 서비스 기능 구현 최종 가이드 (Tmap 경로 계산)

## 1. 개요

이 문서는 `schedule` 서비스에 있던 Tmap 경로 계산 기능을 `recommend` 서비스로 이전하고, 독립적인 경로 계산 전문 서비스로 완성하기 위한 모든 기술적 절차와 코드 설명을 담고 있습니다. `recommend` 서비스 담당자는 이 가이드를 따라 파일 이전, 코드 구현, 테스트를 진행합니다.

**최종 목표:** `schedule` 서비스로부터 gRPC로 스케줄 정보를 받아, Tmap API를 통해 경로 및 식사 좌표를 계산한 후, 그 결과를 다시 gRPC로 반환하는 독립적인 마이크로서비스를 완성합니다.

---

## 2. 이전된 코드 상세 분석 (Code Deep Dive)

`schedule` 서비스에서 아래 파일들을 `recommend` 서비스로 이전했습니다. **코드를 직접 분석하기 전에 이 설명을 먼저 읽으면 전체 구조를 빠르게 파악할 수 있습니다.**

### `client/TmapClient.java`
- **기능:** Spring WebFlux의 `WebClient`를 사용하여 Tmap의 "자동차 경로 예측" API를 비동기 방식으로 호출하는 HTTP 클라이언트입니다.
- **의의:** `recommend` 서비스가 외부 Tmap API와 통신하는 **유일한 창구**입니다. 모든 API 요청/응답 처리가 이 클래스에 캡슐화되어 있어, 향후 Tmap API의 사양이 변경되더라도 이 파일만 수정하면 됩니다.

### `service/TmapCalculationProcessor.java`
- **기능:** 경로 계산의 전체 과정을 총괄하는 **메인 컨트롤러(Orchestrator)** 역할을 합니다.
- **의의:** gRPC로 들어온 요청을 받아 `TmapClient`와 `RouteService`를 순서대로 호출하고, 최종 결과를 만들어내는 핵심 비즈니스 로직을 포함합니다.

### `service/RouteService.java`
- **기능:** Tmap API로부터 받은 경로 데이터(수많은 좌표와 각 구간의 소요 시간)와 사용자가 입력한 식사 시간을 분석하여, **최적의 식사 시간대 좌표를 계산**하는 순수 계산 전문 서비스입니다.
- **의의:** 복잡한 시간/거리 계산 로직을 `TmapCalculationProcessor`로부터 분리하여 코드의 가독성과 유지보수성을 높입니다.

### `dto/*.java` (11개 파일) - 상세 설명
- **기능:** Tmap API 및 서비스 내부에서 사용하는 데이터 전송 객체(DTO)입니다.
- **의의:** JSON/객체 변환을 용이하게 하고, 계층 간 데이터 전달을 명확하게 합니다.
- **주요 DTO 설명:**
    - **`TmapRouteRequest` / `TmapRouteResponse`**: Tmap API와 직접 통신하는 데 사용되는 최상위 요청/응답 DTO입니다.
    - **`TmapRoutesInfo`**: 요청의 핵심 파라미터(출발/도착/경유지, 옵션 등)를 담는 객체입니다.
    - **`Feature`, `Geometry`, `Properties`**: Tmap API 응답의 복잡한 GeoJSON 중첩 구조를 표현합니다. `Feature`가 경로의 각 구간(Point 또는 LineString)을 나타내며, `Properties`에 소요 시간, 거리 등의 핵심 정보가 들어있습니다.
    - **`TmapLocation`, `TmapWaypoint`, `WaypointsContainer`**: Tmap API 요청에 필요한 출발지, 도착지, 경유지 정보를 구조화하여 담습니다.
    - **`MealSlotData`**: `schedule` 서비스에서 전달받은 식사 시간 정보를 `TmapCalculationProcessor` 내부에서 사용하기 위한 DTO입니다. gRPC 메시지를 Java 객체로 변환하여 사용합니다.

### `util/TimeUtil.java`
- **기능:** "오후 01:30"과 같은 한국어 시간 형식을 `LocalDateTime` 객체로 변환하거나, Tmap API가 요구하는 ISO 형식으로 변환하는 등 시간 관련 편의 기능을 제공합니다.
- **의의:** 서비스 전반에 걸쳐 분산된 시간 관련 로직을 한 곳에 모아 일관성을 유지하고 코드 중복을 방지합니다.

---

## 3. `recommend` 서비스 구현 가이드

### 3.1. `recommend.yml` 설정 추가 (`config-repo`)

`recommend` 서비스가 Tmap API를 호출하고 gRPC 서버로 동작하려면, `config-repo` 레포지토리에 **`recommend.yml`** 파일을 생성하고 아래 설정들을 추가해야 합니다.

```yaml
# recommend.yml

server:
  port: 8081 # 다른 서비스와 겹치지 않는 포트 (예시)

spring:
  application:
    name: recommend

# gRPC 서버 설정
grpc:
  server:
    port: 9091 # 다른 서비스와 겹치지 않는 gRPC 포트 (예시)
    reflection-service-enabled: true # gRPC 클라이언트가 서버의 API를 탐색할 수 있도록 허용 (개발 시 유용)

# 외부 Tmap API 설정
external:
  api:
    tmap:
      url: https://apis.openapi.sk.com
      # schedule.yml에 있던 암호화된 Tmap API 키를 여기에 붙여넣으세요.
      key: '{cipher}b4164c17f59386ba11272ff9aaf04f9a184680c6e7362e0772830576667b0bc29b9bfe829ec5e4e4c9a585c861629e77a1c2db6594b9fdea2fc0c3dc4c2e1dc7'

# 로깅 레벨 설정 (개발 중 디버깅을 위해 DEBUG로 설정)
logging:
  level:
    com.mapzip.recommend: DEBUG
```

### 3.2. `recommend.proto` gRPC 인터페이스 정의

`recommend` 서비스가 제공할 gRPC API의 명세(`proto` 파일)를 작성해야 합니다. 참고용으로 복사된 `schedule.proto`와 `ScheduleGrpcService_reference.java`를 활용하여 `RouteRequest` 메시지를 정의할 수 있습니다.

**핵심:** `TmapCalculationProcessor`가 동작하려면 `schedule` 서비스로부터 특정 데이터들이 필요합니다. **`ScheduleGrpcService_reference.java`** 파일의 `processSchedule` 메소드를 보면, `jobData`라는 `Map`에 필요한 데이터들을 담는 과정이 있습니다. 이 `jobData`가 바로 `RouteRequest` 메시지의 설계도입니다.

### 3.3. `RouteService.java` 시그니처 수정 (필수)

코드를 이전하면서 `RouteService`가 `schedule` 서비스의 엔티티를 참조하게 되어 컴파일 오류가 발생합니다. 이 메소드가 gRPC 메시지 타입을 받도록 아래와 같이 수정해야 합니다.

```java
// In: backend/recommend/src/main/java/com/mapzip/recommend/service/RouteService.java

public List<CalculatedLocation> calculateMealLocations(
        TmapRouteResponse tmapResponse,
        List<com.mapzip.recommend.grpc.MealSlot> mealSlots, // gRPC 메시지를 받도록 수정
        LocalDateTime departureDateTime
) { ... }
```

### 3.4. gRPC 서비스 및 비즈니스 로직 구현

- 3.2에서 정의한 `recommend.proto`를 구현하는 `RouteCalculatorGrpcService.java` 클래스를 생성합니다.
- `TmapCalculationProcessor`에서 `ScheduleRepository` 의존성을 제거하고, DB 저장 로직 없이 `RouteResponse` gRPC 메시지를 생성하여 반환하도록 수정합니다.

---

## 4. 참고 파일 목록

- **구현 대상 코드:**
  - `client/TmapClient.java`
  - `service/TmapCalculationProcessor.java`
  - `service/RouteService.java`
  - `dto/*.java` (11개 파일)
  - `util/TimeUtil.java`
- **참고용 파일:**
  - `pom.xml` (원본)
  - `src/main/proto/schedule.proto` (원본)
  - `src/main/java/com/mapzip/recommend/service/ScheduleGrpcService_reference.java` (원본)

이 가이드를 통해 `recommend` 서비스의 역할을 명확히 이해하고 성공적으로 기능을 구현하시기 바랍니다.