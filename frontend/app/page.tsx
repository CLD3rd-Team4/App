"use client"
import { useEffect, useState } from "react"
import LoginScreen from "@/components/screens/LoginScreen"
import HomeScreen from "@/components/screens/HomeScreen"
import ScheduleSummaryScreen from "@/components/screens/ScheduleSummaryScreen"
import PWAInstaller from "@/components/PWAInstaller"
import useSchedule from "@/hooks/useSchedule" // useSchedule 훅 임포트

export default function HomePage() {
  const [isClient, setIsClient] = useState(false)
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  
  // useSchedule 훅을 사용하여 selectedSchedule 상태와 로딩 상태를 관리
  const { selectedSchedule, isLoading: isScheduleLoading, loadSelectedSchedule } = useSchedule();

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return

    const loginDone = sessionStorage.getItem("kakaoLoginDone")
    setIsLoggedIn(!!loginDone)

    // loadSelectedSchedule()를 호출하여 선택된 스케줄 로딩을 트리거
    loadSelectedSchedule(); 

  }, [isClient, loadSelectedSchedule]) // loadSelectedSchedule를 의존성 배열에 추가

  // 로컬 로딩 상태와 훅의 로딩 상태를 결합
  const isLoading = !isClient || isScheduleLoading;

  if (isLoading) { // 결합된 로딩 상태 사용
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
      {!isLoggedIn ? (
        <LoginScreen />
      ) : selectedSchedule ? ( // 훅에서 가져온 selectedSchedule 사용
        <ScheduleSummaryScreen />
      ) : (
        <HomeScreen />
      )}
      <PWAInstaller />
    </>
  )
}
