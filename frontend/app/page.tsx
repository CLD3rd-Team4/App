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
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  
  const { selectedSchedule, isLoading: isScheduleLoading, loadSelectedSchedule } = useSchedule();

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return

    const loginDone = sessionStorage.getItem("kakaoLoginDone")
    if (!loginDone) {
      router.push('/auth/login')
      return
    }
    
    setIsLoggedIn(true)
    loadSelectedSchedule(); 

  }, [isClient, loadSelectedSchedule, router])

  const isLoading = !isClient || isScheduleLoading;

  if (isLoading) {
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
      ) : selectedSchedule ? (
        <ScheduleSummaryScreen />
      ) : (
        <HomeScreen />
      )}
      <PWAInstaller />
    </>
  )
}