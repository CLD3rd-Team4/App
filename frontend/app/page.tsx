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
  const { selectedSchedule } = useSchedule()
  const [isLoading, setIsLoading] = useState(true)
  const [isClient, setIsClient] = useState(false)
  const router = useRouter()

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return

    // 로그인 상태 체크
    const isLoggedIn = localStorage.getItem("isLoggedIn")
    if (!isLoggedIn) {
      router.push("/login/")
      return
    }

    // 앱 초기화
    setIsLoading(false)
  }, [isClient, router])

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
