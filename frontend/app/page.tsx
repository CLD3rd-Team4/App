"use client"

import { useEffect, useState } from "react"
import LoginScreen from "@/components/screens/LoginScreen"
import HomeScreen from "@/components/screens/HomeScreen"
import ScheduleSummaryScreen from "@/components/screens/ScheduleSummaryScreen"
import PWAInstaller from "@/components/PWAInstaller"

export default function HomePage() {
  const [isLoading, setIsLoading] = useState(true)
  const [isClient, setIsClient] = useState(false)
  const [scheduleSelected, setScheduleSelected] = useState(false)
  const isLoggedIn = sessionStorage.getItem("kakaoLoginDone")

  useEffect(() => {
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return

    const checkLoginAndSchedule = () => {
      if (!isLoggedIn) {
        setIsLoading(false)
        return
      }

      const isSelected = localStorage.getItem('scheduleSelected') === 'true';
      setScheduleSelected(isSelected);
      setIsLoading(false)
    }

    checkLoginAndSchedule()
  }, [isClient,isLoggedIn])

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
      {!isLoggedIn ? (
        <LoginScreen />
      ) : scheduleSelected ? (
        <ScheduleSummaryScreen />
      ) : (
        <HomeScreen />
      )}
      <PWAInstaller />
    </>
  )
}