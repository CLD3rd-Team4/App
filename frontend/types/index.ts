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

export interface Schedule {
  id: string
  title: string
  departure: string
  destination: string
  waypoints?: string[]
  departureTime: string
  arrivalTime: string
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
  isValid: boolean          // 검증 통과 여부
  restaurantName: string    // 추출된 식당명
  address: string           // 추출된 주소
  visitDate: string         // 방문 날짜 (yyyy-MM-dd)
  totalAmount: string       // 총 결제 금액
  rawText: string           // OCR 원본 텍스트 (extractedText → rawText 변경)
  confidence: number        // 신뢰도 (0.0 - 1.0)
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
