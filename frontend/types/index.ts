export enum MealType {
  MEAL = 0,
  SNACK = 1,
}

export interface LocationInfo {
  lat: number;
  lng: number;
  name: string;
  address: string;
}

export interface Waypoint extends LocationInfo {
  arrivalTime?: string;
}

export interface MealSlot {
  mealType: MealType;
  scheduledTime: string;
  radius: number;
}

export interface SchedulePayload {
  title: string;
  departureTime: string;
  departure: LocationInfo;
  destination: LocationInfo;
  waypoints: Waypoint[];
  mealSlots: MealSlot[];
  purpose?: string;
  companions?: string[];
  userNote?: string;
  arrivalBufferMinutes?: number;
}

export interface Schedule extends SchedulePayload {
  id: string;
  calculatedArrivalTime?: string;
  selectedRestaurants?: Array<{
    sectionId: string;
    restaurant: Restaurant;
  }>;
}

export interface LocationData {
  departure: LocationInfo | null;
  destination: LocationInfo | null;
  waypoints: (LocationInfo | null)[];
}

export interface RequiredFormData {
    scheduleName: string;
    departureTime: string;
    targetMealTimes: Array<{
        type: '식사' | '간식';
        time: string;
        radius: string;
    }>;
    arrivalBufferMinutes?: number;
}

export interface OptionalFormData {
    userRequirements: string;
    travelPurpose: string;
    companions: string;
}

export interface User {
  id: string
  name: string
  email: string
  provider: string
}

export interface Restaurant {
  id: string
  placeName: string
  description: string
  aiReason: string
  rating?: number
  distance?: string
  image?: string
  addressName?: string
}

export interface VisitedRestaurant {
  id: string
  restaurantId: string
  placeName: string
  addressName?: string
  visitDate?: string
  scheduledTime?: string
  rating?: number
  review?: string
  image?: string
  placeUrl?: string
  isCompleted?: boolean
  createdAt?: string
  updatedAt?: string
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
  updatedAt: string
}

export interface OCRResult {
  isValid: boolean
  restaurantName: string
  address: string
  visitDate: string
  totalAmount: string
  rawText: string
  confidence: number
}

export interface CreateReviewRequest {
  restaurantId: string
  restaurantName: string
  restaurantAddress: string
  rating: number
  content: string
  receiptImages?: string[]
  reviewImages?: string[]
  ocrData?: OCRResult
  scheduledTime?: string
  visitDate?: string
}

export interface CreateReviewResponse {
  reviewId: string;
  message: string;
  success: boolean;
}
