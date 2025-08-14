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
  
  // useSchedule provides all necessary state
  const { selectedSchedule, isLoading, loadSelectedSchedule } = useSchedule();

  useEffect(() => {
    // This effect runs once on mount to confirm we are on the client
    setIsClient(true)
  }, [])

  useEffect(() => {
    if (!isClient) return

    const loginDone = sessionStorage.getItem("kakaoLoginDone")
    if (!loginDone) {
      router.push('/auth/login')
      return
    }

    // If the hook initializes with a schedule from localStorage,
    // we must verify its status with the server.
    if (selectedSchedule) {
      loadSelectedSchedule();
    }
    // No need for an else, as the initial state of the hook handles the no-selection case.

  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isClient, router]) // We only want this to run once when the client is ready

  // The initial render on the server or before the client is ready can be a loader
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

  // After the client is ready, we can check for login status
  const loginDone = sessionStorage.getItem("kakaoLoginDone")
  if (!loginDone) {
    return <LoginScreen />;
  }

  // Now, the main logic based on the hook's state
  return (
    <>
      {selectedSchedule ? <ScheduleSummaryScreen /> : <HomeScreen />}
      <PWAInstaller />
    </>
  )
}
