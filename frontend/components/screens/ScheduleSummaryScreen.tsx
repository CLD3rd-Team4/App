"use client"

import { useEffect, useMemo, useState } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import BottomNavigation from "@/components/common/BottomNavigation"
import { RefreshCw } from "lucide-react"
import api from "@/lib/interceptor"
import type { Restaurant } from "@/types"
import { MealType } from "@/types"
import useSchedule from "@/hooks/useSchedule"

type TimelineItem = {
  type: "departure" | "waypoint" | "destination" | "restaurant"
  time?: string
  title: string
  icon: string
  color: string
  description?: string       // 추천 이유 (aiReason)
  url?: string               // 카카오 지도 URL
  restaurant?: Restaurant
}

type ScheduleDetailResp = {
  status: "OK" | "NOT_FOUND" | "ERROR"
  message?: string
  scheduleDetail?: {
    departureTime: string
    departureName: string
    destinationName: string
    estimatedArrivalTime: string
    waypointNames: string[]
    waypointTimes: string[]
  }
}

type ViewModel = {
  scheduleId: string
  departureTime?: string
  departure?: { name: string }
  destination?: { name: string }
  calculatedArrivalTime?: string
  waypoints?: Array<{ name: string; arrivalTime?: string }>
  mealSlots?: Array<{ slotId: string; mealType: number; scheduledTime?: string }>
  selectedRestaurants?: Array<{
    sectionId: string
    restaurant: Restaurant
  }>
}

// ✅ 키 통일
const LAST_SUBMIT_KEY = "recommend:lastSubmit"

export default function ScheduleSummaryScreen() {
  const router = useRouter()
  const { isProcessing } = useSchedule()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [vm, setVm] = useState<ViewModel | null>(null)

  // ---- 데이터 로드: 로컬 lastSubmit (+ 가능하면 서버 scheduleDetail)
  useEffect(() => {
    let mounted = true
    ;(async () => {
      try {
        setLoading(true)
        setError(null)

        // 1) 로컬 lastSubmit 읽기
        const raw = localStorage.getItem(LAST_SUBMIT_KEY)
        if (!raw) {
          setError("선택 내역이 없습니다. 스케줄을 먼저 선택해주세요.")
          setVm(null)
          return
        }

        const lastSubmit = JSON.parse(raw) as {
          scheduleId: string
          isSelected: boolean
          selectedPlaces: Array<{
            slotId: string
            mealType: number
            scheduledTime?: string
            id: string
            placeName: string
            reason?: string
            distance?: string
            placeUrl?: string
            addressName?: string
            averageRating?: number
            representativeReview?: string
          }>
          expiryAt?: number
          submittedAt?: string
        }

        const scheduleId = lastSubmit.scheduleId
        if (!scheduleId) {
          setError("scheduleId가 없습니다. 스케줄을 다시 선택해주세요.")
          setVm(null)
          return
        }

        // 2) 서버에서 출발/도착/경유 가져오기 (있으면 보강, 없어도 로컬만으로 렌더)
        let d: ScheduleDetailResp["scheduleDetail"] | undefined
        try {
          const { data } = await api.get<ScheduleDetailResp>(
            `/recommend/schedule/${encodeURIComponent(scheduleId)}`
          )
          if (data.status === "OK" && data.scheduleDetail) d = data.scheduleDetail
        } catch {
          // 서버 요약 없어도 무시하고 로컬로 렌더
        }

        // 3) view model 합치기 (로컬 선택식당 + 서버 스케줄 보강)
        const waypoints =
          d?.waypointNames?.map((name, i) => ({
            name,
            arrivalTime: d?.waypointTimes?.[i] || "",
          })) ?? []

        // 로컬 선택 슬롯/시간
        const mealSlots = lastSubmit.selectedPlaces.map((p) => ({
          slotId: p.slotId,
          mealType: p.mealType,
          scheduledTime: p.scheduledTime,
        }))

        // 로컬 선택 식당 → Restaurant
        const selectedRestaurants = lastSubmit.selectedPlaces.map((p) => ({
          sectionId: (p.mealType === MealType.MEAL ? "meal-" : "snack-") + p.slotId,
          restaurant: {
            id: p.id,
            placeName: p.placeName,
            aiReason: p.reason ?? "",          // ✅ 추천 이유
            // rating: 사용 안 함
            addressName: p.addressName,
            // 외부 타입에 있지만 화면에서 쓸 거라 유지
            // @ts-ignore
            placeUrl: p.placeUrl,
          } as Restaurant,
        }))

        const nextVm: ViewModel = {
          scheduleId,
          departureTime: d?.departureTime,
          departure: d?.departureName ? { name: d.departureName } : undefined,
          destination: d?.destinationName ? { name: d.destinationName } : undefined,
          calculatedArrivalTime: d?.estimatedArrivalTime,
          waypoints,
          mealSlots,
          selectedRestaurants,
        }

        if (mounted) setVm(nextVm)
      } catch (e: any) {
        if (mounted) {
          setError(e?.message || "알 수 없는 오류가 발생했습니다.")
          setVm(null)
        }
      } finally {
        if (mounted) setLoading(false)
      }
    })()
    return () => {
      mounted = false
    }
  }, [])

  const handleUpdate = () => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const { latitude, longitude } = position.coords
          alert(`현재 위치: 위도 ${latitude}, 경도 ${longitude}`)
        },
        (error) => {
          let errorMessage = "위치 정보를 가져오는 데 실패했습니다."
          switch (error.code) {
            case error.PERMISSION_DENIED:
              errorMessage = "위치 정보 접근 권한이 거부되었습니다. 설정에서 권한을 허용해주세요."
              break
            case error.POSITION_UNAVAILABLE:
              errorMessage = "현재 위치를 파악할 수 없습니다."
              break
            case error.TIMEOUT:
              errorMessage = "위치 정보를 가져오는 데 시간이 초과되었습니다."
              break
          }
          alert(errorMessage)
        }
      )
    } else {
      alert("이 브라우저에서는 위치 정보 기능을 사용할 수 없습니다.")
    }
  }

  // "HH:mm"도 지원하여 오전/오후로 포맷
  const formatTime = (time: string) => {
    if (!time || typeof time !== "string") return "시간 미정"
    // 이미 오전/오후 형태면 그대로
    if (/(오전|오후)\s*\d{1,2}:\d{2}/.test(time)) return time
    // 24시간 → 오전/오후
    const m = time.match(/^(\d{1,2}):(\d{2})$/)
    if (!m) return "시간 미정"
    let h = parseInt(m[1], 10)
    const mm = m[2]
    if (isNaN(h)) return "시간 미정"
    const period = h >= 12 ? "오후" : "오전"
    const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h
    return `${period} ${displayHour}:${mm}`
  }

  const timelineItems: TimelineItem[] = useMemo(() => {
    if (!vm) return []

    const items: TimelineItem[] = []

    if (vm.departureTime && vm.departure) {
      items.push({
        type: "departure",
        time: vm.departureTime,
        title: vm.departure.name,
        icon: "출발",
        color: "red",
      })
    }

    vm.waypoints?.forEach((wp) => {
      items.push({
        type: "waypoint",
        time: wp.arrivalTime || "",
        title: wp.name,
        icon: "경유",
        color: "blue",
      })
    })

    // ✅ 식당: 로컬 scheduledTime, aiReason/URL만 사용
    vm.selectedRestaurants?.forEach((item) => {
      const slotId = item.sectionId.split("-").pop()
      const mt = vm.mealSlots?.find((ms) => ms.slotId === slotId)

      items.push({
        type: "restaurant",
        time: mt?.scheduledTime || "",
        title: item.restaurant.placeName || "선택된 식당",
        description: item.restaurant.aiReason || "",
        // @ts-ignore
        url: (item.restaurant as any).placeUrl || "",
        icon: item.sectionId.startsWith("meal-") ? "식사" : "간식",
        color: "orange",
        restaurant: item.restaurant,
      })
    })

    if (vm.destination) {
      items.push({
        type: "destination",
        time: vm.calculatedArrivalTime,
        title: vm.destination.name,
        icon: "도착",
        color: "green",
      })
    }

    // 시간순 정렬 (오전/오후, 24시간 모두 지원)
    const toComparable = (timeStr?: string) => {
      if (!timeStr) return 0
      // 오전/오후 HH:mm
      const ampm = timeStr.match(/(오전|오후)\s*(\d{1,2}):(\d{2})/)
      if (ampm) {
        let [, period, hh, mm] = ampm
        let h = parseInt(hh, 10)
        if (period === "오후" && h !== 12) h += 12
        if (period === "오전" && h === 12) h = 0
        return h * 100 + parseInt(mm, 10)
      }
      // HH:mm
      const m = timeStr.match(/^(\d{1,2}):(\d{2})$/)
      if (m) {
        const h = parseInt(m[1], 10)
        const mm = parseInt(m[2], 10)
        return h * 100 + mm
      }
      return 0
    }

    return items.sort((a, b) => toComparable(a.time) - toComparable(b.time))
  }, [vm])

  if (loading) {
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
            <RefreshCw className={`w-4 h-4 ${isProcessing ? "animate-spin" : ""}`} />
            {isProcessing ? "업데이트 중..." : "추천 업데이트"}
          </Button>
        </div>
      </div>

      <div className="flex-1 content-with-bottom-nav">
        <div className="p-4">
          <div className="bg-white rounded-lg p-4 shadow-sm">
            {error || timelineItems.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-gray-600 mb-4">
                  {error ?? "스케줄 정보가 없습니다. 다시 선택해주세요."}
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
                      item.type === "restaurant" ? "bg-orange-50 rounded-lg p-3 -mx-3" : ""
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
                          {/* 추천 이유 */}
                          {item.description && (
                            <p className="text-sm text-gray-600">{item.description}</p>
                          )}

                          {/* 링크 */}
                          {item.url && (
                            <div className="mt-1">
                              <a
                                href={item.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-sm text-blue-600 underline"
                              >
                                카카오 지도
                              </a>
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
  )
}
