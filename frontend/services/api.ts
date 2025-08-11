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

// axios 인터셉터를 통해 공통 헤더는 자동으로 처리되므로 해당 함수 제거

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
  getSchedules: async () => {
    try {
      // userId는 JWT 토큰에서 자동으로 추출되므로 파라미터 불필요
      const response = await api.get("/schedule");
      const data = response.data;
      return data.schedules ? data.schedules.map(mapScheduleResponse) : [];
    } catch (error: any) {
      console.error("스케줄 목록 조회 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message || "스케줄 목록을 불러오지 못했습니다.",
          error.response.status,
          error.response.data
        );
      } else if (error.request) {
        throw new APIError(
          "서버에서 응답이 없습니다. 네트워크 연결을 확인해주세요.",
          0
        );
      } else {
        throw new APIError(
          `요청 설정 중 오류가 발생했습니다: ${error.message}`,
          -1
        );
      }
    }
  },

  createSchedule: async (scheduleData: Omit<Schedule, "id">) => {
    try {
      const response = await api.post("/schedule", scheduleData);
      return mapScheduleResponse(response.data);
    } catch (error: any) {
      console.error("스케줄 생성 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message || "스케줄을 생성하지 못했습니다.",
          error.response.status,
          error.response.data
        );
      } else if (error.request) {
        throw new APIError(
          "서버에서 응답이 없습니다. 네트워크 연결을 확인해주세요.",
          0
        );
      } else {
        throw new APIError(
          `요청 설정 중 오류가 발생했습니다: ${error.message}`,
          -1
        );
      }
    }
  },

  updateSchedule: async (scheduleData: Schedule) => {
    try {
      const { id, ...rest } = scheduleData;
      const requestBody = {
        ...rest,
        scheduleId: id,
      };
      const response = await api.put(`/schedule/${id}`, requestBody);
      return response.data;
    } catch (error: any) {
      console.error("스케줄 수정 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message || "스케줄을 수정하지 못했습니다.",
          error.response.status,
          error.response.data
        );
      } else if (error.request) {
        throw new APIError(
          "서버에서 응답이 없습니다. 네트워크 연결을 확인해주세요.",
          0
        );
      } else {
        throw new APIError(
          `요청 설정 중 오류가 발생했습니다: ${error.message}`,
          -1
        );
      }
    }
  },

  deleteSchedule: async (scheduleId: string) => {
    try {
      // userId는 JWT 토큰에서 자동으로 추출되므로 파라미터 불필요
      const response = await api.delete(`/schedule/${scheduleId}`);
      return response.data;
    } catch (error: any) {
      console.error("스케줄 삭제 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message || "스케줄을 삭제하지 못했습니다.",
          error.response.status,
          error.response.data
        );
      } else if (error.request) {
        throw new APIError(
          "서버에서 응답이 없습니다. 네트워크 연결을 확인해주세요.",
          0
        );
      } else {
        throw new APIError(
          `요청 설정 중 오류가 발생했습니다: ${error.message}`,
          -1
        );
      }
    }
  },

  processSchedule: async (scheduleId: string, data: any) => {
    try {
      const response = await api.post(`/schedule/${scheduleId}`, data);
      return response.data;
    } catch (error: any) {
      console.error("스케줄 처리 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message || "스케줄을 처리하지 못했습니다.",
          error.response.status,
          error.response.data
        );
      } else if (error.request) {
        throw new APIError(
          "서버에서 응답이 없습니다. 네트워크 연결을 확인해주세요.",
          0
        );
      } else {
        throw new APIError(
          `요청 설정 중 오류가 발생했습니다: ${error.message}`,
          -1
        );
      }
    }
  },

  getScheduleDetail: async (scheduleId: string) => {
    try {
      // userId는 JWT 토큰에서 자동으로 추출되므로 파라미터 불필요
      const response = await api.get(`/schedule/${scheduleId}`);
      const data = response.data;
      if (data && data.schedule) {
        return { ...data, schedule: mapScheduleResponse(data.schedule) };
      }
      return data;
    } catch (error: any) {
      console.error("스케줄 상세 정보 조회 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message ||
            "스케줄 상세 정보를 불러오지 못했습니다.",
          error.response.status,
          error.response.data
        );
      } else if (error.request) {
        throw new APIError(
          "서버에서 응답이 없습니다. 네트워크 연결을 확인해주세요.",
          0
        );
      } else {
        throw new APIError(
          `요청 설정 중 오류가 발생했습니다: ${error.message}`,
          -1
        );
      }
    }
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
    const response = await scheduleApi.getScheduleDetail(scheduleId); 
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

  // 특정 스케줄 ID에 대한 요약 정보를 가져오는 API (가상)
  getSummaryById: async (scheduleId: string) => {
    console.log(`[가상 API] recommend 서비스에 ${scheduleId} 요약 정보 요청`);
    // 실제로는 GET /recommend/summary/{scheduleId} 와 같은 API를 호출하게 됩니다.
    // 임시로 scheduleApi.getScheduleDetail을 호출하여 목 데이터를 반환합니다.
    const response = await scheduleApi.getScheduleDetail(scheduleId);
    return response;
  },
};



export const visitedRestaurantApi = {
  // 미작성 리뷰 목록 조회 (기존 방문한 식당 화면에서 사용)
  getVisitedRestaurants: async () => {
    try {
      return await reviewApi.getPendingReviews();
    } catch (error) {
      console.error('미작성 리뷰 목록 조회 실패:', error);
      
      // 인증 실패 시 더미 데이터 반환 (테스트용)
      if (error instanceof Error && (error.message?.includes('TOKEN_INVALID') || error.message?.includes('네트워크'))) {
        console.log('인증 실패 - 더미 데이터 반환');
        return [
          {
            id: 'pending-1',
            restaurantId: 'rest-001',
            placeName: '맛있는 집',
            addressName: '서울시 강남구 역삼동',
            scheduledTime: '12:30',
            isCompleted: false,
            createdAt: new Date().toISOString()
          },
          {
            id: 'pending-2', 
            restaurantId: 'rest-002',
            placeName: '한정식 레스토랑',
            addressName: '서울시 종로구 인사동',
            scheduledTime: '18:00',
            isCompleted: false,
            createdAt: new Date().toISOString()
          }
        ];
      }
      
      return [];
    }
  },
  
  // 미작성 리뷰 삭제 (사용자가 안간 경우)
  deletePendingReview: async (restaurantId: string, scheduledTime: string) => {
    return await reviewApi.deletePendingReview(restaurantId, scheduledTime);
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

      // axios를 사용하여 FormData 전송
      const response = await api.post('/review/verify-receipt', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      
      return response.data;
    } catch (error) {
      if (error instanceof APIError) throw error;
      if (error instanceof Error) {
        throw new APIError('네트워크 오류가 발생했습니다: ' + error.message, 0, { originalError: error });
      }
      throw new APIError('알 수 없는 네트워크 오류가 발생했습니다', 0, { originalError: error });
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

      // scheduledTime이 있으면 추가 (미작성 리뷰 완료 처리용)
      if (reviewData.scheduledTime) {
        formData.append('scheduledTime', reviewData.scheduledTime);
      }
      
      // 방문 날짜 추가 (OCR 날짜 검증용)
      if (reviewData.visitDate) {
        formData.append('visitDate', reviewData.visitDate);
      }

      // axios를 사용하여 FormData 전송
      const response = await api.post('/review', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      
      return response.data;
    } catch (error) {
      if (error instanceof APIError) throw error;
      if (error instanceof Error) {
        throw new APIError('네트워크 오류가 발생했습니다: ' + error.message, 0, { originalError: error });
      }
      throw new APIError('알 수 없는 네트워크 오류가 발생했습니다', 0, { originalError: error });
    }
  },

  // 미작성 리뷰 목록 조회
  getPendingReviews: async (): Promise<any[]> => {
    try {
      console.log('미작성 리뷰 목록 요청 시작');
      const response = await api.get('/review/pending');
      console.log('미작성 리뷰 API 응답:', response);
      
      return response.data.data || [];
    } catch (error: any) {
      console.error('미작성 리뷰 API 에러:', {
        message: error.message,
        status: error.response?.status,
        data: error.response?.data,
        config: {
          url: error.config?.url,
          method: error.config?.method,
          headers: error.config?.headers
        }
      });
      
      // Axios 에러인 경우
      if (error.response && error.response.data) {
        const { message, status, data } = error.response;
        // 서버에서 내려준 에러 메시지가 있으면 사용
        if (data && data.message) {
          throw new APIError(data.message, status, data);
        }
        // 그렇지 않으면 일반적인 네트워크 오류 메시지 사용
        throw new APIError(`네트워크 오류: ${status}`, status, data);
      } 
      // 일반 자바스크립트 에러인 경우
      else if (error instanceof Error) {
        throw new APIError(`클라이언트 오류: ${error.message}`, 0, { originalError: error });
      } 
      // 그 외 알 수 없는 에러
      else {
        throw new APIError('알 수 없는 오류가 발생했습니다.', 0, { originalError: error });
      }
    }
  },

  // 미작성 리뷰 삭제 (사용자가 안간 경우)
  deletePendingReview: async (restaurantId: string, scheduledTime: string): Promise<void> => {
    try {
      await api.delete(`/review/pending/${restaurantId}`, {
        params: { scheduledTime },
      });
    } catch (error) {
      if (error instanceof Error && error.message) {
        if (error instanceof APIError) {
            throw error; // APIError는 그대로 다시 던집니다.
        }
        throw new APIError(error.message, 0, { originalError: error });
      }
      throw new APIError('알 수 없는 오류가 발생했습니다', 0, { originalError: error });
    }
  },

  // 특정 미작성 리뷰 상세 조회
  getPendingReviewDetail: async (restaurantId: string, scheduledTime: string): Promise<any> => {
    try {
      const response = await api.get(`/review/pending/${restaurantId}`, {
        params: { scheduledTime },
      });

      return response.data.data;
    } catch (error) {
      if (error instanceof Error && error.message) {
        if (error instanceof APIError) {
            throw error; // APIError는 그대로 다시 던집니다.
        }
        throw new APIError(error.message, 0, { originalError: error });
      }
      throw new APIError('알 수 없는 오류가 발생했습니다', 0, { originalError: error });
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