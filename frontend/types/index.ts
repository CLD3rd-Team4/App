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
  hasMeal: boolean
  companions: string[]
  purpose: string
  tags: string[]
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
  visitDate: string
  rating?: number
  review?: string
  image?: string
}

export interface OCRResult {
  restaurantName: string
  visitDate: string
  isValid: boolean
  extractedText: string
}

export interface Review {
  id: string
  restaurantName: string
  visitDate: string
  rating: number
  review: string
  images: string[]
  createdAt: string
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