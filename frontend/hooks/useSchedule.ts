"use client"

import { useState, useEffect } from "react"
import { scheduleApi } from "@/services/api"
import type { Schedule, Restaurant } from "@/types"

export function useSchedule() {
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null)

  useEffect(() => {
    // 로컬 스토리지에서 선택된 스케줄 확인
    const savedSchedule = localStorage.getItem("selectedSchedule")
    if (savedSchedule) {
      try {
        const scheduleData = JSON.parse(savedSchedule)
        setSelectedSchedule(scheduleData)
      } catch (error) {
        console.error("스케줄 정보 파싱 실패:", error)
      }
    }

    // 로컬 스토리지에서 스케줄 목록 확인
    const savedSchedules = localStorage.getItem("schedules")
    if (savedSchedules) {
      try {
        const schedulesData = JSON.parse(savedSchedules)
        setSchedules(schedulesData)
      } catch (error) {
        console.error("스케줄 목록 파싱 실패:", error)
      }
    }
  }, [])

  const saveSchedulesToStorage = (scheduleList: Schedule[]) => {
    localStorage.setItem("schedules", JSON.stringify(scheduleList))
    setSchedules(scheduleList)
  }

  const loadSchedules = async () => {
    try {
      // TODO: REST API 연동 - 스케줄 목록 가져오기
      const data = await scheduleApi.getSchedules()
      saveSchedulesToStorage(data)
    } catch (error) {
      console.error("스케줄 목록 로드 실패:", error)
      throw error
    }
  }

  const createSchedule = async (scheduleData: Omit<Schedule, "id">) => {
    try {
      // TODO: REST API 연동 - 스케줄 생성
      const newSchedule = await scheduleApi.createSchedule(scheduleData)
      const updatedSchedules = [...schedules, newSchedule]
      setSchedules(updatedSchedules)
      localStorage.setItem("schedules", JSON.stringify(updatedSchedules))

      // 자동 선택 제거 - 스케줄 목록에만 추가
      // setSelectedSchedule(newSchedule)
      // localStorage.setItem("selectedSchedule", JSON.stringify(newSchedule))

      return newSchedule
    } catch (error) {
      console.error("스케줄 생성 실패:", error)
      throw error
    }
  }

  const updateSchedule = async (scheduleData: Schedule) => {
    try {
      // TODO: REST API 연동 - 스케줄 수정
      await scheduleApi.updateSchedule(scheduleData)
      const updatedSchedules = schedules.map((s) => (s.id === scheduleData.id ? scheduleData : s))
      saveSchedulesToStorage(updatedSchedules)

      // 선택된 스케줄이 수정된 스케줄이면 업데이트
      if (selectedSchedule?.id === scheduleData.id) {
        setSelectedSchedule(scheduleData)
        localStorage.setItem("selectedSchedule", JSON.stringify(scheduleData))
      }
    } catch (error) {
      console.error("스케줄 수정 실패:", error)
      throw error
    }
  }

  const deleteSchedule = async (scheduleId: string) => {
    try {
      // TODO: REST API 연동 - 스케줄 삭제
      await scheduleApi.deleteSchedule(scheduleId)
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

  const selectSchedule = (schedule: Schedule) => {
    // 목데이터로 targetMealTimes가 없는 경우 기본값 추가
    const scheduleWithMealTimes = {
      ...schedule,
      targetMealTimes: schedule.targetMealTimes || [
        { type: "식사" as const, time: "12:00" },
        { type: "간식" as const, time: "15:00" },
        { type: "식사" as const, time: "18:00" },
      ],
    }

    setSelectedSchedule(scheduleWithMealTimes)
    localStorage.setItem("selectedSchedule", JSON.stringify(scheduleWithMealTimes))
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
    loadSchedules,
    createSchedule,
    updateSchedule,
    deleteSchedule,
    selectSchedule,
    updateSelectedRestaurant,
  }
}
