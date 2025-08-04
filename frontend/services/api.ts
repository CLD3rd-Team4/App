// API 기본 설정
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"

// API 함수들
export const authApi = {
  login: async (provider: string) => {
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
    const response = await fetch(`${API_BASE_URL}/schedule?userId=${userId}`);
    if (!response.ok) {
      throw new Error('Failed to fetch schedules');
    }
    const data = await response.json();
    // scheduleId를 id로 매핑
    return data.schedules ? data.schedules.map((s: any) => ({ ...s, id: s.scheduleId })) : [];
  },

  createSchedule: async (scheduleData: any) => {
    const response = await fetch(`${API_BASE_URL}/schedule`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(scheduleData),
    });
    if (!response.ok) {
      const errorBody = await response.text();
      console.error("스케줄 생성 실패 응답:", errorBody);
      throw new Error('Failed to create schedule');
    }
    const result = await response.json();
    // 반환된 scheduleId를 id로 매핑
    return { ...result, id: result.scheduleId };
  },

  updateSchedule: async (scheduleData: any) => {
    const { id, ...rest } = scheduleData;
    // 백엔드 gRPC 요청 형식에 맞게 id를 scheduleId로, userId를 명시적으로 전달합니다.
    const requestBody = {
      ...rest,
      scheduleId: id,
      userId: scheduleData.userId || 'test-user-123', // 임시 userId 추가
    };
    // URL 경로에 ID를 다시 포함시키고, PUT 요청을 보냅니다.
    const response = await fetch(`${API_BASE_URL}/schedule/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(requestBody),
    });
    if (!response.ok) {
      const errorBody = await response.text();
      console.error("스케줄 업데이트 실패 응답:", errorBody);
      throw new Error('Failed to update schedule');
    }
    return response.json();
  },

  deleteSchedule: async (scheduleId: string, userId: string) => {
    // userId를 쿼리 파라미터로 전달
    const response = await fetch(`${API_BASE_URL}/schedule/${scheduleId}?userId=${userId}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      throw new Error('Failed to delete schedule');
    }
    return response.json();
  },

  processSchedule: async (scheduleId: string, data: any) => {
    const requestBody = {
      ...data,
      userId: 'test-user-123', // TODO: 실제 사용자 ID로 교체
    };
    const response = await fetch(`${API_BASE_URL}/schedule/${scheduleId}/process`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(requestBody),
    });
    if (!response.ok) {
      throw new Error('Failed to process schedule');
    }
    // 이제 응답에 calculatedArrivalTime이 포함됩니다.
    return response.json();
  },

  getScheduleDetail: async (scheduleId: string, userId: string) => {
    const response = await fetch(`${API_BASE_URL}/schedule/${scheduleId}?userId=${userId}`, { cache: 'no-store' });
    if (!response.ok) {
      throw new Error('Failed to fetch schedule detail');
    }
    const data = await response.json();
    // 백엔드 응답에 ID가 포함되지 않을 수 있으므로, 요청 시 사용한 scheduleId를 직접 주입해줍니다.
    if (data.schedule) {
      data.schedule.id = scheduleId;
    }
    return data;
  },
}

export const recommendationApi = {
  getRecommendations: async (scheduleId?: string) => {
    // TODO: 실제 API 연동
    console.log(`추천 목록 요청: scheduleId=${scheduleId}`);
    return [];
  },
}

export const visitedRestaurantApi = {
  getVisitedRestaurants: async () => {
    // TODO: 실제 API 연동
    return [];
  },
}

export const ocrApi = {
  processReceipt: async (imageData: string) => {
    // TODO: 실제 API 연동
    console.log("영수증 처리 요청");
    return {
      restaurantName: "처리된 식당 이름",
      visitDate: "2024.07.18",
      isValid: true,
      extractedText: "영수증 텍스트 내용...",
    };
  },
}

export const reviewApi = {
  createReview: async (reviewData: any) => {
    // TODO: 실제 API 연동
    console.log("리뷰 생성 요청:", reviewData);
    return {
      id: Date.now().toString(),
      ...reviewData,
      createdAt: new Date().toISOString(),
    };
  },
}
