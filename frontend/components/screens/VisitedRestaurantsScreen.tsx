"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useRouter } from "next/navigation"
import { Camera, Star, MapPin } from "lucide-react"
import BottomNavigation from "@/components/common/BottomNavigation"
import { visitedRestaurantApi, reviewApi } from "@/services/api"
import type { VisitedRestaurant } from "@/types"
import { ReviewWriteModal } from "@/components/modals/ReviewWriteModal"

// 주소에서 동네 이름 추출 헬퍼 함수
const extractDong = (addr?: string) => {
  if (!addr) return ""
  const tokens = addr.split(/\s+/)
  const dongLike = [...tokens].reverse().find(t => /(동|가|읍|면|리)$/.test(t))
  return dongLike || tokens[tokens.length - 2] || tokens[tokens.length - 1] || ""
}

// 사진이 없을 때 표시할 PIN 타일 컴포넌트
const PinTile = ({ addressName, size = "small" }: { addressName?: string, size?: "small" | "large" }) => {
  const dong = extractDong(addressName)
  const sizeClasses = size === "large" 
    ? "w-12 h-12" 
    : "w-full h-full"
  const iconSize = size === "large" 
    ? "w-5 h-5" 
    : "w-4 h-4"
  
  return (
    <div className={`${sizeClasses} rounded-lg bg-blue-100 flex flex-col items-center justify-center relative overflow-hidden`}>
      <MapPin className={`${iconSize} text-blue-600`} />
      {dong ? (
        <span className="absolute bottom-1 text-[10px] px-1 py-0.5 rounded-full bg-white/90 text-gray-700">
          {dong}
        </span>
      ) : null}
    </div>
  )
}

export default function VisitedRestaurantsScreen() {
  const router = useRouter()
  const [visitedRestaurants, setVisitedRestaurants] = useState<VisitedRestaurant[]>([])
  const [completedReviews, setCompletedReviews] = useState<any[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showReviewModal, setShowReviewModal] = useState(false)
  const [selectedRestaurant, setSelectedRestaurant] = useState<any>(null)
  const [showNewRestaurantForm, setShowNewRestaurantForm] = useState(false)
  const [newRestaurantName, setNewRestaurantName] = useState('')
  const [newRestaurantAddress, setNewRestaurantAddress] = useState('')

  useEffect(() => {
    const initializeData = async () => {
      try {
        console.log('방문식당 페이지 초기화 시작')
        await Promise.allSettled([
          loadVisitedRestaurants(),
          loadCompletedReviews()
        ])
        console.log('방문식당 페이지 초기화 완료')
      } catch (error) {
        console.error('방문식당 페이지 초기화 실패:', error)
        setError('페이지를 불러오는 중 오류가 발생했습니다.')
      }
    }
    
    initializeData()
  }, [])

  const loadVisitedRestaurants = async () => {
    try {
      setIsLoading(true)
      setError(null)
      console.log('미작성 리뷰 데이터 로드 시작')
      
      const data = await visitedRestaurantApi.getVisitedRestaurants()
      console.log('미작성 리뷰 데이터:', data)
      
      if (Array.isArray(data)) {
        setVisitedRestaurants(data)
      } else {
        console.warn('미작성 리뷰 데이터가 배열이 아님:', data)
        setVisitedRestaurants([])
      }
    } catch (error: any) {
      console.error("미작성 리뷰 목록 로드 실패:", error)
      setError(error.message || '미작성 리뷰 데이터를 불러오는 데 실패했습니다.')
      setVisitedRestaurants([])
    } finally {
      setIsLoading(false)
    }
  }

  const loadCompletedReviews = async () => {
    try {
      console.log('작성된 리뷰 데이터 로드 시작')
      const response = await reviewApi.getUserReviews(0, 10) // page=0부터 시작
      console.log('작성된 리뷰 API 응답:', response)
      
      if (response && Array.isArray(response.data)) {
        setCompletedReviews(response.data)
        console.log('작성된 리뷰 로드 성공:', response.data.length, '개')
      } else {
        console.warn('작성된 리뷰 응답 데이터가 예상 형식이 아님:', response)
        setCompletedReviews([])
      }
    } catch (error: any) {
      console.error("작성된 리뷰 목록 로드 실패:", error)
      console.error("에러 상세:", {
        status: error.status,
        code: error.code,
        message: error.message,
        response: error.response
      })
      
      if (error.status === 500) {
        console.error('서버 내부 오류 - 백엔드 수정이 아직 배포되지 않음')
      }
      
      setCompletedReviews([])
    }
  }

  const handleDeleteUnwritten = async (restaurantId: string, scheduledTime: string) => {
    if (!restaurantId || !scheduledTime) {
      alert('삭제에 필요한 정보가 누락되었습니다.')
      console.error('Missing data:', { restaurantId, scheduledTime })
      return
    }

    try {
      console.log('삭제 요청:', { restaurantId, scheduledTime })
      const response = await visitedRestaurantApi.deletePendingReview(restaurantId, scheduledTime)
      console.log('삭제 API 응답:', response)
      
      if (response && response.success) {
        console.log('미작성 리뷰가 삭제되었습니다.')
        
        setVisitedRestaurants(prev => 
          prev.filter(r => {
            const rId = r.id;
            return !(rId === restaurantId && r.scheduledTime === scheduledTime);
          })
        );
      } else {
        console.error('삭제 실패 - 서버 응답:', response)
        alert('삭제에 실패했습니다: ' + (response?.message || '알 수 없는 오류'))
        loadVisitedRestaurants()
      }
    } catch (error: any) {
      console.error('미작성 리뷰 삭제 실패:', error)
      alert('삭제에 실패했습니다: ' + (error.message || '네트워크 오류'))
      loadVisitedRestaurants()
    }
  }

  const handleWriteReview = (restaurant: any) => {
    setSelectedRestaurant(restaurant)
    setShowReviewModal(true)
  }

  const handleNewRestaurantSubmit = () => {
    if (!newRestaurantName.trim() || !newRestaurantAddress.trim()) {
      alert('식당명과 주소를 모두 입력해주세요.')
      return
    }
    
    const newRestaurant = {
      id: `new-${Date.now()}`,
      restaurantId: `new-${Date.now()}`,
      placeName: newRestaurantName.trim(),
      addressName: newRestaurantAddress.trim(),
      scheduledTime: new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
    }
    
    setSelectedRestaurant(newRestaurant)
    setShowReviewModal(true)
    setShowNewRestaurantForm(false)
    setNewRestaurantName('')
    setNewRestaurantAddress('')
  }

  const handleReviewComplete = (reviewData: any) => {
    setShowReviewModal(false)
    setSelectedRestaurant(null)
    loadVisitedRestaurants()
    loadCompletedReviews()
  }

  const handleReviewClick = (review: any) => {
    // 쿼리 파라미터를 사용하여 상세 페이지로 이동하는 올바른 방식
    if (review.restaurantId && review.reviewId) {
      router.push(`/review/detail?restaurantId=${review.restaurantId}&reviewId=${encodeURIComponent(review.reviewId)}`);
    } else {
      console.error("리뷰 상세 정보에 필요한 ID가 없습니다:", review);
      alert("리뷰 정보를 여는 데 실패했습니다.");
    }
  }

  const handleDeleteReview = async (restaurantId: string, reviewId: string) => {
    if (!window.confirm('정말로 이 리뷰를 삭제하시겠습니까?')) {
      return;
    }

    try {
      await reviewApi.deleteReview(restaurantId, reviewId);
      console.log('리뷰가 삭제되었습니다.');
      loadCompletedReviews();
    } catch (error: any) {
      console.error('리뷰 삭제 실패:', error);
      if (error.message.includes('권한이 없습니다')) {
        alert('삭제 권한이 없습니다.');
      } else {
        alert('리뷰 삭제에 실패했습니다. 다시 시도해주세요.');
      }
    }
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
          ) : error ? (
            <div className="text-center py-8">
              <p className="text-red-600 mb-2">⚠️ 로드 오류</p>
              <p className="text-sm text-gray-600 mb-4">{error}</p>
              <div className="flex gap-2 justify-center">
                <Button
                  onClick={() => {
                    setError(null)
                    loadVisitedRestaurants()
                  }}
                  className="bg-blue-500 hover:bg-blue-600 text-white"
                >
                  미작성 리뷰 다시 로드
                </Button>
                <Button
                  onClick={() => {
                    setError(null)
                    loadCompletedReviews()
                  }}
                  variant="outline"
                  className="border-blue-500 text-blue-500"
                >
                  작성된 리뷰 다시 로드
                </Button>
              </div>
            </div>
          ) : visitedRestaurants.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-gray-600 mb-4">미작성 리뷰가 없습니다.</p>
              <p className="text-sm text-gray-500 mb-4">최근 방문하신 식당의 후기를 남겨보세요.</p>
              <Button
                onClick={() => setShowNewRestaurantForm(true)}
                className="bg-blue-500 hover:bg-blue-600 text-white flex items-center gap-2"
              >
                <Camera className="w-4 h-4" />첫 리뷰 작성하기
              </Button>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="bg-blue-500 text-white p-4 rounded-lg">
                <h2 className="font-medium mb-2">미 작성 리뷰</h2>
                <p className="text-sm opacity-90">최근 방문하신 식당의 후기를 남겨보세요.</p>
              </div>

              <div className="space-y-3">
                {visitedRestaurants.map((restaurant) => (
                  <div key={restaurant.id} className="bg-white p-4 rounded-lg shadow-sm">
                    <div className="flex items-start gap-3">
                      {restaurant.image ? (
                        <img
                          src={restaurant.image}
                          alt={restaurant.placeName || '식당'}
                          className="w-12 h-12 rounded-lg object-cover flex-shrink-0"
                        />
                      ) : (
                        <div className="w-12 h-12 flex-shrink-0">
                          <PinTile addressName={restaurant.addressName} size="large" />
                        </div>
                      )}
                      <div className="flex-1 min-w-0">
                        <h3 className="font-medium mb-1">{restaurant.placeName}</h3>
                        <p className="text-sm text-gray-500 mb-2">{restaurant.addressName}</p>
                        {restaurant.scheduledTime && (
                          <p className="text-xs text-blue-600 mb-2">예정 시간: {restaurant.scheduledTime}</p>
                        )}
                        {restaurant.rating && (
                          <div className="flex items-center mb-2">
                            <Star className="w-4 h-4 text-yellow-400 fill-current" />
                            <span className="text-sm ml-1">{restaurant.rating}</span>
                          </div>
                        )}
                        {restaurant.review && <p className="text-sm text-gray-700 mb-2">{restaurant.review}</p>}
                        <div className="flex gap-2">
                          <Button
                            onClick={() => handleDeleteUnwritten(restaurant.id, restaurant.scheduledTime || '')}
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

              {/* 작성 리뷰 섹션 - 실제 API 연동 */}
              <div className="bg-white p-4 rounded-lg shadow-sm">
                <h3 className="font-medium mb-3">작성 리뷰</h3>
                {completedReviews.length === 0 ? (
                  <div className="text-center py-4">
                    <p className="text-sm text-gray-500">작성된 리뷰가 없습니다.</p>
                  </div>
                ) : (
                  <div className="grid grid-cols-2 gap-3">
                    {completedReviews.slice(0, 4).map((review, index) => (
                      <div
                        key={review.reviewId || index}
                        className="bg-gray-100 rounded-lg p-3 relative hover:bg-gray-200 transition-colors"
                      >
                        <div 
                          className="cursor-pointer"
                          onClick={() => handleReviewClick(review)}
                        >
                        {review.imageUrls && review.imageUrls.length > 0 ? (
                          <img
                            src={review.imageUrls[0]}
                            alt={`${review.restaurantName || '식당'} 리뷰 이미지`}
                            className="w-full h-16 object-cover rounded mb-2"
                          />
                        ) : (
                          <div className="w-full h-16 rounded mb-2 flex items-center justify-center">
                            <PinTile addressName={review.restaurantAddress} />
                          </div>
                        )}
                        <div className="flex items-center justify-between">
                          <div className="flex items-center">
                            {'★'.repeat(review.rating || 0)}<span className="text-gray-300">{'★'.repeat(5 - (review.rating || 0))}</span>
                          </div>
                          <span className="text-xs text-gray-500 truncate ml-2">
                            {review.restaurantName || '식당'}
                          </span>
                        </div>
                        </div>
                        {/* 삭제 버튼 */}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteReview(review.restaurantId, review.reviewId);
                          }}
                          className="absolute top-1 right-1 w-6 h-6 bg-red-500 text-white rounded-full flex items-center justify-center text-xs hover:bg-red-600 transition-colors"
                        >
                          ×
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 카메라 버튼을 하단 네비게이션 위에 위치 */}
      <div className="floating-action-button">
        <Button
          onClick={() => setShowNewRestaurantForm(true)}
          className="w-12 h-12 bg-blue-500 hover:bg-blue-600 text-white rounded-full shadow-lg flex items-center justify-center"
        >
          <Camera className="w-5 h-5" />
        </Button>
      </div>

      <BottomNavigation currentTab="visited" />
      
      {/* 새 식당 정보 입력 모달 */}
      {showNewRestaurantForm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg w-full max-w-md p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-medium">새 식당 정보 입력</h2>
              <Button 
                onClick={() => {
                  setShowNewRestaurantForm(false)
                  setNewRestaurantName('')
                  setNewRestaurantAddress('')
                }} 
                variant="ghost" 
                size="sm"
              >
                ✕
              </Button>
            </div>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  식당명 *
                </label>
                <Input
                  type="text"
                  value={newRestaurantName}
                  onChange={(e) => setNewRestaurantName(e.target.value)}
                  placeholder="식당명을 입력하세요"
                  className="w-full"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  주소 *
                </label>
                <Input
                  type="text"
                  value={newRestaurantAddress}
                  onChange={(e) => setNewRestaurantAddress(e.target.value)}
                  placeholder="주소를 입력하세요"
                  className="w-full"
                />
              </div>
            </div>
            
            <div className="flex gap-3 mt-6">
              <Button
                onClick={() => {
                  setShowNewRestaurantForm(false)
                  setNewRestaurantName('')
                  setNewRestaurantAddress('')
                }}
                variant="outline"
                className="flex-1"
              >
                취소
              </Button>
              <Button
                onClick={handleNewRestaurantSubmit}
                disabled={!newRestaurantName.trim() || !newRestaurantAddress.trim()}
                className="flex-1 bg-blue-500 hover:bg-blue-600 text-white disabled:bg-gray-400"
              >
                리뷰 작성하기
              </Button>
            </div>
          </div>
        </div>
      )}
      
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