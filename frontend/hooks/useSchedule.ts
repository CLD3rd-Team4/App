"use client"

import { useState, useCallback } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi } from "@/services/api"
import type { Schedule, SchedulePayload } from "@/types"

// 이 훅은 이제 상태와 그 상태를 변경하는 함수만 제공하는 단순한 저장소 역할을 합니다.
export default function useSchedule() {
  const router = useRouter()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null)
  const [isProcessing, setIsProcessing] = useState(false)
  const [isLoading, setIsLoading] = useState(true);

  // 데이터 로딩을 책임지는 함수. ScheduleSummaryScreen에서 호출됩니다.
  const loadSelectedScheduleData = useCallback(async () => {
    console.log("--- 디버그: loadSelectedScheduleData 호출됨 ---");
    setIsLoading(true);
    try {
      const summaryResponse = await recommendApi.getActiveScheduleSummary();
      if (summaryResponse && summaryResponse.schedule) {
        console.log("--- 디버그: 데이터 로딩 성공 ---");
        setSelectedSchedule(summaryResponse.schedule);
      } else {
        console.log("--- 디버그: 데이터 없음. 선택 해제 처리. ---");
        localStorage.removeItem("scheduleSelected"); // 상태 불일치이므로 로컬스토리지도 정리
        setSelectedSchedule(null);
      }
    } catch (error) {
      console.error("--- 디버그: 데이터 로딩 실패. 선택 해제 처리. ---", error);
      localStorage.removeItem("scheduleSelected");
      setSelectedSchedule(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // 스케줄 목록 로딩 함수
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

  // 스케줄 선택 함수
  const selectSchedule = useCallback(async (scheduleId: string): Promise<Schedule | null> => {
    setIsProcessing(true);
    try {
      const response = await recommendApi.selectAndGetSummary(scheduleId);
      if (response && response.schedule) {
        localStorage.setItem("scheduleSelected", JSON.stringify({ value: true, timestamp: Date.now() }));
        setSelectedSchedule(response.schedule);
        return response.schedule;
      } else {
        localStorage.removeItem("scheduleSelected");
        setSelectedSchedule(null);
        return null;
      }
    } catch (error) {
      console.error("스케줄 선택 처리 실패:", error);
      localStorage.removeItem("scheduleSelected");
      setSelectedSchedule(null);
      alert("스케줄 선택 처리에 실패했습니다.");
      return null;
    } finally {
      setIsProcessing(false);
    }
  }, []);

  // 스케줄 선택 해제 함수
  const deselectSchedule = useCallback(() => {
    localStorage.removeItem("scheduleSelected");
    setSelectedSchedule(null);
  }, []);

  // 빌드 에러 방지를 위한 함수들
  const createSchedule = async (scheduleData: SchedulePayload) => { /* ... */ };
  const updateSchedule = async (scheduleId: string, scheduleData: SchedulePayload) => { /* ... */ };
  const deleteSchedule = async (scheduleId: string) => { /* ... */ };

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
    loadSelectedScheduleData, // 데이터 로딩 함수 내보내기
  }
}
