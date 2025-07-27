# 2025년 7월 25-27일 작업 기록 통합 요약

## 주요 해결 문제 및 성과

### 1. Tmap API 연동 문제 완전 해결 (25-26일)

- **시간 형식, 파싱 안전성, DTO 완성, ID 생성, API 응답 처리** 등 Tmap API 연동과 관련된 대부분의 문제를 해결하여 안정적인 경로 및 시간 계산의 기반을 마련했습니다.
- 특히, 복잡한 보간법 대신 **Tmap API 응답의 Point Feature(안내점)을 직접 활용**하여 각 식사 시간별 예상 위치(`calculated_location`)를 계산하는 로직으로 변경하여 정확성과 안정성을 크게 향상시켰습니다.
- `predictionType`을 `departure`로 변경하고 `totalTime`을 직접 더하는 방식으로 도착 시간(`calculated_arrival_time`) 계산의 정확성을 확보했습니다.

### 2. Kakao Local API 연동 및 테스트 기반 마련 (27일)

- **Kakao Local API 연동**: `KakaoClient`를 구현하여, `RouteService`가 계산한 예상 위치 좌표를 기반으로 주변 음식점을 검색하는 기능을 추가했습니다.
- **빌드 오류 해결**: `RestTemplate` Bean 중복 정의, `jackson-databind` 의존성 누락 등 빌드 과정에서 발생한 여러 오류를 해결하여 안정적인 개발 환경을 구축했습니다.
- **API 권한 문제 해결**: 카카오 개발자 사이트에서 API 사용 권한을 활성화하여 `403 Forbidden` 에러를 해결했습니다.
- **테스트 효율성 증대**: 카카오 API 응답 전체를 **JSON 파일로 저장**하는 기능을 구현했습니다. 이를 통해 gRPC 응답을 수정하지 않고도 API 연동 결과를 명확하게 확인할 수 있으며, 생성된 파일을 추천 서버와의 연동 테스트 데이터로 활용할 수 있게 되었습니다.

### 3. 빌드 및 IDE 인식 문제 해결 (25-26일)

- Maven 빌드는 성공하지만 STS(Eclipse) IDE에서 gRPC 자동 생성 클래스들을 인식하지 못하는 문제를 `pom.xml` 수정 및 IDE 설정 갱신을 통해 해결했습니다.

## 현재 구현된 기능 (핵심 기능)

1.  **스케줄 생성 (CreateSchedule)**:
    -   사용자 요청을 받아 스케줄 기본 정보 저장
    -   Tmap API 연동을 통한 경로 및 총 소요 시간 계산
    -   `calculated_arrival_time` 정확성 확보
    -   `calculated_location` 정확성 확보
    -   **Kakao Local API 연동**: 계산된 위치 기반으로 주변 음식점 검색
    -   **테스트용 파일 출력**: Kakao API 응답 결과를 `kakao_response_[schedule_id]_[slot_id].json` 형식의 파일로 저장하여 API 연동 결과 확인 용이

2.  **스케줄 목록/상세 조회 및 맛집 선택** 

## 현재 미구현된 기능 (향후 개발 계획)

1.  **추천 서버 연동**: `KakaoClient`를 통해 얻은 음식점 목록을 `RecommendationClient`를 통해 추천 서버로 전달하는 로직 구현
2.  **맛집 추천 결과 조회 (GetRecommendationResults)**: 추천 서버로부터 받은 맛집 추천 결과를 조회하는 기능
3.  **스케줄 정보 업데이트 (RefreshSchedule)**: 스케줄 정보를 수동으로 새로고침하는 기능
4.  **스케줄 수정/삭제 기능**
5.  **고도화 및 안정성 관련**: 캐싱, Rate Limiting, Retry 로직, JWT 토큰 검증 등

## 코드 정리 필요 사항

-   `TmapClient.java`에 임시로 추가했던 디버깅용 파일 저장 로직(`tmap_request_payload.json`, `tmap_response_payload.json`)을 제거해야 합니다.
-   `ScheduleGrpcService.java`에 추가된 카카오 API 응답 파일 저장 로직은 추천 서버 연동이 완료된 후 제거 또는 비활성화하는 것을 검토해야 합니다.