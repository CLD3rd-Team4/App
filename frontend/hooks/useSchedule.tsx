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
      const ls = readLastSubmit()

      // 1) 로컬 TTL 유효 → 무조건 선택 유지 (서버 실패해도 해제 X)
      if (ls?.isSelected && ls.scheduleId && typeof ls.expiryAt === "number" && Date.now() <= ls.expiryAt) {
        console.log("[useSchedule] 로컬 선택 유지:", ls.scheduleId, "→ 요약 불러오기(실패해도 유지)")
        setIsSelected(true)

        try {
          const resp = await recommendApi.getActiveScheduleSummary(ls.scheduleId)
          const schedule = pickSchedule(resp)
          if (schedule) setSelectedSchedule(schedule)
          else console.warn("[useSchedule] 로컬 복구: 요약 없음(선택은 유지)")
        } catch (err) {
          console.warn("[useSchedule] 로컬 복구 실패(선택은 유지):", err)
        }

        setIsLoading(false)
        return
      }

      // 2) 로컬 없으면 서버 선택 상태 확인(보조)
      console.log("[useSchedule] 서버 선택 상태 확인")
      const { isSelected: serverIsSelected, scheduleId } = await scheduleApi.getSelectionStatus()
      if (serverIsSelected && scheduleId) {
        console.log("[useSchedule] 서버 선택 존재:", scheduleId, "→ 요약 불러오기(실패해도 유지)")
        setIsSelected(true)

        try {
          const resp = await recommendApi.getActiveScheduleSummary(scheduleId)
          const schedule = pickSchedule(resp)
          if (schedule) setSelectedSchedule(schedule)
          else console.warn("[useSchedule] 서버 복구: 요약 없음(선택은 유지)")
        } catch (err) {
          console.warn("[useSchedule] 서버 복구 실패(선택은 유지):", err)
        }

        // 로컬에도 동기화(오늘 밤까지)
        writeLastSubmit({
          scheduleId,
          isSelected: true,
          submittedAt: new Date().toISOString(),
          expiryAt: endOfTodayTs(),
        })
        setIsLoading(false)
        return
      }

      // 3) 정말 아무 것도 없으면만 선택 해제
      console.log("[useSchedule] 복구 불가 → 선택 상태 초기화")
      await deselectAndClear()
    } catch (error) {
      console.error("홈 초기화 에러 → 선택 상태 유지 시도 후 필요 시 초기화", error)
      // 필요 시만 해제. 기본은 유지 권장.
      // await deselectAndClear()
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
