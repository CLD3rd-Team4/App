// src/hooks/useSchedule.tsx
"use client"

import { createContext, useContext, useState, useCallback, ReactNode } from "react"
import { useRouter } from "next/navigation"
import { scheduleApi, recommendApi } from "@/services/api"
import type { Schedule, SchedulePayload } from "@/types"
import api from "@/lib/interceptor"

// =======================
// 로컬 키/타입 & 헬퍼들
// =======================
const LS_KEY_LAST_SUBMIT = "recommend:lastSubmit"

type LastSubmit = {
  scheduleId?: string
  submittedAt?: string
  selectedPlaces?: any
  isSelected?: boolean
  expiryAt?: number // 오늘 밤 23:59:59.999
}

function endOfTodayTs() {
  const d = new Date()
  d.setHours(23, 59, 59, 999)
  return d.getTime()
}

function readLastSubmit(): LastSubmit | null {
  if (typeof window === "undefined") return null
  try {
    const raw = localStorage.getItem(LS_KEY_LAST_SUBMIT)
    return raw ? JSON.parse(raw) : null
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

// 서버 응답에서 schedule 꺼내기(안전)
const pickSchedule = (resp: any): Schedule | null =>
  resp?.schedule ?? resp?.data?.schedule ?? (resp?.id ? resp : null)

// =======================
// 컨텍스트 타입
// =======================
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

// =======================
// 컨텍스트 생성
// =======================
const ScheduleContext = createContext<ScheduleContextType | undefined>(undefined)

// =======================
// Provider
// =======================
export function ScheduleProvider({ children }: { children: ReactNode }) {
  const router = useRouter()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null)
  const [isProcessing, setIsProcessing] = useState(false)

  // 초기 isSelected: 오로지 로컬 스토리지 기반
  const getInitialSelectionStatus = useCallback((): boolean => {
    if (typeof window === "undefined") return false
    const ls = readLastSubmit()
    const ok = !!(ls?.isSelected && ls?.scheduleId && typeof ls?.expiryAt === "number" && Date.now() <= ls.expiryAt)
    console.log("[useSchedule] init isSelected:", ok, "payload:", ls)
    return ok
  }, [])

  const [isSelected, setIsSelected] = useState<boolean>(getInitialSelectionStatus())
  const [isLoading, setIsLoading] = useState(true)

  // 명시 해제만 서버/로컬 동시 해제
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

  // =======================
  // 새로고침(홈 진입) 복구 로직
  // =======================
  const initializeHomepage = useCallback(async () => {
  setIsLoading(true)
  try {
    console.log("[useSchedule] initializeHomepage: 로컬 recent submit 확인")
    const ls = readLastSubmit() // 반드시 'recommendations:lastSubmit' 키를 읽도록 통일

    // 1) 로컬 TTL 유효 → 무조건 선택 유지 (어떠한 서버 요청도 하지 않음)
    if (ls?.isSelected && ls.scheduleId && typeof ls.expiryAt === "number" && Date.now() <= ls.expiryAt) {
      console.log("[useSchedule] 로컬 선택 유지:", ls.scheduleId, "(서버 호출 없음)")
      setIsSelected(true)
      return
    }

    // 2) 로컬이 없거나 만료 → 선택 해제 & 정리 (서버 확인도 하지 않음)
    console.log("[useSchedule] 로컬 없음/만료 → 선택 해제")
    await deselectAndClear()
  } catch (error) {
    console.error("[useSchedule] 홈 초기화 에러(로컬 전용 모드)", error)
    // 오류 시에도 서버 재시도는 하지 않음. 필요하면 최소 상태만 유지.
    await deselectAndClear()
  } finally {
    setIsLoading(false)
  }
}, [deselectAndClear])

  // =======================
  // 스케줄 선택/요약 가져오기
  // =======================
  const selectSchedule = useCallback(async (scheduleId: string): Promise<Schedule | null> => {
    setIsProcessing(true)
    try {
      console.log("[useSchedule] selectSchedule:", scheduleId)
      const response = await recommendApi.selectAndGetSummary(scheduleId)
      const schedule = pickSchedule(response)
      if (schedule) {
        writeLastSubmit({
          scheduleId,
          isSelected: true,
          submittedAt: new Date().toISOString(),
          expiryAt: endOfTodayTs(),
        })
        setSelectedSchedule(schedule)
        setIsSelected(true)
        console.log("[useSchedule] selectSchedule 완료:", schedule.title || scheduleId)
        return schedule
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

  // =======================
  // 추천 트리거 (원할 때만 사용)
  // =======================
  const triggerRecommendRequest = useCallback(async (scheduleId: string) => {
    try {
      console.log("[useSchedule] triggerRecommendRequest:", scheduleId)
      await api.post("/recommend/request", { scheduleId })
    } catch (e) {
      console.error("[useSchedule] triggerRecommendRequest 실패:", e)
    }
  }, [])

  // =======================
  // 스케줄 목록 CRUD (원본 유지)
  // =======================
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

  const deleteSchedule = async (scheduleId: string) => {
    setIsProcessing(true)
    try {
      await scheduleApi.deleteSchedule(scheduleId)
      setSchedules(s => s.filter(sch => sch.id !== scheduleId))
      if (selectedSchedule?.id === scheduleId) {
        await deselectAndClear()
      }
    } catch (e) {
      console.error(e)
    } finally {
      setIsProcessing(false)
    }
  }

  const deselectSchedule = useCallback(() => {
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

// =======================
// 커스텀 훅
// =======================
export default function useSchedule() {
  const context = useContext(ScheduleContext)
  if (context === undefined) {
    throw new Error("useSchedule must be used within a ScheduleProvider")
  }
  return context
}
