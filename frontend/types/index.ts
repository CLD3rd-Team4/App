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
