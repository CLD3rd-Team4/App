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
      const storedSelection = localStorage.getItem("scheduleSelected");
      let shouldCheckValkey = false; // scheduleApi.getSelectionStatus()를 호출해야 하는지 결정하는 플래그

      if (storedSelection) {
        try {
          const parsedSelection = JSON.parse(storedSelection);
          const twentyFourHours = 24 * 60 * 60 * 1000; // 24시간을 밀리초로

          if (parsedSelection.value === true && (Date.now() - parsedSelection.timestamp < twentyFourHours)) {
            // 로컬 스토리지에 저장된 선택 상태가 유효하고 만료되지 않았습니다. 일단 신뢰합니다.
            // recommendApi에서 요약 정보를 직접 로드하려고 시도합니다.
            const response = await recommendApi.getActiveScheduleSummary();
            if (response && response.schedule) {
              setSelectedSchedule(response.schedule);
            } else {
              // recommend-service에서 스케줄을 찾지 못했습니다. 로컬 스토리지가 오래된 것입니다.
              // schedule-service의 Valkey와 다시 동기화해야 합니다.
              shouldCheckValkey = true; 
            }
          } else {
            // 로컬 스토리지가 만료되었거나 유효하지 않습니다 (value가 true가 아님).
            // schedule-service의 Valkey와 다시 동기화해야 합니다.
            shouldCheckValkey = true;
          }
        } catch (parseError) {
          // JSON 파싱 실패 (예: 이전 "true" 문자열이거나 손상된 데이터).
          // 유효하지 않은 것으로 간주하고 schedule-service의 Valkey와 다시 동기화해야 합니다.
          console.error("Error parsing localStorage 'scheduleSelected':", parseError);
          shouldCheckValkey = true;
        }
      } else {
        // localStorage에 'scheduleSelected'가 없습니다.
        // schedule-service의 Valkey와 다시 동기화해야 합니다.
        shouldCheckValkey = true;
      }

      if (shouldCheckValkey) {
        // localStorage가 만료되었거나, 유효하지 않거나, recommendApi가 null을 반환한 경우 이 경로를 따릅니다.
        // 이제 schedule-service의 Valkey를 직접 확인합니다.
        const selectionStatusResponse = await scheduleApi.getSelectionStatus();
        if (selectionStatusResponse.isSelected) {
          // schedule-service의 Valkey가 선택되었다고 합니다.
          // recommendApi에서 요약 정보를 다시 로드하려고 시도합니다 (일시적인 문제였거나 recommend-service에서 만료되었을 수 있음).
          const response = await recommendApi.getActiveScheduleSummary();
          if (response && response.schedule) {
            setSelectedSchedule(response.schedule);
            // 이제 유효하다고 확인되었으므로, 새 타임스탬프와 함께 localStorage를 다시 저장합니다.
            localStorage.setItem("scheduleSelected", JSON.stringify({ value: true, timestamp: Date.now() }));
          } else {
            // schedule-service가 선택되었다고 하지만, recommend-service가 요약을 제공할 수 없습니다.
            // 이는 동기화 문제입니다. 로컬 상태를 지웁니다.
            deselectSchedule();
          }
        } else {
          // schedule-service의 Valkey가 선택되지 않았다고 합니다.
          // 로컬 상태를 지웁니다。
          deselectSchedule();
        }
      }
    } catch (error) {
      console.error("선택된 스케줄 로드 및 동기화 실패:", error);
      deselectSchedule(); // 에러 발생 시에도 상태를 초기화합니다.
    } finally {
      setIsLoading(false);
    }
  }, [deselectSchedule]);

  useEffect(() => {
    const scheduleSelected = localStorage.getItem("scheduleSelected"); // Read raw string
    if (scheduleSelected) { // Check if any value exists
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
      localStorage.setItem("scheduleSelected", JSON.stringify({ value: true, timestamp: Date.now() }));
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