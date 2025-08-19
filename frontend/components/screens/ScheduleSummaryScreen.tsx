// components/screens/ScheduleSummaryScreen.tsx
"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import BottomNavigation from "@/components/common/BottomNavigation"
import { RefreshCw } from "lucide-react"
import api from "@/lib/interceptor"
import type { Restaurant } from "@/types"
import { MealType } from "@/types"
import useSchedule from "@/hooks/useSchedule"

// 팝업
import ScheduleProcessingPopup from "@/components/modals/ScheduleProcessingPopup"
import RecommendationReadyPopup from "@/components/modals/RecommendationReadyPopup"

// 파일 로컬 전용 runId 생성기(공통 유틸 사용 안 함, export 안 함)
const genRunId = () => {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}
const LS_RUN_PREFIX = "recommend:lastRun:"
const setLastRunId = (scheduleId: string, runId: string) => {
  try { localStorage.setItem(`${LS_RUN_PREFIX}${scheduleId}`, runId) } catch {}
}

type TimelineItem = {
  type: "departure" | "waypoint" | "destination" | "restaurant" | "update"
  time?: string
  title: string
  icon: string
  color: "red" | "blue" | "orange" | "green" | "purple"
  description?: string
  url?: string
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
    // ★ 서버 proto 변경: 업데이트는 시간만 전달
    updates?: Array<{ time: string }>
  }
}

type ViewModel = {
  scheduleId: string
  departureTime?: string
  departure?: { name: string }
  destination?: { name: string }
  calculatedArrivalTime?: string
  waypoints?: Array<{ name: string; arrivalTime?: string }>
  // ★ 업데이트는 시간만
  updates?: Array<{ time: string }>
  mealSlots?: Array<{ slotId: string; mealType: number; scheduledTime?: string }>
  selectedRestaurants?: Array<{
    sectionId: string
    restaurant: Restaurant
  }>
}

const LAST_SUBMIT_KEY = "recommend:lastSubmit"
const POLL_INTERVAL_MS = 1500
const RECOMMEND_SEND_URL = "/recommend/request"
const RECOMMEND_RESULT_URL = "/recommend/result"

type GetResultsResponse = {
  status: "PENDING" | "OK" | "ERROR" | string
  message?: string
  runId?: string
  slotRecommendations?: Array<{
    slotId: string
    places: Array<{ id: string; placeName: string }>
  }>
}

type PopupType = "processing" | "recommendation_ready"

/** "오전/오후 HH:mm" | "HH:mm" | ISO → Date(오늘 날짜) */
const parseDisplayTimeToDate = (time?: string): Date | null => {
  if (!time || typeof time !== "string") return null
  const ampm = time.match(/(오전|오후)\s*(\d{1,2}):(\d{2})/)
  if (ampm) {
    const [, period, hhStr, mmStr] = ampm
    let h = parseInt(hhStr, 10)
    const m = parseInt(mmStr, 10)
    if (period === "오후" && h !== 12) h += 12
    if (period === "오전" && h === 12) h = 0
    const d = new Date()
    d.setHours(h, m, 0, 0)
    return d
  }
  const h24 = time.match(/^(\d{1,2}):(\d{2})$/)
  if (h24) {
    const h = parseInt(h24[1], 10)
    const m = parseInt(h24[2], 10)
    const d = new Date()
    d.setHours(h, m, 0, 0)
    return d
  }
  const maybe = new Date(time)
  return isNaN(maybe.getTime()) ? null : maybe
}

/** 표시용: “오전/오후 HH:mm” */
const toKoreanAmPm = (time?: string): string => {
  if (!time) return "시간 미정"
  if (/(오전|오후)\s*\d{1,2}:\d{2}/.test(time)) return time
  const h24 = time.match(/^(\d{1,2}):(\d{2})$/)
  if (h24) {
    let h = parseInt(h24[1], 10)
    const mm = h24[2]
    const period = h >= 12 ? "오후" : "오전"
    const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h
    return `${period} ${displayHour}:${mm}`
  }
  const d = parseDisplayTimeToDate(time)
  if (!d) return "시간 미정"
  const h = d.getHours()
  const m = d.getMinutes()
  const period = h >= 12 ? "오후" : "오전"
  const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h
  const mm = String(m).padStart(2, "0")
  return `${period} ${displayHour}:${mm}`
}

/** 분 단위 비교값(0~1439). 인식 실패 시 null */
const minutesOfDay = (time?: string): number | null => {
  const d = parseDisplayTimeToDate(time)
  if (!d) return null
  return d.getHours() * 60 + d.getMinutes()
}

/** 출발시간을 기준으로, 시각이 출발보다 이르면 +1일 보정된 Date 반환 */
const anchorToScheduleDay = (time?: string, departureTime?: string): Date | null => {
  const t = parseDisplayTimeToDate(time)
  if (!t) return null
  if (!departureTime) return t
  const tMin = minutesOfDay(time)
  const depMin = minutesOfDay(departureTime)
  if (tMin == null || depMin == null) return t
  if (tMin < depMin) {
    const anchored = new Date(t)
    anchored.setDate(anchored.getDate() + 1)
    return anchored
  }
  return t
}

export default function ScheduleSummaryScreen() {
  const router = useRouter()
  const { isProcessing } = useSchedule()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [vm, setVm] = useState<ViewModel | null>(null)

  const [isPopupOpen, setIsPopupOpen] = useState(false)
  const [currentPopup, setCurrentPopup] = useState<PopupType>("processing")
  const [updating, setUpdating] = useState(false)

  // 폴링 및 요청 식별자
  const pollingStopRef = useRef<() => void>(() => {})
  const currentRunIdRef = useRef<string | null>(null)

  useEffect(() => {
    let mounted = true
    ;(async () => {
      try {
        setLoading(true)
        setError(null)

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

        let d: ScheduleDetailResp["scheduleDetail"] | undefined
        try {
          const { data } = await api.get<ScheduleDetailResp>(
            `/recommend/schedule/${encodeURIComponent(scheduleId)}`
          )
          if (data.status === "OK" && data.scheduleDetail) d = data.scheduleDetail
        } catch {}

        const waypoints =
          d?.waypointNames?.map((name, i) => ({
            name,
            arrivalTime: d?.waypointTimes?.[i] || "",
          })) ?? []

        // ★ 업데이트는 시간만
        const updates =
          d?.updates?.map(u => ({ time: u?.time })) ?? []

        const mealSlots = lastSubmit.selectedPlaces.map((p) => ({
          slotId: p.slotId,
          mealType: p.mealType,
          scheduledTime: p.scheduledTime,
        }))

        const selectedRestaurants = lastSubmit.selectedPlaces.map((p) => ({
          sectionId: (p.mealType === MealType.MEAL ? "meal-" : "snack-") + p.slotId,
          restaurant: {
            id: p.id,
            placeName: p.placeName,
            aiReason: p.reason ?? "",
            addressName: p.addressName,
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
          updates,
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

  /** 폴링: 같은 scheduleId라도 runId가 일치할 때만 OK로 인정(서버 미지원 시 무시) */
  const startPollingResults = (scheduleId: string) => {
    let active = true
    let timer: any = null

    const tick = async () => {
      if (!active) return
      try {
        const runId = currentRunIdRef.current
        const params: any = { scheduleId }
        if (runId) params.runId = runId

        const res = await api.get<GetResultsResponse>(RECOMMEND_RESULT_URL, { params })

        if (res.data.status === "OK") {
          if (runId && res.data.runId && res.data.runId !== runId) {
            timer = setTimeout(tick, POLL_INTERVAL_MS)
            return
          }
          setCurrentPopup("recommendation_ready")
          setUpdating(false)
          return
        }
        timer = setTimeout(tick, POLL_INTERVAL_MS)
      } catch {
        timer = setTimeout(tick, POLL_INTERVAL_MS * 2)
      }
    }

    tick()
    pollingStopRef.current = () => {
      let _ = active
      active = false
      if (timer) clearTimeout(timer)
    }
  }
  const stopPollingResults = () => pollingStopRef.current?.()

  const getCurrentPositionAsync = (opts?: PositionOptions) =>
    new Promise<GeolocationPosition>((resolve, reject) => {
      if (!navigator.geolocation) return reject(new Error("Geolocation not supported"))
      navigator.geolocation.getCurrentPosition(resolve, reject, opts)
    })

  /** 업데이트 트리거: 같은 runId로 POST */
  const triggerRecommendUpdate = async (scheduleId: string) => {
    const pos = await getCurrentPositionAsync({
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 0,
    })

    const runId = currentRunIdRef.current || genRunId()
    currentRunIdRef.current = runId
    setLastRunId(scheduleId, runId)

    const payload = {
      scheduleId,
      clientNowIso: new Date().toISOString(),
      currentLat: pos.coords.latitude,
      currentLng: pos.coords.longitude,
      runId,
    }
    await api.post(RECOMMEND_SEND_URL, payload)
  }

  const handleUpdate = async () => {
    if (!vm?.scheduleId) {
      alert("스케줄을 먼저 선택해주세요.")
      return
    }

    // ETA 선검사 (출발보다 이르면 다음날로 보정)
    if (vm.calculatedArrivalTime) {
      const anchoredEta = anchorToScheduleDay(vm.calculatedArrivalTime, vm.departureTime)
      if (anchoredEta && Date.now() > anchoredEta.getTime()) {
        alert("도착 예상 시간을 이미 지났습니다. 추천 업데이트 요청을 보낼 수 없어요.")
        return
      }
    }

    try {
      setUpdating(true)
      setIsPopupOpen(true)
      setCurrentPopup("processing")

      stopPollingResults()
      currentRunIdRef.current = genRunId()
      setLastRunId(vm.scheduleId, currentRunIdRef.current)

      startPollingResults(vm.scheduleId)
      await triggerRecommendUpdate(vm.scheduleId)
    } catch (e: any) {
      const code = typeof e?.code === "number" ? e.code : 0
      const msg =
        code === 1
          ? "위치 정보 접근 권한이 거부되었습니다. 설정에서 권한을 허용해주세요."
          : code === 2
          ? "현재 위치를 파악할 수 없습니다."
          : code === 3
          ? "위치 정보를 가져오는 데 시간이 초과되었습니다."
          : e?.message || "업데이트 요청 중 오류가 발생했습니다."
      console.error("[RecommendUpdate] failed:", e)
      alert(msg)
      setUpdating(false)
      stopPollingResults()
      setIsPopupOpen(false)
    }
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

    // ★ 업데이트 항목: 시간만, 시각적 구분을 위해 보라색 + 배지
    vm.updates?.forEach((u) => {
      items.push({
        type: "update",
        time: u.time,
        title: "추천 업데이트",
        icon: "업뎃",
        color: "purple",
      })
    })

    vm.waypoints?.forEach((wp) => {
      items.push({
        type: "waypoint",
        time: wp.arrivalTime || "",
        title: wp.name,
        icon: "경유",
        color: "blue",
      })
    })

    vm.selectedRestaurants?.forEach((item) => {
      const slotId = item.sectionId.replace(/^(meal|snack)-/, "")
      const mt = vm.mealSlots?.find(ms => ms.slotId === slotId)
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

    const sortKey = (t?: string) => {
      const d = anchorToScheduleDay(t, vm.departureTime)
      return d ? d.getTime() : Number.MAX_SAFE_INTEGER
    }
    return items.sort((a, b) => sortKey(a.time) - sortKey(b.time))
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
              disabled={isProcessing || updating}
            >
              <RefreshCw className={`w-4 h-4 ${isProcessing || updating ? "animate-spin" : ""}`} />
              {isProcessing || updating ? "업데이트 중..." : "추천 업데이트"}
            </Button>
          </div>
        </div>

        <div className="flex-1 content-with-bottom-nav overflow-y-auto pb-24">
          <div className="p-4">
            <div className="bg-white rounded-lg p-4 shadow-sm">
              {error || timelineItems.length === 0 ? (
                <div className="text-center py-8">
                  <p className="text-gray-600 mb-4">{error ?? "스케줄 정보가 없습니다. 다시 선택해주세요."}</p>
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
                          : item.type === "update"
                          ? "bg-purple-50 rounded-lg p-3 -mx-3"
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
                            : item.color === "purple"
                            ? "bg-purple-500"
                            : "bg-green-100"
                        } rounded-full flex items-center justify-center`}
                      >
                        <span
                          className={`text-sm font-medium ${
                            item.color === "orange" || item.color === "purple"
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
                          {item.time ? toKoreanAmPm(item.time) : "시간 미정"}
                        </p>
                        <p className="font-medium">
                          {item.title}
                          {item.type === "update" && (
                            <span className="ml-2 text-xs px-2 py-0.5 rounded-full bg-purple-600 text-white align-middle">
                              업데이트
                            </span>
                          )}
                        </p>

                        {item.type === "restaurant" && (
                          <>
                            {item.description && (
                              <p className="text-sm text-gray-600">{item.description}</p>
                            )}
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

      {isPopupOpen && (
        <>
          <ScheduleProcessingPopup
            isOpen={currentPopup === "processing"}
            onClose={() => {
              setIsPopupOpen(false)
              setUpdating(false)
              stopPollingResults()
            }}
            scheduleTitle={vm?.destination?.name || vm?.departure?.name || "스케줄"}
            timelineItems={[]}
            statusText={currentPopup === "processing" ? "맞춤 식당 추천 검색 중..." : "맞춤 식당 추천 완료!"}
            isProcessing={currentPopup === "processing"}
          />
          <RecommendationReadyPopup
            isOpen={currentPopup === "recommendation_ready"}
            onViewResults={async () => {
              stopPollingResults()
              setIsPopupOpen(false)
              setUpdating(false)
              router.push("/recommendations/")
            }}
            onGoBack={() => {
              stopPollingResults()
              setIsPopupOpen(false)
              setUpdating(false)
            }}
          />
        </>
      )}
    </>
  )
}
