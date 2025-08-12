"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import useSchedule from "@/hooks/useSchedule"
import BottomNavigation from "@/components/common/BottomNavigation"
import { Plus } from "lucide-react"
import type { Schedule } from "@/types"
import ScheduleProcessingPopup from "@/components/modals/ScheduleProcessingPopup"
import RecommendationReadyPopup from "@/components/modals/RecommendationReadyPopup"
import { generateTimelineItems, TimelineItem } from "@/lib/timeline"

// 팝업의 현재 상태 (메인 처리 / 추천 완료)
type PopupType = "processing" | "recommendation_ready"

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

  // --- 팝업 관련 상태 ---
  const [isPopupOpen, setIsPopupOpen] = useState(false)
  const [selectedScheduleForPopup, setSelectedScheduleForPopup] = useState<Schedule | null>(null)
  const [currentPopup, setCurrentPopup] = useState<PopupType>("processing")
  const [timelineItems, setTimelineItems] = useState<TimelineItem[]>([])
  // --- 끝: 팝업 관련 상태 ---

  useEffect(() => {
    setIsClient(true)
    loadSchedules()
  }, [loadSchedules])

  // 팝업 자동 진행 시뮬레이션 (API 연동 전 임시 로직)
  useEffect(() => {
    if (!isPopupOpen || currentPopup !== "processing") return

    const timer = setTimeout(() => {
      setTimelineItems((prev) =>
        prev.map((item) =>
          item.type === "meal_plan"
            ? { ...item, status: "completed", description: "추천 완료" }
            : item
        )
      )
      setCurrentPopup("recommendation_ready")
    }, 3000)

    return () => clearTimeout(timer)
  }, [isPopupOpen, currentPopup])

  const handleScheduleSelect = async (schedule: Schedule) => {
    if (!schedule || !schedule.id) return
    // 팝업 열기 로직 추가
    setSelectedScheduleForPopup(schedule)
    const items = generateTimelineItems(schedule)
    setTimelineItems(items)
    setCurrentPopup("processing")
    setIsPopupOpen(true)
    // 실제 API 호출은 팝업 플로우와 연계하여 처리 (예: 팝업 완료 후 호출)
    // await selectSchedule(schedule.id)
  }

  const handleScheduleEdit = (schedule: Schedule) => {
    localStorage.setItem("editingSchedule", JSON.stringify(schedule))
    router.push(`/schedule/edit?id=${schedule.id}`)
  }

  const closePopup = () => {
    setIsPopupOpen(false)
    setSelectedScheduleForPopup(null)
  }

  const handleViewResults = async () => {
    if (!selectedScheduleForPopup?.id) return
    try {
      await selectSchedule(selectedScheduleForPopup.id)
      // selectSchedule 내부에서 라우팅이 처리되므로, 여기서는 팝업만 닫습니다.
      closePopup()
    } catch (error) {
      // 에러 처리는 selectSchedule 훅 내부에서 이미 처리(alert)되므로 여기서는 추가 작업이 불필요할 수 있습니다.
      // 필요 시, 여기서 추가적인 UI 피드백을 줄 수 있습니다.
      console.error("Failed to view results:", error)
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
                <Button onClick={() => router.push("/schedule/create/")} className="bg-blue-500 hover:bg-blue-600 text-white">
                  첫 스케줄 만들기
                </Button>
              </div>
            ) : (
              <div className="space-y-3">
                {schedules.map((schedule) => (
                  <div key={schedule.id} className="bg-white rounded-lg p-4 shadow-sm">
                    <h3 className="font-medium mb-3">{schedule.title}</h3>
                    <div className="flex gap-2">
                      <Button onClick={() => deleteSchedule(schedule.id!)} size="sm" variant="outline" className="flex-1 text-red-600 border-red-200 hover:bg-red-50">삭제</Button>
                      <Button onClick={() => handleScheduleEdit(schedule)} size="sm" variant="outline" className="flex-1 text-gray-700 border-gray-200 hover:bg-gray-50">수정</Button>
                      <Button onClick={() => handleScheduleSelect(schedule)} size="sm" className="flex-1 bg-blue-500 hover:bg-blue-600 text-white" disabled={isProcessing}>
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
          <Button onClick={() => router.push("/schedule/create/")} className="w-12 h-12 bg-blue-500 hover:bg-blue-600 text-white rounded-full shadow-lg flex items-center justify-center">
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
            statusText={currentPopup === "processing" ? "맞춤 식당 추천 검색 중..." : "맞춤 식당 추천 완료!"}
            isProcessing={currentPopup === "processing"}
          />
          <RecommendationReadyPopup
            isOpen={currentPopup === "recommendation_ready"}
            onViewResults={handleViewResults}
            onGoBack={closePopup} // 이전으로 버튼은 그냥 팝업을 닫도록 처리
          />
        </>
      )}
    </>
  )
}
