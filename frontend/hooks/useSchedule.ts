"use client"

import { useState, useEffect, useCallback } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi } from "@/services/api"
import type { Schedule, SchedulePayload } from "@/types"

const getInitialSchedule = (): Schedule | null => {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const item = localStorage.getItem("scheduleSelected");
    if (!item) return null;

    const parsed = JSON.parse(item);
    const twentyFourHours = 24 * 60 * 60 * 1000;
    if (parsed.schedule && parsed.timestamp && (Date.now() - parsed.timestamp < twentyFourHours)) {
      return parsed.schedule as Schedule;
    }
  } catch (e) {
    console.error("Failed to parse initial schedule from localStorage:", e);
  }
  localStorage.removeItem("scheduleSelected"); // Clean up invalid/expired item
  return null;
};

export default function useSchedule() {
  const router = useRouter()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(getInitialSchedule);
  const [isLoading, setIsLoading] = useState(false)
  const [isProcessing, setIsProcessing] = useState(false)

  const loadSchedules = useCallback(async () => {
    setIsLoading(true)
    try {
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
    console.log("--- DEBUG: loadSelectedSchedule started ---");
    setIsLoading(true);
    try {
      const selectionStatusResponse = await scheduleApi.getSelectionStatus();
      console.log("--- DEBUG: Valkey status from server:", selectionStatusResponse.isSelected);

      if (selectionStatusResponse.isSelected) {
        console.log("--- DEBUG: Valkey status is true. Fetching from recommend-server... ---");
        const response = await recommendApi.getActiveScheduleSummary();
        console.log("--- DEBUG: recommend-server response:", response);

        if (response && response.schedule) {
          console.log("--- DEBUG: Successfully loaded schedule. Updating state. ---");
          setSelectedSchedule(response.schedule);
          localStorage.setItem("scheduleSelected", JSON.stringify({ schedule: response.schedule, timestamp: Date.now() }));
        } else {
          console.log("--- DEBUG: recommend-server has no schedule. Deletion logic is now commented out. ---");
          // deselectSchedule();
        }
      } else {
        console.log("--- DEBUG: Valkey status is false. Deletion logic is now commented out. ---");
        deselectSchedule();
      }
    } catch (error) {
      console.error("--- DEBUG: Error during loadSelectedSchedule. Deletion logic is now commented out. ---", error);
      // deselectSchedule();
    } finally {
      console.log("--- DEBUG: loadSelectedSchedule finished ---");
      setIsLoading(false);
    }
  }, [deselectSchedule]);

  const selectSchedule = async (scheduleId: string): Promise<Schedule | null> => {
    setIsProcessing(true);
    try {
      const response = await recommendApi.selectAndGetSummary(scheduleId);
      if (response && response.schedule) {
        const scheduleWithTimestamp = { schedule: response.schedule, timestamp: Date.now() };
        localStorage.setItem("scheduleSelected", JSON.stringify(scheduleWithTimestamp));
        setSelectedSchedule(response.schedule);
        return response.schedule;
      } else {
        console.error("selectAndGetSummary did not return a schedule.");
        deselectSchedule();
        return null;
      }
    } catch (error) {
      console.error("스케줄 선택 처리 실패:", error);
      alert("스케줄 선택 처리에 실패했습니다.");
      deselectSchedule();
      return null;
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
        deselectSchedule();
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
    loadSelectedSchedule,
  }
}