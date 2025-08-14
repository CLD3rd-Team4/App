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
  const [isLoading, setIsLoading] = useState(false);

  const deselectAndClear = useCallback(() => {
    localStorage.removeItem("scheduleSelected");
    setSelectedSchedule(null);
    setIsSelected(false);
  }, []);

  // HomePage에서만 사용하는 초기화 함수
  const checkAndSyncSelection = useCallback(async () => {
    setIsLoading(true);
    let finalIsSelected = getInitialSelectionStatus();
    if (!finalIsSelected) {
      try {
        const statusResponse = await scheduleApi.getSelectionStatus();
        finalIsSelected = statusResponse.isSelected;
        if(finalIsSelected) localStorage.setItem('scheduleSelected', JSON.stringify({ value: true, timestamp: Date.now() }));
      } catch (e) { finalIsSelected = false; }
    }
    setIsSelected(finalIsSelected);
    if (!finalIsSelected) {
        setSelectedSchedule(null);
    }
    setIsLoading(false);
  }, []);

  // ScheduleSummaryScreen에서 사용하는 데이터 로딩 함수
  const loadActiveSchedule = useCallback(async () => {
    setIsLoading(true);
    try {
        const summaryResponse = await recommendApi.getActiveScheduleSummary();
        if (summaryResponse && summaryResponse.schedule) {
          setSelectedSchedule(summaryResponse.schedule);
        } else {
          // 데이터가 없으면 선택 상태를 해제합니다.
          deselectAndClear();
        }
      } catch (e) {
        deselectAndClear();
      }
    setIsLoading(false);
  }, [deselectAndClear]);

  const selectSchedule = useCallback(async (scheduleId: string): Promise<Schedule | null> => {
    setIsProcessing(true);
    try {
      const response = await recommendApi.selectAndGetSummary(scheduleId);
      if (response && response.schedule) {
        localStorage.setItem("scheduleSelected", JSON.stringify({ value: true, timestamp: Date.now(), id: scheduleId }));
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
    checkAndSyncSelection, // HomePage용
    selectSchedule, // ScheduleListScreen용
    triggerRecommendRequest, // ScheduleListScreen용
    loadActiveSchedule, // ScheduleSummaryScreen용
  }
}