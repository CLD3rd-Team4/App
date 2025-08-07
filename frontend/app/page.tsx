"use client"

import { useEffect, useState } from "react"
import LoginScreen from "@/components/screens/LoginScreen"
import HomeScreen from "@/components/screens/HomeScreen"
import ScheduleSummaryScreen from "@/components/screens/ScheduleSummaryScreen"
import PWAInstaller from "@/components/PWAInstaller"
import { useAuth } from "@/hooks/useAuth"
import { useSchedule } from "@/hooks/useSchedule"
import { recommendApi } from "@/services/api";
import { useRouter } from "next/navigation"
import type { Schedule } from "@/types"

export default function HomePage() {
  const { isAuthenticated, user } = useAuth()
  const { selectedSchedule, selectSchedule, schedules, loadSchedules } = useSchedule()
  const [isLoading, setIsLoading] = useState(true)
  const [isClient, setIsClient] = useState(false)
  const router = useRouter()

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return

    const checkLoginAndLoadSchedule = async () => {
      // 로그인 상태 체크
      const isLoggedIn = localStorage.getItem("isLoggedIn")
      if (!isLoggedIn) {
        router.push("/login/")
        return
      }

      // 1. 로컬 스토리지에서 캐시 확인
      const cachedData = localStorage.getItem('selectedScheduleId');
      if (cachedData) {
        const { id, expiresAt } = JSON.parse(cachedData);
        if (new Date().getTime() < expiresAt) {
          // 캐시가 유효하면 해당 ID로 요약 정보 요청
          try {
            const response = await recommendApi.getSummaryById(id);
            if (response && response.schedule) {
              selectSchedule(response.schedule as Schedule);
            }
          } catch (error) {
            console.error("캐시된 스케줄 요약 정보 로드 실패:", error);
            localStorage.removeItem('selectedScheduleId'); // 오류 발생 시 캐시 제거
          }
        } else {
          // 캐시가 만료되었으면 제거
          localStorage.removeItem('selectedScheduleId');
        }
      }
      
      // 캐시가 없거나 만료되었으면, 더 이상 선택된 스케줄이 없는 것으로 간주하고 추가 요청 없음
      setIsLoading(false)
    }

    checkLoginAndLoadSchedule();

  }, [isClient, router, selectSchedule, schedules, loadSchedules])

  if (!isClient || isLoading) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">로딩 중...</p>
        </div>
      </div>
    )
  }

  return (
    <>
      {!isAuthenticated ? <LoginScreen /> : selectedSchedule ? <ScheduleSummaryScreen /> : <HomeScreen />}
      <PWAInstaller />

      
    </>
  )
}
