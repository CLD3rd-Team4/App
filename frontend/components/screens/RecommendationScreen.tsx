// app/recommendations/page.tsx

"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import { ArrowLeft, Star, ChevronDown, ChevronUp } from "lucide-react"
import useSchedule from "@/hooks/useSchedule"
import type { Restaurant } from "@/types"
import api from "@/lib/interceptor"

// ==== 서버 proto에 맞춘 응답 타입 ====
type ApiPlace = {
  id: string
  placeName: string
  reason?: string
  distance?: string
  scheduledTime?: string
  mealType?: number // 0=식사, 1=간식
  placeUrl?: string
  addressName?: string
  averageRating?: number
  representativeReview?: string
  image?: string
}

type ApiSlot = { slotId: string; places: ApiPlace[] }
type GetResultsResponse = {
  slotRecommendations: ApiSlot[]
  status: "OK" | "PENDING" | "ERROR"
  message?: string
}

type SubmitPlace = {
  slotId: string
  mealType: number
  scheduledTime: string
  id: string
  placeName: string
  reason?: string
  distance?: string
  addressName?: string
  placeUrl?: string
  averageRating?: number
  representativeReview?: string
}

type SubmitRequest = {
  scheduleId: string
  selectedPlaces: SubmitPlace[]
}

// ==== 화면용 타입 ====
interface MealSection {
  id: string                 // 화면용 id
  originSlotId?: string      // 서버 slotId (있으면 이걸 우선 사용)
  title: string
  type: "식사" | "간식"
  index: number
  time: string
  restaurants: Restaurant[]
  previousSelection?: Restaurant
}

// ==== 헬퍼 ====
const mealTypeToLabel = (t: number): "식사" | "간식" => (t === 0 ? "식사" : "간식")
const sectionTitle = (label: "식사" | "간식", idx: number) =>
  label === "식사" ? `식사${idx}` : `간식${idx}`

const toRestaurant = (p: ApiPlace): Restaurant => ({
  id: p.id,
  placeName: p.placeName,
  description: p.representativeReview || p.reason || "추천 사유 없음",
  aiReason: p.reason || "",
  rating: p.averageRating,
  distance: p.distance || "",
  addressName: p.addressName,
  image: p.image || "/placeholder.svg?height=80&width=80",
  // @ts-ignore 외부 타입
  placeUrl: p.placeUrl,
})

export default function RecommendationScreen() {
  const router = useRouter()
  const { selectedSchedule, selectSchedule } = useSchedule()

  // refs
  const activeScheduleIdRef = useRef<string | null>(null)

  // state
  const [mealSections, setMealSections] = useState<MealSection[]>([])
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set())
  const [selectedRestaurants, setSelectedRestaurants] = useState<Record<string, Restaurant>>({})
  const [isLoading, setIsLoading] = useState(true)

  // 데이터 로드
  const loadRecommendations = useCallback(async () => {
    try {
      setIsLoading(true)

      const scheduleId = selectedSchedule?.id
      if (!scheduleId) {
        setMealSections([])
        setExpandedSections(new Set())
        setIsLoading(false)
        return
      }

      // 현재 페이지에서 다루는 유효 스케줄 ID 저장
      activeScheduleIdRef.current = scheduleId

      const { data } = await api.get<GetResultsResponse>("/recommend/result", {
      params: { scheduleId },   // ← 쿼리스트링으로 전달
      headers: {
      accept: "application/json",
      "cache-control": "no-cache",
      pragma: "no-cache",
  },
})

      // ✅ 가드: 응답이 현재 선택된 스케줄의 것이 아니면 무시
      if (activeScheduleIdRef.current !== scheduleId) {
        console.log("Stale response 무시:", scheduleId, "≠", activeScheduleIdRef.current)
        return
      }

      if (!data?.slotRecommendations?.length || data.status === "PENDING") {
        setMealSections([])
        setExpandedSections(new Set())
        return
      }

      // 시간(문자열) → 식사/간식 우선 정렬
      const slots = [...data.slotRecommendations].sort((a, b) => {
        const af = a.places?.[0]
        const bf = b.places?.[0]
        const t = (af?.scheduledTime ?? "").localeCompare(bf?.scheduledTime ?? "")
        if (t !== 0) return t
        const am = af?.mealType ?? 0
        const bm = bf?.mealType ?? 0
        return am - bm
      })

      let mealIdx = 1
      let snackIdx = 1

      const sections: MealSection[] = slots.map((slot) => {
        const first = slot.places?.[0]
        const label = mealTypeToLabel(first?.mealType ?? 0)
        const idx = label === "식사" ? mealIdx++ : snackIdx++
        const restaurants: Restaurant[] = (slot.places || []).map(toRestaurant)

        return {
          id: `${label === "식사" ? "meal" : "snack"}-${idx}`,
          originSlotId: slot.slotId, // ✅ 서버 slotId 보존
          title: sectionTitle(label, idx),
          type: label,
          index: idx,
          time: first?.scheduledTime ?? "",
          restaurants,
          previousSelection: undefined,
        }
      })

      setMealSections(sections)
      setExpandedSections(new Set())
    } catch (e) {
      console.error("추천 결과 로드 실패:", e)
      setMealSections([])
      setExpandedSections(new Set())
    } finally {
      setIsLoading(false)
    }
  }, [selectedSchedule])

  useEffect(() => {
    loadRecommendations()
  }, [loadRecommendations])

  // UI 핸들러
  const toggleSection = useCallback((sectionId: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev)
      next.has(sectionId) ? next.delete(sectionId) : next.add(sectionId)
      return next
    })
  }, [])

  const handleRestaurantSelect = useCallback((sectionId: string, restaurant: Restaurant) => {
    setSelectedRestaurants((prev) => ({ ...prev, [sectionId]: restaurant }))
  }, [])

  const formatTime = (time: string) => {
    if (!time) return ""
    if (/^(오전|오후)\s?\d{1,2}:\d{2}$/.test(time)) return time
    if (/^\d{1,2}:\d{2}$/.test(time)) {
      const [hour, minute] = time.split(":")
      const h = Number.parseInt(hour, 10)
      const period = h >= 12 ? "오후" : "오전"
      const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h
      return `${period} ${displayHour}:${minute}`
    }
    return time
  }

  const isAllSectionsSelected = useCallback(() => {
    return mealSections.every((section) => !!selectedRestaurants[section.id])
  }, [mealSections, selectedRestaurants])

  // ✅ 제출: proto 맞춰 POST /recommend/submit 호출 + localStorage 보관
  const handleComplete = async () => {
    if (!isAllSectionsSelected()) {
      alert("모든 식사/간식 시간에 대해 식당을 선택해주세요.")
      return
    }

    const scheduleId = selectedSchedule?.id
    if (!scheduleId) {
      alert("오류: 스케줄 ID가 없습니다.")
      return
    }

    const selectedPlaces: SubmitPlace[] = mealSections.map(sec => {
      const r = selectedRestaurants[sec.id]!
      return {
        slotId: sec.originSlotId || sec.id,
        mealType: sec.type === "식사" ? 0 : 1,
        scheduledTime: sec.time,
        id: r.id,
        placeName: r.placeName,
        reason: r.aiReason || r.description || "",
        distance: r.distance || "",
        addressName: (r as any).addressName || "",
        placeUrl: (r as any).placeUrl || "",
        averageRating: r.rating ?? 0,
        representativeReview: r.description || "",
      }
    })

    const payload: SubmitRequest = {
      scheduleId,
      selectedPlaces,
    }

    try {
      await api.post<{ status: "OK" | "ERROR"; message?: string }>("/recommend/submit", payload)

      // 중앙 상태 업데이트(옵션)
      await selectSchedule(scheduleId)

      // 최근 제출 내역 저장 (홈 등에서 안내 용도)
      const LS_KEY_SELECTED = "recommend:lastSubmit"
      localStorage.setItem(
        LS_KEY_SELECTED,
        JSON.stringify({
          scheduleId,
          submittedAt: new Date().toISOString(),
          selectedPlaces,
        })
      )

      alert("선택을 저장했습니다.")
      router.push("/")
    } catch (e: any) {
      console.error("submit 실패:", e?.response?.data || e)
      alert("저장 중 오류가 발생했습니다.")
    }
  }

  // 렌더
  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center">
          <Button onClick={() => router.push("/")} variant="ghost" size="sm" className="mr-3">
            <ArrowLeft className="w-5 h-5" />
          </Button>
          <h1 className="text-lg font-medium">추천 결과</h1>
        </div>
        <Button
          onClick={handleComplete}
          disabled={!isAllSectionsSelected()}
          size="sm"
          className={`px-4 py-2 font-medium ${
            !isAllSectionsSelected()
              ? "bg-gray-300 text-gray-500 cursor-not-allowed"
              : "bg-blue-500 hover:bg-blue-600 text-white shadow-md"
          }`}
        >
          입력완료 ({Object.keys(selectedRestaurants).length}/{mealSections.length})
        </Button>
      </div>

      <div className="flex-1 content-with-bottom-nav">
        <div className="p-4">
          {isLoading ? (
            <div className="text-center py-8">
              <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
              <p className="text-gray-600">추천 결과를 불러오는 중...</p>
            </div>
          ) : mealSections.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-gray-600 mb-4">식사 시간이 설정되지 않았거나 결과가 아직 준비되지 않았습니다.</p>
              <Button onClick={() => router.push("/schedule")} className="bg-blue-500 hover:bg-blue-600 text-white">
                스케줄 수정하기
              </Button>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="bg-blue-500 text-white p-4 rounded-lg">
                <h2 className="font-medium mb-2">추천 결과</h2>
                <p className="text-sm opacity-90">사용자의 이동경로와 선호도에 따라 추천된 장소입니다</p>
              </div>

              {mealSections.map((section) => (
                <div key={section.id} className="bg-white rounded-lg shadow-sm border">
                  <button
                    onClick={() => toggleSection(section.id)}
                    className="w-full p-4 flex items-center justify-between text-left hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                        <span className="text-sm font-medium text-blue-600">
                          {section.type === "식사" ? "🍽️" : "🍪"}
                        </span>
                      </div>
                      <div>
                        <span className="font-medium text-lg">{section.title}</span>
                        <span className="text-sm text-gray-500 ml-2">({formatTime(section.time)})</span>
                      </div>
                      {selectedRestaurants[section.id] && (
                        <span className="text-xs bg-green-100 text-green-600 px-2 py-1 rounded-full">선택완료</span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      {selectedRestaurants[section.id] && (
                        <span className="text-sm text-gray-600">{selectedRestaurants[section.id].placeName}</span>
                      )}
                      {expandedSections.has(section.id) ? (
                        <ChevronUp className="w-5 h-5 text-gray-400" />
                      ) : (
                        <ChevronDown className="w-5 h-5 text-gray-400" />
                      )}
                    </div>
                  </button>

                  {expandedSections.has(section.id) && (
                    <div className="px-4 pb-4 border-t bg-gray-50">
                      <div className="space-y-3 pt-4">
                        {section.previousSelection && (
                          <div className="bg-green-50 p-4 rounded-lg border-2 border-green-200">
                            <div className="flex items-center gap-2 mb-3">
                              <span className="text-xs bg-green-500 text-white px-2 py-1 rounded-full font-medium">
                                이전 선택
                              </span>
                              <h3 className="font-medium">{section.previousSelection.placeName}</h3>
                            </div>
                            <div className="flex items-start gap-3">
                              <img
                                src={
                                  section.previousSelection.image ||
                                  "/placeholder.svg?height=60&width=60&query=restaurant"
                                }
                                alt={section.previousSelection.placeName}
                                className="w-16 h-16 rounded-lg object-cover flex-shrink-0"
                              />
                              <div className="flex-1 min-w-0">
                                <p className="text-sm text-gray-600 mb-2">{section.previousSelection.description}</p>
                                <p className="text-sm text-blue-600 mb-2">{section.previousSelection.aiReason}</p>
                                <div className="flex items-center justify-between">
                                  <div className="flex items-center gap-4">
                                    {section.previousSelection.rating && (
                                      <div className="flex items-center">
                                        <Star className="w-4 h-4" />
                                        <span className="text-sm ml-1 font-medium">
                                          {section.previousSelection.rating}
                                        </span>
                                      </div>
                                    )}
                                    <span className="text-sm text-gray-500">
                                      거리: {section.previousSelection.distance || "1.2km"}
                                    </span>
                                  </div>
                                </div>
                                <Button
                                  onClick={() => handleRestaurantSelect(section.id, section.previousSelection!)}
                                  size="sm"
                                  className={`w-full mt-2 ${
                                    selectedRestaurants[section.id]?.id === section.previousSelection!.id
                                      ? "bg-green-500 hover:bg-green-600 text-white"
                                      : "bg-blue-500 hover:bg-blue-600 text-white"
                                  }`}
                                >
                                  {selectedRestaurants[section.id]?.id === section.previousSelection!.id ? "✓ 선택됨" : "다시 선택"}
                                </Button>
                              </div>
                            </div>
                          </div>
                        )}

                        <div className="space-y-3">
                          <h4 className="font-medium text-gray-800">새로운 추천</h4>
                          {section.restaurants.map((restaurant) => (
                            <div
                              key={restaurant.id}
                              className={`bg-white border rounded-lg p-4 hover:border-blue-200 transition-all ${
                                selectedRestaurants[section.id]?.id === restaurant.id ? "border-blue-500 bg-blue-50" : ""
                              }`}
                            >
                              <div className="flex items-start gap-4">
                                <img
                                  src={restaurant.image || "/placeholder.svg?height=80&width=80&query=restaurant"}
                                  alt={restaurant.placeName}
                                  className="w-20 h-20 rounded-lg object-cover flex-shrink-0"
                                />
                                <div className="flex-1 min-w-0">
                                  {/* 이름 */}
                                  <h3 className="font-semibold text-lg mb-1">{restaurant.placeName}</h3>

                                  {/* 한줄평 */}
                                  <p className="text-sm text-gray-600 mb-2">{restaurant.description}</p>
                                  <p className="text-sm text-blue-600 mb-3">{restaurant.aiReason}</p>
                                  <div className="flex items-center justify-between mb-3">
                                    <div className="flex items-center gap-4">
                                      {restaurant.rating && (
                                        <div className="flex items-center">
                                          <Star className="w-4 h-4" />
                                          <span className="text-sm ml-1 font-medium">{restaurant.rating}</span>
                                        </div>
                                      )}
                                      <span className="text-sm text-gray-500">거리: {restaurant.distance}</span>
                                    </div>
                                  </div>
                                  <Button
                                    onClick={() => handleRestaurantSelect(section.id, restaurant)}
                                    size="sm"
                                    className={`w-full ${
                                      selectedRestaurants[section.id]?.id === restaurant.id
                                        ? "bg-green-500 hover:bg-green-600 text-white"
                                        : "bg-blue-500 hover:bg-blue-600 text-white"
                                    }`}
                                  >
                                    {selectedRestaurants[section.id]?.id === restaurant.id ? "✓ 선택됨" : "선택하기"}
                                  </Button>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ))}

              {Object.keys(selectedRestaurants).length > 0 && (
                <div className="bg-blue-50 p-4 rounded-lg">
                  <h3 className="font-medium mb-2">선택된 식당</h3>
                  <div className="space-y-2">
                    {Object.entries(selectedRestaurants).map(([sectionId, restaurant]) => {
                      const section = mealSections.find((s) => s.id === sectionId)
                      return (
                        <div key={sectionId} className="flex items-center gap-2 text-sm">
                          <span className="font-medium">{section?.title}:</span>
                          <span>{restaurant.placeName}</span>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
