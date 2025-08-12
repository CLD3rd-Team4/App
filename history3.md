## 2025년 8월 12일 - 프론트엔드 안정화 및 백엔드 통신 문제 해결 시도

### 1. 프론트엔드 코드 및 타입 안정화
*   **'도착 여유 시간' UI 복원:** `ScheduleCreateRequiredScreen.tsx`에 '30분, 1시간, 2시간' 선택 버튼을 다시 추가하여 사용자 요구사항을 반영했습니다.
*   **스케줄 생성/수정 로직 개선:** `useSchedule.ts`와 `services/api.ts` 간의 스케줄 생성 및 수정 API 호출 로직을 일관성 있게 수정하여, 데이터가 올바르게 전달되고 상태가 업데이트되도록 했습니다.
*   **타입 불일치 및 빌드 에러 해결:** `history2.md`에서 발생했던 대규모 타입 리팩토링 이후의 연쇄적인 빌드 에러들을 모두 해결했습니다. 특히 `VisitedRestaurantsScreen.tsx`와 `frontend/types/index.ts` 파일의 타입 정의 충돌 문제를 해결하고, `ScheduleCreateScreen.tsx`에서 UI 컴포넌트로부터 받은 데이터를 API Payload 형식으로 변환하는 로직을 강화했습니다.
*   **`MealType` 등 핵심 타입 복구:** `index.ts` 파일이 이전 버전으로 되돌려지면서 사라졌던 `MealType`, `SchedulePayload`, `LocationInfo` 등 스케줄 기능에 필수적인 타입 정의들을 다시 추가하여 프론트엔드 코드의 일관성을 확보했습니다.
*   **`log.txt` 에러 메시지 개선:** `lib/interceptor.ts`의 에러 처리 로직을 개선하여, 네트워크 연결 실패 시 `TypeError` 대신 명확한 '네트워크 연결 불가' 메시지가 표시되도록 했습니다.

### 2. 백엔드 통신 문제 진단 및 해결 시도
*   **초기 연결 문제 해결:** `frontend/.env.local` 파일에 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8000`을 설정하여 프론트엔드가 로컬 Envoy/백엔드 서버로 API 요청을 보낼 수 있도록 했습니다.
*   **현재 문제 진단:**
    *   프론트엔드에서 `http://localhost:8000/schedule`로 요청을 보낼 때 `net::ERR_FAILED` 또는 `Access to XMLHttpRequest ... has been blocked by CORS policy` 에러가 발생하고 있습니다.
    *   Envoy 로그에서는 `OPTIONS` 및 `POST` 요청이 `200 OK`로 처리되지만, Spring 로그에서는 `x-user-id header not found` 경고가 발생하며 DB에 데이터가 저장되지 않습니다.
    *   **근본 원인:** Envoy의 `envoy.yaml` 설정에 CORS 관련 헤더(`Access-Control-Allow-Credentials: true` 등)와 JWT 인증 처리(`envoy.filters.http.jwt_authn` 필터)가 누락되어 있기 때문으로 파악됩니다. Envoy가 브라우저의 CORS 정책을 만족시키지 못하고, JWT를 파싱하여 `x-user-id` 헤더로 Spring에 전달하지 못하고 있습니다.
*   **`envoy.yaml` 수정:** Envoy가 CORS를 올바르게 처리하고 JWT를 `x-user-id` 헤더로 변환하여 백엔드에 전달하도록 `envoy.yaml` 파일을 업데이트했습니다.

### 3. 현재 상태
*   프론트엔드 코드와 타입은 현재 일관성을 유지하며 빌드에 성공합니다.
*   Envoy 설정이 업데이트되었으나, **Envoy 자체가 현재 실행되지 않는 문제**가 발생하고 있습니다. 이 문제가 해결되어야 프론트엔드와 백엔드 간의 통신이 가능해집니다.
*   JWT 토큰을 쿠키에 설정하는 것은 Envoy가 정상 작동하여 `x-user-id` 헤더를 Spring에 전달하는 단계 이후에 필요한 작업입니다.
