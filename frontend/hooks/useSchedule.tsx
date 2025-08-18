"use client"

import { createContext, useContext, useState, useCallback, ReactNode } from "react";
import { useRouter } from "next/navigation";
import { scheduleApi, recommendApi } from "@/services/api";
import type { Schedule, SchedulePayload } from "@/types";
import api from "@/lib/interceptor";

// 1. 컨텍스트의 타입 정의
interface ScheduleContextType {
  schedules: Schedule[];
  selectedSchedule: Schedule | null;
  isLoading: boolean;
  isProcessing: boolean;
  isSelected: boolean;
  loadSchedules: () => Promise<void>;
  deselectSchedule: () => void;
  createSchedule: (scheduleData: SchedulePayload) => Promise<void>;
  updateSchedule: (scheduleId: string, scheduleData: SchedulePayload) => Promise<void>;
  deleteSchedule: (scheduleId: string) => Promise<void>;
  initializeHomepage: () => Promise<void>;
  selectSchedule: (scheduleId: string) => Promise<Schedule | null>;
  triggerRecommendRequest: (scheduleId: string) => Promise<void>;
}

// 2. 컨텍스트 생성
const ScheduleContext = createContext<ScheduleContextType | undefined>(undefined);

// 3. 프로바이더 컴포넌트 생성 (Named Export)
export function ScheduleProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  
  const getInitialSelectionStatus = useCallback((): boolean => {
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
  }, []);

  const [isSelected, setIsSelected] = useState<boolean>(getInitialSelectionStatus());
  const [isLoading, setIsLoading] = useState(true);

  const deselectAndClear = useCallback(async () => {
    try {
      await scheduleApi.deselectSchedule();
    } catch (error) {
      console.error("서버 선택 상태 해제 실패:", error);
    } finally {
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
        if (finalIsSelected) {
          localStorage.setItem('scheduleSelected', JSON.stringify({ value: true, timestamp: Date.now() }));
        }
      } catch (e) {
        finalIsSelected = false;
      }
    }
    
    setIsSelected(finalIsSelected);

    // 무한 로딩 방지를 위해 요약 정보 로딩 로직 제거
    if (!finalIsSelected && isSelected) { 
       await deselectAndClear();
    }
    
    setIsLoading(false);
  }, [deselectAndClear, getInitialSelectionStatus, isSelected]);

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
        await deselectAndClear();
        return null;
      }
    } catch (error) {
      await deselectAndClear();
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

  const value = {
    schedules, selectedSchedule, isLoading, isProcessing, isSelected,
    loadSchedules, deselectSchedule, createSchedule, updateSchedule, deleteSchedule,
    initializeHomepage, selectSchedule, triggerRecommendRequest,
  };

  return <ScheduleContext.Provider value={value}>{children}</ScheduleContext.Provider>;
}

// 4. 커스텀 훅 생성 (Default Export)
const useSchedule = () => {
  const context = useContext(ScheduleContext);
  if (context === undefined) {
    throw new Error("useSchedule must be used within a ScheduleProvider");
  }
  return context;
};

export default useSchedule;