"use client"
import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import LoginScreen from "@/components/screens/LoginScreen"
import HomeScreen from "@/components/screens/HomeScreen"
import ScheduleSummaryScreen from "@/components/screens/ScheduleSummaryScreen"
import PWAInstaller from "@/components/PWAInstaller"

// 이 함수는 클라이언트 사이드에서만 실행되어야 합니다.
const getInitialSelectionStatus = (): boolean => {
  if (typeof window === "undefined") {
    return false;
  }
  try {
    const item = localStorage.getItem("scheduleSelected");
    if (!item) return false;
    const parsed = JSON.parse(item);
    const isExpired = Date.now() - parsed.timestamp > 24 * 60 * 60 * 1000;
    return parsed.value === true && !isExpired;
  } catch (e) {
    return false;
  }
};

export default function HomePage() {
  const router = useRouter()
  const [isClient, setIsClient] = useState(false)
  // page.tsx는 동기적인 상태값만 관리합니다.
  const [isSelected, setIsSelected] = useState(false)

  useEffect(() => {
    setIsClient(true)
    // 클라이언트에서만 실행, localStorage를 읽어 초기 상태 결정
    setIsSelected(getInitialSelectionStatus());
  }, [])

  useEffect(() => {
    if (!isClient) return
    const loginDone = sessionStorage.getItem("kakaoLoginDone")
    if (!loginDone) {
      router.push('/auth/login')
    }
  }, [isClient, router])

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

  const loginDone = sessionStorage.getItem("kakaoLoginDone")
  if (!loginDone) {
    return <LoginScreen />;
  }

  return (
    <>
      {isSelected ? <ScheduleSummaryScreen /> : <HomeScreen />}
      <PWAInstaller />
    </>
  )
}
