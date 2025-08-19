// app/recommendations/page.tsx
"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import { ArrowLeft, ChevronDown, ChevronUp, MapPin } from "lucide-react"
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
  selectedSlotPlaces?: ApiSlot[]     // ✅ 이전선택(신규 필드, 서버가 추가)
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
  restaurants: Restaurant[]          // 새 후보 (최대 2개)
  previousSelection?: Restaurant     // 이전 선택 (있으면 1개)
}

// ==== 헬퍼 ====
const mealTypeToLabel = (t: number): "식사" | "간식" => (t === 0 ? "식사" : "간식")
const sectionTitle = (label: "식사" | "간식", idx: number) =>
  label === "식사" ? `식사${idx}` : `간식${idx}`

const extractDong = (addr?: string) => {
  if (!addr) return ""
  const tokens = addr.split(/\s+/)
  const dongLike = [...tokens].reverse().find(t => /(동|가|읍|면|리)$/.test(t))
  return dongLike || tokens[tokens.length - 2] || tokens[tokens.length - 1] || ""
}

const renderStars = (rating: number) => {
  const v = Math.round(Math.max(0, Math.min(5, rating || 0)))
  const full = "★".repeat(v)
  const empty = "☆".repeat(5 - v)
  return (
    <span className="text-yellow-500 tracking-tight" aria-label={`카카오 평점 ${v}점/5점`}>
      {full}{empty}
    </span>
  )
}

function endOfTodayTs(): number {
  const d = new Date();
  d.setHours(23, 59, 59, 999);
  return d.getTime();
}
const LS_KEY_SELECTED = "recommend:lastSubmit";
function writeLastSubmitSafely(entry: any) {
  try {
    localStorage.setItem(LS_KEY_SELECTED, JSON.stringify(entry));
    console.log("[recommendations] lastSubmit saved:", entry);
  } catch (e) {
    console.error("[recommendations] lastSubmit save failed:", e);
  }
}

// API → 화면 모델
const toRestaurant = (p: ApiPlace): Restaurant => ({
  id: p.id,
  placeName: p.placeName,
  description: p.representativeReview || "",
  aiReason: p.reason || "",
  rating: (typeof p.averageRating === "number" && p.averageRating > 0) ? p.averageRating : undefined,
  distance: "",
  addressName: p.addressName,
  image: "",
  // @ts-ignore
  placeUrl: p.placeUrl,
})

export default function RecommendationScreen() {
  const router = useRouter()
  const { selectedSchedule, selectSchedule } = useSchedule()

  const activeScheduleIdRef = useRef<string | null>(null)

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

      activeScheduleIdRef.current = scheduleId

      const { data } = await api.get<GetResultsResponse>("/recommend/result", {
        params: { scheduleId },
      })

      if (activeScheduleIdRef.current !== scheduleId) return

      const candidates = data.slotRecommendations ?? []
      const selected = data.selectedSlotPlaces ?? []

      if ((candidates.length === 0 && selected.length === 0) || data.status === "PENDING") {
        setMealSections([])
        setExpandedSections(new Set())
        return
      }

      // slotId → 슬롯 매핑
      const candMap = new Map<string, ApiSlot>(candidates.map(s => [s.slotId, s]))
      const selMap = new Map<string, ApiSlot>(selected.map(s => [s.slotId, s]))

      // 슬롯 합집합
      const slotIds = Array.from(new Set<string>([
        ...candidates.map(s => s.slotId),
        ...selected.map(s => s.slotId),
      ]))

      // 정렬 기준: 후보 첫 장소의 scheduledTime → 없으면 이전선택 첫 장소의 scheduledTime → 빈문자
      slotIds.sort((a, b) => {
        const at = candMap.get(a)?.places?.[0]?.scheduledTime ?? selMap.get(a)?.places?.[0]?.scheduledTime ?? ""
        const bt = candMap.get(b)?.places?.[0]?.scheduledTime ?? selMap.get(b)?.places?.[0]?.scheduledTime ?? ""
        const t = at.localeCompare(bt)
        if (t !== 0) return t
        // 동률이면 mealType(식사 먼저)
        const am = candMap.get(a)?.places?.[0]?.mealType ?? selMap.get(a)?.places?.[0]?.mealType ?? 0
        const bm = candMap.get(b)?.places?.[0]?.mealType ?? selMap.get(b)?.places?.[0]?.mealType ?? 0
        return am - bm
      })

      let mealIdx = 1
      let snackIdx = 1
      const sections: MealSection[] = slotIds.map(slotId => {
        const candSlot = candMap.get(slotId)
        const selSlot = selMap.get(slotId)

        const firstCand = candSlot?.places?.[0]
        const firstSel  = selSlot?.places?.[0]

        const mealType = firstCand?.mealType ?? firstSel?.mealType ?? 0
        const label: "식사" | "간식" = mealTypeToLabel(mealType)
        const idx = label === "식사" ? mealIdx++ : snackIdx++

        // 섹션 생성 부분 중 일부 (slotIds.map 내부)

        // 이전선택 (있으면 1개만 사용)
          const previousSelection: Restaurant | undefined = firstSel ? toRestaurant(firstSel) : undefined

        // ✅ 후보 최대 개수: 이전선택 있으면 2개, 없으면 3개
          const maxCandidates = previousSelection ? 2 : 3

        // 후보: 이전선택과 id 중복 제거 후 최대 maxCandidates개
        const prevId = previousSelection?.id
        const restaurants: Restaurant[] = (candSlot?.places ?? [])
        .filter(p => !prevId || p.id !== prevId)
        .slice(0, maxCandidates)   // ⬅️ 여기만 변경!
        .map(toRestaurant)


        // 섹션 시간 결정
        const time = firstCand?.scheduledTime ?? firstSel?.scheduledTime ?? ""

        return {
          id: `${label === "식사" ? "meal" : "snack"}-${idx}`,
          originSlotId: slotId,
          title: sectionTitle(label, idx),
          type: label,
          index: idx,
          time,
          restaurants,
          previousSelection,
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

  // 제출
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
        reason: r.aiReason || "",
        distance: "",
        addressName: (r as any).addressName || "",
        placeUrl: (r as any).placeUrl || "",
        averageRating: r.rating ?? 0,
        representativeReview: r.description || "",
      }
    })

    const payload: SubmitRequest = { scheduleId, selectedPlaces }

    try {
      await api.post<{ status: "OK" | "ERROR"; message?: string }>("/recommend/submit", payload)
      await selectSchedule(scheduleId)

      const entry = {
        scheduleId,
        submittedAt: new Date().toISOString(),
        selectedPlaces,
        isSelected: true,
        expiryAt: endOfTodayTs(),
      }
      writeLastSubmitSafely(entry)

      alert("선택을 저장했습니다.")
      router.push("/")
    } catch (e: any) {
      console.error("submit 실패:", e?.response?.data || e)
      alert("저장 중 오류가 발생했습니다.")
    }
  }

  const PinTile = ({ addressName }: { addressName?: string }) => {
    const dong = extractDong(addressName)
    return (
      <div className="w-20 h-20 rounded-lg bg-blue-100 flex flex-col items-center justify-center relative overflow-hidden">
        <MapPin className="w-7 h-7 text-blue-600" />
        {dong ? (
          <span className="absolute bottom-1 text-[11px] px-1.5 py-0.5 rounded-full bg-white/90 text-gray-700">
            {dong}
          </span>
        ) : null}
      </div>
    )
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
                        {/* 이전 선택 (있을 때만) */}
                        {section.previousSelection && (
                          <div className="bg-green-50 p-4 rounded-lg border-2 border-green-200">
                            <div className="flex items-start gap-4">
                              <PinTile addressName={section.previousSelection.addressName as any} />
                              <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 mb-2">
                                  <span className="text-xs bg-green-500 text-white px-2 py-1 rounded-full font-medium">
                                    이전 선택
                                  </span>
                                  <h3 className="font-medium">{section.previousSelection.placeName}</h3>
                                </div>

                                {section.previousSelection.description && (
                                  <p className="text-sm text-gray-600 mb-2">{section.previousSelection.description}</p>
                                )}
                                {section.previousSelection.aiReason && (
                                  <p className="text-sm text-blue-600 mb-2">{section.previousSelection.aiReason}</p>
                                )}

                                {(
                                  (!!section.previousSelection.rating && section.previousSelection.rating > 0) ||
                                  !!section.previousSelection.aiReason
                                ) && (
                                  <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-4">
                                      {section.previousSelection.rating && section.previousSelection.rating > 0 && (
                                        <div className="flex items-center gap-2">
                                          <span className="text-xs text-gray-500">카카오</span>
                                          {renderStars(section.previousSelection.rating)}
                                        </div>
                                      )}
                                      {(section.previousSelection as any).placeUrl && (
                                        <a
                                          href={(section.previousSelection as any).placeUrl}
                                          target="_blank"
                                          rel="noopener noreferrer"
                                          className="text-sm text-blue-600 underline"
                                        >
                                          카카오 지도
                                        </a>
                                      )}
                                    </div>
                                  </div>
                                )}

                                <Button
                                  onClick={() => handleRestaurantSelect(section.id, section.previousSelection!)}
                                  size="sm"
                                  className={`w-full mt-3 ${
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

                        {/* 새로운 추천 (최대 2개) */}
                        <div className="space-y-3">
                          <h4 className="font-medium text-gray-800">새로운 추천</h4>
                          {section.restaurants.map((restaurant) => {
                            const hasRating = !!restaurant.rating && restaurant.rating > 0
                            const hasReason = !!restaurant.aiReason
                            const showMeta = hasRating || hasReason

                            return (
                              <div
                                key={restaurant.id}
                                className={`bg-white border rounded-lg p-4 hover:border-blue-200 transition-all ${
                                  selectedRestaurants[section.id]?.id === restaurant.id ? "border-blue-500 bg-blue-50" : ""
                                }`}
                              >
                                <div className="flex items-start gap-4">
                                  <PinTile addressName={(restaurant as any).addressName} />

                                  <div className="flex-1 min-w-0">
                                    <h3 className="font-semibold text-lg mb-1">{restaurant.placeName}</h3>

                                    {restaurant.description && (
                                      <p className="text-sm text-gray-600 mb-2">{restaurant.description}</p>
                                    )}
                                    {restaurant.aiReason && (
                                      <p className="text-sm text-blue-600 mb-3">{restaurant.aiReason}</p>
                                    )}

                                    {showMeta && (
                                      <div className="flex items-center justify-between mb-3">
                                        <div className="flex items-center gap-4">
                                          {hasRating && (
                                            <div className="flex items-center gap-2">
                                              <span className="text-xs text-gray-500">카카오</span>
                                              {renderStars(restaurant.rating as number)}
                                            </div>
                                          )}

                                          {(restaurant as any).placeUrl && (
                                            <a
                                              href={(restaurant as any).placeUrl}
                                              target="_blank"
                                              rel="noopener noreferrer"
                                              className="text-sm text-blue-600 underline"
                                            >
                                              카카오 지도
                                            </a>
                                          )}
                                        </div>
                                      </div>
                                    )}

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
                            )
                          })}
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
