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
      } catch (e) { 
        finalIsSelected = false; 
      }
    }

    if (finalIsSelected) {
      setIsSelected(true);
      try {
        // Promise.race를 사용하여 타임아웃 (10초) 구현
        const summaryPromise = recommendApi.getActiveScheduleSummary();
        const timeoutPromise = new Promise((_, reject) => 
          setTimeout(() => reject(new Error('API call timed out after 10 seconds')), 10000)
        );

        // API 호출과 타임아웃 중 먼저 끝나는 것을 기다립니다.
        const summaryResponse = await Promise.race([summaryPromise, timeoutPromise]) as { schedule: Schedule | null };

        if (summaryResponse && summaryResponse.schedule) {
          setSelectedSchedule(summaryResponse.schedule);
        } else {
          // API 호출은 성공했으나 데이터가 없는 경우 (추천 정보 생성 실패 등)
          throw new Error("No schedule summary data found, deselecting.");
        }
      } catch (e) {
        // API 호출 자체가 실패하거나, 타임아웃되거나, 데이터가 없는 경우
        console.error("요약 정보 로딩 실패. 선택 상태를 초기화합니다:", e);
        await deselectAndClear();
      }
    } else {
      // 선택된 스케줄이 없는 것이 확인된 경우
      if (isSelected) await deselectAndClear(); // 혹시 모를 프론트 상태 불일치 정리
    }
    setIsLoading(false);
  }, [deselectAndClear]);

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
      deselectAndClear();
      throw error;
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
