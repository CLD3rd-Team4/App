"use client"

import { useEffect, useState } from "react"
import LoginScreen from "@/components/screens/LoginScreen"
import HomeScreen from "@/components/screens/HomeScreen"
import ScheduleSummaryScreen from "@/components/screens/ScheduleSummaryScreen"
import PWAInstaller from "@/components/PWAInstaller"
import { useAuth } from "@/hooks/useAuth"
import { useSchedule } from "@/hooks/useSchedule"
import { useRouter } from "next/navigation"

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

      // 로컬 스토리지에서 선택된 스케줄 확인
      const storedSchedule = localStorage.getItem('selectedSchedule');
      if (storedSchedule) {
        const { id, expiresAt } = JSON.parse(storedSchedule);
        if (new Date().getTime() < expiresAt) {
          // 만료되지 않았다면, 전체 스케줄 목록을 로드하고 해당 스케줄을 선택 상태로 설정
          if (schedules.length === 0) {
            await loadSchedules("test-user-123"); // 사용자 ID는 임시값 사용
          }
          const scheduleToSelect = schedules.find(s => s.id === id);
          if (scheduleToSelect) {
            selectSchedule(scheduleToSelect);
          }
        } else {
          // 만료되었다면 로컬 스토리지에서 삭제
          localStorage.removeItem('selectedSchedule');
        }
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
