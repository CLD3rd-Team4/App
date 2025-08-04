"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import { useSchedule } from "@/hooks/useSchedule"
import { scheduleApi } from "@/services/api"
import BottomNavigation from "@/components/common/BottomNavigation"
import { Plus } from "lucide-react"
import type { Schedule } from "@/types"

export default function ScheduleListScreen() {
  const router = useRouter()
  const { schedules, selectSchedule, loadSchedules, deleteSchedule } = useSchedule()
  const [isLoading, setIsLoading] = useState(true)
  const [isClient, setIsClient] = useState(false)
  const [isProcessing, setIsProcessing] = useState<string | null>(null);

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (isClient) {
      loadScheduleList()
    }
  }, [isClient])

  const loadScheduleList = async () => {
    try {
      setIsLoading(true)
      await loadSchedules("test-user-123")
    } catch (error) {
      console.error("스케줄 목록 로드 실패:", error)
    } finally {
      setIsLoading(false)
    }
  }

  const handleScheduleSelect = async (schedule: Schedule) => {
    if (!schedule || !schedule.id) {
      console.error("Invalid schedule or schedule ID");
      alert("유효하지 않은 스케줄입니다.");
      return;
    }
    
    setIsProcessing(schedule.id);

    try {
      const response = await scheduleApi.processSchedule(schedule.id, { type: 'SELECT' });

      if (response && response.schedule) {
        // 백엔드에서 받은 상세 스케줄 정보에 프론트엔드에서 사용하는 id를 추가합니다.
        const updatedSchedule = {
          ...response.schedule,
          id: schedule.id, 
        };
        
        selectSchedule(updatedSchedule as Schedule);
        router.push('/');
      } else {
        throw new Error("서버에서 스케줄 정보를 받아오지 못했습니다.");
      }

    } catch (error) {
      console.error("스케줄 처리 실패:", error);
      alert('스케줄 처리에 실패했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsProcessing(null);
    }
  };

  const handleScheduleEdit = (schedule: Schedule) => {
    if (!schedule || !schedule.id) {
      console.error("Invalid schedule or schedule ID");
      alert("유효하지 않은 스케줄입니다.");
      return;
    }
    router.push(`/schedule/edit/${schedule.id}/`)
  }

  const handleScheduleDelete = async (scheduleId: string) => {
    if (!scheduleId) {
      console.error("Invalid schedule ID");
      alert("유효하지 않은 스케줄 ID입니다.");
      return;
    }
    try {
      await deleteSchedule(scheduleId, "test-user-123")
    } catch (error) {
      console.error("스케줄 삭제 실패:", error)
    }
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
                        e.stopPropagation();
                        if (!schedule.id) {
                          console.error("Delete Error: Invalid schedule ID", schedule);
                          alert("유효하지 않은 스케줄 ID입니다.");
                          return;
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
                        e.stopPropagation();
                        if (!schedule.id) {
                          console.error("Edit Error: Invalid schedule ID", schedule);
                          alert("유효하지 않은 스케줄 ID입니다.");
                          return;
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
                        e.stopPropagation();
                        await handleScheduleSelect(schedule)
                      }}
                      size="sm"
                      className="flex-1 bg-blue-500 hover:bg-blue-600 text-white"
                      disabled={isProcessing === schedule.id}
                    >
                      {isProcessing === schedule.id ? '처리 중...' : '선택'}
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
