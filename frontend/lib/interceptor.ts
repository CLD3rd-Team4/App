// src/lib/interceptor.ts
import axios, { AxiosInstance, AxiosRequestConfig, AxiosError } from "axios";

// 1) 런타임(hostname) 기준 자동 전환
const LOCAL_BASE = "http://localhost:9090";
const PROD_BASE  = "https://api.mapzip.shop";

// 2) 빌드타임 env 가 있으면 그걸 최우선으로 사용 (선택)
const buildTimeBase = process.env.NEXT_PUBLIC_API_BASE_URL;

// 3) 최종 baseURL 결정 로직 (env.js 없이 동작)
function resolveBaseURL() {
  if (buildTimeBase) return buildTimeBase; // 빌드타임 override
  if (typeof window === "undefined") return LOCAL_BASE; // SSR/export 안전망
  const host = window.location.hostname;
  // 로컬 정적서버(ex: localhost:4173, 127.0.0.1) → 로컬 백엔드
  if (host === "localhost" || host === "127.0.0.1") return LOCAL_BASE;
  // 그 외(배포 도메인 등) → PROD
  return PROD_BASE;
}

const api: AxiosInstance = axios.create({
  baseURL: resolveBaseURL(),
  withCredentials: true,
  timeout: 15000,
  headers: { "X-Requested-With": "XMLHttpRequest" },
});

let isRefreshing = false;

type Subscriber = { resolve: (v?: any) => void; reject: (e?: any) => void };
let refreshSubscribers: Subscriber[] = [];

function subscribeTokenRefresh(
  resolve: Subscriber["resolve"],
  reject: Subscriber["reject"]
) {
  refreshSubscribers.push({ resolve, reject });
}
function onTokenRefreshed() {
  refreshSubscribers.forEach(({ resolve }) => resolve());
  refreshSubscribers = [];
}
function onRefreshFailed(e: any) {
  refreshSubscribers.forEach(({ reject }) => reject(e));
  refreshSubscribers = [];
}

// 응답 인터셉터
api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<any>) => {
    if (!error || !error.config) return Promise.reject(error);

    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

    // 네트워크/프리플라이트 실패
    if (!error.response) {
      alert("네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
      return Promise.reject(error);
    }

    const { status, data } = error.response as { status: number; data?: any };

    // 토큰 만료 → 리프레시
    if (status === 401 && data?.error === "TOKEN_EXPIRED") {
      if (originalRequest._retry) return Promise.reject(error); // 무한루프 방지

      if (!isRefreshing) {
        isRefreshing = true;
        try {
          // 현재 인스턴스 baseURL 기준 상대경로로 호출
          await api.post("/auth/token/refresh", {}, { withCredentials: true });
          isRefreshing = false;
          onTokenRefreshed();

          originalRequest._retry = true;
          return api(originalRequest);
        } catch (e) {
          isRefreshing = false;
          onRefreshFailed(e);
          alert("세션이 만료되었습니다. 다시 로그인해주세요.");
          window.location.href = "/login.html";
          return Promise.reject(e);
        }
      }

      // 리프레시 진행 중 → 큐 대기
      return new Promise((resolve, reject) => {
        subscribeTokenRefresh(
          () => {
            originalRequest._retry = true;
            resolve(api(originalRequest));
          },
          (err) => reject(err)
        );
      });
    }

    // 토큰 무효
    if (status === 401 && data?.error === "TOKEN_INVALID") {
      alert("인증되지 않은 사용자입니다. 로그인해주세요.");
      window.location.href = "/login.html";
      return Promise.reject(error);
    }

    // 리뷰 API는 컴포넌트에서 처리
    if (originalRequest.url?.includes("/review/")) {
      return Promise.reject(error);
    }

    alert("문제가 발생했습니다. 홈으로 이동합니다.");
    window.location.href = "/";
    return Promise.reject(error);
  }
);

export default api;
