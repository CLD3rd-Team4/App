"use client"

import { useState, useEffect, useCallback } from "react"
import { scheduleApi } from "@/services/api"
import type { Schedule, Restaurant } from "@/types"

export function useSchedule() {
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const initFromLocalStorage = useCallback(() => {
    setIsLoading(true);
    try {
      const savedSchedule = localStorage.getItem("selectedSchedule");
      if (savedSchedule) {
        selectSchedule(JSON.parse(savedSchedule));
      }
      const savedSchedules = localStorage.getItem("schedules");
      if (savedSchedules) {
        setSchedules(JSON.parse(savedSchedules));
      }
    } catch (error) {
      console.error("로컬 스토리지 파싱/접근 실패:", error);
      // 문제가 있는 데이터는 지웁니다.
      localStorage.removeItem("selectedSchedule");
      localStorage.removeItem("schedules");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    initFromLocalStorage();
  }, [initFromLocalStorage]);

  const saveSchedulesToStorage = (scheduleList: Schedule[]) => {
    localStorage.setItem("schedules", JSON.stringify(scheduleList))
    setSchedules(scheduleList)
  }

  const loadSchedules = async (userId: string) => {
    try {
      const data = await scheduleApi.getSchedules(userId)
      saveSchedulesToStorage(data)
    } catch (error) {
      console.error("스케줄 목록 로드 실패:", error)
      throw error
    }
  }

  const createSchedule = async (scheduleData: Omit<Schedule, "id">) => {
    try {
      const newSchedule = await scheduleApi.createSchedule(scheduleData)
      const updatedSchedules = [...schedules, newSchedule]
      setSchedules(updatedSchedules)
      localStorage.setItem("schedules", JSON.stringify(updatedSchedules))
      return newSchedule
    } catch (error) {
      console.error("스케줄 생성 실패:", error)
      throw error
    }
  }

  const updateSchedule = async (scheduleData: Schedule) => {
    try {
      await scheduleApi.updateSchedule(scheduleData);
      await loadSchedules(scheduleData.userId); 

      if (selectedSchedule?.id === scheduleData.id) {
        const updatedSelected = await scheduleApi.getScheduleDetail(scheduleData.id, scheduleData.userId);
        if (updatedSelected.schedule) {
          // selectSchedule을 호출하여 파싱 로직을 재사용합니다.
          selectSchedule({ ...updatedSelected.schedule, id: updatedSelected.schedule.scheduleId || scheduleData.id });
        }
      }
    } catch (error) {
      console.error("스케줄 수정 실패:", error);
      throw error;
    }
  };

  const deleteSchedule = async (scheduleId: string, userId: string) => {
    try {
      await scheduleApi.deleteSchedule(scheduleId, userId)
      const updatedSchedules = schedules.filter((schedule) => schedule.id !== scheduleId)
      setSchedules(updatedSchedules)
      localStorage.setItem("schedules", JSON.stringify(updatedSchedules))

      if (selectedSchedule?.id === scheduleId) {
        setSelectedSchedule(null)
        localStorage.removeItem("selectedSchedule")
      }
    } catch (error) {
      console.error("스케줄 삭제 실패:", error)
      throw error
    }
  }

  const selectSchedule = (schedule: Schedule | null) => {
    if (schedule) {
      const parsedSchedule = {
        ...schedule,
        departure: typeof schedule.departure === 'string' ? JSON.parse(schedule.departure) : schedule.departure,
        destination: typeof schedule.destination === 'string' ? JSON.parse(schedule.destination) : schedule.destination,
        waypoints: typeof schedule.waypoints === 'string' ? JSON.parse(schedule.waypoints) : schedule.waypoints,
      };

      const scheduleWithMealTimes = {
        ...parsedSchedule,
        targetMealTimes: parsedSchedule.targetMealTimes || [
          { type: "식사" as const, time: "12:00" },
          { type: "간식" as const, time: "15:00" },
          { type: "식사" as const, time: "18:00" },
        ],
      }
      setSelectedSchedule(scheduleWithMealTimes)
      localStorage.setItem("selectedSchedule", JSON.stringify(scheduleWithMealTimes))
    } else {
      setSelectedSchedule(null);
      localStorage.removeItem("selectedSchedule");
    }
  }
  
  const updateSelectedRestaurant = (restaurant: Restaurant) => {
    if (selectedSchedule) {
      const updatedSchedule = {
        ...selectedSchedule,
        selectedRestaurant: restaurant,
      }
      setSelectedSchedule(updatedSchedule)
      localStorage.setItem("selectedSchedule", JSON.stringify(updatedSchedule))
    }
  }

  return {
    schedules,
    selectedSchedule,
    isLoading,
    loadSchedules,
    createSchedule,
    updateSchedule,
    deleteSchedule,
    selectSchedule,
    updateSelectedRestaurant,
  }
}
