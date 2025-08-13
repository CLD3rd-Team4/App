"use client"

import type React from "react"

import { useState, useEffect, useRef } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { useRouter, useParams } from "next/navigation"
import { ArrowLeft, Star, ChevronLeft, ChevronRight, Edit, Save, X, Plus } from "lucide-react"
import { reviewApi, APIError } from "@/services/api"

export default function ReviewDetailPageClient() {
  const router = useRouter()
  const params = useParams()
  const [review, setReview] = useState<any>(null)
  const [currentImageIndex, setCurrentImageIndex] = useState(0)
  const [isEditing, setIsEditing] = useState(false)
  const [editedRating, setEditedRating] = useState(0)
  const [editedReview, setEditedReview] = useState("")
  const [editedImages, setEditedImages] = useState<string[]>([])
  const [isClient, setIsClient] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const photoInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return

    const loadReviewDetail = async () => {
      try {
        setIsLoading(true)
        setError(null)
        
        // params.id는 실제로는 reviewId이고, restaurantId가 필요함
        // URL 패턴을 /review/detail/{restaurantId}/{reviewId}로 변경하거나
        // 임시로 reviewId만으로 조회 가능하도록 API 수정 필요
        
        // 현재는 reviewId만 있으므로 getUserReviews에서 해당 리뷰를 찾는 방식 사용
        const userReviews = await reviewApi.getUserReviews(1, 100); // 많은 수 조회
        const targetReview = userReviews.data?.find((r: any) => r.id === params.id);
        
        if (!targetReview) {
          setError("리뷰를 찾을 수 없습니다.");
          return;
        }
        
        // 실제 API 데이터 구조에 맞게 변환
        const reviewData = {
          id: targetReview.id,
          restaurantId: targetReview.restaurantId,
          restaurantName: targetReview.restaurantName || "식당",
          address: targetReview.restaurantAddress || "주소 정보 없음",
          rating: targetReview.rating || 0,
          visitDate: targetReview.visitDate || targetReview.createdAt?.split('T')[0] || "",
          review: targetReview.content || "",
          images: targetReview.imageUrls || [],
          isOwner: true // getUserReviews는 본인 리뷰만 조회하므로 항상 true
        };
        
        setReview(reviewData)
        setEditedRating(reviewData.rating)
        setEditedReview(reviewData.review)
        setEditedImages([...reviewData.images])
        
      } catch (error: any) {
        console.error("리뷰 상세 정보 로드 실패:", error)
        if (error instanceof APIError) {
          setError(error.message)
        } else {
          setError("리뷰 정보를 불러오는데 실패했습니다.")
        }
      } finally {
        setIsLoading(false)
      }
    }

    loadReviewDetail()
  }, [params.id, isClient])

  const handleEdit = () => {
    setIsEditing(true)
  }

  const handleSave = async () => {
    if (!review?.restaurantId) {
      alert("리뷰 정보가 올바르지 않습니다.");
      return;
    }

    try {
      setIsLoading(true)
      
      // 실제 수정 API 호출
      const updateData = {
        rating: editedRating,
        content: editedReview,
        reviewImages: editedImages // 새로 추가된 이미지들
      };
      
      const updatedReview = await reviewApi.updateReview(
        review.restaurantId, 
        review.id, 
        updateData
      );
      
      // 성공 시 UI 업데이트
      const newReviewData = {
        ...review,
        rating: editedRating,
        review: editedReview,
        images: editedImages,
      };
      
      setReview(newReviewData)
      setIsEditing(false)

      alert("리뷰가 수정되었습니다.")
    } catch (error: any) {
      console.error("리뷰 수정 실패:", error)
      if (error instanceof APIError) {
        if (error.status === 403) {
          alert("수정 권한이 없습니다.")
        } else {
          alert(error.message)
        }
      } else {
        alert("리뷰 수정에 실패했습니다.")
      }
    } finally {
      setIsLoading(false)
    }
  }

  const handleCancelEdit = () => {
    setEditedRating(review.rating)
    setEditedReview(review.review)
    setEditedImages([...review.images])
    setIsEditing(false)
  }

  const handleDelete = async () => {
    if (confirm("리뷰를 삭제하시겠습니까?")) {
      // TODO: 실제 삭제 API 호출
      router.push("/visited/")
    }
  }

  const handlePhotoAdd = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || [])
    files.forEach((file) => {
      if (editedImages.length < 5) {
        // 최대 5개까지
        const reader = new FileReader()
        reader.onload = (e) => {
          setEditedImages((prev) => [...prev, e.target?.result as string])
        }
        reader.readAsDataURL(file)
      }
    })
  }

  const handlePhotoRemove = (index: number) => {
    setEditedImages((prev) => prev.filter((_, i) => i !== index))
    // 현재 보고 있던 이미지가 삭제된 경우 인덱스 조정
    if (currentImageIndex >= editedImages.length - 1) {
      setCurrentImageIndex(Math.max(0, editedImages.length - 2))
    }
  }

  const nextImage = () => {
    const images = isEditing ? editedImages : review?.images
    if (images) {
      setCurrentImageIndex((prev) => (prev + 1) % images.length)
    }
  }

  const prevImage = () => {
    const images = isEditing ? editedImages : review?.images
    if (images) {
      setCurrentImageIndex((prev) => (prev - 1 + images.length) % images.length)
    }
  }

  if (!isClient) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">초기화 중...</p>
        </div>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">리뷰를 불러오는 중...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-600 mb-4">{error}</p>
          <button
            onClick={() => router.push("/visited/")}
            className="bg-blue-500 text-white px-4 py-2 rounded-lg hover:bg-blue-600"
          >
            뒤로 가기
          </button>
        </div>
      </div>
    )
  }

  if (!review) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">로딩 중...</p>
        </div>
      </div>
    )
  }

  const currentImages = isEditing ? editedImages : review.images

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm flex items-center justify-between">
        <div className="flex items-center">
          <Button onClick={() => router.push("/visited/")} variant="ghost" size="sm" className="mr-3">
            <ArrowLeft className="w-5 h-5" />
          </Button>
          <h1 className="text-lg font-medium">리뷰 상세</h1>
        </div>
        {isEditing && (
          <Button onClick={handleCancelEdit} variant="ghost" size="sm">
            <X className="w-4 h-4" />
          </Button>
        )}
      </div>

      <div className="flex-1 p-4 space-y-4">
        {/* 식당 정보 */}
        <div className="bg-white rounded-lg p-4 shadow-sm">
          <h2 className="text-lg font-medium mb-2">{review.restaurantName}</h2>
          <p className="text-sm text-gray-600 mb-2">{review.address}</p>
          <div className="flex items-center gap-2">
            <div className="flex items-center">
              {isEditing ? (
                // 수정 모드: 별점 선택 가능
                <div className="flex items-center gap-1">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button key={star} onClick={() => setEditedRating(star)} className="p-1">
                      <Star
                        className={`w-5 h-5 ${star <= editedRating ? "text-yellow-400 fill-current" : "text-gray-300"}`}
                      />
                    </button>
                  ))}
                </div>
              ) : (
                // 보기 모드: 기존 별점 표시
                <>
                  {[1, 2, 3, 4, 5].map((star) => (
                    <Star
                      key={star}
                      className={`w-5 h-5 ${star <= review.rating ? "text-yellow-400 fill-current" : "text-gray-300"}`}
                    />
                  ))}
                </>
              )}
            </div>
            <span className="text-sm text-gray-600">방문일: {review.visitDate}</span>
          </div>
        </div>

        {/* 사진 슬라이드 */}
        {currentImages && currentImages.length > 0 && (
          <div className="bg-white rounded-lg p-4 shadow-sm">
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-medium">방문 사진</h3>
              {isEditing && (
                <Button
                  onClick={() => photoInputRef.current?.click()}
                  size="sm"
                  variant="outline"
                  className="flex items-center gap-2"
                >
                  <Plus className="w-4 h-4" />
                  사진 추가
                </Button>
              )}
            </div>

            <div className="relative">
              <img
                src={currentImages[currentImageIndex] || "/placeholder.svg"}
                alt={`리뷰 사진 ${currentImageIndex + 1}`}
                className="w-full h-64 object-cover rounded-lg"
              />

              {/* 수정 모드에서 사진 삭제 버튼 */}
              {isEditing && (
                <button
                  onClick={() => handlePhotoRemove(currentImageIndex)}
                  className="absolute top-2 right-2 w-8 h-8 bg-red-500 text-white rounded-full flex items-center justify-center text-sm hover:bg-red-600"
                >
                  <X className="w-4 h-4" />
                </button>
              )}

              {currentImages.length > 1 && (
                <>
                  <button
                    onClick={prevImage}
                    className="absolute left-2 top-1/2 transform -translate-y-1/2 bg-black bg-opacity-50 text-white rounded-full p-2"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    onClick={nextImage}
                    className="absolute right-2 top-1/2 transform -translate-y-1/2 bg-black bg-opacity-50 text-white rounded-full p-2"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>

                  <div className="flex justify-center mt-3 gap-2">
                    {currentImages.map((_: any, index: number) => (
                      <button
                        key={index}
                        onClick={() => setCurrentImageIndex(index)}
                        className={`w-2 h-2 rounded-full ${
                          index === currentImageIndex ? "bg-blue-500" : "bg-gray-300"
                        }`}
                      />
                    ))}
                  </div>
                </>
              )}
            </div>
          </div>
        )}

        {/* 리뷰 내용 */}
        <div className="bg-white rounded-lg p-4 shadow-sm">
          <h3 className="font-medium mb-3">리뷰</h3>
          {isEditing ? (
            // 수정 모드: 텍스트 에어리어
            <Textarea
              value={editedReview}
              onChange={(e) => setEditedReview(e.target.value)}
              className="min-h-[120px] resize-none"
              placeholder="리뷰를 입력해주세요..."
            />
          ) : (
            // 보기 모드: 기존 리뷰 표시
            <p className="text-gray-700 leading-relaxed">{review.review}</p>
          )}
        </div>

        {/* 수정/삭제 버튼 */}
        <div className="flex gap-3">
          {isEditing ? (
            // 수정 모드: 저장 버튼
            <Button
              onClick={handleSave}
              disabled={!editedReview.trim() || editedRating === 0}
              className="flex-1 bg-blue-500 hover:bg-blue-600 text-white flex items-center justify-center gap-2"
            >
              <Save className="w-4 h-4" />
              저장
            </Button>
          ) : (
            // 보기 모드: 수정/삭제 버튼
            <>
              <Button
                onClick={handleDelete}
                variant="outline"
                className="flex-1 text-red-600 border-red-200 bg-transparent"
              >
                삭제
              </Button>
              <Button
                onClick={handleEdit}
                className="flex-1 bg-blue-500 hover:bg-blue-600 text-white flex items-center justify-center gap-2"
              >
                <Edit className="w-4 h-4" />
                수정
              </Button>
            </>
          )}
        </div>
      </div>

      {/* 사진 추가용 숨겨진 input */}
      <input ref={photoInputRef} type="file" accept="image/*" multiple onChange={handlePhotoAdd} className="hidden" />
    </div>
  )
}
