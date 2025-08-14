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

  const deselectSchedule = useCallback(() => {
    setSelectedSchedule(null);
    localStorage.removeItem("scheduleSelected");
  }, []);

  const loadSelectedSchedule = useCallback(async () => {
    setIsLoading(true);
    try {
      // 1. schedule-service에 Valkey 선택 상태 플래그를 먼저 확인합니다.
      const selectionStatusResponse = await scheduleApi.getSelectionStatus();
      
      if (selectionStatusResponse.isSelected) {
        // 2. schedule-service의 플래그가 true이면, recommend-service에 상세 요약 정보를 요청합니다.
        const response = await recommendApi.getActiveScheduleSummary();
        if (response && response.schedule) {
          setSelectedSchedule(response.schedule);
        } else {
          // recommend-service에서 스케줄을 찾지 못하면, 로컬 상태를 초기화합니다.
          deselectSchedule();
        }
      } else {
        // 3. schedule-service의 플래그가 false이면, 로컬 상태를 초기화합니다.
        deselectSchedule();
      }
    } catch (error) {
      console.error("선택된 스케줄 로드 및 동기화 실패:", error);
      deselectSchedule(); // 에러 발생 시에도 상태를 초기화합니다.
    } finally {
      setIsLoading(false);
    }
  }, [deselectSchedule]);

  useEffect(() => {
    const scheduleSelected = localStorage.getItem("scheduleSelected") === "true";
    if (scheduleSelected) {
      loadSelectedSchedule();
    } else {
      setIsLoading(false);
    }
  }, [loadSelectedSchedule]);

  const selectSchedule = async (scheduleId: string) => {
    setIsProcessing(true);
    try {
      // 백엔드에 선택 사실을 알려 Valkey 상태 등을 업데이트하게 합니다.
      await recommendApi.selectAndGetSummary(scheduleId);
      // 프론트엔드 UI를 위해 localStorage에 플래그를 저장합니다.
      localStorage.setItem("scheduleSelected", "true");
    } catch (error) {
      console.error("스케줄 선택 처리 실패:", error);
      alert("스케줄 선택 처리에 실패했습니다.");
      localStorage.removeItem("scheduleSelected");
    } finally {
      setIsProcessing(false);
    }
  };

  const createSchedule = async (scheduleData: SchedulePayload) => {
    setIsProcessing(true)
    try {
      const response = await scheduleApi.createSchedule(scheduleData)
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

      await scheduleApi.updateSchedule(scheduleToUpdate);
      
      setSchedules((prev) => 
        prev.map((s) => (s.id === scheduleId ? { ...s, ...scheduleData } : s))
      );

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
        // 선택 해제 시에는 deselectSchedule 콜백을 사용합니다.
        const freshDeselect = deselectSchedule;
        freshDeselect();
        router.push("/");
        router.refresh();
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