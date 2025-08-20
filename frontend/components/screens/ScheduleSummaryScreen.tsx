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

// 파일 로컬 전용 runId 생성기
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

/** 백 문자열 그대로 표시 */
const toKoreanAmPmRaw = (time?: string): string => (!time ? "시간 미정" : time)

/** 분 단위 비교값 */
const minutesOfDay = (time?: string): number | null => {
  const d = parseDisplayTimeToDate(time)
  if (!d) return null
  return d.getHours() * 60 + d.getMinutes()
}

/** 출발시간 기준, t가 출발보다 이르면 "익일 " 접두어를 붙여 표시 */
const displayWithNextDayPrefix = (t?: string, departureTime?: string): string => {
  if (!t) return "시간 미정"
  if (!departureTime) return toKoreanAmPmRaw(t)
  const tMin = minutesOfDay(t)
  const depMin = minutesOfDay(departureTime)
  if (tMin == null || depMin == null) return toKoreanAmPmRaw(t)
  return (tMin < depMin ? "익일 " : "") + toKoreanAmPmRaw(t)
}

/** 출발시간을 기준으로 정렬 비교용 timestamp (익일 보정) */
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

  // 팝업에서 좌표 보여주기용 (값은 "37.123456, 127.123456")
  const [coordText, setCoordText] = useState<string>("")

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
          setError("선택 내역이 없습니다. 스케줄을 먼저 선택해주세요.")
          setVm(null)
          return
        }

        // 스케줄 상세 조회
        let d: ScheduleDetailResp["scheduleDetail"] | undefined
        try {
          const { data } = await api.get<ScheduleDetailResp>(
            `/recommend/schedule/${encodeURIComponent(scheduleId)}`
          )
          if (data.status !== "OK" || !data.scheduleDetail) {
            setError("선택 내역이 없습니다. 스케줄을 먼저 선택해주세요.")
            setVm(null)
            return
          }
          d = data.scheduleDetail
        } catch {
          setError("선택 내역이 없습니다. 스케줄을 먼저 선택해주세요.")
          setVm(null)
          return
        }

        const waypoints =
          d?.waypointNames?.map((name, i) => ({
            name,
            arrivalTime: d?.waypointTimes?.[i] || "",
          })) ?? []

        const updates = d?.updates?.map(u => ({ time: u?.time })) ?? []

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
      } catch {
        if (mounted) {
          setError("선택 내역이 없습니다. 스케줄을 먼저 선택해주세요.")
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

  /** 출발 전 여부 (버튼 비활성화에도 사용) */
  const isBeforeDeparture = useMemo(() => {
    if (!vm?.departureTime) return false
    const dep = parseDisplayTimeToDate(vm.departureTime)
    return dep ? Date.now() < dep.getTime() : false
  }, [vm?.departureTime])

  /** 폴링 */
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

  /** 업데이트 트리거 */
  const triggerRecommendUpdate = async (scheduleId: string, lat: number, lng: number) => {
    const runId = currentRunIdRef.current || genRunId()
    currentRunIdRef.current = runId
    setLastRunId(scheduleId, runId)

    const payload = {
      scheduleId,
      clientNowIso: new Date().toISOString(),
      currentLat: lat,
      currentLng: lng,
      runId,
    }
    await api.post(RECOMMEND_SEND_URL, payload)
  }

  const handleUpdate = async () => {
  if (!vm?.scheduleId) {
    alert("스케줄을 먼저 선택해주세요.")
    return
  }

  // ✅ 출발 전이면 즉시 차단 + 안내
  if (isBeforeDeparture) {
    alert("아직 출발 전입니다. 출발 이후에 추천 업데이트를 요청할 수 있어요.")
    return
  }

    // ETA 선검사 (출발보다 이르면 다음날로 보정하여 비교)
    if (vm.calculatedArrivalTime) {
      const anchoredEta = anchorToScheduleDay(vm.calculatedArrivalTime, vm.departureTime)
      if (anchoredEta && Date.now() > anchoredEta.getTime()) {
        alert("도착 예상 시간을 이미 지났습니다. 추천 업데이트 요청을 보낼 수 없어요.")
        return
      }
    }

    try {
      // 위치 먼저 받아서 팝업에 표시
      const pos = await getCurrentPositionAsync({
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 0,
      })
      const lat = +pos.coords.latitude.toFixed(6)
      const lng = +pos.coords.longitude.toFixed(6)
      setCoordText(`${lat}, ${lng}`) // 저장은 좌표만

      setUpdating(true)
      setIsPopupOpen(true)
      setCurrentPopup("processing")

      stopPollingResults()
      currentRunIdRef.current = genRunId()
      setLastRunId(vm.scheduleId, currentRunIdRef.current)

      startPollingResults(vm.scheduleId)
      await triggerRecommendUpdate(vm.scheduleId, lat, lng)
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

    vm.updates?.forEach((u) => {
      items.push({
        type: "update",
        time: u.time,
        title: "위치 갱신",
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
            <div className="flex flex-col items-end">
              <Button
  onClick={handleUpdate}
  variant="outline"
  size="sm"
  className="flex items-center gap-2 border-blue-200 text-blue-600 hover:bg-blue-50"
  disabled={isProcessing || updating}   // ← isBeforeDeparture 제거
  title={isBeforeDeparture ? "아직 출발 전이에요. 클릭하면 안내를 드려요." : undefined}
>
  <RefreshCw className={`w-4 h-4 ${isProcessing || updating ? "animate-spin" : ""}`} />
  {isProcessing || updating ? "업데이트 중..." : "추천 업데이트"}
</Button>

{/* 안내 문구는 그대로 유지해도 좋음 */}
{isBeforeDeparture && (
  <span className="mt-1 text-xs text-red-500">
    아직 출발 전입니다. 출발 후 요청해 주세요.
  </span>
)}
<Button
  onClick={handleUpdate}
  variant="outline"
  size="sm"
  className="flex items-center gap-2 border-blue-200 text-blue-600 hover:bg-blue-50"
  disabled={isProcessing || updating}   // ← isBeforeDeparture 제거
  title={isBeforeDeparture ? "아직 출발 전이에요. 클릭하면 안내를 드려요." : undefined}
>
  <RefreshCw className={`w-4 h-4 ${isProcessing || updating ? "animate-spin" : ""}`} />
  {isProcessing || updating ? "업데이트 중..." : "추천 업데이트"}
</Button>

{/* 안내 문구는 그대로 유지해도 좋음 */}
{isBeforeDeparture && (
  <span className="mt-1 text-xs text-red-500">
    아직 출발 전입니다. 출발 후 요청해 주세요.
  </span>
)}

            </div>
          </div>
        </div>

        <div className="flex-1 content-with-bottom-nav">
          <div className="p-4 pb-24">
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
                          {displayWithNextDayPrefix(item.time, vm!.departureTime)}
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
  scheduleTitle="위치 업데이트 중"
  variant="location"                    // ✅ 위치 모드(중앙 정렬)
  coordText={coordText}                 // "37.598007, 126.931804"
  statusText="맞춤 식당 추천 검색 중..."
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
