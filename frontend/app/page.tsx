"use client"
import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import HomeScreen from "@/components/screens/HomeScreen"
import ScheduleSummaryScreen from "@/components/screens/ScheduleSummaryScreen"
import PWAInstaller from "@/components/PWAInstaller"
import useSchedule from "@/hooks/useSchedule"

export default function HomePage() {
  const router = useRouter() // 기존 코드 유지
  const [isClient, setIsClient] = useState(false)
  const [isLoggedIn, setIsLoggedIn] = useState(false) // 로그인 상태 명시적 관리

  const { isSelected, isLoading, initializeHomepage } = useSchedule()

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return
    const loginDone = sessionStorage.getItem("kakaoLoginDone")

    if (loginDone) {
      setIsLoggedIn(true)
      console.log("로그인 확인됨, 스케줄 상태 초기화를 시작합니다.")
      // 로그인 확인된 후에만 스케줄 상태 동기화 시작
      initializeHomepage()
    } else {
      setIsLoggedIn(false)
      console.log("로그인되지 않음, 로그인 페이지로 이동합니다.")
      router.push('/auth/login');
    }
  }, [isClient, initializeHomepage])

  // 초기 클라이언트 확인 또는 로그인 이후 스케줄 로딩 중 로더 표시
  if (!isClient || (isLoggedIn && isLoading)) {
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
      {isSelected ? <ScheduleSummaryScreen /> : <HomeScreen />}
      <PWAInstaller />
    </>
  )
}
