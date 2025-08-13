"use client"

import { useState, useEffect, useCallback } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi, APIError } from "@/services/api"
import type { Schedule, SchedulePayload } from "@/types"

export default function useSchedule() {
  const router = useRouter()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isProcessing, setIsProcessing] = useState(false)

  const loadSchedules = useCallback(async () => {
    try {
      setIsLoading(true)
      const data = await scheduleApi.getSchedules()
      setSchedules(data)
    } catch (error) {
      console.error("스케줄 목록 로드 실패:", error)
    } finally {
      setIsLoading(false)
    }
  }, [])

  const loadSelectedSchedule = useCallback(async () => {
    try {
      setIsLoading(true)
      const response = await recommendApi.getActiveScheduleSummary()
      if (response && response.schedule) {
        setSelectedSchedule(response.schedule)
      } else {
        setSelectedSchedule(null)
        localStorage.removeItem("scheduleSelected")
      }
    } catch (error) {
      console.error("선택된 스케줄 요약 로드 실패:", error)
      setSelectedSchedule(null)
      localStorage.removeItem("scheduleSelected")
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const scheduleSelected = localStorage.getItem("scheduleSelected") === "true"
    if (scheduleSelected) {
      loadSelectedSchedule()
    } else {
      setIsLoading(false)
    }
  }, [loadSelectedSchedule])

  const selectSchedule = async (scheduleId: string) => {
    setIsProcessing(true);
    try {
      // 추천 서버에 스케줄이 선택되었음을 알립니다.
      await recommendApi.selectAndGetSummary(scheduleId);

      // 프론트엔드 UI 상태를 위해 localStorage에 플래그를 저장합니다.
      localStorage.setItem("scheduleSelected", "true");

      router.push("/recommendations");
    } catch (error) {
      console.error("스케줄 선택 및 처리 실패:", error);
      alert("스케줄 처리에 실패했습니다. 잠시 후 다시 시도해주세요.");
      localStorage.removeItem("scheduleSelected");
    } finally {
      setIsProcessing(false);
    }
  };

  const deselectSchedule = () => {
    // TODO: 백엔드에 선택 해제를 알리는 API 호출 추가 (예: recommendApi.deselectSchedule())
    setSelectedSchedule(null);
    localStorage.removeItem("scheduleSelected");
    router.push("/");
    router.refresh();
  };

  const createSchedule = async (scheduleData: SchedulePayload) => {
    setIsProcessing(true)
    try {
      const response = await scheduleApi.createSchedule(scheduleData)
      // API 응답으로 받은 scheduleId와 요청 시 사용된 scheduleData를 결합하여 완전한 Schedule 객체를 만듭니다.
      const newSchedule: Schedule = {
        id: response.scheduleId,
        ...scheduleData,
      };
      setSchedules((prev) => [...prev, newSchedule])
      router.push("/schedule")
    } catch (error) {
      console.error("스케줄 생성 실패:", error)
      throw error
    } finally {
      setIsProcessing(false)
    }
  }

  const updateSchedule = async (scheduleId: string, scheduleData: SchedulePayload) => {
    setIsProcessing(true)
    try {
      const scheduleToUpdate: Schedule = {
        id: scheduleId,
        ...scheduleData,
      };

      const updatedScheduleDetail = await scheduleApi.updateSchedule(scheduleToUpdate);
      
      // 상태를 업데이트하여 UI에 즉시 반영
      setSchedules((prev) => 
        prev.map((s) => (s.id === scheduleId ? { ...s, ...scheduleData } : s))
      );

      // 선택된 스케줄 정보도 업데이트 (선택된 상태였다면)
      if (selectedSchedule?.id === scheduleId) {
        setSelectedSchedule(prev => prev ? { ...prev, ...scheduleData, id: scheduleId } : null);
      }

      router.push("/schedule");

    } catch (error) {
      console.error("스케줄 업데이트 실패:", error)
      alert("스케줄 업데이트에 실패했습니다.")
    } finally {
      setIsProcessing(false)
    }
  }

  const deleteSchedule = async (scheduleId: string) => {
    setIsProcessing(true)
    try {
      await scheduleApi.deleteSchedule(scheduleId)
      setSchedules((prev) => prev.filter((s) => s.id !== scheduleId))
      if (selectedSchedule?.id === scheduleId) {
        deselectSchedule()
      }
    } catch (error) {
      console.error("스케줄 삭제 실패:", error)
    } finally {
      setIsProcessing(false)
    }
  }

  return {
    schedules,
    selectedSchedule,
    isLoading,
    isProcessing,
    loadSchedules,
    selectSchedule,
    deselectSchedule,
    createSchedule,
    updateSchedule,
    deleteSchedule,
  }
}
