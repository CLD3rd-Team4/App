"use client"

import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import { useSchedule } from "@/hooks/useSchedule"
import BottomNavigation from "@/components/common/BottomNavigation"
import { RefreshCw, Star } from "lucide-react"

// 목데이터 - 백엔드 연동 전 테스트용
const MOCK_SCHEDULE = {
  id: "mock-1",
  title: "테스트 스케줄",
  departure: "서울역",
  destination: "부산역",
  waypoints: ["대전역"],
  departureTime: "09:00",
  arrivalTime: "20:00",
  targetMealTimes: [
    { type: "식사" as const, time: "12:00" },
    { type: "식사" as const, time: "18:00" },
    { type: "간식" as const, time: "15:00" },
  ],
}

export default function ScheduleSummaryScreen() {
  const router = useRouter()
  const { selectedSchedule } = useSchedule()

  // 목데이터 사용 - 백엔드 연동 시 주석 처리하고 아래 주석 해제
  const currentSchedule = MOCK_SCHEDULE

  // 백엔드 연동 시 주석 해제
  // const currentSchedule = selectedSchedule
  // if (!selectedSchedule) {
  //   return null
  // }

  const formatTime = (time: string) => {
    if (!time) return "시간 미정"
    const [hour, minute] = time.split(":")
    const h = Number.parseInt(hour)
    const period = h >= 12 ? "오후" : "오전"
    const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h
    return `${period} ${displayHour}:${minute}`
  }

  // 시간순으로 정렬된 일정 생성
  const createTimelineItems = () => {
    const items = []

    // 출발지
    if (currentSchedule.departureTime && currentSchedule.departure) {
      items.push({
        type: "departure",
        time: currentSchedule.departureTime,
        title: currentSchedule.departure,
        icon: "출발",
        color: "red",
      })
    }

    // 경유지들
    currentSchedule.waypoints?.forEach((waypoint, index) => {
      if (waypoint) {
        items.push({
          type: "waypoint",
          time: "11:30", // TODO: 실제 시간 계산
          title: waypoint,
          icon: "경유",
          color: "blue",
        })
      }
    })

    // 목데이터 - 로컬스토리지에서 선택된 식당들 가져오기
    try {
      const savedSchedule = localStorage.getItem("selectedSchedule")
      if (savedSchedule) {
        const parsedSchedule = JSON.parse(savedSchedule)
        if (parsedSchedule.selectedRestaurants) {
          parsedSchedule.selectedRestaurants.forEach((item: any) => {
            // 섹션 ID를 기반으로 시간 찾기
            const sectionType = item.sectionId.includes("meal") ? "식사" : "간식"
            const sectionIndex = Number.parseInt(item.sectionId.split("-")[1])

            let targetTime = "12:00" // 기본값
            let currentIndex = 1

            for (const mealTime of currentSchedule.targetMealTimes || []) {
              if (mealTime.type === sectionType) {
                if (currentIndex === sectionIndex) {
                  targetTime = mealTime.time
                  break
                }
                currentIndex++
              }
            }

            items.push({
              type: "restaurant",
              time: targetTime,
              title: item.restaurant.name || "선택된 식당",
              description: item.restaurant.description || "",
              rating: item.restaurant.rating || 0,
              icon: item.sectionId.includes("meal") ? "식사" : "간식",
              color: "orange",
              restaurant: item.restaurant,
            })
          })
        }
      }
    } catch (error) {
      console.error("로컬스토리지 파싱 에러:", error)
    }

    // 백엔드 연동 시 주석 해제
    // if (currentSchedule.selectedRestaurants) {
    //   currentSchedule.selectedRestaurants.forEach((item: any) => {
    //     const section = currentSchedule.targetMealTimes?.find((meal) => {
    //       const sectionType = item.sectionId.includes("meal") ? "식사" : "간식"
    //       const sectionIndex = Number.parseInt(item.sectionId.split("-")[1])
    //       let currentIndex = 1

    //       for (const mealTime of currentSchedule.targetMealTimes || []) {
    //         if (mealTime.type === sectionType) {
    //           if (currentIndex === sectionIndex) {
    //             return true
    //           }
    //           currentIndex++
    //         }
    //       }
    //       return false
    //     })

    //     if (section) {
    //       items.push({
    //         type: "restaurant",
    //         time: section.time,
    //         title: item.restaurant.name,
    //         description: item.restaurant.description,
    //         rating: item.restaurant.rating,
    //         icon: item.sectionId.includes("meal") ? "식사" : "간식",
    //         color: "orange",
    //         restaurant: item.restaurant,
    //       })
    //     }
    //   })
    // } else if (currentSchedule.selectedRestaurant) {
    //   // 기존 단일 식당 표시 (하위 호환성)
    //   items.push({
    //     type: "restaurant",
    //     time: "12:30",
    //     title: currentSchedule.selectedRestaurant.name,
    //     description: currentSchedule.selectedRestaurant.description,
    //     rating: currentSchedule.selectedRestaurant.rating,
    //     icon: "식사",
    //     color: "blue",
    //     restaurant: currentSchedule.selectedRestaurant,
    //   })
    // }

    // 도착지
    if (currentSchedule.arrivalTime && currentSchedule.destination) {
      items.push({
        type: "destination",
        time: currentSchedule.arrivalTime,
        title: currentSchedule.destination,
        icon: "도착",
        color: "green",
      })
    }

    // 시간순으로 정렬 (time이 undefined인 경우 처리)
    return items
      .filter((item) => item.time) // time이 있는 항목만 필터링
      .sort((a, b) => {
        if (!a.time || !b.time) return 0
        return a.time.localeCompare(b.time)
      })
  }

  const timelineItems = createTimelineItems()

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-medium">나의 스케줄 요약</h1>
          <Button
            onClick={() => router.push("/recommendations")}
            variant="outline"
            size="sm"
            className="flex items-center gap-2 border-blue-200 text-blue-600 hover:bg-blue-50"
          >
            <RefreshCw className="w-4 h-4" />
            추천 업데이트
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
                  onClick={() => router.push("/recommendations")}
                  className="bg-blue-500 hover:bg-blue-600 text-white"
                >
                  추천 결과 보기
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
                      <p className="text-sm text-gray-500">{formatTime(item.time)}</p>
                      <p className="font-medium">{item.title}</p>
                      {item.description && <p className="text-sm text-gray-600">{item.description}</p>}
                      {item.rating && item.rating > 0 && (
                        <div className="flex items-center mt-1">
                          <Star className="w-4 h-4 text-yellow-400 fill-current" />
                          <span className="text-sm ml-1">{item.rating}</span>
                        </div>
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
  )
}
