// API 기본 설정

const API_BASE_URL = "https://api.mapzip.shop";

// 타입 임포트 추가
import type { 
  OCRResult, 
  CreateReviewRequest, 
  CreateReviewResponse, 
  GetUserReviewsResponse,
  GetReviewResponse,
  UpdateReviewRequest,
  UpdateReviewResponse,
  GetPendingReviewDetailResponse,
  DeleteReviewResponse,
  DeletePendingReviewResponse,
  PendingReviewDetail,
  User, 
  LocationData, 
  Schedule 
} from "@/types";
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


// Helper function to map schedule response
const mapScheduleResponse = (scheduleData: any) => {
  if (!scheduleData) return null;
  // 백엔드 응답 필드 (scheduleId 또는 id)에 유연하게 대응
  if (scheduleData.scheduleId && !scheduleData.id) {
    const { scheduleId, ...rest } = scheduleData;
    return { id: scheduleId, ...rest };
  }
  return scheduleData;
};

export const scheduleApi = {
  getSchedules: async (): Promise<Schedule[]> => {
    try {
      // userId는 JWT 토큰에서 자동으로 추출되므로 파라미터 불필요
      const response = await api.get("/schedule");
      const data = response.data;
      return data.schedules ? data.schedules.map(mapScheduleResponse) : [];
    } catch (error: any) {
      console.error("스케줄 목록 조회 실패 - 목업 데이터를 반환합니다:", error);

      // E2E 테스트 또는 로컬 개발을 위한 목업 데이터
      const mockSchedules: Schedule[] = [
        {
          id: "mock-schedule-1",
          title: "강릉 당일치기 여행",
          departureTime: "09:00",
          departure: { name: "서울역", address: "서울 용산구 한강대로 405", lat: 37.5547, lng: 126.9704 },
          destination: { name: "강릉 커피거리", address: "강원 강릉시 창해로 14번길 20-1", lat: 37.7933, lng: 128.9189 },
          waypoints: [],
          mealSlots: [
            { mealType: 0, scheduledTime: "12:30", radius: 5000 },
            { mealType: 1, scheduledTime: "15:00", radius: 2000 },
          ],
          purpose: "휴식",
          companions: ["친구"],
          userNote: "바다가 보이는 카페였으면 좋겠어요.",
          arrivalBufferMinutes: 60,
        },
        {
          id: "mock-schedule-2",
          title: "부산 출장",
          departureTime: "08:00",
          departure: { name: "광명역", address: "경기 광명시 광명역로 21", lat: 37.4169, lng: 126.8882 },
          destination: { name: "벡스코", address: "부산 해운대구 APEC로 55", lat: 35.1689, lng: 129.1353 },
          waypoints: [
            { name: "부산역", address: "부산 동구 중앙대로 206", lat: 35.1149, lng: 129.0422, arrivalTime: "11:00" },
          ],
          mealSlots: [
            { mealType: 0, scheduledTime: "13:00", radius: 10000 },
          ],
          purpose: "업무",
          companions: [],
          userNote: "점심은 간단하게 국밥 원합니다.",
          arrivalBufferMinutes: 30,
        },
        {
          id: "mock-schedule-3",
          title: "서울-부산 드라이브 (최대 시나리오)",
          departureTime: "07:00",
          departure: { name: "서울시청", address: "서울 중구 세종대로 110", lat: 37.5665, lng: 126.9780 },
          destination: { name: "해운대해수욕장", address: "부산 해운대구 우동", lat: 35.1587, lng: 129.1604 },
          waypoints: [
            { name: "대전 성심당", address: "대전 중구 대종로480번길 15", lat: 36.3275, lng: 127.4272, arrivalTime: "09:30" },
            { name: "대구 서문시장", address: "대구 중구 큰장로26길 45", lat: 35.8714, lng: 128.5788, arrivalTime: "12:30" },
            { name: "경주 첨성대", address: "경북 경주시 인왕동 839-1", lat: 35.8342, lng: 129.2191, arrivalTime: "15:30" },
          ],
          mealSlots: [
            { mealType: 0, scheduledTime: "10:00", radius: 5000 },
            { mealType: 0, scheduledTime: "13:00", radius: 5000 },
            { mealType: 0, scheduledTime: "18:00", radius: 10000 },
          ],
          purpose: "여행",
          companions: ["가족"],
          userNote: "휴게소는 1번만 들르고 싶어요.",
          arrivalBufferMinutes: 120,
        },
      ];
      return mockSchedules;
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

      // 서버 응답 형식에 유연하게 대처:
      // 1. 응답 데이터 자체가 schedule 객체인 경우 (e.g., { scheduleId: '...' })
      // 2. 응답 데이터가 { schedule: { ... } } 형태로 감싸져 있는 경우
      const scheduleData = data.schedule ? data.schedule : data;

      if (scheduleData && Object.keys(scheduleData).length > 0) {
        // scheduleId를 id로 매핑하고, 수정 페이지가 기대하는 { schedule: { ... } } 형식으로 반환
        return { schedule: mapScheduleResponse(scheduleData) };
      }

      // 유효한 스케줄 데이터가 없는 경우
      return { schedule: null };
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

  // 스케줄 선택 상태 조회
  getSelectionStatus: async (): Promise<{ isSelected: boolean; scheduleId: string | null }> => {
    try {
      const response = await api.get("/schedule/selectedStatus");
      return response.data; // 백엔드가 { isSelected: boolean, scheduleId: string | null }를 반환한다고 가정
    } catch (error: any) {
      console.error("스케줄 선택 상태 조회 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message || "스케줄 선택 상태를 불러오지 못했습니다.",
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

  // 스케줄 선택 해제
  deselectSchedule: async (): Promise<{ success: boolean; message: string }> => {
    try {
      const response = await api.delete("/schedule/selection");
      return response.data;
    } catch (error: any) {
      console.error("스케줄 선택 해제 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message || "스케줄 선택을 해제하지 못했습니다.",
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
  // 스케줄을 선택하고 요약 정보를 받아오는 API
  selectAndGetSummary: async (scheduleId: string) => {
    try {
      console.log(`[API] 스케줄 서비스에 ${scheduleId} 선택 및 상세 정보 요청`);
      const response = await api.get(`/schedule/${scheduleId}:select`);
      const data = response.data;

      const scheduleData = data.schedule ? data.schedule : data;

      if (scheduleData && Object.keys(scheduleData).length > 0) {
        return { schedule: mapScheduleResponse(scheduleData) };
      }
      return { schedule: null };
    } catch (error: any) {
      console.error("스케줄 선택 및 상세 정보 조회 실패:", error);
      if (error.response) {
        throw new APIError(
          error.response.data?.message || "스케줄 선택 및 상세 정보를 불러오지 못했습니다.",
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

  // 현재 선택된 스케줄의 요약 정보를 가져오는 API
  getActiveScheduleSummary: async (scheduleId: string) => {
    try {
      console.log(`[API] 추천 서버에 최종 요약 결과 요청 (scheduleId: ${scheduleId})`);
      const response = await api.get("/recommend/result", { 
        params: { scheduleId } 
      });
      const data = response.data;

      // API 응답에 명시적으로 schedule 객체가 있고, status가 OK일 때만 유효한 요약 정보로 간주합니다.
      const scheduleData = data.schedule; 
      if (data.status === 'OK' && scheduleData) {
        return { schedule: mapScheduleResponse(scheduleData) };
      }
      
      // PENDING이거나, status가 OK여도 schedule 필드가 없으면 null을 반환합니다.
      return { schedule: null };
    } catch (error) { 
      console.error("getActiveScheduleSummary failed, returning null schedule. Error:", error);
      return { schedule: null }; // 에러 발생 시에도 null을 반환하여 인터셉터의 페이지 리로드 방지
    }
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
      // 파라미터 전송
      formData.append('receiptImage', blob, 'receipt.jpg');
      formData.append('expectedRestaurantName', expectedRestaurantName);
      formData.append('expectedAddress', expectedAddress);

      // POST /review/verify-receipt
      const response = await api.post('/review/verify-receipt', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      
      // 응답 구조: { success: true, ocrResult: {...}, message: "..." }
      return response.data?.ocrResult || response.data;
    } catch (error: any) {
      if (error instanceof APIError) throw error;
      if (error.response?.data) {
        throw new APIError(
          error.response.data.message || '영수증 검증 중 오류가 발생했습니다',
          error.response.status,
          error.response.data
        );
      }
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

      // 파라미터 전송
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

      // POST /review
      const response = await api.post('/review', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      
      // 응답 구조: { success: true, message: "...", reviewId: "...", isVerified: true }
      return {
        success: response.data.success,
        message: response.data.message,
        review: response.data.reviewId ? {
          id: response.data.reviewId,
          reviewId: response.data.reviewId,
          restaurantId: reviewData.restaurantId,
          restaurantName: reviewData.restaurantName,
          restaurantAddress: reviewData.restaurantAddress,
          userId: '',
          visitDate: reviewData.visitDate || '',
          rating: reviewData.rating,
          content: reviewData.content,
          imageUrls: [],
          isVerified: response.data.isVerified || false,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        } : null,
        ocrResult: { 
          valid: response.data.isVerified || false,
          restaurantName: reviewData.restaurantName,
          address: reviewData.restaurantAddress,
          visitDate: reviewData.visitDate || '',
          totalAmount: '',
          rawText: '',
          confidence: 0
        }
      };
    } catch (error: any) {
      if (error instanceof APIError) throw error;
      if (error.response?.data) {
        throw new APIError(
          error.response.data.message || '리뷰 작성 중 오류가 발생했습니다',
          error.response.status,
          error.response.data
        );
      }
      throw new APIError('네트워크 오류가 발생했습니다', 0, { originalError: error });
    }
  },

  // 사용자 리뷰 목록 조회 (JWT 토큰에서 userId 자동 추출)
  getUserReviews: async (page: number = 1, size: number = 10): Promise<GetUserReviewsResponse> => {
    try {
      const response = await api.get('/review/user', {
        params: { page, size }
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
      
      // 응답 구조: { success: true, data: [...], count: N }
      return response.data?.data || [];
    } catch (error: any) {
      console.error('미작성 리뷰 API 에러:', error);
      
      if (error.response?.data) {
        throw new APIError(
          error.response.data.message || `네트워크 오류: ${error.response.status}`,
          error.response.status,
          error.response.data
        );
      } else if (error instanceof Error) {
        throw new APIError(`클라이언트 오류: ${error.message}`, 0, { originalError: error });
      } else {
        throw new APIError('알 수 없는 오류가 발생했습니다.', 0, { originalError: error });
      }
    }
  },

  // 미작성 리뷰 삭제
  deletePendingReview: async (restaurantId: string, scheduledTime: string): Promise<DeletePendingReviewResponse> => {
    try {
      // DELETE /review/pending/{restaurantId}?scheduledTime={scheduledTime}
      const response = await api.delete(`/review/pending/${restaurantId}`, {
        params: { scheduledTime },
      });
      return response.data;
    } catch (error: any) {
      if (error instanceof APIError) {
        throw error;
      }
      if (error.response?.data) {
        throw new APIError(
          error.response.data.message || '미작성 리뷰 삭제 실패',
          error.response.status,
          error.response.data
        );
      }
      throw new APIError('알 수 없는 오류가 발생했습니다', 0, { originalError: error });
    }
  },

  // 특정 미작성 리뷰 상세 조회
  getPendingReviewDetail: async (restaurantId: string, scheduledTime: string): Promise<GetPendingReviewDetailResponse> => {
    try {
      // GET /review/pending/{restaurantId}/detail?scheduledTime={scheduledTime}
      const response = await api.get(`/review/pending/${restaurantId}/detail`, {
        params: { scheduledTime },
      });

      // 응답 구조: { success: true, data: {...} }
      return response.data;
    } catch (error: any) {
      if (error instanceof APIError) {
        throw error;
      }
      if (error.response?.data) {
        throw new APIError(
          error.response.data.message || '미작성 리뷰 조회 실패',
          error.response.status,
          error.response.data
        );
      }
      throw new APIError('알 수 없는 오류가 발생했습니다', 0, { originalError: error });
    }
  },

  // 작성된 리뷰 삭제
  deleteReview: async (restaurantId: string, reviewId: string): Promise<DeleteReviewResponse> => {
    try {
      // DELETE /review/{restaurantId}/{reviewId}
      const response = await api.delete(`/review/${restaurantId}/${reviewId}`);
      return response.data;
    } catch (error: any) {
      if (error instanceof APIError) {
        throw error;
      }
      if (error.response?.data) {
        throw new APIError(
          error.response.data.message || '리뷰 삭제 실패',
          error.response.status,
          error.response.data
        );
      }
      throw new APIError('알 수 없는 오류가 발생했습니다', 0, { originalError: error });
    }
  },

  // 특정 리뷰 상세 조회 (reviewId만 사용)
  getReview: async (reviewId: string): Promise<GetReviewResponse> => {
    try {
      // GET /review/detail/{reviewId}
      const response = await api.get(`/review/detail/${reviewId}`);
      
      // 응답 구조: { success: true, data: {...} }
      return response.data;
    } catch (error: any) {
      if (error instanceof APIError) {
        throw error;
      }
      if (error.response?.data) {
        throw new APIError(
          error.response.data.message || '리뷰 조회 실패',
          error.response.status,
          error.response.data
        );
      }
      throw new APIError('알 수 없는 오류가 발생했습니다', 0, { originalError: error });
    }
  },

  // 리뷰 수정
  updateReview: async (restaurantId: string, reviewId: string, reviewData: {
    rating: number;
    content: string;
    reviewImages?: string[];
  }): Promise<UpdateReviewResponse> => {
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

      // 파라미터 전송
      formData.append('rating', reviewData.rating.toString());
      formData.append('content', reviewData.content);

      if (reviewData.reviewImages) {
        for (const image of reviewData.reviewImages) {
          if (image) {
            const blob = dataUrlToBlob(image);
            formData.append('reviewImages', blob, 'review.jpg');
          }
        }
      }

      // PUT /review/{restaurantId}/{reviewId}
      const response = await api.put(`/review/${restaurantId}/${reviewId}`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      
      return response.data;
    } catch (error: any) {
      if (error instanceof APIError) throw error;
      if (error.response?.data) {
        throw new APIError(
          error.response.data.message || '리뷰 수정 중 오류가 발생했습니다',
          error.response.status,
          error.response.data
        );
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