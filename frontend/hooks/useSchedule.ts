"use client"

import { useState, useEffect, useCallback } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi } from "@/services/api"
import type { Schedule, SchedulePayload } from "@/types"

// 훅 외부에서 실행되는 순수 함수: 초기 선택 상태를 동기적으로 결정합니다.
const getInitialSelectionStatus = (): boolean => {
  // 서버 사이드 렌더링 환경에서는 window 객체가 없으므로 false를 반환합니다.
  if (typeof window === "undefined") {
    return false;
  }
  try {
    const item = localStorage.getItem("scheduleSelected");
    if (!item) return false;

    const parsed = JSON.parse(item);
    const isExpired = Date.now() - parsed.timestamp > 24 * 60 * 60 * 1000; // 24시간
    
    // 값이 true이고 만료되지 않았을 때만 true를 반환합니다.
    return parsed.value === true && !isExpired;
  } catch (e) {
    // 파싱 에러 등 예외 발생 시 false를 반환합니다.
    return false;
  }
};

export default function useSchedule() {
  const router = useRouter()
  const [schedules, setSchedules] = useState<Schedule[]>([]) // 스케줄 목록 상태
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null) // 선택된 스케줄 객체 상태
  const [isProcessing, setIsProcessing] = useState(false) // 생성/수정/삭제 등 처리 중 상태

  // 선택 여부(boolean)와 데이터 로딩 상태를 분리하여 관리합니다.
  const [isSelected, setIsSelected] = useState<boolean>(getInitialSelectionStatus);
  const [isLoading, setIsLoading] = useState(true); // 초기 로딩 상태

  // 상태 초기화 유틸리티 함수
  const deselectAndClear = useCallback(() => {
    console.log("--- 디버그: 스케줄 상태와 localStorage를 초기화합니다. ---");
    localStorage.removeItem("scheduleSelected");
    setSelectedSchedule(null);
    setIsSelected(false);
  }, []);

  // 컴포넌트 마운트 시 실행되는 핵심 로직
  useEffect(() => {
    const syncAndFetch = async () => {
      console.log(`--- 디버그: 마운트 이펙트 시작. 초기 isSelected 상태: ${isSelected} ---`);
      setIsLoading(true);

      if (isSelected) {
        console.log("--- 디버그: 초기 상태 '선택됨'. 추천 서버에서 데이터 로딩 시도... ---");
        try {
          const summaryResponse = await recommendApi.getActiveScheduleSummary();
          console.log("--- 디버그: 추천 서버 응답:", summaryResponse);
          if (summaryResponse && summaryResponse.schedule) {
            console.log("--- 디버그: 성공: 스케줄 데이터를 가져왔습니다. ---");
            setSelectedSchedule(summaryResponse.schedule);
          } else {
            console.log("--- 디버그: 실패: 추천 서버에 데이터 없음. 선택 상태를 해제합니다. ---");
            deselectAndClear();
          }
        } catch (e) {
          console.error("--- 디버그: 추천 서버 데이터 요청 실패. 선택 상태를 해제합니다. ---", e);
          deselectAndClear();
        }
      } else {
        // 로컬스토리지 정보가 없거나 만료된 경우, Valkey 상태를 확인합니다.
        console.log("--- 디버그: 초기 상태 '선택 안됨'. Valkey와 동기화를 시도합니다... ---");
        try {
          const statusResponse = await scheduleApi.getSelectionStatus();
          if (statusResponse.isSelected) {
            console.log("--- 디버그: Valkey 상태는 '선택됨'. 상태를 true로 변경 후 데이터 로딩을 재시도합니다. ---");
            localStorage.setItem('scheduleSelected', JSON.stringify({ value: true, timestamp: Date.now() }));
            setIsSelected(true); // 이 상태 변경으로 useEffect가 다시 실행되어 데이터 로딩을 시도합니다.
          } else {
            deselectAndClear();
          }
        } catch (e) {
          console.error("--- 디버그: Valkey 상태 조회 실패. ---", e);
        }
      }
      setIsLoading(false);
    };
    syncAndFetch();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isSelected]);

  // 스케줄 목록을 불러오는 함수
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

  // 스케줄을 선택하는 함수
  const selectSchedule = useCallback(async (scheduleId: string): Promise<Schedule | null> => {
    setIsProcessing(true);
    try {
      const response = await recommendApi.selectAndGetSummary(scheduleId);
      if (response && response.schedule) {
        localStorage.setItem("scheduleSelected", JSON.stringify({ value: true, timestamp: Date.now() }));
        setSelectedSchedule(response.schedule);
        setIsSelected(true);
        return response.schedule; // 스케줄 객체 반환
      } else {
        deselectAndClear();
        return null; // null 반환
      }
    } catch (error) {
      console.error("스케줄 선택 처리 실패:", error);
      deselectAndClear();
      alert("스케줄 선택 처리에 실패했습니다.");
      return null; // null 반환
    } finally {
      setIsProcessing(false);
    }
  }, [deselectAndClear]);

  // 스케줄 선택을 해제하는 함수
  const deselectSchedule = useCallback(() => {
    deselectAndClear();
  }, [deselectAndClear]);

  // 스케줄을 생성하는 함수
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

  // 스케줄을 수정하는 함수
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

  // 스케줄을 삭제하는 함수
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
