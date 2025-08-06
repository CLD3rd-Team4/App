
# `recommend` 서비스 기능 이전 및 구현 가이드 (Tmap 경로 계산)

## 1. 개요

이 문서는 `schedule` 서비스에 있던 Tmap 경로 계산 기능을 `recommend` 서비스로 이전하고, 독립적인 경로 계산 전문 서비스로 완성하기 위한 모든 기술적 절차와 코드 설명을 담고 있습니다. `recommend` 서비스 담당자는 이 가이드를 따라 파일 이전, 코드 구현, 테스트를 진행합니다.

**최종 목표:** `schedule` 서비스로부터 gRPC로 스케줄 정보를 받아, Tmap API를 통해 경로 및 식사 좌표를 계산한 후, 그 결과를 다시 gRPC로 반환하는 독립적인 마이크로서비스를 완성합니다.

---

## 2. 핵심 아키텍처: `MealTimeSlot` 데이터 처리 (매우 중요)

`MealTimeSlot` 정보는 두 서비스 모두에 필요하지만, 그 형태와 역할이 다릅니다. 이 개념을 이해하는 것이 서비스 분리의 핵심입니다.

- **`schedule` 서비스의 `MealTimeSlot`**: **JPA 엔티티 (`@Entity`)** 입니다. 데이터베이스와 직접 매핑되는 **데이터의 원본(Source of Truth)** 이므로, **`schedule` 서비스에 반드시 유지되어야 합니다.**

- **`recommend` 서비스의 `MealSlot`**: **gRPC 메시지 (DTO)** 입니다. `schedule` 서비스로부터 경로 계산에 **필요한 데이터만 전달받는** 단순 데이터 객체입니다.

**잘못된 설계:** `recommend` 서비스가 `schedule` 서비스의 JPA 엔티티(`com.mapzip.schedule.entity.MealTimeSlot`)를 직접 참조하는 것은 서비스 간의 강한 결합(Tight Coupling)을 유발하므로 **절대 금물**입니다.

**올바른 설계:**
1. `schedule` 서비스는 DB에서 `MealTimeSlot` **엔티티**를 조회합니다.
2. 조회한 엔티티에서 필요한 데이터(시간, 반경 등)를 꺼내 `MealSlot` **gRPC 메시지**에 담습니다.
3. 이 gRPC 메시지를 `recommend` 서비스로 전송합니다.
4. `recommend` 서비스는 gRPC 메시지를 받아 계산을 수행합니다.

---

## 3. 이전 대상 코드 상세 분석 (Code Deep Dive)

`schedule` 서비스에서 아래 파일들을 `recommend` 서비스로 이전했습니다. 각 컴포넌트의 기능과 서비스 내에서의 역할을 설명합니다.

### `client/TmapClient.java`
- **기능:** Spring WebFlux의 `WebClient`를 사용하여 Tmap의 "자동차 경로 예측" API를 비동기 방식으로 호출하는 HTTP 클라이언트입니다.
- **의의:** `recommend` 서비스가 외부 Tmap API와 통신하는 **유일한 창구**입니다.

### `service/TmapCalculationProcessor.java`
- **기능:** 경로 계산의 전체 과정을 총괄하는 **메인 컨트롤러(Orchestrator)** 역할을 합니다.
- **의의:** gRPC로 들어온 요청을 받아 `TmapClient`와 `RouteService`를 순서대로 호출하고, 최종 결과를 만들어내는 핵심 비즈니스 로직을 포함합니다.

### `service/RouteService.java`
- **기능:** Tmap API 응답과 식사 시간 정보를 바탕으로 **최적의 식사 시간대 좌표를 계산**하는 순수 계산 전문 서비스입니다.
- **의의:** 복잡한 계산 로직을 분리하여 코드의 가독성과 유지보수성을 높입니다.
- **[수정 필요]** 이 서비스의 `calculateMealLocations` 메소드는 현재 다른 서비스의 엔티티를 참조하고 있어 반드시 수정이 필요합니다. (아래 6.1. 항목 참고)

### `dto/*.java` (11개 파일)
- **기능:** Tmap API 및 서비스 내부에서 사용하는 데이터 전송 객체(DTO)입니다.
- **의의:** JSON/객체 변환을 용이하게 하고, 계층 간 데이터 전달을 명확하게 합니다.
- **주요 DTO:** `TmapRouteRequest`/`Response`, `Feature`, `Geometry`, `Properties`, `MealSlotData` 등

### `util/TimeUtil.java`
- **기능:** 시간 관련 편의 기능을 제공합니다.
- **의의:** 시간 관련 로직을 한 곳에 모아 일관성을 유지하고 코드 중복을 방지합니다.

---

## 4. 참고용 파일 (`pom.xml`, `schedule.proto`)

`recommend` 서비스의 `pom.xml`과 `proto` 파일을 구성하는 데 참고할 수 있도록 `schedule` 서비스의 원본 파일을 복사해 두었습니다.

- **`backend/recommend/pom.xml`**: `schedule` 서비스의 `pom.xml`을 그대로 복사했습니다. **`spring-boot-starter-webflux` 의존성이 이미 포함되어 있으므로 그대로 사용**하고, 불필요한 의존성(예: `spring-boot-starter-data-jpa` 등 DB 관련)은 제거하여 최적화하는 것을 권장합니다.
- **`backend/recommend/src/main/proto/schedule.proto`**: `schedule` 서비스의 전체 gRPC 인터페이스 정의입니다. 이 파일을 참고하여 `recommend` 서비스가 제공해야 할 `RouteCalculatorService`와 관련 메시지 타입(`RouteRequest`, `RouteResponse` 등)을 정의하는 **새로운 `recommend.proto` 파일을 작성**해야 합니다.

---

## 5. 프론트엔드 연동 시나리오 (Frontend Context)

`recommend` 서비스는 프론트엔드와 직접 통신하지 않지만, 최종적으로 프론트엔드의 사용자 경험을 완성하는 중요한 역할을 합니다.

1.  **스케줄 생성 (`app/schedule/create/page.tsx`):** 사용자가 스케줄 정보를 입력하면 `schedule` 서비스 DB에 저장됩니다.
2.  **경로 계산 요청 (`components/screens/ScheduleListScreen.tsx`):** 사용자가 '선택' 버튼을 누르면 `schedule` 서비스가 `recommend` 서비스의 gRPC를 호출하여 작업이 시작됩니다.
3.  **결과 표시 (`components/screens/ScheduleSummaryScreen.tsx`):** `recommend` 서비스의 계산 결과가 `schedule` 서비스를 거쳐 프론트엔드의 타임라인에 표시됩니다.

---

## 6. `recommend` 서비스 구현 가이드

### 6.1. `RouteService.java` 시그니처 수정 (필수)

이전된 `RouteService`의 `calculateMealLocations` 메소드는 `schedule`의 엔티티를 직접 참조하고 있어 컴파일 오류가 발생합니다. 아래와 같이 **gRPC 메시지 타입을 받도록** 시그니처를 수정해야 합니다.

```java
// In: backend/recommend/src/main/java/com/mapzip/recommend/service/RouteService.java

// AS-IS (Incorrect - 컴파일 오류 발생)
/*
public List<CalculatedLocation> calculateMealLocations(
        TmapRouteResponse tmapResponse,
        List<com.mapzip.schedule.entity.MealTimeSlot> mealSlots, // 다른 서비스의 엔티티 참조 불가!
        LocalDateTime departureDateTime
) { ... }
*/

// TO-BE (Correct)
public List<CalculatedLocation> calculateMealLocations(
        TmapRouteResponse tmapResponse,
        List<com.mapzip.recommend.grpc.MealSlot> mealSlots, // gRPC 메시지를 받도록 수정
        LocalDateTime departureDateTime
) {
    log.info("Starting meal location calculation for {} meal slots.", mealSlots.size());
    List<TimePoint> timePoints = createTimePointsFromResponse(tmapResponse);

    List<CalculatedLocation> calculatedLocations = new ArrayList<>();
    for (var mealSlot : mealSlots) { // mealSlot은 이제 gRPC 메시지 타입입니다.
        try {
            // gRPC 메시지의 필드를 사용하여 로직 수행
            LocalDateTime mealDateTime = TimeUtil.parseKoreanAmPmToFuture(mealSlot.getScheduledTime(), departureDateTime.toLocalDate());
            // ... (이하 로직은 대부분 동일) ...
        } catch (Exception e) {
            log.error("Error calculating location for meal slot: {}", mealSlot.getSlotId(), e);
        }
    }
    return calculatedLocations;
}
```

### 6.2. gRPC 인터페이스 구현

- `schedule.proto`를 참고하여 `recommend.proto` 파일을 작성합니다.
- `RouteCalculatorService`를 구현하는 gRPC 서비스 클래스를 작성하여 `TmapCalculationProcessor`와 연결합니다.

### 6.3. `TmapCalculationProcessor` 수정

- `schedule`의 `ScheduleRepository`에 대한 의존성을 제거합니다.
- `calculateAndSave` 메소드를 `calculateAndBuildResponse`와 같이 변경하여, DB 저장 로직 없이 `RouteResponse` gRPC 메시지를 생성하여 반환하도록 수정합니다.

---

## 7. `schedule` 서비스 구현 가이드

`schedule` 서비스 담당자는 `recommend` 서비스의 gRPC 클라이언트를 호출하는 로직을 구현해야 합니다.

### 7.1. 엔티티를 gRPC 메시지로 변환 (매핑)

`ScheduleGrpcService`의 `processSchedule` 메소드에서 `recommend` 서비스를 호출하기 전에, DB에서 조회한 엔티티 리스트를 gRPC 메시지 리스트로 변환하는 과정이 필요합니다.

```java
// In: backend/schedule/src/main/java/com/mapzip/schedule/service/ScheduleGrpcService.java

// ...
// 1. DB에서 Schedule과 연관된 MealTimeSlot 엔티티 리스트를 조회합니다.
List<com.mapzip.schedule.entity.MealTimeSlot> mealTimeSlotEntities = schedule.getMealTimeSlots();

// 2. 엔티티 리스트를 gRPC 메시지 리스트로 변환합니다.
List<com.mapzip.recommend.grpc.MealSlot> mealSlotMessages = mealTimeSlotEntities.stream()
        .map(entity -> com.mapzip.recommend.grpc.MealSlot.newBuilder()
                .setSlotId(entity.getId())
                .setScheduledTime(entity.getScheduledTime())
                .setRadius(entity.getRadius())
                .setMealType(entity.getMealType()) // Enum 타입 변환이 필요할 수 있음
                .build())
        .collect(Collectors.toList());

// 3. 변환된 메시지 리스트를 RouteRequest에 담아 전송합니다.
RouteRequest.Builder routeRequestBuilder = RouteRequest.newBuilder()
    // ... 다른 필드들 설정 ...
    .addAllMealSlots(mealSlotMessages);

// 4. gRPC 클라이언트 호출
RouteResponse routeResponse = recommendClient.calculateRoute(routeRequestBuilder.build());
// ...
```

