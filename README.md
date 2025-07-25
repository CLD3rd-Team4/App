# 2025년 7월 25-26일 작업 기록 통합 요약

## 주요 해결 문제 및 성과

### 1. Tmap API 연동 문제 완전 해결

**초기 문제 상황**:
- Tmap API 요청 시 `predictionTime` 형식 오류
- Tmap API 응답 `arrivalTime` 파싱 오류
- 응답 `coordinates` 타입 안전성 문제
- `Properties` DTO `pointType` 필드 누락
- `MealTimeSlot` ID 중복 오류

**해결 내용**:
1. **요청/응답 시간 형식 통일**: `TimeUtil.java`에서 Tmap API가 요구하는 `yyyy-MM-dd'T'HH:mm:ssZ` 형식(타임존 오프셋에 콜론 없음)으로 `ISO_FORMATTER` 수정 및 `TMAP_RESPONSE_FORMATTER` 추가
2. **응답 파싱 안전성 강화**: `RouteService.java`에서 `coordinates` 필드 파싱 시 타입 검사 및 안전한 형변환 로직 추가
3. **DTO 완성도 개선**: `Properties.java`에 누락된 `pointType` 필드 추가
4. **ID 생성 로직 수정**: `MealTimeSlot` 엔티티의 ID를 클라이언트 요청 대신 `UUID.randomUUID().toString()`으로 서버에서 고유 생성
5. **API 응답 처리 최적화**: `TmapClient.java`에서 `RestTemplate` 자동 변환 대신 `String`으로 직접 받은 후 `ObjectMapper`로 수동 변환하여 정확한 JSON 매핑 보장

### 2. 경로 계산 결과 (`calculatedLocation`) 정확성 확보

**문제**: 스케줄 생성 시 `meal_time_slots` 테이블의 `calculated_location` 필드에 식사 시간 지점의 정확한 좌표가 아닌 출발지 좌표만 저장되는 현상

**해결 과정**:
1. **시간 계산 로직 수정**: `TimeUtil.parseKoreanAmPmToFuture`가 `departureDateTime`을 기준으로 식사 시간을 파싱하도록 개선
2. **좌표 파싱 로직 강화**: `RouteService.java`의 `parseCoordinates` 메소드가 `Point` Feature의 단일 좌표와 `LineString` Feature의 중첩 좌표를 모두 처리하도록 분기 로직 구현
3. **DTO 매핑 완성**: `Properties.java`, `Geometry.java`, `Feature.java`의 모든 필드에 `@JsonProperty` 어노테이션 추가
4. **최종 해결**: 복잡한 보간법 대신 **Tmap API 응답의 Point Feature(안내점)들을 직접 활용**하여 각 식사 시간과 가장 가까운 경로상의 실제 위치 좌표를 찾는 로직으로 완전 변경

### 3. 도착 시간 계산 (`calculated_arrival_time`) 정확성 확보

**문제**: Tmap API 요청 시 `predictionType`을 `arrival`로 설정했을 때 비효율적인 경로 계산으로 인한 부정확한 도착 시간

**해결**: 
- `ScheduleMapper.java`에서 `predictionType`을 `departure`로 변경
- `ScheduleGrpcService.java`에서 `departureDateTime`에 Tmap API의 `totalTime`을 더하여 정확한 도착 시간 직접 계산

### 4. 빌드 및 IDE 인식 문제 해결

**문제**: Maven 빌드는 성공하지만 STS(Eclipse) IDE에서 gRPC 자동 생성 클래스들을 인식하지 못하는 문제

**해결**:
- `TmapClient.java`에서 `@RequiredArgsConstructor` 제거 후 명시적 생성자 정의
- `pom.xml`에서 `build-helper-maven-plugin` 중복 선언 제거
- `target` 폴더 삭제 후 Maven 프로젝트 업데이트 및 IDE 재시작으로 최종 해결

## 현재 구현된 기능 (핵심 기능)

1. **스케줄 생성 (CreateSchedule)**:
   - 사용자 요청을 받아 스케줄 기본 정보 저장
   - Tmap API 연동을 통한 경로 및 총 소요 시간 계산
   - `calculated_arrival_time` 정확성 확보: Tmap API의 totalTime을 기반으로 정확한 도착 시간 계산 및 저장
   - `calculated_location` 정확성 확보: Tmap API 응답의 Point Feature(안내점)들을 활용하여 각 식사 시간과 가장 가까운 경로상의 위치 좌표 계산 및 저장
   - MealTimeSlot 엔티티의 ID를 서버에서 고유하게 생성
   - Tmap API 응답 파싱 시 ObjectMapper를 통한 안전한 JSON 변환 및 알 수 없는 필드 무시 처리
   - Tmap API predictionType을 departure로 설정하여 올바른 경로 요청

2. **스케줄 목록 조회 (GetScheduleList)**:
   - 사용자 ID 기반으로 스케줄 요약 목록 조회

3. **스케줄 상세 조회 (GetScheduleDetail)**:
   - 특정 스케줄의 상세 정보 조회 (사용자 권한 확인 포함)

4. **맛집 선택 반영 (SelectRestaurant)**:
   - 특정 시간 슬롯에 대한 맛집 선택 및 저장

5. **기본적인 외부 API 연동**:
   - Tmap 미래 경로 예측 API 연동 (경로 및 시간 정보 획득)

6. **빌드 및 IDE 인식 문제 해결**:
   - Maven 빌드 시 발생했던 TmapClient.java의 컴파일 에러 해결
   - pom.xml의 build-helper-maven-plugin 중복 선언 문제 해결
   - STS(Eclipse) IDE에서 gRPC 생성 클래스 인식 문제 해결 (IDE 설정 갱신 필요)

## 현재 미구현된 기능 (향후 개발 계획)

memo.md의 "개발 우선순위" 및 "스케줄 서비스 gRPC API 명세서"를 기반으로 합니다.

1. **맛집 추천 결과 조회 (GetRecommendationResults)**:
   - 추천 서버로부터 받은 맛집 추천 결과를 조회하는 기능 (Kafka 연동 및 Valkey 저장 로직 필요)

2. **스케줄 정보 업데이트 (RefreshSchedule)**:
   - 스케줄 정보를 수동으로 새로고침하는 기능

3. **추천서버로 맛집 추천 요청 (RequestRestaurantRecommendation)**:
   - 스케줄 서비스에서 추천 서버로 맛집 추천을 요청하는 송신 API (Kafka 메시지 전송 로직 필요)

4. **Kakao 로컬 검색 API 연동**:
   - 계산된 예상 식사/간식 지점 주변의 음식점을 검색하는 기능

5. **스케줄 수정/삭제 기능**:
   - 기존 스케줄을 수정하거나 삭제하는 기능 (CRUD의 Update, Delete)

6. **고도화 및 안정성 관련**:
   - 캐싱 전략 (동일한 출발지-도착지-경유지 조합)
   - 외부 API 호출 Rate Limiting 및 Retry 로직
   - 사용자 권한 검증 강화 (JWT 토큰 검증 등)
   - 외부 API 장애 시 대응 방안
   - 모니터링 및 로깅 강화

## 데이터 저장 구조

경로 계산 결과(예상 식사/간식 위치)는 `MealTimeSlot` 엔티티의 `calculatedLocation` 필드에 JSON 형태로 저장됩니다. `MealTimeSlot.java` 엔티티의 `calculatedLocation` 필드는 `@JdbcTypeCode(SqlTypes.JSON)` 어노테이션과 `columnDefinition = "jsonb"` 설정을 통해 PostgreSQL의 JSONB 타입으로 매핑되어 있습니다. 따라서 데이터베이스의 `meal_time_slots` 테이블에 각 식사 시간 슬롯별로 계산된 위도(lat)와 경도(lon) 정보가 JSON 문자열로 저장됩니다.

## 코드 정리 필요 사항

현재 `TmapClient.java`에 임시로 추가했던 디버깅용 파일 저장 로직(`tmap_request_payload.json`, `tmap_response_payload.json`)을 제거해야 합니다.

---

**결론**: 지금까지의 작업으로 스케줄 서비스의 핵심 기능인 스케줄 생성 시 경로 및 식사 시간 위치 계산이 정상적으로 동작하게 되었습니다. Tmap API 연동 문제가 완전히 해결되었으며, `calculated_arrival_time`과 `calculated_location` 모두 정확하게 계산되어 저장됩니다.