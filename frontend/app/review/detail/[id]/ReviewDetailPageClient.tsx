"use client"

import type React from "react"

import { useState, useEffect, useRef } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { useRouter, useParams } from "next/navigation"
import { ArrowLeft, Star, ChevronLeft, ChevronRight, Edit, Save, X, Plus } from "lucide-react"

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
  const photoInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return

    // TODO: 실제 API에서 리뷰 상세 정보 가져오기
    const mockReview = {
      id: params.id,
      restaurantName: "OO 음식점",
      address: "서울시 강남구 테헤란로 123",
      rating: 4,
      visitDate: "2024.07.18",
      review: "맛있었어요! 분위기도 좋고 서비스도 친절했습니다. 다음에 또 방문하고 싶어요.",
      images: [
        "/placeholder.svg?height=300&width=400",
        "/placeholder.svg?height=300&width=400",
        "/placeholder.svg?height=300&width=400",
      ],
    }
    setReview(mockReview)
    setEditedRating(mockReview.rating)
    setEditedReview(mockReview.review)
    setEditedImages([...mockReview.images])
  }, [params.id, isClient])

  const handleEdit = () => {
    setIsEditing(true)
  }

  const handleSave = async () => {
    try {
      // TODO: 실제 수정 API 호출
      const updatedReview = {
        ...review,
        rating: editedRating,
        review: editedReview,
        images: editedImages,
      }
      setReview(updatedReview)
      setIsEditing(false)

      // 성공 메시지 표시 (선택사항)
      alert("리뷰가 수정되었습니다.")
    } catch (error) {
      console.error("리뷰 수정 실패:", error)
      alert("리뷰 수정에 실패했습니다.")
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

  if (!isClient || !review) {
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
          <Button onClick={() => router.back()} variant="ghost" size="sm" className="mr-3">
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
