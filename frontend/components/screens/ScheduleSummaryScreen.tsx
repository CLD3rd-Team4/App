"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import useSchedule from "@/hooks/useSchedule"
import BottomNavigation from "@/components/common/BottomNavigation"
import { RefreshCw, Star } from "lucide-react"
import type { Restaurant, Schedule } from "@/types"
import { MealType } from "@/types"

// 타임라인 아이템 타입을 명시적으로 정의
type TimelineItem = {
  type: "departure" | "waypoint" | "destination" | "restaurant"
  time?: string
  title: string
  icon: string
  color: string
  description?: string
  rating?: number
  restaurant?: Restaurant
}

function RecommendationReadyPopup({ onConfirm }: { onConfirm: () => void }) {
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 max-w-sm mx-4">
        <div className="text-center">
          <div className="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <span className="text-2xl">🍽️</span>
          </div>
          <h3 className="text-lg font-medium mb-2">추천 결과 준비 완료!</h3>
          <p className="text-gray-600 mb-4">맞춤형 식당 추천이 준비되었습니다.</p>
          <Button
            onClick={onConfirm}
            className="w-full bg-blue-500 hover:bg-blue-600 text-white"
          >
            추천 결과 보기
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function ScheduleSummaryScreen() {
  const router = useRouter()
  const { selectedSchedule, isLoading, isProcessing, updateSchedule, deselectSchedule } = useSchedule()
  const [showRecommendationPopup, setShowRecommendationPopup] = useState(false)

  useEffect(() => {
    if (!isLoading && !selectedSchedule) {
        deselectSchedule();
        router.push("/");
    }
  }, [isLoading, selectedSchedule, deselectSchedule, router]);

  const handleUpdate = () => {
    if (!selectedSchedule || !selectedSchedule.id) return
    // 수정 페이지로 이동시키기 위해 localStorage에 데이터 저장
    localStorage.setItem("editingSchedule", JSON.stringify(selectedSchedule));
    router.push(`/schedule/edit?id=${selectedSchedule.id}`);
  }

  const handleRecommendationConfirm = () => {
    setShowRecommendationPopup(false)
    router.push("/recommendations/")
  }

  const formatTime = (time: string) => {
    if (!time || typeof time !== "string") return "시간 미정"
    const match = time.match(/(오전|오후)\s*(\d{1,2}):(\d{2})/)
    if (match) return time
    const parts = time.split(":")
    if (parts.length < 2) return "시간 미정"
    const h = parseInt(parts[0], 10)
    const m = parts[1]
    if (isNaN(h)) return "시간 미정"
    const period = h >= 12 ? "오후" : "오전"
    const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h
    return `${period} ${displayHour}:${m}`
  }

  const createTimelineItems = (): TimelineItem[] => {
    if (!selectedSchedule) return []

    const items: TimelineItem[] = []

    if (selectedSchedule.departureTime && selectedSchedule.departure) {
      items.push({
        type: "departure",
        time: selectedSchedule.departureTime,
        title: selectedSchedule.departure.name,
        icon: "출발",
        color: "red",
      })
    }

    selectedSchedule.waypoints?.forEach((waypoint) => {
      if (waypoint) {
        items.push({
          type: "waypoint",
          time: waypoint.arrivalTime || "",
          title: waypoint.name,
          icon: "경유",
          color: "blue",
        })
      }
    })

    selectedSchedule.selectedRestaurants?.forEach((item) => {
      const mealTime = selectedSchedule.mealSlots?.find(
        (mt) => mt.mealType === (item.sectionId.includes("meal") ? MealType.MEAL : MealType.SNACK)
      )
      items.push({
        type: "restaurant",
        time: mealTime?.scheduledTime || "",
        title: item.restaurant.placeName || "선택된 식당",
        description: item.restaurant.description || "",
        rating: item.restaurant.rating || 0,
        icon: item.sectionId.includes("meal") ? "식사" : "간식",
        color: "orange",
        restaurant: item.restaurant,
      })
    })

    if (selectedSchedule.destination) {
      items.push({
        type: "destination",
        time: selectedSchedule.calculatedArrivalTime,
        title: selectedSchedule.destination.name,
        icon: "도착",
        color: "green",
      })
    }

    return items.sort((a, b) => {
      if (!a.time || !b.time) return 0
      const toComparable = (timeStr: string) => {
        const match = timeStr.match(/(오전|오후)\s*(\d{1,2}):(\d{2})/) // 정규식 수정
        if (!match) return 0
        let [, period, hourStr, minuteStr] = match
        let hour = parseInt(hourStr, 10)
        if (period === "오후" && hour !== 12) hour += 12
        if (period === "오전" && hour === 12) hour = 0
        return hour * 100 + parseInt(minuteStr, 10)
      }
      return toComparable(a.time) - toComparable(b.time)
    })
  }

  const timelineItems = createTimelineItems()

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">선택한 스케줄을 불러오는 중...</p>
        </div>
      </div>
    )
  }

  return (
    <>
      <div className="min-h-screen bg-gray-100 flex flex-col">
        <div className="bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <h1 className="text-lg font-medium">나의 스케줄 요약</h1>
            <Button
              onClick={handleUpdate}
              variant="outline"
              size="sm"
              className="flex items-center gap-2 border-blue-200 text-blue-600 hover:bg-blue-50"
              disabled={isProcessing}
            >
              <RefreshCw
                className={`w-4 h-4 ${isProcessing ? "animate-spin" : ""}`}
              />
              {isProcessing ? "업데이트 중..." : "추천 업데이트"}
            </Button>
          </div>
        </div>

        <div className="flex-1 content-with-bottom-nav">
          <div className="p-4">
            <div className="bg-white rounded-lg p-4 shadow-sm">
              {timelineItems.length === 0 ? (
                <div className="text-center py-8">
                  <p className="text-gray-600 mb-4">
                    스케줄 정보가 없습니다. 다시 선택해주세요.
                  </p>
                  <Button
                    onClick={() => router.push("/schedule/")}
                    className="bg-blue-500 hover:bg-blue-600 text-white"
                  >
                    스케줄 선택하기
                  </Button>
                </div>
              ) : (
                <div className="space-y-4">
                  {timelineItems.map((item, index) => (
                    <div
                      key={index}
                      className={`flex items-center gap-3 ${
                        item.type === "restaurant"
                          ? "bg-orange-50 rounded-lg p-3 -mx-3"
                          : ""
                      }`}
                    >
                      <div
                        className={`w-8 h-8 ${
                          item.color === "red"
                            ? "bg-red-100"
                            : item.color === "blue"
                            ? "bg-blue-100"
                            : item.color === "orange"
                            ? "bg-orange-500"
                            : "bg-green-100"
                        } rounded-full flex items-center justify-center`}
                      >
                        <span
                          className={`text-sm font-medium ${
                            item.color === "orange"
                              ? "text-white"
                              : item.color === "red"
                              ? "text-red-600"
                              : item.color === "blue"
                              ? "text-blue-600"
                              : "text-green-600"
                          }`}
                        >
                          {item.icon}
                        </span>
                      </div>
                      <div className="flex-1">
                        <p className="text-sm text-gray-500">
                          {item.time ? formatTime(item.time) : "시간 미정"}
                        </p>
                        <p className="font-medium">{item.title}</p>
                        {item.type === "restaurant" && (
                          <>
                            {item.description && (
                              <p className="text-sm text-gray-600">
                                {item.description}
                              </p>
                            )}
                            {item.rating && item.rating > 0 && (
                              <div className="flex items-center mt-1">
                                <Star className="w-4 h-4 text-yellow-400 fill-current" />
                                <span className="text-sm ml-1">
                                  {item.rating}
                                </span>
                              </div>
                            )}
                          </>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        <BottomNavigation currentTab="home" />
      </div>

      {showRecommendationPopup && (
        <RecommendationReadyPopup onConfirm={handleRecommendationConfirm} />
      )}
    </>
  )
}