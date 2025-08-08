"use client"

import { useState, useEffect, useCallback } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi, APIError } from "@/services/api"
import type { Schedule } from "@/types"

export default function useSchedule() {
  const router = useRouter()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isProcessing, setIsProcessing] = useState(false)

  const loadSchedules = useCallback(async () => {
    try {
      setIsLoading(true)
      const data = await scheduleApi.getSchedules()
      setSchedules(data)
    } catch (error) {
      console.error("스케줄 목록 로드 실패:", error)
    } finally {
      setIsLoading(false)
    }
  }, [])

  const loadSelectedSchedule = useCallback(async () => {
    try {
      setIsLoading(true)
      const response = await recommendApi.getActiveScheduleSummary()
      if (response && response.schedule) {
        setSelectedSchedule(response.schedule)
      } else {
        setSelectedSchedule(null)
        localStorage.removeItem("scheduleSelected")
      }
    } catch (error) {
      console.error("선택된 스케줄 요약 로드 실패:", error)
      setSelectedSchedule(null)
      localStorage.removeItem("scheduleSelected")
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const scheduleSelected = localStorage.getItem("scheduleSelected") === "true"
    if (scheduleSelected) {
      loadSelectedSchedule()
    } else {
      setIsLoading(false)
    }
  }, [loadSelectedSchedule])

  const selectSchedule = async (scheduleId: string) => {
    setIsProcessing(true)
    try {
      await recommendApi.selectAndGetSummary(scheduleId)
      localStorage.setItem("scheduleSelected", "true")
      router.push("/")
    } catch (error) {
      console.error("스케줄 선택 및 처리 실패:", error)
      alert("스케줄 처리에 실패했습니다. 잠시 후 다시 시도해주세요.")
    } finally {
      setIsProcessing(false)
    }
  }

  const deselectSchedule = () => {
    setSelectedSchedule(null)
    localStorage.removeItem("scheduleSelected")
    router.push("/")
    router.refresh()
  }

  const createSchedule = async (scheduleData: Omit<Schedule, "id">) => {
    setIsProcessing(true)
    try {
      const newSchedule = await scheduleApi.createSchedule(scheduleData)
      setSchedules((prev) => [...prev, newSchedule])
      router.push("/schedule")
    } catch (error) {
      console.error("스케줄 생성 실패:", error)
      throw error
    } finally {
      setIsProcessing(false)
    }
  }

  const updateSchedule = async (scheduleId: string) => {
    setIsProcessing(true)
    try {
      // This is a placeholder for the actual update logic.
      // You might need to fetch current location and pass it to the API.
      await scheduleApi.processSchedule(scheduleId, { type: "UPDATE" })
      alert("스케줄이 업데이트되었습니다.")
      // Reload the summary
      await loadSelectedSchedule()
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
        deselectSchedule()
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
  }
}
