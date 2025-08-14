"use client"

import { useState, useEffect, useCallback } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi } from "@/services/api"
import type { Schedule, SchedulePayload } from "@/types"

// 훅 외부에서 실행되는 순수 함수: 초기 선택 상태를 동기적으로 결정합니다.
const getInitialSelectionStatus = (): boolean => {
  if (typeof window === "undefined") {
    return false;
  }
  try {
    const item = localStorage.getItem("scheduleSelected");
    if (!item) return false;

    const parsed = JSON.parse(item);
    const isExpired = Date.now() - parsed.timestamp > 24 * 60 * 60 * 1000; // 24시간
    
    return parsed.value === true && !isExpired;
  } catch (e) {
    return false;
  }
};

export default function useSchedule() {
  const router = useRouter()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null)
  const [isProcessing, setIsProcessing] = useState(false)
  const [isSelected, setIsSelected] = useState<boolean>(getInitialSelectionStatus);
  const [isLoading, setIsLoading] = useState(true);

  const deselectAndClear = useCallback(() => {
    console.log("--- 디버그: 스케줄 상태와 localStorage를 초기화합니다. ---");
    localStorage.removeItem("scheduleSelected");
    setSelectedSchedule(null);
    setIsSelected(false);
  }, []);

  const checkInitialSelection = useCallback(async () => {
    console.log("--- 디버그: HomePage에서 초기화 함수 실행. ---");
    setIsLoading(true);
    let finalIsSelected = getInitialSelectionStatus();

    if (!finalIsSelected) {
      try {
        const statusResponse = await scheduleApi.getSelectionStatus();
        finalIsSelected = statusResponse.isSelected;
        localStorage.setItem('scheduleSelected', JSON.stringify({ value: finalIsSelected, timestamp: Date.now() }));
      } catch (e) {
        finalIsSelected = false;
      }
    }

    if (finalIsSelected) {
      // isSelected 상태를 먼저 true로 설정하여 화면이 유지되도록 합니다.
      setIsSelected(true);
      try {
        const summaryResponse = await recommendApi.getActiveScheduleSummary();
        if (summaryResponse && summaryResponse.schedule) {
          setSelectedSchedule(summaryResponse.schedule);
        } else {
          // 추천서버에 데이터가 없어도 선택 상태는 유지하고, 데이터만 null로 설정합니다.
          console.log("--- 디버그: 추천 서버에 데이터 없음. 데이터만 null로 설정. ---");
          setSelectedSchedule(null);
        }
      } catch (e) {
        console.error("--- 디버그: 추천 서버 데이터 요청 실패. 데이터만 null로 설정. ---", e);
        setSelectedSchedule(null);
      }
    } else {
      deselectAndClear();
    }
    setIsLoading(false);
  }, [deselectAndClear]);

  const loadSchedules = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await scheduleApi.getSchedules();
      setSchedules(data);
    } catch (error) {
      console.error("스케줄 목록 로드 실패:", error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const selectSchedule = useCallback(async (scheduleId: string): Promise<Schedule | null> => {
    setIsProcessing(true);
    try {
      const response = await recommendApi.selectAndGetSummary(scheduleId);
      // API 호출이 성공하면 (Valkey에 저장 성공), 로컬스토리지에 상태를 저장합니다.
      localStorage.setItem("scheduleSelected", JSON.stringify({ value: true, timestamp: Date.now() }));
      
      if (response && response.schedule) {
        setSelectedSchedule(response.schedule);
        setIsSelected(true);
        return response.schedule;
      } else {
        // 선택은 성공했으나, 데이터가 없는 경우
        setIsSelected(true);
        setSelectedSchedule(null);
        return null;
      }
    } catch (error) {
      console.error("스케줄 선택 처리 실패:", error);
      deselectAndClear();
      alert("스케줄 선택 처리에 실패했습니다.");
      return null;
    } finally {
      setIsProcessing(false);
    }
  }, [deselectAndClear]);

  const deselectSchedule = useCallback(() => {
    deselectAndClear();
  }, [deselectAndClear]);

  const createSchedule = async (scheduleData: SchedulePayload) => {
    setIsProcessing(true);
    try {
      await scheduleApi.createSchedule(scheduleData);
      router.push("/schedule");
    } catch (error) {
      console.error("스케줄 생성 실패:", error);
      alert("스케줄 생성에 실패했습니다.");
    } finally {
      setIsProcessing(false);
    }
  };

  const updateSchedule = async (scheduleId: string, scheduleData: SchedulePayload) => {
    setIsProcessing(true);
    try {
      await scheduleApi.updateSchedule({ id: scheduleId, ...scheduleData });
      router.push("/schedule");
    } catch (error) {
      console.error("스케줄 업데이트 실패:", error);
      alert("스케줄 업데이트에 실패했습니다.");
    } finally {
      setIsProcessing(false);
    }
  };

  const deleteSchedule = async (scheduleId: string) => {
    setIsProcessing(true);
    try {
      await scheduleApi.deleteSchedule(scheduleId);
      setSchedules((prev) => prev.filter((s) => s.id !== scheduleId));
    } catch (error) {
      console.error("스케줄 삭제 실패:", error);
    } finally {
      setIsProcessing(false);
    }
  };

  return {
    schedules,
    selectedSchedule,
    isLoading,
    isProcessing,
    isSelected,
    loadSchedules,
    selectSchedule,
    deselectSchedule,
    createSchedule,
    updateSchedule,
    deleteSchedule,
    checkInitialSelection,
  }
}