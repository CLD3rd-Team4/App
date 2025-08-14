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

  // 컴포넌트 마운트 시 단 한 번만 실행되는 최종 초기화 로직
  useEffect(() => {
    const initialize = async () => {
      console.log("--- 디버그: 마운트 이펙트 시작 (단 한번 실행) ---");
      setIsLoading(true);

      let finalIsSelected = getInitialSelectionStatus();
      console.log(`--- 디버그: localStorage 초기 상태: ${finalIsSelected} ---`);

      // 로컬 상태가 유효하지 않으면 (없거나, 만료되었으면) 서버(Valkey)와 동기화
      if (!finalIsSelected) {
        try {
          console.log("--- 디버그: 로컬 상태 무효. Valkey와 동기화 시도... ---");
          const statusResponse = await scheduleApi.getSelectionStatus();
          finalIsSelected = statusResponse.isSelected;
          console.log(`--- 디버그: Valkey 조회 결과: ${finalIsSelected} ---`);
          localStorage.setItem('scheduleSelected', JSON.stringify({ value: finalIsSelected, timestamp: Date.now() }));
        } catch (e) {
          console.error("--- 디버그: Valkey 상태 조회 실패. ---", e);
          finalIsSelected = false; // 에러 시 false로 간주
        }
      }

      // 모든 확인 절차 후, 최종적으로 선택된 상태라면 실제 데이터 로드
      if (finalIsSelected) {
        try {
          console.log("--- 디버그: 최종 상태 '선택됨'. 추천 서버에서 데이터 로딩... ---");
          const summaryResponse = await recommendApi.getActiveScheduleSummary();
          if (summaryResponse && summaryResponse.schedule) {
            console.log("--- 디버그: 데이터 로딩 성공. ---");
            setSelectedSchedule(summaryResponse.schedule);
            setIsSelected(true); // 상태 확정
          } else {
            console.log("--- 디버그: 추천 서버에 데이터 없음. 최종 초기화. ---");
            deselectAndClear();
          }
        } catch (e) {
          console.error("--- 디버그: 추천 서버 데이터 요청 실패. 최종 초기화. ---", e);
          deselectAndClear();
        }
      } else {
        // 최종적으로 선택되지 않은 상태라면 모든 것을 초기화
        deselectAndClear();
      }
      
      console.log("--- 디버그: 초기화 로직 종료. ---");
      setIsLoading(false);
    };

    initialize();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // 의존성 배열을 비워 무한 루프를 방지합니다.

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
      if (response && response.schedule) {
        localStorage.setItem("scheduleSelected", JSON.stringify({ value: true, timestamp: Date.now() }));
        setSelectedSchedule(response.schedule);
        setIsSelected(true);
        return response.schedule;
      } else {
        deselectAndClear();
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
  }
}