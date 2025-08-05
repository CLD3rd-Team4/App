"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import { useSchedule } from "@/hooks/useSchedule"
import BottomNavigation from "@/components/common/BottomNavigation"
import { RefreshCw, Star } from "lucide-react"
import { scheduleApi } from "@/services/api"
import type { Restaurant } from "@/types";

// 타임라인 아이템 타입을 명시적으로 정의
type TimelineItem = {
  type: "departure" | "waypoint" | "destination" | "restaurant";
  time?: string;
  title: string;
  icon: string;
  color: string;
  description?: string;
  rating?: number;
  restaurant?: Restaurant;
};

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
          <Button onClick={onConfirm} className="w-full bg-blue-500 hover:bg-blue-600 text-white">
            추천 결과 보기
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function ScheduleSummaryScreen() {
  const router = useRouter()
  const { selectedSchedule } = useSchedule()
  const [isUpdating, setIsUpdating] = useState(false)
  const [showRecommendationPopup, setShowRecommendationPopup] = useState(false)

  const handleUpdate = () => {
    if (!selectedSchedule || !selectedSchedule.id) return;

    setIsUpdating(true);
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const { latitude, longitude } = position.coords;
        try {
          await scheduleApi.processSchedule(selectedSchedule.id!, {
            type: 'UPDATE',
            currentLat: latitude,
            currentLng: longitude,
            currentTime: new Date().toISOString(),
          });
          alert('스케줄이 업데이트되었습니다.');
        } catch (error) {
          console.error("스케줄 업데이트 실패:", error);
          alert('스케줄 업데이트에 실패했습니다.');
        } finally {
          setIsUpdating(false);
        }
      },
      (error) => {
        console.error("GPS 위치 정보 가져오기 실패:", error);
        alert('GPS 위치 정보를 가져올 수 없습니다.');
        setIsUpdating(false);
      }
    );
  };

  const handleRecommendationConfirm = () => {
    setShowRecommendationPopup(false)
    router.push("/recommendations/")
  }

  const formatTime = (time: string) => {
    if (!time || typeof time !== 'string') return "시간 미정";
    const match = time.match(/(오전|오후)\s*(\d{1,2}):(\d{2})/);
    if (match) return time;
    const parts = time.split(":");
    if (parts.length < 2) return "시간 미정";
    const h = parseInt(parts[0], 10);
    const m = parts[1];
    if (isNaN(h)) return "시간 미정";
    const period = h >= 12 ? "오후" : "오전";
    const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h;
    return `${period} ${displayHour}:${m}`;
  }

  const createTimelineItems = (): TimelineItem[] => {
    if (!selectedSchedule) return [];

    const items = []

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
          time: waypoint.arrivalTime || "", // 수정된 부분
          title: waypoint.name,
          icon: "경유",
          color: "blue",
        })
      }
    })

    selectedSchedule.selectedRestaurants?.forEach((item) => {
        const mealTime = selectedSchedule.targetMealTimes?.find(mt => mt.type === (item.sectionId.includes('meal') ? '식사' : '간식'));
        items.push({
            type: "restaurant",
            time: mealTime?.time || "",
            title: item.restaurant.name || "선택된 식당",
            description: item.restaurant.description || "",
            rating: item.restaurant.rating || 0,
            icon: item.sectionId.includes("meal") ? "식사" : "간식",
            color: "orange",
            restaurant: item.restaurant,
        });
    });

    if (selectedSchedule.destination) {
      items.push({
        type: "destination",
        time: selectedSchedule.calculatedArrivalTime,
        title: selectedSchedule.destination.name,
        icon: "도착",
        color: "green",
      })
    }

    return items
      .sort((a, b) => {
        if (!a.time || !b.time) return 0
        // 시간 문자열을 비교 가능한 형태로 변환 (예: "오후 1:30" -> 1330)
        const toComparable = (timeStr: string) => {
            const match = timeStr.match(/(오전|오후)\s*(\d{1,2}):(\d{2})/);
            if (!match) return 0;
            let [ , period, hourStr, minuteStr ] = match;
            let hour = parseInt(hourStr, 10);
            if (period === '오후' && hour !== 12) hour += 12;
            if (period === '오전' && hour === 12) hour = 0;
            return hour * 100 + parseInt(minuteStr, 10);
        };
        return toComparable(a.time) - toComparable(b.time);
      })
  }

  const timelineItems = createTimelineItems()

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
              disabled={isUpdating}
            >
              <RefreshCw className={`w-4 h-4 ${isUpdating ? 'animate-spin' : ''}`} />
              {isUpdating ? '업데이트 중...' : '추천 업데이트'}
            </Button>
          </div>
        </div>

        <div className="flex-1 content-with-bottom-nav">
          <div className="p-4">
            <div className="bg-white rounded-lg p-4 shadow-sm">
              {timelineItems.length === 0 ? (
                <div className="text-center py-8">
                  <p className="text-gray-600 mb-4">스케줄 정보가 없습니다.</p>
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
                      className={`flex items-center gap-3 ${item.type === "restaurant" ? "bg-orange-50 rounded-lg p-3 -mx-3" : ""}`}
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
                        <p className="text-sm text-gray-500">{item.time ? formatTime(item.time) : "시간 미정"}</p>
                        <p className="font-medium">{item.title}</p>
                        {item.type === 'restaurant' && (
                          <>
                            {item.description && <p className="text-sm text-gray-600">{item.description}</p>}
                            {item.rating && item.rating > 0 && (
                              <div className="flex items-center mt-1">
                                <Star className="w-4 h-4 text-yellow-400 fill-current" />
                                <span className="text-sm ml-1">{item.rating}</span>
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

      {showRecommendationPopup && <RecommendationReadyPopup onConfirm={handleRecommendationConfirm} />}
    </>
  )
}