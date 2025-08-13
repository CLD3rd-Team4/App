"use client"

import { useState, useEffect, useRef } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import useSchedule from "@/hooks/useSchedule"
import BottomNavigation from "@/components/common/BottomNavigation"
import { Plus } from "lucide-react"
import type { Schedule } from "@/types"
import { generateTimelineItems, TimelineItem } from "@/lib/timeline"
import ScheduleProcessingPopup from "@/components/modals/ScheduleProcessingPopup"
import RecommendationReadyPopup from "@/components/modals/RecommendationReadyPopup"
import { scheduleApi } from "@/services/api"
import api from "@/lib/interceptor"
import { useAuth } from "@/hooks/useAuth"

// ===== 폴링 주기 =====
const POLL_INTERVAL_MS = 1500

// ===== 결과 응답 타입(일부 필드만) =====
type GetResultsResponse = {
  status: "PENDING" | "OK" | "ERROR" | string
  message?: string
  slotRecommendations?: Array<{
    slotId: string
    places: Array<{
      id: string
      placeName: string
    }>
  }>
}

// 팝업 상태
type PopupType = "processing" | "recommendation_ready"

export default function ScheduleListScreen() {
  const router = useRouter()
  const { user } = useAuth() // user?.id 사용 (없으면 localStorage fallback)
  const {
    schedules,
    isLoading,
    isProcessing,
    selectSchedule,
    deleteSchedule,
    loadSchedules,
  } = useSchedule()

  const [isClient, setIsClient] = useState(false)

  // 팝업/타임라인 상태
  const [isPopupOpen, setIsPopupOpen] = useState(false)
  const [currentPopup, setCurrentPopup] = useState<PopupType>("processing")
  const [selectedScheduleForPopup, setSelectedScheduleForPopup] = useState<Schedule | null>(null)
  const [timelineItems, setTimelineItems] = useState<TimelineItem[]>([])

  // 폴링 제어
  const pollingStopRef = useRef<() => void>(() => {})
  const resultsRef = useRef<GetResultsResponse | null>(null)

  useEffect(() => {
    setIsClient(true)
    loadSchedules()
  }, [loadSchedules])

  // ===== 추천 트리거 =====
  const triggerRecommendRequest = async (scheduleId: string) => {
    try {
      await api.post("/recommend/request", null, {
        params: { scheduleId },
        headers: { "Cache-Control": "no-cache" },
      })
    } catch (e) {
      // 트리거 실패해도 폴링으로 재시도 UX는 유지됨
      console.error("POST /recommend/request failed:", e)
    }
  }

  // ===== 결과 폴링 =====
  const startPollingResults = (userId: string, scheduleId: string) => {
    let active = true
    let timer: any = null

    const tick = async () => {
      if (!active) return
      try {
        const res = await api.get<GetResultsResponse>("/recommend/result", {
          params: { userId, scheduleId },
          headers: { "Cache-Control": "no-cache" },
        })

        if (res.data.status === "OK") {
          resultsRef.current = res.data
          // 타임라인의 진행 상태 업데이트
          setTimelineItems(prev =>
            prev.map((item: any) =>
              item?.type === "meal_plan"
                ? { ...item, status: "completed", description: "추천 완료" }
                : item
            )
          )
          setCurrentPopup("recommendation_ready")
          return // 완료 → 폴링 종료
        }

        // PENDING이면 다음 틱 예약
        timer = setTimeout(tick, POLL_INTERVAL_MS)
      } catch (err) {
        console.error("GET /recommend/result polling error:", err)
        // 에러 시 지수 백오프 대신 단순 딜레이
        timer = setTimeout(tick, POLL_INTERVAL_MS * 2)
      }
    }

    tick()

    pollingStopRef.current = () => {
      active = false
      if (timer) clearTimeout(timer)
    }
  }

  const stopPollingResults = () => {
    pollingStopRef.current?.()
  }

  // ===== 선택 클릭 =====
  const handleScheduleSelect = async (schedule: Schedule) => {
    if (!schedule?.id) return

    // userId 확보 (useAuth → localStorage 순)
    const uid =
      user?.id ||
      (typeof window !== "undefined" ? localStorage.getItem("userId") || "" : "")
    if (!uid) {
      alert("로그인 정보가 없습니다. 다시 로그인 후 시도해주세요.")
      return
    }

    // 팝업 먼저 오픈(즉시 피드백)
    setSelectedScheduleForPopup(schedule)
    setTimelineItems([]) // 로딩 상태 → 상세 불러온 뒤 채움
    setCurrentPopup("processing")
    setIsPopupOpen(true)

    try {
      // 타임라인 생성을 위해 상세 조회
      const detail = await scheduleApi.getScheduleDetail(schedule.id)
      const fullSchedule: Schedule | undefined = detail?.schedule
      if (fullSchedule) {
        setTimelineItems(generateTimelineItems(fullSchedule))
      }

      // 추천 트리거 (백그라운드)
      triggerRecommendRequest(schedule.id)

      // 결과 폴링 시작
      startPollingResults(uid, schedule.id)
    } catch (e) {
      console.error("스케줄 상세/트리거 준비 실패:", e)
      alert("스케줄 정보를 준비하는 중 오류가 발생했습니다.")
      closePopup()
    }
  }

  const handleScheduleEdit = (schedule: Schedule) => {
    router.push(`/schedule/edit?id=${schedule.id}`)
  }

  const closePopup = () => {
    setIsPopupOpen(false)
    setSelectedScheduleForPopup(null)
    setCurrentPopup("processing")
    stopPollingResults()
    resultsRef.current = null
  }

  // 추천 완료 팝업에서 “결과 보기”
  const handleViewResults = async () => {
    if (!selectedScheduleForPopup?.id) return
    try {
      await selectSchedule(selectedScheduleForPopup.id) // 훅 내부에서 라우팅 처리 가정
      closePopup()
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

  return (
    <>
      <div className="min-h-screen bg-gray-100 flex flex-col">
        <div className="bg-white p-4 shadow-sm">
          <h1 className="text-lg font-medium">스케줄 선택하기</h1>
        </div>

        <div className="flex-1 content-with-bottom-nav">
          <div className="p-4">
            {schedules.length === 0 ? (
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
                {schedules.map((schedule) => (
                  <div key={schedule.id} className="bg-white rounded-lg p-4 shadow-sm">
                    <h3 className="font-medium mb-3">{schedule.title}</h3>
                    <div className="flex gap-2">
                      <Button
                        onClick={() => deleteSchedule(schedule.id!)}
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
                      <Button
                        onClick={() => handleScheduleSelect(schedule)}
                        size="sm"
                        className="flex-1 bg-blue-500 hover:bg-blue-600 text-white"
                        disabled={isProcessing}
                      >
                        {isProcessing ? "처리 중..." : "선택"}
                      </Button>
                    </div>
                  </div>
                ))}
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
