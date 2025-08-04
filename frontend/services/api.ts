// API 기본 설정
const API_BASE_URL = "https://api.mapzip.shop";

// 테스트용 데이터 - 실제 API 연동 시 제거
const TEST_DATA = {
  schedules: [
    {
      id: "1",
      title: "전인여행 첫째날 → 나주 출장",
      departure: "전인여행 첫째날",
      destination: "나주 출장",
      waypoints: ["금산동 부부막국"],
      departureTime: "09:30",
      arrivalTime: "18:30",
      hasMeal: true,
      companions: ["친구"],
      purpose: "출장",
      tags: ["맛집"],
      mealRadius: "5km",
      targetMealTimes: [
        { type: "식사", time: "12:00" },
        { type: "간식", time: "15:00" },
        { type: "식사", time: "18:00" },
      ],
      userRequirements: "비건식을 원합니다",
    },
    {
      id: "2",
      title: "서울 → 부산 여행",
      departure: "서울역",
      destination: "부산역",
      waypoints: ["대전역"],
      departureTime: "08:00",
      arrivalTime: "20:00",
      hasMeal: true,
      companions: ["가족"],
      purpose: "여행",
      tags: ["관광"],
      mealRadius: "10km",
      targetMealTimes: [
        { type: "식사", time: "11:30" },
        { type: "간식", time: "14:30" },
        { type: "식사", time: "17:30" },
      ],
      userRequirements: "아이들이 좋아할 만한 음식",
    },
  ],
  restaurants: [
    {
      id: "1",
      name: "맛있는 한식당",
      description: "전통 한식 전문점",
      aiReason: "사용자의 비건 요구사항에 맞는 다양한 채식 메뉴 제공",
      rating: 4.2,
      distance: "1.2km",
      image: "/placeholder.svg?height=60&width=60",
    },
    {
      id: "2",
      name: "건강한 샐러드바",
      description: "신선한 채소와 건강식",
      aiReason: "비건 친화적인 메뉴와 신선한 재료 사용",
      rating: 4.5,
      distance: "0.8km",
      image: "/placeholder.svg?height=60&width=60",
    },
    {
      id: "3",
      name: "이탈리안 파스타",
      description: "수제 파스타 전문점",
      aiReason: "비건 파스타 옵션과 다양한 채식 메뉴 보유",
      rating: 4.0,
      distance: "2.1km",
      image: "/placeholder.svg?height=60&width=60",
    },
    {
      id: "4",
      name: "카페 브런치",
      description: "분위기 좋은 브런치 카페",
      aiReason: "간식 시간에 적합한 비건 디저트와 음료 제공",
      rating: 4.3,
      distance: "1.5km",
      image: "/placeholder.svg?height=60&width=60",
    },
    {
      id: "5",
      name: "아시안 퓨전",
      description: "아시아 요리 전문점",
      aiReason: "다양한 채식 아시아 요리와 건강한 재료 사용",
      rating: 4.1,
      distance: "1.8km",
      image: "/placeholder.svg?height=60&width=60",
    },
    {
      id: "6",
      name: "디저트 하우스",
      description: "수제 디저트 전문점",
      aiReason: "비건 디저트 옵션과 간식 시간에 완벽한 메뉴",
      rating: 4.4,
      distance: "1.0km",
      image: "/placeholder.svg?height=60&width=60",
    },
    {
      id: "7",
      name: "해산물 전문점",
      description: "신선한 해산물 요리",
      aiReason: "지역 특산물을 활용한 신선한 해산물 메뉴",
      rating: 4.6,
      distance: "2.3km",
      image: "/placeholder.svg?height=60&width=60",
    },
    {
      id: "8",
      name: "고기구이 전문점",
      description: "숯불구이 전문점",
      aiReason: "고품질 한우와 숯불구이의 깊은 맛",
      rating: 4.7,
      distance: "1.9km",
      image: "/placeholder.svg?height=60&width=60",
    },
    {
      id: "9",
      name: "중식당",
      description: "정통 중화요리",
      aiReason: "다양한 중화요리와 합리적인 가격",
      rating: 4.2,
      distance: "1.4km",
      image: "/placeholder.svg?height=60&width=60",
    },
  ],
  visitedRestaurants: [
    {
      id: "1",
      name: "OO 식당",
      address: "서울시 강남구 테헤란로 123",
      visitDate: "2024.07.15",
      rating: 4,
      review: "맛있었어요!",
      image: "/placeholder.svg?height=60&width=60",
    },
  ],
}

// API 함수들
export const authApi = {
  login: async (provider: string) => {
    // TODO: 실제 API 연동
    return new Promise((resolve) => {
      setTimeout(() => {
        resolve({
          id: "1",
          name: "테스트 사용자",
          email: "test@example.com",
          provider: provider,
        })
      }, 1000)
    })
  },

  logout: async () => {
    // TODO: 실제 API 연동
    return new Promise((resolve) => setTimeout(resolve, 500))
  },
}

export const scheduleApi = {
  getSchedules: async () => {
    const response = await fetch(`${API_BASE_URL}/schedule`);
    if (!response.ok) {
      throw new Error('Failed to fetch schedules');
    }
    return response.json();
  },

  createSchedule: async (scheduleData: any) => {
    // TODO: 실제 API 연동
    return new Promise((resolve) => {
      setTimeout(() => {
        const newSchedule = {
          id: Date.now().toString(),
          ...scheduleData,
        }
        resolve(newSchedule)
      }, 1000)
    })
  },

  updateSchedule: async (scheduleData: any) => {
    // TODO: 실제 API 연동
    return new Promise((resolve) => {
      setTimeout(() => resolve(scheduleData), 1000)
    })
  },

  deleteSchedule: async (scheduleId: string) => {
    // TODO: 실제 API 연동
    return new Promise((resolve) => setTimeout(resolve, 500))
  },
}

export const recommendationApi = {
  getRecommendations: async (scheduleId?: string) => {
    // TODO: 실제 API 연동
    return new Promise((resolve) => {
      setTimeout(() => {
        // 다양한 식당 데이터를 섞어서 반환
        const shuffled = [...TEST_DATA.restaurants].sort(() => 0.5 - Math.random())
        resolve(shuffled)
      }, 800)
    })
  },
}

export const visitedRestaurantApi = {
  getVisitedRestaurants: async () => {
    // TODO: 실제 API 연동
    return new Promise((resolve) => {
      setTimeout(() => resolve(TEST_DATA.visitedRestaurants), 500)
    })
  },
}

export const ocrApi = {
  processReceipt: async (imageData: string, expectedRestaurantName: string, expectedAddress: string) => {
    const formData = new FormData();
    const blob = await (await fetch(imageData)).blob();
    formData.append('receiptImage', blob, 'receipt.jpg');
    formData.append('expectedRestaurantName', expectedRestaurantName);
    formData.append('expectedAddress', expectedAddress);

    const response = await fetch(`${API_BASE_URL}/review/verify-receipt`, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      throw new Error('Failed to process receipt');
    }
    return response.json();
  },
};

export const reviewApi = {
  createReview: async (reviewData: any) => {
    const formData = new FormData();
    formData.append('restaurantId', reviewData.restaurantId);
    formData.append('restaurantName', reviewData.restaurantName);
    formData.append('restaurantAddress', reviewData.restaurantAddress);
    formData.append('rating', reviewData.rating.toString());
    formData.append('content', reviewData.content);

    if (reviewData.receiptImages) {
      for (const image of reviewData.receiptImages) {
        const blob = await (await fetch(image)).blob();
        formData.append('receiptImages', blob, 'receipt.jpg');
      }
    }

    if (reviewData.reviewImages) {
      for (const image of reviewData.reviewImages) {
        const blob = await (await fetch(image)).blob();
        formData.append('reviewImages', blob, 'review.jpg');
      }
    }

    const response = await fetch(`${API_BASE_URL}/review`, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      throw new Error('Failed to create review');
    }
    return response.json();
  },
};

// 🆕 새로 추가: 위치 관련 유틸리티 함수들
export const locationUtils = {
  // 직선거리 계산 (km 단위)
  calculateDistance(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const R = 6371 // 지구 반지름 (km)
    const dLat = (lat2 - lat1) * Math.PI / 180
    const dLng = (lng2 - lng1) * Math.PI / 180
    const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLng/2) * Math.sin(dLng/2)
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    return R * c
  },

  // 예상 소요시간 계산 (분 단위)
  estimateTravelTime(distance: number, transportType: 'car' | 'walk' | 'public' = 'car'): number {
    const speeds = {
      car: 40,     // km/h (도시 평균)
      walk: 4,     // km/h
      public: 25   // km/h (대중교통 평균)
    }
    
    return Math.round((distance / speeds[transportType]) * 60)
  },

  // 위치 데이터 검증
  validateLocationData(locationData: LocationData): boolean {
    if (!locationData.departure || !locationData.destination) {
      return false
    }

    // 위도/경도 범위 검증
    const isValidCoord = (lat: number, lng: number) => {
      return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
    }

    if (!isValidCoord(locationData.departure.lat, locationData.departure.lng) ||
        !isValidCoord(locationData.destination.lat, locationData.destination.lng)) {
      return false
    }

    // 경유지 검증
    for (const waypoint of locationData.waypoints) {
      if (waypoint && !isValidCoord(waypoint.lat, waypoint.lng)) {
        return false
      }
    }

    return true
  },

  // 위치 데이터를 문자열로 변환 (기존 시스템과의 호환성)
  locationToString(locationData: LocationData) {
    return {
      departure: locationData.departure?.name || "",
      destination: locationData.destination?.name || "",
      waypoints: locationData.waypoints.map(w => w?.name).filter(Boolean)
    }
  }
}
