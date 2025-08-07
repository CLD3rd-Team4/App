// API 기본 설정

const API_BASE_URL = "https://api.mapzip.shop";

// 타입 임포트 추가
import type { OCRResult, CreateReviewRequest, CreateReviewResponse, User, LocationData, Schedule } from "@/types";
import api from '@/lib/interceptor';

// 커스텀 에러 클래스
export class APIError extends Error {
  public status: number;
  public data: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'APIError';
    this.status = status;
    this.data = data;
  }
}

// 공통 헤더 설정 함수 (조건부 인증)
const getCommonHeaders = (): Record<string, string> => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  // TODO: 실제 토큰 저장 방식(예: localStorage, 쿠키)에 따라 토큰을 가져와야 함
  // const token = localStorage.getItem("authToken");
  // if (token) {
  //   headers['Authorization'] = `Bearer ${token}`;
  // }
  return headers;
};

// API 함수들
export const authApi = {
  login: async (provider: string): Promise<User> => {
    // TODO: 실제 API 연동
    console.log(`${provider}로 로그인 시도`);
    return {
      id: "1",
      name: "테스트 사용자",
      email: "test@example.com",
      provider: provider,
    };
  },

  logout: async () => {
    // TODO: 실제 API 연동
    console.log("로그아웃");
    return;
  },
}

// Helper function to map schedule response
const mapScheduleResponse = (scheduleData: any) => {
  if (!scheduleData) return null;
  const { scheduleId, ...rest } = scheduleData;
  return { id: scheduleId, ...rest };
};

export const scheduleApi = {
  getSchedules: async (userId: string) => {
    // userId는 인터셉터에서 헤더로 전달하므로 파라미터는 사용하지 않음
    const response = await api.get('/schedule');
    const data = response.data;
    return data.schedules ? data.schedules.map(mapScheduleResponse) : [];
  },

  createSchedule: async (scheduleData: Omit<Schedule, 'id'>) => {
    const response = await api.post('/schedule', scheduleData);
    return mapScheduleResponse(response.data);
  },

  updateSchedule: async (scheduleData: Schedule) => {
    const { id, ...rest } = scheduleData;
    const requestBody = {
      ...rest,
      scheduleId: id, // gRPC 요청 본문에 scheduleId 포함
    };
    // HTTP REST API의 관례에 따라 URL에 id를 포함
    const response = await api.put(`/schedule/${id}`, requestBody);
    return response.data;
  },

  deleteSchedule: async (scheduleId: string, userId: string) => {
    // userId는 인터셉터에서 헤더로 전달하므로 파라미터는 사용하지 않음
    const response = await api.delete(`/schedule/${scheduleId}`);
    return response.data;
  },

  processSchedule: async (scheduleId: string, data: any) => {
    const response = await api.post(`/schedule/${scheduleId}`, data);
    return response.data;
  },

  getScheduleDetail: async (scheduleId: string, userId: string) => {
    // userId는 인터셉터에서 헤더로 전달하므로 파라미터는 사용하지 않음
    const response = await api.get(`/schedule/${scheduleId}`);
    const data = response.data;
    if (data && data.schedule) {
      return { ...data, schedule: mapScheduleResponse(data.schedule) };
    }
    return data;
  },
};

export const recommendApi = {
  // 스케줄을 선택하고 요약 정보를 받아오는 API (가상)
  selectAndGetSummary: async (scheduleId: string) => {
    // 실제 아키텍처:
    // 1. 프론트엔드는 이 함수를 호출해 API 게이트웨이의 특정 엔드포인트(예: /recommendations/summary)를 호출합니다.
    // 2. 게이트웨이는 요청을 recommend 서비스로 라우팅합니다.
    // 3. recommend 서비스는 schedule 서비스의 getScheduleDetail gRPC 메서드를 호출합니다.
    // 4. schedule 서비스는 DB의 is_selected 플래그를 업데이트하고, 원본 스케줄 데이터를 recommend 서비스에 반환합니다.
    // 5. recommend 서비스는 모든 계산(TMap, Kakao)을 수행하여 최종 "요약 정보"를 생성하고, 이를 프론트엔드에 반환합니다.
    console.log(`[가상 API] recommend 서비스에 ${scheduleId} 선택 및 요약 요청`);
    
    // 개발 단계에서는 recommend 서비스가 없으므로, 임시로 schedule 서비스의 상세 정보를 그대로 반환하는 것처럼 시뮬레이션합니다.
    const response = await scheduleApi.getScheduleDetail(scheduleId, "test-user-123"); 
    return response;
  },

  // 현재 선택된 스케줄의 요약 정보를 가져오는 API (가상)
  getActiveScheduleSummary: async () => {
    // 실제 아키텍처:
    // 1. 프론트엔드는 이 함수를 호출해 API 게이트웨이의 엔드포인트(예: /recommendations/summary/active)를 호출합니다.
    // 2. 게이트웨이는 요청을 recommend 서비스로 라우팅합니다.
    // 3. recommend 서비스는 schedule 서비스 DB에서 is_selected가 true인 스케줄을 찾고, 없으면 null을 반환합니다.
    // 4. 스케줄이 있으면, 해당 스케줄의 최종 "요약 정보"를 찾아 프론트엔드에 반환합니다. (캐시 또는 DB에서 조회)
    console.log("[가상 API] recommend 서비스에 현재 활성화된 스케줄 요약 요청");

    // 개발 단계에서는 임시로 비어있는 응답을 반환합니다.
    return Promise.resolve({ schedule: null });
  },
};



export const visitedRestaurantApi = {
  getVisitedRestaurants: async () => {
    // TODO: 실제 API 연동
    return [];
  },
};

export const ocrApi = {
  processReceipt: async (imageData: string, expectedRestaurantName: string, expectedAddress: string): Promise<OCRResult> => {
    try {
      const formData = new FormData();
      
      const dataUrlToBlob = (dataUrl: string): Blob => {
        const arr = dataUrl.split(',');
        const mime = arr[0].match(/:(.*?);/)?.[1] || 'image/jpeg';
        const bstr = atob(arr[1]);
        let n = bstr.length;
        const u8arr = new Uint8Array(n);
        while (n--) {
          u8arr[n] = bstr.charCodeAt(n);
        }
        return new Blob([u8arr], { type: mime });
      };

      const blob = dataUrlToBlob(imageData);
      formData.append('receiptImage', blob, 'receipt.jpg');
      formData.append('expectedRestaurantName', expectedRestaurantName);
      formData.append('expectedAddress', expectedAddress);

      const response = await fetch(`${API_BASE_URL}/review/verify-receipt`, {
        method: 'POST',
        // FormData의 경우 Content-Type을 브라우저가 자동으로 설정하도록 헤더를 비워둠
        // headers: getCommonHeaders(), // getCommonHeaders는 application/json을 가정하므로 여기서는 사용하지 않음
        body: formData,
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new APIError(
          errorData.message || 'OCR 처리에 실패했습니다',
          response.status,
          errorData
        );
      }
      
      return response.json();
    } catch (error) {
      if (error instanceof APIError) throw error;
      throw new APIError('네트워크 오류가 발생했습니다', 0, { originalError: error });
    }
  },
};

export const reviewApi = {
  createReview: async (reviewData: CreateReviewRequest): Promise<CreateReviewResponse> => {
    try {
      const formData = new FormData();
      
      const dataUrlToBlob = (dataUrl: string): Blob => {
        const arr = dataUrl.split(',');
        const mime = arr[0].match(/:(.*?);/)?.[1] || 'image/jpeg';
        const bstr = atob(arr[1]);
        let n = bstr.length;
        const u8arr = new Uint8Array(n);
        while (n--) {
          u8arr[n] = bstr.charCodeAt(n);
        }
        return new Blob([u8arr], { type: mime });
      };

      formData.append('restaurantId', reviewData.restaurantId);
      formData.append('restaurantName', reviewData.restaurantName);
      formData.append('restaurantAddress', reviewData.restaurantAddress);
      formData.append('rating', reviewData.rating.toString());
      formData.append('content', reviewData.content);

      if (reviewData.receiptImages) {
        for (const image of reviewData.receiptImages) {
          if (image) {
            const blob = dataUrlToBlob(image);
            formData.append('receiptImages', blob, 'receipt.jpg');
          }
        }
      }

      if (reviewData.reviewImages) {
        for (const image of reviewData.reviewImages) {
          if (image) {
            const blob = dataUrlToBlob(image);
            formData.append('reviewImages', blob, 'review.jpg');
          }
        }
      }

      const response = await fetch(`${API_BASE_URL}/review`, {
        method: 'POST',
        // headers: getCommonHeaders(), // FormData 사용 시 Content-Type 자동 설정
        body: formData,
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new APIError(
          errorData.message || '리뷰 작성에 실패했습니다',
          response.status,
          errorData
        );
      }
      
      return response.json();
    } catch (error) {
      if (error instanceof APIError) throw error;
      throw new APIError('네트워크 오류가 발생했습니다', 0, { originalError: error });
    }
  },
};

export const locationUtils = {
  calculateDistance(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLng = (lng2 - lng1) * Math.PI / 180;
    const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLng/2) * Math.sin(dLng/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
  },

  estimateTravelTime(distance: number, transportType: 'car' | 'walk' | 'public' = 'car'): number {
    const speeds = {
      car: 40,
      walk: 4,
      public: 25
    };
    return Math.round((distance / speeds[transportType]) * 60);
  },

  validateLocationData(locationData: LocationData): boolean {
    if (!locationData.departure || !locationData.destination) return false;
    const isValidCoord = (lat: number, lng: number) => lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    if (!isValidCoord(locationData.departure.lat, locationData.departure.lng) ||
        !isValidCoord(locationData.destination.lat, locationData.destination.lng)) {
      return false;
    }
    for (const waypoint of locationData.waypoints) {
      if (waypoint && !isValidCoord(waypoint.lat, waypoint.lng)) return false;
    }
    return true;
  },

  locationToString(locationData: LocationData) {
    return {
      departure: locationData.departure?.name || "",
      destination: locationData.destination?.name || "",
      waypoints: locationData.waypoints.map(w => w?.name).filter(Boolean)
    };
  }
};