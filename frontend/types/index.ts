export interface User {
  id: string
  name: string
  email: string
  provider: string
}

export interface MealTime {
  type: "식사" | "간식"
  time: string
}

// Waypoint 타입을 명확하게 정의합니다.
export interface Waypoint {
  lat: number;
  lng: number;
  name: string;
  arrivalTime?: string; // 도착 시간은 선택적 필드
}

export interface Schedule {
  id: string
  title: string
  // departure와 destination도 LocationInfo 타입을 사용하도록 개선할 수 있으나, 우선 waypoints만 수정합니다.
  departure: any 
  destination: any
  waypoints?: Waypoint[] // string[]에서 Waypoint[]로 수정
  departureTime: string
  arrivalTime: string // 이 필드는 calculatedArrivalTime로 대체될 수 있습니다.
  calculatedArrivalTime?: string; // 계산된 도착 시간 추가
  companions: string[]
  purpose: string
  selectedRestaurant?: Restaurant
  selectedRestaurants?: Array<{
    sectionId: string
    restaurant: Restaurant
  }>
  mealRadius?: "5km" | "10km" | "20km"
  targetMealTimes?: MealTime[]
  userRequirements?: string
  userId?: string; // userId 추가
}

export interface Restaurant {
  id: string
  name: string
  description: string
  aiReason: string
  rating?: number
  distance?: string
  image?: string
}

export interface VisitedRestaurant {
  id: string
  name: string
  address?: string           // 누락된 필드 추가
  visitDate: string
  rating?: number
  review?: string
  image?: string
}

export interface OCRResult {
  isValid: boolean          // 검증 통과 여부
  restaurantName: string    // 추출된 식당명
  address: string           // 추출된 주소
  visitDate: string         // 방문 날짜 (yyyy-MM-dd)
  totalAmount: string       // 총 결제 금액
  rawText: string           // OCR 원본 텍스트 (extractedText → rawText 변경)
  confidence: number        // 신뢰도 (0.0 - 1.0)
}

// API 요청/응답 관련 타입 추가
export interface CreateReviewRequest {
  restaurantId: string
  restaurantName: string
  restaurantAddress: string
  rating: number
  content: string
  receiptImages?: string[]   // Data URL 형태
  reviewImages?: string[]    // Data URL 형태
  ocrData?: OCRResult
}

export interface CreateReviewResponse {
  review: Review
  ocrResult?: OCRResult
  message: string
  success: boolean
}

export interface Review {
  id: string
  restaurantId: string       
  restaurantName: string
  restaurantAddress?: string 
  userId: string             
  visitDate: string
  rating: number
  content: string            
  imageUrls: string[]        
  isVerified?: boolean       
  createdAt: string
  updatedAt?: string         
}

// API 에러 타입
export interface APIErrorData {
  message: string
  status: number
  data?: any
}

export interface LocationInfo {
  name: string;
  address: string;
  lat: number;
  lng: number;
}

export interface LocationData {
  departure: LocationInfo | null;
  destination: LocationInfo | null;
  waypoints: (LocationInfo | null)[];
}

