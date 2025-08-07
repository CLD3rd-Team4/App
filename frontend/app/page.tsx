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
      const cachedData = localStorage.getItem('selectedSchedule');
      if (cachedData) {
        const { schedule, expiresAt } = JSON.parse(cachedData);
        if (new Date().getTime() < expiresAt) {
          // 캐시가 유효하면 바로 사용
          selectSchedule(schedule as Schedule);
          setIsLoading(false);
          return;
        }
      }

      // 2. 캐시가 없거나 만료되었으면 DB에서 조회
      try {
        const response = await recommendApi.getActiveScheduleSummary();
        if (response && response.schedule) {
          const schedule = response.schedule as Schedule;
          selectSchedule(schedule);
          // DB에서 가져온 정보를 다시 캐시
          const expiryTime = new Date().getTime() + 24 * 60 * 60 * 1000;
          localStorage.setItem('selectedSchedule', JSON.stringify({ schedule, expiresAt: expiryTime }));
        }
      } catch (error) {
        console.error("활성화된 스케줄 요약 정보 로드 실패:", error);
        localStorage.removeItem('selectedSchedule'); // 오류 발생 시 캐시 제거
      }
      
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
