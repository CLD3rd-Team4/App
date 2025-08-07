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

export const scheduleApi = {
  getSchedules: async (userId: string) => {
    // userId는 인터셉터에서 헤더로 전달하므로 파라미터는 사용하지 않음
    const response = await api.get('/schedule');
    const data = response.data;
    // gRPC 응답에 맞게, scheduleId를 id로 매핑
    return data.schedules ? data.schedules.map((s: any) => ({ ...s, id: s.scheduleId })) : [];
  },

  createSchedule: async (scheduleData: Omit<Schedule, 'id'>) => {
    const response = await api.post('/schedule', scheduleData);
    const result = response.data;
    // gRPC 응답에 맞게, scheduleId를 id로 매핑
    return { ...result, id: result.scheduleId };
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
    if (data.schedule) {
      data.schedule.id = scheduleId;
    }
    return data;
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
      if (error.message?.includes('TOKEN_INVALID') || error.message?.includes('네트워크')) {
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
      throw new APIError('네트워크 오류가 발생했습니다', 0, { originalError: error });
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
      
      if (error.response?.data?.message) {
        throw new APIError(error.response.data.message, error.response.status, error.response.data);
      }
      throw new APIError('네트워크 오류가 발생했습니다: ' + (error.message || '알 수 없는 오류'), error.response?.status || 0, { originalError: error });
    }
  },

  // 미작성 리뷰 삭제 (사용자가 안간 경우)
  deletePendingReview: async (restaurantId: string, scheduledTime: string): Promise<void> => {
    try {
      await api.delete(`/review/pending/${restaurantId}`, {
        params: { scheduledTime },
      });
    } catch (error) {
      if (error.response?.data?.message) {
        throw new APIError(error.response.data.message, error.response.status, error.response.data);
      }
      throw new APIError('네트워크 오류가 발생했습니다', 0, { originalError: error });
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
      if (error.response?.data?.message) {
        throw new APIError(error.response.data.message, error.response.status, error.response.data);
      }
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