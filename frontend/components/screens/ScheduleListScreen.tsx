// components/screens/ScheduleListScreen.tsx
"use client"

import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import useSchedule from "@/hooks/useSchedule"
import BottomNavigation from "@/components/common/BottomNavigation"
import { Plus } from "lucide-react"
import type { Schedule } from "@/types"
import { generateTimelineItems, type TimelineItem } from "@/lib/timeline"
import ScheduleProcessingPopup from "@/components/modals/ScheduleProcessingPopup"
import RecommendationReadyPopup from "@/components/modals/RecommendationReadyPopup"
import api from "@/lib/interceptor"

// ===== 상수 =====
const POLL_INTERVAL_MS = 1500
const RESULT_URL = "/recommend/result"
const REQUEST_URL = "/recommend/request"
const LS_RUN_PREFIX = "recommend:lastRun:";
const LS_SELECTED_KEY = "schedule:lastSelected";

const setLastRunId = (scheduleId: string, runId: string) => {
  try { localStorage.setItem(`${LS_RUN_PREFIX}${scheduleId}`, runId) } catch {}
};

const genRunId = () =>
  typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

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
type LastSelected = { scheduleId: string; date: string }

const todayStr = () => {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, "0")
  const dd = String(d.getDate()).padStart(2, "0")
  return `${d.getFullYear()}-${mm}-${dd}`
}

const loadLastSelected = (): LastSelected | null => {
  try {
    const raw = localStorage.getItem(LS_SELECTED_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as LastSelected
    if (!parsed?.scheduleId || !parsed?.date) return null
    if (parsed.date !== todayStr()) {
      localStorage.removeItem(LS_SELECTED_KEY)
      return null
    }
    return parsed
  } catch {
    return null
  }
}

const saveLastSelected = (scheduleId: string) => {
  try {
    const payload: LastSelected = { scheduleId, date: todayStr() }
    localStorage.setItem(LS_SELECTED_KEY, JSON.stringify(payload))
  } catch {}
}

export default function ScheduleListScreen() {
  const router = useRouter()
  const {
    schedules,
    isLoading,
    isProcessing,
    selectSchedule,
    deleteSchedule,
    loadSchedules,
  } = useSchedule()

  const [isClient, setIsClient] = useState(false)

  const [isPopupOpen, setIsPopupOpen] = useState(false)
  const [currentPopup, setCurrentPopup] = useState<PopupType>("processing")
  const [selectedScheduleForPopup, setSelectedScheduleForPopup] = useState<Schedule | null>(null)
  const [timelineItems, setTimelineItems] = useState<TimelineItem[]>([])

  // ✅ 오늘 기준 강조 표시할 scheduleId
  const [selectedTodayId, setSelectedTodayId] = useState<string | null>(null)

  // 폴링 제어
  const pollingStopRef = useRef<() => void>(() => {})
  const resultsRef = useRef<GetResultsResponse | null>(null)
  const currentRunIdRef = useRef<string | null>(null)

  useEffect(() => {
    setIsClient(true)
    loadSchedules()

    const last = loadLastSelected()
    setSelectedTodayId(last?.scheduleId ?? null)
  }, [loadSchedules])

  const triggerRecommendRequest = async (scheduleId: string, runId: string) => {
    try {
      await api.post(REQUEST_URL, { scheduleId, runId })
    } catch (e) {
      console.error("POST /recommend/request failed:", e)
    }
  }

  const startPollingResults = (scheduleId: string, runId: string) => {
    let active = true
    let timer: any = null

    const tick = async () => {
      if (!active) return
      try {
        const res = await api.get<GetResultsResponse>(RESULT_URL, {
          params: { scheduleId, runId },
        })

        if (res.data.status === "OK") {
          if (res.data.runId && res.data.runId !== runId) {
            timer = setTimeout(tick, POLL_INTERVAL_MS)
            return
          }

          resultsRef.current = res.data
          setTimelineItems(prev =>
            prev.map((it: any) =>
              it?.type === "meal_plan"
                ? { ...it, status: "completed", description: "추천 완료" }
                : it
            )
          )
          setCurrentPopup("recommendation_ready")

          // ✅ 오늘자 선택으로 기록
          saveLastSelected(scheduleId)
          setSelectedTodayId(scheduleId)

          return
        }

        timer = setTimeout(tick, POLL_INTERVAL_MS)
      } catch (err) {
        console.error("GET /recommend/result polling error:", err)
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

  const handleScheduleSelect = async (schedule: Schedule) => {
    if (!schedule?.id) return
    stopPollingResults()

    setSelectedScheduleForPopup(schedule)
    setTimelineItems([])
    setCurrentPopup("processing")
    setIsPopupOpen(true)

    try {
      const fullSchedule = await selectSchedule(schedule.id)
      if (fullSchedule) setTimelineItems(generateTimelineItems(fullSchedule))
      else throw new Error("selectSchedule did not return schedule details.")
    } catch (e) {
      console.error("스케줄 선택 또는 상세 조회 실패:", e)
    }

    const runId = genRunId()
    currentRunIdRef.current = runId
    setLastRunId(schedule.id, runId)

    await triggerRecommendRequest(schedule.id, runId)
    startPollingResults(schedule.id, runId)
  }

  const handleScheduleEdit = (schedule: Schedule) => {
    router.push(`/schedule/edit?id=${schedule.id}`)
  }

  const handleDelete = async (id: string) => {
    await deleteSchedule(id)
    if (selectedTodayId === id) {
      localStorage.removeItem(LS_SELECTED_KEY)
      setSelectedTodayId(null)
    }
  }

  const closePopup = () => {
    setIsPopupOpen(false)
    setSelectedScheduleForPopup(null)
    setCurrentPopup("processing")
    stopPollingResults()
    resultsRef.current = null
    currentRunIdRef.current = null
  }

  const handleViewResults = async () => {
    if (!selectedScheduleForPopup?.id) return
    try {
      await selectSchedule(selectedScheduleForPopup.id)
      closePopup()
      router.push("/recommendations/")
    } catch (error) {
      console.error("결과 보기 실패:", error)
    }
  }

  if (!isClient || isLoading) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">로딩 중...</p>
        </div>
      </div>
    )
  }

  // ✅ 선택된 스케줄을 최상단으로 정렬
  const orderedSchedules = selectedTodayId
    ? [...schedules].sort((a, b) =>
        a.id === selectedTodayId ? -1 : b.id === selectedTodayId ? 1 : 0
      )
    : schedules

  return (
    <>
      <div className="min-h-screen bg-gray-100 flex flex-col">
        <div className="bg-white p-4 shadow-sm">
          <h1 className="text-lg font-medium">스케줄 선택하기</h1>
        </div>

        <div className="flex-1 content-with-bottom-nav">
          <div className="p-4 pb-24">
            {orderedSchedules.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-gray-600 mb-4">생성된 스케줄이 없습니다.</p>
                <Button
                  onClick={() => router.push("/schedule/create/")}
                  className="bg-blue-500 hover:bg-blue-600 text-white"
                >
                  첫 스케줄 만들기
                </Button>
              </div>
            ) : (
              <div className="space-y-3">
                {orderedSchedules.map((schedule) => {
                  const isSelectedToday = selectedTodayId === schedule.id
                  return (
                    <div
                      key={schedule.id}
                      className={
                        `bg-white rounded-lg p-4 shadow-sm ` +
                        (isSelectedToday ? "border-2 border-blue-500" : "border border-transparent")
                      }
                    >
                      {/* 제목 + 배지 한 줄 */}
                      <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                          <h3 className="font-medium">{schedule.title}</h3>
                          {isSelectedToday && (
                            <span className="inline-flex items-center h-5 px-2 rounded-full bg-blue-100 text-blue-600 text-xs">
                              현재 선택
                            </span>
                          )}
                        </div>
                      </div>

                      <div className="flex gap-2">
                        <Button
                          onClick={() => handleDelete(schedule.id!)}
                          size="sm"
                          variant="outline"
                          className="flex-1 text-red-600 border-red-200 hover:bg-red-50"
                        >
                          삭제
                        </Button>
                        <Button
                          onClick={() => handleScheduleEdit(schedule)}
                          size="sm"
                          variant="outline"
                          className="flex-1 text-gray-700 border-gray-200 hover:bg-gray-50"
                        >
                          수정
                        </Button>

                        {/* ✅ 선택된 카드에는 '선택/다시 선택' 버튼 숨김 */}
                        {!isSelectedToday && (
                          <Button
                            onClick={() => handleScheduleSelect(schedule)}
                            size="sm"
                            className="flex-1 bg-blue-500 hover:bg-blue-600 text-white"
                            disabled={isProcessing}
                          >
                            {isProcessing ? "처리 중..." : "선택"}
                          </Button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        <div className="floating-action-button">
          <Button
            onClick={() => router.push("/schedule/create/")}
            className="w-12 h-12 bg-blue-500 hover:bg-blue-600 text-white rounded-full shadow-lg flex items-center justify-center"
          >
            <Plus className="w-5 h-5" />
          </Button>
        </div>

        <BottomNavigation currentTab="schedule" />
      </div>

      {isPopupOpen && selectedScheduleForPopup && (
        <>
          <ScheduleProcessingPopup
  isOpen={currentPopup === "processing"}
  onClose={closePopup}
  scheduleTitle={selectedScheduleForPopup.title}
  variant="timeline"                    // ✅ 일정 요약 모드
  timelineItems={timelineItems}
  statusText={
    currentPopup === "processing"
      ? "맞춤 식당 추천 검색 중..."
      : "맞춤 식당 추천 완료!"
  }
  isProcessing={currentPopup === "processing"}
/>

          <RecommendationReadyPopup
            isOpen={currentPopup === "recommendation_ready"}
            onViewResults={handleViewResults}
            onGoBack={closePopup}
          />
        </>
      )}
    </>
  )
}
