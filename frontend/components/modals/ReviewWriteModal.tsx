"use client"

import type React from "react"
import { useState, useRef } from "react"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { Camera, X, Star } from "lucide-react"
import { ocrApi, reviewApi, APIError } from "@/services/api"
import type { OCRResult, CreateReviewRequest } from "@/types"

interface ReviewWriteModalProps {
  restaurant: any
  onComplete: (reviewData: any) => void
  onCancel: () => void
}

export function ReviewWriteModal({ restaurant, onComplete, onCancel }: ReviewWriteModalProps) {
  const [step, setStep] = useState<1 | 2 | 3>(1)
  const [capturedImage, setCapturedImage] = useState<string | null>(null)
  const [ocrResult, setOcrResult] = useState<OCRResult | null>(null)
  const [rating, setRating] = useState(0)
  const [reviewText, setReviewText] = useState("")
  const [reviewImages, setReviewImages] = useState<string[]>([])
  const [visitDate, setVisitDate] = useState("")
  const [isProcessing, setIsProcessing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  
  // OCR에서 추출한 식당명과 주소 상태 추가
  const [restaurantName, setRestaurantName] = useState(restaurant.placeName || restaurant.name || '')
  const [restaurantAddress, setRestaurantAddress] = useState(restaurant.addressName || restaurant.address || '')
  const fileInputRef = useRef<HTMLInputElement>(null)
  const photoInputRef = useRef<HTMLInputElement>(null)

  // OCR 날짜를 HTML date input 형식(yyyy-MM-dd)으로 변환
  const formatDateForInput = (ocrDate: string): string | null => {
    try {
      // 다양한 날짜 형식 처리: 2024-08-11, 2024/08/11, 24-08-11, 08/11/2024 등
      const patterns = [
        /(\d{4})[-\/.](\d{1,2})[-\/.](\d{1,2})/, // 2024-08-11, 2024/08/11
        /(\d{1,2})[-\/.](\d{1,2})[-\/.](\d{4})/, // 08/11/2024, 11-08-2024
        /(\d{2})[-\/.](\d{1,2})[-\/.](\d{1,2})/, // 24-08-11, 24/08/11
      ]
      
      for (let pattern of patterns) {
        const match = ocrDate.match(pattern)
        if (match) {
          let year: string, month: string, day: string
          
          if (pattern === patterns[0]) { // yyyy-MM-dd 형식
            [, year, month, day] = match
          } else if (pattern === patterns[1]) { // MM/dd/yyyy 형식
            [, month, day, year] = match
          } else if (pattern === patterns[2]) { // yy-MM-dd 형식
            [, year, month, day] = match
            year = parseInt(year) > 50 ? `19${year}` : `20${year}` // 50보다 크면 1900년대, 작으면 2000년대
          } else {
            continue // 매칭되지 않은 경우 다음 패턴으로
          }
          
          // 모든 값이 존재하는지 확인
          if (!year || !month || !day) continue
          
          // 월과 일을 2자리로 패딩
          month = month.padStart(2, '0')
          day = day.padStart(2, '0')
          
          return `${year}-${month}-${day}`
        }
      }
      
      return null
    } catch (error) {
      console.error('날짜 변환 오류:', error)
      return null
    }
  }

  const handleImageCapture = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      const reader = new FileReader()
      reader.onload = (e) => {
        setCapturedImage(e.target?.result as string)
      }
      reader.readAsDataURL(file)
    }
  }

  const handleOCRProcess = async () => {
    if (!capturedImage) return

    try {
      setIsProcessing(true)
      setError(null)
      
      const result = await ocrApi.processReceipt(
        capturedImage,
        restaurant.placeName || restaurant.name || '',
        restaurant.addressName || restaurant.address || ''
      )
      
      // OCR 결과에서 추출한 식당명, 주소, 방문날짜로 자동 업데이트
      if (result.restaurantName && result.restaurantName.trim()) {
        setRestaurantName(result.restaurantName.trim())
      }
      if (result.address && result.address.trim()) {
        setRestaurantAddress(result.address.trim())
      }
      if (result.visitDate && result.visitDate.trim()) {
        // OCR에서 추출한 날짜를 yyyy-MM-dd 형식으로 변환
        const formattedDate = formatDateForInput(result.visitDate.trim())
        if (formattedDate) {
          setVisitDate(formattedDate)
        } else {
          // OCR 날짜 변환에 실패한 경우 오늘 날짜로 설정
          setVisitDate(new Date().toISOString().split("T")[0])
        }
      } else {
        // OCR에서 날짜를 추출하지 못한 경우 오늘 날짜로 설정
        setVisitDate(new Date().toISOString().split("T")[0])
      }
      
      setOcrResult(result)
      setStep(2)
    } catch (error) {
      console.error("OCR 처리 실패:", error)
      
      if (error instanceof APIError) {
        setError(error.message)
        
        // 인증 에러인 경우 로그인 페이지로 이동
        if (error.status === 401) {
          // TODO: 로그인 페이지로 리다이렉트
          alert('로그인이 필요합니다. 다시 로그인해주세요.')
          return
        }
      } else {
        setError('영수증 처리 중 오류가 발생했습니다. 다시 시도해주세요.')
      }
    } finally {
      setIsProcessing(false)
    }
  }

  const handlePhotoAdd = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || [])
    files.forEach((file) => {
      if (reviewImages.length < 3) {
        const reader = new FileReader()
        reader.onload = (e) => {
          setReviewImages((prev) => [...prev, e.target?.result as string])
        }
        reader.readAsDataURL(file)
      }
    })
  }

  const handlePhotoRemove = (index: number) => {
    setReviewImages((prev) => prev.filter((_, i) => i !== index))
  }

  const handleComplete = async () => {
    try {
      setIsProcessing(true)
      setError(null)
      
      const reviewData: CreateReviewRequest = {
        restaurantId: restaurant.restaurantId || restaurant.id || '',
        restaurantName: restaurantName, // OCR에서 추출된 식당명 사용
        restaurantAddress: restaurantAddress, // OCR에서 추출된 주소 사용
        rating,
        content: reviewText,
        receiptImages: capturedImage ? [capturedImage] : [],
        reviewImages: reviewImages,
        ocrData: ocrResult || undefined,
        scheduledTime: restaurant.scheduledTime, // 미작성 리뷰 완료 처리용
        visitDate: visitDate, // OCR 날짜 검증용
      }

      const result = await reviewApi.createReview(reviewData)
      onComplete(result)
    } catch (error) {
      console.error("리뷰 작성 실패:", error)
      
      if (error instanceof APIError) {
        setError(error.message)
        
        if (error.status === 401) {
          alert('로그인이 필요합니다. 다시 로그인해주세요.')
          return
        }
      } else {
        setError('리뷰 작성 중 오류가 발생했습니다. 다시 시도해주세요.')
      }
    } finally {
      setIsProcessing(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-end justify-center z-50">
      <div className="bg-white rounded-t-2xl w-full max-w-md max-h-[80vh] overflow-y-auto">
        {/* Step 1: 영수증 인증 */}
        {step === 1 && (
          <div className="p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-medium">영수증 인증</h2>
              <Button onClick={onCancel} variant="ghost" size="sm">
                <X className="w-5 h-5" />
              </Button>
            </div>

            <div className="mb-4">
              <p className="text-sm text-gray-600 mb-2">식당명: {restaurantName || '식당명'}</p>
              <p className="text-sm text-gray-600 mb-2">주소: {restaurantAddress || "주소 정보"}</p>
              {restaurant.scheduledTime && (
                <p className="text-sm text-blue-600">예정 시간: {restaurant.scheduledTime}</p>
              )}
            </div>

            {/* 에러 메시지 표시 */}
            {error && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg">
                <p className="text-sm text-red-600">{error}</p>
                <button
                  onClick={() => setError(null)}
                  className="text-xs text-red-500 hover:text-red-700 mt-1"
                >
                  확인
                </button>
              </div>
            )}
            
            

            {!capturedImage ? (
              <div className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center mb-4">
                <Camera className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <p className="text-gray-600 mb-4">영수증을 촬영하거나 업로드하세요</p>
                <Button
                  onClick={() => fileInputRef.current?.click()}
                  className="bg-blue-500 hover:bg-blue-600 text-white"
                >
                  <Camera className="w-4 h-4 mr-2" />
                  촬영하기
                </Button>
              </div>
            ) : (
              <div className="mb-4">
                <img
                  src={capturedImage || "/placeholder.svg"}
                  alt="촬영된 영수증"
                  className="w-full max-h-48 object-contain rounded-lg border mb-4"
                />
                <Button onClick={() => setCapturedImage(null)} variant="outline" size="sm" className="w-full mb-2">
                  다시 촬영
                </Button>
              </div>
            )}

            <div className="flex gap-3">
              <Button onClick={onCancel} variant="outline" className="flex-1 bg-transparent">
                취소
              </Button>
              <Button
                onClick={handleOCRProcess}
                disabled={!capturedImage || isProcessing}
                className="flex-1 bg-blue-500 hover:bg-blue-600 text-white disabled:bg-gray-400"
              >
                {isProcessing ? "영수증을 분석하고 있습니다..." : "다음"}
              </Button>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              capture="environment"
              onChange={handleImageCapture}
              className="hidden"
            />
          </div>
        )}

        {/* Step 2: OCR 결과 확인 및 수정 */}
        {step === 2 && ocrResult && (
          <div className="p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-medium">OCR 결과 확인 및 수정</h2>
              <Button onClick={onCancel} variant="ghost" size="sm">
                <X className="w-5 h-5" />
              </Button>
            </div>

            {/* 식당 정보 수정 섹션 */}
            <div className="space-y-4 mb-4">
              <div>
                <Label htmlFor="restaurantName" className="text-sm font-medium">
                  식당명 {ocrResult.restaurantName ? '(OCR 자동 입력됨)' : ''}
                </Label>
                <Input
                  id="restaurantName"
                  type="text"
                  value={restaurantName}
                  onChange={(e) => setRestaurantName(e.target.value)}
                  placeholder="식당명을 입력해주세요"
                  className="mt-1"
                />
              </div>

              <div>
                <Label htmlFor="restaurantAddress" className="text-sm font-medium">
                  주소 {ocrResult.address ? '(OCR 자동 입력됨)' : ''}
                </Label>
                <Input
                  id="restaurantAddress"
                  type="text"
                  value={restaurantAddress}
                  onChange={(e) => setRestaurantAddress(e.target.value)}
                  placeholder="주소를 입력해주세요"
                  className="mt-1"
                />
              </div>

              <div>
                <Label htmlFor="visitDateStep2" className="text-sm font-medium">
                  방문날짜 {ocrResult.visitDate ? '(OCR 자동 입력됨)' : ''}
                </Label>
                <Input
                  id="visitDateStep2"
                  type="date"
                  value={visitDate}
                  onChange={(e) => setVisitDate(e.target.value)}
                  className="mt-1"
                />
              </div>
            </div>

            <div className="bg-gray-50 rounded-lg p-4 mb-4">
              <h3 className="font-medium mb-2">OCR 추출 정보</h3>
              <div className="space-y-2 text-sm">
                <p>
                  <span className="font-medium">방문일:</span> {ocrResult.visitDate || '추출되지 않음'}
                </p>
                {ocrResult.totalAmount && (
                  <p>
                    <span className="font-medium">결제금액:</span> {ocrResult.totalAmount}
                  </p>
                )}
                <p>
                  <span className="font-medium">검증결과:</span>
                  <span className={ocrResult.isValid ? "text-green-600" : "text-red-600"}>
                    {ocrResult.isValid ? " 통과" : " 실패"}
                  </span>
                  <span className="text-gray-500 ml-2">
                    (신뢰도: {Math.round((ocrResult.confidence || 0) * 100)}%)
                  </span>
                </p>
                {ocrResult.rawText && (
                  <details className="mt-2">
                    <summary className="cursor-pointer text-gray-600 hover:text-gray-800">
                      원본 텍스트 보기
                    </summary>
                    <pre className="mt-1 p-2 bg-gray-50 rounded text-xs whitespace-pre-wrap">
                      {ocrResult.rawText}
                    </pre>
                  </details>
                )}
              </div>
            </div>

            <div className="flex gap-3">
              <Button onClick={() => setStep(1)} variant="outline" className="flex-1">
                이전
              </Button>
              <Button
                onClick={() => setStep(3)}
                disabled={!restaurantName.trim() || !restaurantAddress.trim()}
                className="flex-1 bg-blue-500 hover:bg-blue-600 text-white disabled:bg-gray-400"
              >
                다음 {!ocrResult.isValid && '(수동 입력)'}
              </Button>
            </div>
            
            {!ocrResult.isValid && (
              <div className="mt-3 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                <p className="text-sm text-yellow-800">
                  <strong>참고:</strong> OCR 검증이 실패했지만, 정보를 수정하여 계속 진행할 수 있습니다.
                </p>
              </div>
            )}
          </div>
        )}

        {/* Step 3: 리뷰 작성 */}
        {step === 3 && (
          <div className="p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-medium">리뷰 작성</h2>
              <Button onClick={onCancel} variant="ghost" size="sm">
                <X className="w-5 h-5" />
              </Button>
            </div>

            <div className="space-y-4">
              {/* OCR 결과 날짜 검증 메시지 */}
              {ocrResult && ocrResult.visitDate && (
                <div className={`mb-4 p-3 border rounded-lg ${
                  formatDateForInput(ocrResult.visitDate) === visitDate 
                    ? 'bg-green-50 border-green-200' 
                    : 'bg-yellow-50 border-yellow-200'
                }`}>
                  <p className={`text-sm ${
                    formatDateForInput(ocrResult.visitDate) === visitDate 
                      ? 'text-green-800' 
                      : 'text-yellow-800'
                  }`}>
                    <strong>OCR 추출 날짜:</strong> {ocrResult.visitDate}
                    {formatDateForInput(ocrResult.visitDate) === visitDate 
                      ? ' ✓ 일치' 
                      : ' ⚠️ 불일치'
                    }
                  </p>
                  <p className={`text-xs mt-1 ${
                    formatDateForInput(ocrResult.visitDate) === visitDate 
                      ? 'text-green-600' 
                      : 'text-yellow-600'
                  }`}>
                    {formatDateForInput(ocrResult.visitDate) === visitDate 
                      ? '영수증 날짜와 방문 날짜가 일치합니다.' 
                      : '영수증 날짜와 방문 날짜가 다릅니다. 정확한 날짜인지 확인해주세요.'
                    }
                  </p>
                </div>
              )}

              {/* 방문일 */}
              <div>
                <Label htmlFor="visitDate" className="text-sm font-medium">
                  방문일
                </Label>
                <Input
                  id="visitDate"
                  type="date"
                  value={visitDate}
                  onChange={(e) => setVisitDate(e.target.value)}
                  className="mt-1"
                />
              </div>

              {/* 별점 */}
              <div>
                <Label className="text-sm font-medium">별점</Label>
                <div className="flex items-center gap-1 mt-2">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button key={star} onClick={() => setRating(star)} className="p-1">
                      <Star
                        className={`w-8 h-8 ${star <= rating ? "text-yellow-400 fill-current" : "text-gray-300"}`}
                      />
                    </button>
                  ))}
                </div>
              </div>

              {/* 방문 사진 */}
              <div>
                <Label className="text-sm font-medium">방문 사진 (최대 3개)</Label>
                <div className="grid grid-cols-3 gap-2 mt-2">
                  {reviewImages.map((image, index) => (
                    <div key={index} className="relative">
                      <img
                        src={image || "/placeholder.svg"}
                        alt={`리뷰 이미지 ${index + 1}`}
                        className="w-full h-20 object-cover rounded-lg"
                      />
                      <button
                        onClick={() => handlePhotoRemove(index)}
                        className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center text-xs"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  ))}

                  {reviewImages.length < 3 && (
                    <button
                      onClick={() => photoInputRef.current?.click()}
                      className="w-full h-20 border-2 border-dashed border-gray-300 rounded-lg flex flex-col items-center justify-center text-gray-500"
                    >
                      <Camera className="w-5 h-5 mb-1" />
                      <span className="text-xs">추가</span>
                    </button>
                  )}
                </div>
              </div>

              {/* 텍스트 리뷰 */}
              <div>
                <Label htmlFor="reviewText" className="text-sm font-medium">
                  리뷰
                </Label>
                <Textarea
                  id="reviewText"
                  value={reviewText}
                  onChange={(e) => setReviewText(e.target.value)}
                  placeholder="식당에 대한 리뷰를 작성해주세요..."
                  className="mt-1 min-h-[100px]"
                />
              </div>
            </div>

            <div className="flex gap-3 mt-6">
              <Button onClick={() => setStep(2)} variant="outline" className="flex-1">
                이전
              </Button>
              <Button
                onClick={handleComplete}
                disabled={rating === 0 || !reviewText.trim() || isProcessing}
                className="flex-1 bg-blue-500 hover:bg-blue-600 text-white disabled:bg-gray-400"
              >
                {isProcessing ? "리뷰를 작성하고 있습니다..." : "완료"}
              </Button>
            </div>

            <input
              ref={photoInputRef}
              type="file"
              accept="image/*"
              multiple
              onChange={handlePhotoAdd}
              className="hidden"
            />
          </div>
        )}
      </div>
    </div>
  )
}
