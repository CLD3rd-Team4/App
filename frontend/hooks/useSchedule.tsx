// src/hooks/useSchedule.tsx
"use client"

import { createContext, useContext, useState, useCallback, ReactNode } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi } from "@/services/api"
import type { Schedule, SchedulePayload } from "@/types"
import api from "@/lib/interceptor"

// =====================
// LocalStorage (lastSubmit) with TTL (오늘 밤 만료)
// =====================
const LS_KEY_LAST_SUBMIT = "recommend:lastSubmit"

type LastSubmit = {
  scheduleId?: string
  submittedAt?: string
  selectedPlaces?: any
  isSelected?: boolean
  expiryAt?: number
}

function readLastSubmit(): LastSubmit | null {
  if (typeof window === "undefined") return null
  try {
    const raw = localStorage.getItem(LS_KEY_LAST_SUBMIT)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}
function writeLastSubmit(next: Partial<LastSubmit>) {
  if (typeof window === "undefined") return
  try {
    const prev = readLastSubmit() || {}
    localStorage.setItem(LS_KEY_LAST_SUBMIT, JSON.stringify({ ...prev, ...next }))
  } catch {}
}
function clearLastSubmit() {
  if (typeof window === "undefined") return
  try {
    localStorage.removeItem(LS_KEY_LAST_SUBMIT)
  } catch {}
}
function endOfTodayTs() {
  const d = new Date()
  d.setHours(23, 59, 59, 999)
  return d.getTime()
}

// =====================
// Context 타입
// =====================
interface ScheduleContextType {
  schedules: Schedule[]
  selectedSchedule: Schedule | null
  isLoading: boolean
  isProcessing: boolean
  isSelected: boolean
  loadSchedules: () => Promise<void>
  deselectSchedule: () => void
  createSchedule: (scheduleData: SchedulePayload) => Promise<void>
  updateSchedule: (scheduleId: string, scheduleData: SchedulePayload) => Promise<void>
  deleteSchedule: (scheduleId: string) => Promise<void>
  initializeHomepage: () => Promise<void>
  selectSchedule: (scheduleId: string) => Promise<Schedule | null>
  triggerRecommendRequest: (scheduleId: string) => Promise<void>
}

// =====================
// Context 생성
// =====================
const ScheduleContext = createContext<ScheduleContextType | undefined>(undefined)

// =====================
// Provider
// =====================
export function ScheduleProvider({ children }: { children: ReactNode }) {
  const router = useRouter()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null)
  const [isProcessing, setIsProcessing] = useState(false)
  const [isLoading, setIsLoading] = useState(true)

  // 초기 isSelected: lastSubmit + TTL 기준
  const getInitialSelectionStatus = useCallback((): boolean => {
    if (typeof window === "undefined") return false
    const ls = readLastSubmit()
    const ok =
      !!ls?.isSelected &&
      !!ls?.scheduleId &&
      typeof ls?.expiryAt === "number" &&
      Date.now() <= (ls.expiryAt as number)

    console.log("[useSchedule] init isSelected:", ok, "payload:", ls)
    return ok
  }, [])
  const [isSelected, setIsSelected] = useState<boolean>(getInitialSelectionStatus())

  // 공통 해제
  const deselectAndClear = useCallback(async () => {
    try {
      console.log("[useSchedule] deselectAndClear: 서버/로컬 선택 상태 해제")
      await scheduleApi.deselectSchedule().catch(() => {})
    } catch (error) {
      console.error("서버 선택 상태 해제 실패:", error)
    } finally {
      clearLastSubmit()
      setSelectedSchedule(null)
      setIsSelected(false)
    }
  }, [])

  // =====================
  // 홈 초기화 (로컬 우선 → 서버 보조)
  // =====================
  const initializeHomepage = useCallback(async () => {
    setIsLoading(true)
    try {
      console.log("[useSchedule] initializeHomepage: 로컬 recent submit 확인")
      const ls = readLastSubmit()

      // 1) 로컬 TTL 유효하면 그 schedule 요약 요청
      if (ls?.isSelected && ls?.scheduleId && typeof ls?.expiryAt === "number" && Date.now() <= ls.expiryAt) {
        console.log("[useSchedule] 로컬 선택 유지 스케줄:", ls.scheduleId, "→ 요약 불러오기")
        const summaryResponse = await recommendApi.getActiveScheduleSummary(ls.scheduleId)
        if (summaryResponse?.schedule) {
          setSelectedSchedule(summaryResponse.schedule)
          setIsSelected(true)
          setIsLoading(false)
          return
        } else {
          console.warn("[useSchedule] 로컬 복구 실패: 요약 없음")
        }
      } else if (ls?.expiryAt && Date.now() > ls.expiryAt) {
        console.log("[useSchedule] 로컬 TTL 만료")
      }

      // 2) 서버 상태 조회
      console.log("[useSchedule] 서버 선택 상태 확인")
      const { isSelected: serverIsSelected, scheduleId } = await scheduleApi.getSelectionStatus()
      if (serverIsSelected && scheduleId) {
        console.log("[useSchedule] 서버 선택 존재:", scheduleId, "→ 요약 불러오기")
        const summaryResponse = await recommendApi.getActiveScheduleSummary(scheduleId)
        if (summaryResponse?.schedule) {
          setSelectedSchedule(summaryResponse.schedule)
          setIsSelected(true)
          writeLastSubmit({
            scheduleId,
            isSelected: true,
            submittedAt: new Date().toISOString(),
            expiryAt: endOfTodayTs(),
          })
          setIsLoading(false)
          return
        }
      }

      // 3) 복구 불가 → 초기화
      console.log("[useSchedule] 복구 불가 → 선택 상태 초기화")
      await deselectAndClear()
    } catch (error) {
      console.error("홈페이지 초기화 중 에러 발생 → 선택 상태 초기화", error)
      await deselectAndClear()
    } finally {
      setIsLoading(false)
    }
  }, [deselectAndClear])

  // =====================
  // 스케줄 선택 (요약까지 받아서 상태/로컬 기록)
  // =====================
  const selectSchedule = useCallback(async (scheduleId: string): Promise<Schedule | null> => {
    setIsProcessing(true)
    try {
      console.log("[useSchedule] selectSchedule:", scheduleId)
      const response = await recommendApi.selectAndGetSummary(scheduleId)
      if (response?.schedule) {
        writeLastSubmit({
          scheduleId,
          isSelected: true,
          submittedAt: new Date().toISOString(),
          expiryAt: endOfTodayTs(),
        })
        setSelectedSchedule(response.schedule)
        setIsSelected(true)
        console.log("[useSchedule] selectSchedule 완료:", response.schedule?.title || scheduleId)
        return response.schedule
      } else {
        console.warn("[useSchedule] selectSchedule: 요약 없음 → 해제")
        await deselectAndClear()
        return null
      }
    } catch (error) {
      console.error("[useSchedule] selectSchedule 실패:", error)
      await deselectAndClear()
      throw error
    } finally {
      setIsProcessing(false)
    }
  }, [deselectAndClear])

  // =====================
  // 🔵 추천 트리거 
  // =====================
  const triggerRecommendRequest = useCallback(async (scheduleId: string) => {
    try {
      console.log("[useSchedule] triggerRecommendRequest:", scheduleId)
      await api.post("/recommend/request", { scheduleId })
    } catch (e) {
      console.error("[useSchedule] triggerRecommendRequest 실패:", e)
    }
  }, [])

  // =====================
  // 🔵 스케줄 CRUD 
  // =====================
  const loadSchedules = useCallback(async () => {
    setIsLoading(true)
    try {
      console.log("[useSchedule] loadSchedules 호출")
      const data = await scheduleApi.getSchedules()
      setSchedules(data)
      console.log("[useSchedule] 스케줄 개수:", data?.length ?? 0)
    } catch (e) {
      console.error("[useSchedule] loadSchedules 실패:", e)
      setSchedules([])
    } finally {
      setIsLoading(false)
    }
  }, [])

  const createSchedule = async (scheduleData: SchedulePayload) => {
    setIsProcessing(true)
    try {
      await scheduleApi.createSchedule(scheduleData)
      router.push("/schedule")
    } catch (e) {
      console.error(e)
    } finally {
      setIsProcessing(false)
    }
  }

  const updateSchedule = async (scheduleId: string, scheduleData: SchedulePayload) => {
    setIsProcessing(true)
    try {
      await scheduleApi.updateSchedule({ id: scheduleId, ...scheduleData })
      router.push("/schedule")
    } catch (e) {
      console.error(e)
    } finally {
      setIsProcessing(false)
    }
  }

  const deleteSchedule = useCallback(async (scheduleId: string) => {
    setIsProcessing(true)
    try {
      console.log("[useSchedule] deleteSchedule:", scheduleId)
      await scheduleApi.deleteSchedule(scheduleId)
      setSchedules(s => s.filter(sch => sch.id !== scheduleId))
      if (selectedSchedule?.id === scheduleId) {
        await deselectAndClear()
      }
    } catch (e) {
      console.error("[useSchedule] deleteSchedule 실패:", e)
    } finally {
      setIsProcessing(false)
    }
  }, [selectedSchedule, deselectAndClear])

  const deselectSchedule = useCallback(() => {
    // 로컬 플래그만 끄고, 서버/상태 초기화
    writeLastSubmit({ isSelected: false })
    deselectAndClear()
  }, [deselectAndClear])

  const value: ScheduleContextType = {
    schedules,
    selectedSchedule,
    isLoading,
    isProcessing,
    isSelected,
    loadSchedules,
    deselectSchedule,
    createSchedule,
    updateSchedule,
    deleteSchedule,
    initializeHomepage,
    selectSchedule,
    triggerRecommendRequest,
  }

  return <ScheduleContext.Provider value={value}>{children}</ScheduleContext.Provider>
}

// =====================
// Hook
// =====================
const useSchedule = () => {
  const context = useContext(ScheduleContext)
  if (context === undefined) {
    throw new Error("useSchedule must be used within a ScheduleProvider")
  }
  return context
}

export default useSchedule
