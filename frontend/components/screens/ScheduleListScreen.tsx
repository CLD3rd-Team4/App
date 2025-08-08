"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import useSchedule from "@/hooks/useSchedule" // useSchedule 훅 임포트
import BottomNavigation from "@/components/common/BottomNavigation"
import { Plus } from "lucide-react"
import type { Schedule } from "@/types"

export default function ScheduleListScreen() {
  const router = useRouter()
  const {
    schedules,
    isLoading,
    isProcessing,
    selectSchedule,
    deleteSchedule,
    loadSchedules,
  } = useSchedule() // useSchedule 훅 사용

  const [isClient, setIsClient] = useState(false)

  useEffect(() => {
    setIsClient(true)
    loadSchedules() // 스케줄 목록 로드
  }, [])
  const handleScheduleSelect = async (schedule: Schedule) => {
    if (!schedule || !schedule.id) {
      console.error("Invalid schedule or schedule ID")
      alert("유효하지 않은 스케줄입니다.")
      return
    }
    await selectSchedule(schedule.id)
  }

  const handleScheduleEdit = (schedule: Schedule) => {
    if (!schedule || !schedule.id) {
      console.error("Invalid schedule or schedule ID")
      alert("유효하지 않은 스케줄입니다.")
      return
    }
    localStorage.setItem("editingSchedule", JSON.stringify(schedule))
    router.push(`/schedule/edit?id=${schedule.id}`)
  }

  const handleScheduleDelete = async (scheduleId: string) => {
    if (!scheduleId) {
      console.error("Invalid schedule ID")
      alert("유효하지 않은 스케줄 ID입니다.")
      return
    }
    await deleteSchedule(scheduleId)
  }

  if (!isClient) {
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
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm">
        <h1 className="text-lg font-medium">스케줄 선택하기</h1>
      </div>

      <div className="flex-1 content-with-bottom-nav">
        <div className="p-4">
          {isLoading ? (
            <div className="text-center py-8">
              <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
              <p className="text-gray-600">로딩 중...</p>
            </div>
          ) : schedules.length === 0 ? (
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
              {schedules.map((schedule, index) => (
                <div key={index} className="bg-white rounded-lg p-4 shadow-sm">
                  <h3 className="font-medium mb-3">{schedule.title}</h3>
                  <div className="flex gap-2">
                    <Button
                      onClick={(e) => {
                        e.stopPropagation()
                        if (!schedule.id) {
                          console.error(
                            "Delete Error: Invalid schedule ID",
                            schedule
                          )
                          alert("유효하지 않은 스케줄 ID입니다.")
                          return
                        }
                        handleScheduleDelete(schedule.id)
                      }}
                      size="sm"
                      variant="outline"
                      className="flex-1 text-red-600 border-red-200 hover:bg-red-50"
                    >
                      삭제
                    </Button>
                    <Button
                      onClick={(e) => {
                        e.stopPropagation()
                        if (!schedule.id) {
                          console.error(
                            "Edit Error: Invalid schedule ID",
                            schedule
                          )
                          alert("유효하지 않은 스케줄 ID입니다.")
                          return
                        }
                        handleScheduleEdit(schedule)
                      }}
                      size="sm"
                      variant="outline"
                      className="flex-1 text-gray-700 border-gray-200 hover:bg-gray-50"
                    >
                      수정
                    </Button>
                    <Button
                      onClick={async (e) => {
                        e.stopPropagation()
                        await handleScheduleSelect(schedule)
                      }}
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
  )
}
