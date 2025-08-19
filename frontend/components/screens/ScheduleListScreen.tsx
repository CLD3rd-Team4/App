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
const POLL_INTERVAL_MS = 1500 // 폴링 주기(ms)

// 결과 응답(일부 필드만)
type GetResultsResponse = {
  status: "PENDING" | "OK" | "ERROR" | string
  message?: string
  runId?: string           // ✅ 서버가 돌려줄 수도 있는 runId
  slotRecommendations?: Array<{
    slotId: string
    places: Array<{ id: string; placeName: string }>
  }>
}

// 팝업 상태
type PopupType = "processing" | "recommendation_ready"

// ✅ HHmm(KST) runId 생성
const genRunId = () =>
  new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Seoul",
  })
    .format(new Date())
    .replace(":", "")

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

  // 팝업/타임라인 상태
  const [isPopupOpen, setIsPopupOpen] = useState(false)
  const [currentPopup, setCurrentPopup] = useState<PopupType>("processing")
  const [selectedScheduleForPopup, setSelectedScheduleForPopup] = useState<Schedule | null>(null)
  const [timelineItems, setTimelineItems] = useState<TimelineItem[]>([])

  // 폴링 제어
  const pollingStopRef = useRef<() => void>(() => {})
  const resultsRef = useRef<GetResultsResponse | null>(null)

  // ✅ 현재 요청의 runId 저장
  const currentRunIdRef = useRef<string | null>(null)

  useEffect(() => {
    setIsClient(true)
    loadSchedules()
  }, [loadSchedules])

  // ===== 추천 트리거 (runId 포함) =====
  const triggerRecommendRequest = async (scheduleId: string, runId: string) => {
    try {
      // 바디에 runId 포함(서버 proto가 runId를 optional로 받도록 반영되어 있어야 함)
      await api.post("/recommend/request", { scheduleId, runId })
    } catch (e) {
      console.error("POST /recommend/request failed:", e)
      // 실패여도 폴링으로 대기 UX 유지
    }
  }

  // ===== 결과 폴링 (runId 일치 확인) =====
  const startPollingResults = (scheduleId: string, runId: string) => {
    let active = true
    let timer: any = null

    const tick = async () => {
      if (!active) return
      try {
        const res = await api.get<GetResultsResponse>("/recommend/result", {
          params: { scheduleId, runId }, // ✅ runId 함께 조회
        })

        if (res.data.status === "OK") {
          // 서버가 다른 runId를 돌려주면 무시하고 계속 대기
          if (res.data.runId && res.data.runId !== runId) {
            timer = setTimeout(tick, POLL_INTERVAL_MS)
            return
          }

          resultsRef.current = res.data
          // 타임라인 진행 상태 업데이트(완료 표시)
          setTimelineItems(prev =>
            prev.map((it: any) =>
              it?.type === "meal_plan"
                ? { ...it, status: "completed", description: "추천 완료" }
                : it
            )
          )
          setCurrentPopup("recommendation_ready")
          return // OK → 폴링 종료
        }

        // PENDING이면 다음 틱 예약
        timer = setTimeout(tick, POLL_INTERVAL_MS)
      } catch (err) {
        console.error("GET /recommend/result polling error:", err)
        // 에러 시 잠시 후 재시도
        timer = setTimeout(tick, POLL_INTERVAL_MS * 2)
      }
    }

    tick()
    pollingStopRef.current = () => { active = false; if (timer) clearTimeout(timer) }
  }

  const stopPollingResults = () => pollingStopRef.current?.()

  // ===== 선택 클릭 =====
  const handleScheduleSelect = async (schedule: Schedule) => {
    if (!schedule?.id) return

    // 기존 폴링 정리 후 새 요청 준비
    stopPollingResults()

    // 팝업 오픈 + 타임라인 기본 세팅
    setSelectedScheduleForPopup(schedule)
    setTimelineItems([]) // 상세 조회 후 채움
    setCurrentPopup("processing")
    setIsPopupOpen(true)

    try {
      // 스케줄을 "선택"하고 상세 정보를 가져옵니다. (Valkey에 저장됨)
      const fullSchedule = await selectSchedule(schedule.id)
      if (fullSchedule) {
        setTimelineItems(generateTimelineItems(fullSchedule))
      } else {
        throw new Error("selectSchedule did not return schedule details.")
      }
    } catch (e) {
      console.error("스케줄 선택 또는 상세 조회 실패:", e)
      // 상세 실패해도 추천은 트리거/폴링 가능하므로 팝업은 유지
    }

    // ✅ runId 생성 및 고정
    const runId = genRunId()
    currentRunIdRef.current = runId

    // 3) 추천 분석 요청(runId 포함) & 4) 결과 폴링 시작(runId 포함)
    await triggerRecommendRequest(schedule.id, runId)
    startPollingResults(schedule.id, runId)
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
    currentRunIdRef.current = null
  }

  // 추천 완료 팝업 → “결과 보기”
  const handleViewResults = async () => {
    if (!selectedScheduleForPopup?.id) return
    try {
      await selectSchedule(selectedScheduleForPopup.id) // 필요 시 훅 상태 반영
      closePopup()
      router.push("/recommendations/") // 추천 결과 페이지로 이동
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
          <div className="p-4 pb-24">
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
