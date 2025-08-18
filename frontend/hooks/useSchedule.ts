"use client"

import { useState, useEffect, useCallback } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi } from "@/services/api"
import type { Schedule, SchedulePayload } from "@/types"
import api from "@/lib/interceptor"

const getInitialSelectionStatus = (): boolean => {
  if (typeof window === "undefined") return false;
  try {
    const item = localStorage.getItem("scheduleSelected");
    if (!item) return false;
    const parsed = JSON.parse(item);
    const isExpired = Date.now() - parsed.timestamp > 24 * 60 * 60 * 1000;
    return parsed.value === true && !isExpired;
  } catch (e) {
    return false;
  }
};

export default function useSchedule() {
  const router = useRouter();
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [isSelected, setIsSelected] = useState<boolean>(getInitialSelectionStatus);
  const [isLoading, setIsLoading] = useState(true);

  const deselectAndClear = useCallback(async () => {
    try {
      // 서버의 선택 상태를 먼저 해제합니다.
      await scheduleApi.deselectSchedule();
    } catch (error) {
      console.error("서버 선택 상태 해제 실패:", error);
      // 실패하더라도 프론트엔드 상태는 초기화하여 사용자 경험을 개선합니다.
    } finally {
      // 프론트엔드의 상태를 초기화합니다.
      localStorage.removeItem("scheduleSelected");
      setSelectedSchedule(null);
      setIsSelected(false);
    }
  }, []);

  const initializeHomepage = useCallback(async () => {
    setIsLoading(true);
    let finalIsSelected = getInitialSelectionStatus();

    if (!finalIsSelected) {
      try {
        const statusResponse = await scheduleApi.getSelectionStatus();
        finalIsSelected = statusResponse.isSelected;
        if(finalIsSelected) localStorage.setItem('scheduleSelected', JSON.stringify({ value: true, timestamp: Date.now() }));
      } catch (e) { finalIsSelected = false; }
    }

    if (finalIsSelected) {
      setIsSelected(true);
      try {
        // Promise.race를 사용하여 타임아웃 구현
        const summaryPromise = recommendApi.getActiveScheduleSummary();
        const timeoutPromise = new Promise((_, reject) => 
          setTimeout(() => reject(new Error('API call timed out after 10 seconds')), 10000)
        );

        const summaryResponse = await Promise.race([summaryPromise, timeoutPromise]);

        if (summaryResponse && summaryResponse.schedule) {
          setSelectedSchedule(summaryResponse.schedule);
        } else {
          // 데이터가 없는 경우, 선택 상태를 해제합니다.
          throw new Error("No schedule summary data found.");
        }
      } catch (e) {
        console.error("요약 정보 로딩 실패 또는 타임아웃. 선택 상태를 초기화합니다:", e);
        await deselectAndClear();
      }
    } else {
      deselectAndClear();
    }
    setIsLoading(false);
  }, [deselectAndClear]);

  const selectSchedule = useCallback(async (scheduleId: string): Promise<void> => {
    setIsProcessing(true);
    try {
      // API를 호출하여 서버에 선택 사실을 알리기만 하고, 응답 데이터는 사용하지 않습니다.
      await recommendApi.selectAndGetSummary(scheduleId);

      // 클라이언트 측에서는 선택되었다는 상태와 타임스탬프만 기록합니다.
      localStorage.setItem("scheduleSelected", JSON.stringify({ value: true, timestamp: Date.now() }));
      setIsSelected(true);
      // 요약 정보 상태는 여기서 관리하지 않으므로 null로 유지합니다.
      setSelectedSchedule(null);

    } catch (error) {
      // 실패 시 상태를 확실하게 되돌립니다.
      deselectAndClear();
      console.error("Failed to select schedule:", error);
      throw error; // 에러를 다시 던져서 호출한 쪽에서 알 수 있도록 합니다.
    } finally {
      setIsProcessing(false);
    }
  }, [deselectAndClear]);

  const triggerRecommendRequest = useCallback(async (scheduleId: string) => {
    try { await api.post("/recommend/request", { scheduleId }); } catch (e) { console.error(e); }
  }, []);

  const loadSchedules = useCallback(async () => { setIsLoading(true); try { const data = await scheduleApi.getSchedules(); setSchedules(data); } catch (e) { console.error(e); } finally { setIsLoading(false); } }, []);
  const createSchedule = async (scheduleData: SchedulePayload) => { setIsProcessing(true); try { await scheduleApi.createSchedule(scheduleData); router.push("/schedule"); } catch (e) { console.error(e); } finally { setIsProcessing(false); } };
  const updateSchedule = async (scheduleId: string, scheduleData: SchedulePayload) => { setIsProcessing(true); try { await scheduleApi.updateSchedule({ id: scheduleId, ...scheduleData }); router.push("/schedule"); } catch (e) { console.error(e); } finally { setIsProcessing(false); } };
  const deleteSchedule = async (scheduleId: string) => { setIsProcessing(true); try { await scheduleApi.deleteSchedule(scheduleId); setSchedules(s => s.filter(sch => sch.id !== scheduleId)); } catch (e) { console.error(e); } finally { setIsProcessing(false); } };
  const deselectSchedule = useCallback(() => { deselectAndClear(); }, [deselectAndClear]);

  return {
    schedules, selectedSchedule, isLoading, isProcessing, isSelected,
    loadSchedules, deselectSchedule, createSchedule, updateSchedule, deleteSchedule,
    initializeHomepage, // HomePage에서 사용
    selectSchedule,
    triggerRecommendRequest,
  }
}
