"use client"
import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import HomeScreen from "@/components/screens/HomeScreen"
import ScheduleSummaryScreen from "@/components/screens/ScheduleSummaryScreen"
import PWAInstaller from "@/components/PWAInstaller"

export default function HomePage() {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(true)
  const [isClient, setIsClient] = useState(false)
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [scheduleSelected, setScheduleSelected] = useState(false)

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
    const isSelected = localStorage.getItem('scheduleSelected') === 'true'
    setScheduleSelected(isSelected)

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
      {scheduleSelected ? (
        <ScheduleSummaryScreen />
      ) : (
        <HomeScreen />
      )}
      <PWAInstaller />
    </>
  )
}
