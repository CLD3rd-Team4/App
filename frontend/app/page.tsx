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

  const { isSelected, isLoading, initializeHomepage } = useSchedule();

  useEffect(() => {
    setIsClient(true);
    initializeHomepage();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!isClient) return;
    const loginDone = sessionStorage.getItem("kakaoLoginDone");
    if (!loginDone) {
      router.push('/auth/login');
    }
  }, [isClient, router]);

  // 초기 동기화 로딩 상태를 표시합니다.
  if (!isClient || isLoading) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">로딩 중...</p>
        </div>
      </div>
    );
  }

  const loginDone = sessionStorage.getItem("kakaoLoginDone");
  if (!loginDone) {
    return <LoginScreen />;
  }

  // 동기화가 끝난 후의 isSelected 상태에 따라 최종 화면을 보여줍니다.
  return (
    <>
      {isSelected ? <ScheduleSummaryScreen /> : <HomeScreen />}
      <PWAInstaller />
    </>
  );
}