"use client"
import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import LoginScreen from "@/components/screens/LoginScreen"
import HomeScreen from "@/components/screens/HomeScreen"
import ScheduleSummaryScreen from "@/components/screens/ScheduleSummaryScreen"
import PWAInstaller from "@/components/PWAInstaller"
import useSchedule from "@/hooks/useSchedule"

export default function HomePage() {
  const router = useRouter()
  const [isClient, setIsClient] = useState(false)

  // isSelected: 동기적으로 localStorage를 확인한 현재 선택 "상태"
  // isLoading: 비동기 데이터(스케줄 객체) 로딩 "과정"
  const { isSelected, isLoading, checkInitialSelection } = useSchedule();

  useEffect(() => {
    setIsClient(true)
    // HomePage가 마운트될 때만 초기 선택 상태를 확인하고 동기화합니다.
    checkInitialSelection();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!isClient) return

    const loginDone = sessionStorage.getItem("kakaoLoginDone")
    if (!loginDone) {
      router.push('/auth/login')
    }
  }, [isClient, router])

  // 클라이언트가 아니면 아무것도 렌더링하지 않거나 기본 로더를 보여줍니다.
  if (!isClient) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">로딩 중...</p>
        </div>
      </div>
    )
  }

  // 로그인 여부 확인
  const loginDone = sessionStorage.getItem("kakaoLoginDone")
  if (!loginDone) {
    return <LoginScreen />;
  }

  // isSelected 값에 따라 동기적으로 화면을 결정합니다.
  // 데이터 로딩(isLoading)은 ScheduleSummaryScreen 내부에서 처리됩니다.
  return (
    <>
      {isSelected ? <ScheduleSummaryScreen /> : <HomeScreen />}
      <PWAInstaller />
    </>
  )
}