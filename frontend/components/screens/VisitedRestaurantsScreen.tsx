"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import { Camera, Star } from "lucide-react"
import BottomNavigation from "@/components/common/BottomNavigation"
import { visitedRestaurantApi } from "@/services/api"
import type { VisitedRestaurant } from "@/types"
import { ReviewWriteModal } from "@/components/modals/ReviewWriteModal"

export default function VisitedRestaurantsScreen() {
  const router = useRouter()
  const [visitedRestaurants, setVisitedRestaurants] = useState<VisitedRestaurant[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [showReviewModal, setShowReviewModal] = useState(false)
  const [selectedRestaurant, setSelectedRestaurant] = useState<any>(null)

  useEffect(() => {
    loadVisitedRestaurants()
  }, [])

  const loadVisitedRestaurants = async () => {
    try {
      setIsLoading(true)
      // TODO: REST API 연동 - 방문한 식당 목록 가져오기
      const data = await visitedRestaurantApi.getVisitedRestaurants()
      setVisitedRestaurants(data)
    } catch (error) {
      console.error("방문한 식당 목록 로드 실패:", error)
    } finally {
      setIsLoading(false)
    }
  }

  const handleDeleteUnwritten = async (restaurantId: string) => {
    // 미작성 리뷰 삭제 로직
    setVisitedRestaurants((prev) => prev.filter((r) => r.id !== restaurantId))
  }

  const handleWriteReview = (restaurant: any) => {
    setSelectedRestaurant(restaurant)
    setShowReviewModal(true)
  }

  const handleReviewComplete = (reviewData: any) => {
    // 작성 완료된 리뷰를 목록에 추가
    setShowReviewModal(false)
    setSelectedRestaurant(null)
    // 리뷰 목록 새로고침
    loadVisitedRestaurants()
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm">
        <h1 className="text-lg font-medium text-blue-600">내가 방문한 식당</h1>
      </div>

      <div className="flex-1 content-with-bottom-nav">
        <div className="p-4">
          {isLoading ? (
            <div className="text-center py-8">
              <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
              <p className="text-gray-600">로딩 중...</p>
            </div>
          ) : visitedRestaurants.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-gray-600 mb-4">방문한 식당이 없습니다.</p>
              <p className="text-sm text-gray-500 mb-4">최근 방문하신 식당? 후기를 남겨보세요.</p>
              <Button
                onClick={() => handleWriteReview({ name: "새 식당", address: "주소 정보" })}
                className="bg-blue-500 hover:bg-blue-600 text-white flex items-center gap-2"
              >
                <Camera className="w-4 h-4" />첫 리뷰 작성하기
              </Button>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="bg-blue-500 text-white p-4 rounded-lg">
                <h2 className="font-medium mb-2">미 작성 리뷰</h2>
                <p className="text-sm opacity-90">최근 방문하신 식당? 후기를 남겨보세요.</p>
              </div>

              <div className="space-y-3">
                {visitedRestaurants.map((restaurant) => (
                  <div key={restaurant.id} className="bg-white p-4 rounded-lg shadow-sm">
                    <div className="flex items-start gap-3">
                      <img
                        src={restaurant.image || "/placeholder.svg?height=50&width=50&query=restaurant"}
                        alt={restaurant.name}
                        className="w-12 h-12 rounded-lg object-cover flex-shrink-0"
                      />
                      <div className="flex-1 min-w-0">
                        <h3 className="font-medium mb-1">{restaurant.name}</h3>
                        <p className="text-sm text-gray-600 mb-2">주소 정보</p>
                        <p className="text-sm text-gray-600 mb-2">방문일: {restaurant.visitDate}</p>
                        {restaurant.rating && (
                          <div className="flex items-center mb-2">
                            <Star className="w-4 h-4 text-yellow-400 fill-current" />
                            <span className="text-sm ml-1">{restaurant.rating}</span>
                          </div>
                        )}
                        {restaurant.review && <p className="text-sm text-gray-700 mb-2">{restaurant.review}</p>}
                        <div className="flex gap-2">
                          <Button
                            onClick={() => handleDeleteUnwritten(restaurant.id)}
                            size="sm"
                            variant="outline"
                            className="text-red-600 border-red-200"
                          >
                            삭제
                          </Button>
                          <Button
                            onClick={() => handleWriteReview(restaurant)}
                            size="sm"
                            className="bg-blue-500 hover:bg-blue-600 text-white"
                          >
                            작성
                          </Button>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {/* 작성 리뷰 섹션 */}
              <div className="bg-white p-4 rounded-lg shadow-sm">
                <h3 className="font-medium mb-3">작성 리뷰</h3>
                <div className="grid grid-cols-2 gap-3">
                  {[1, 2, 3, 4].map((index) => (
                    <div
                      key={index}
                      className="bg-gray-100 rounded-lg p-3 cursor-pointer hover:bg-gray-200"
                      onClick={() => router.push(`/review/detail/${index + 1}`)}
                    >
                      <img
                        src={`/placeholder.svg?height=80&width=120&query=food${index}`}
                        alt={`리뷰 이미지 ${index}`}
                        className="w-full h-16 object-cover rounded mb-2"
                      />
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-gray-600">★★★★</span>
                        <span className="text-xs text-gray-500">★★★★</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 카메라 버튼을 하단 네비게이션 위에 위치 */}
      <div className="floating-action-button">
        <Button
          onClick={() => handleWriteReview({ name: "새 식당", address: "주소 정보" })}
          className="w-12 h-12 bg-blue-500 hover:bg-blue-600 text-white rounded-full shadow-lg flex items-center justify-center"
        >
          <Camera className="w-5 h-5" />
        </Button>
      </div>

      <BottomNavigation currentTab="visited" />
      {showReviewModal && selectedRestaurant && (
        <ReviewWriteModal
          restaurant={selectedRestaurant}
          onComplete={handleReviewComplete}
          onCancel={() => setShowReviewModal(false)}
        />
      )}
    </div>
  )
}
