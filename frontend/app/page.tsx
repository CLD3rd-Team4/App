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
  const [isLoggedIn, setIsLoggedIn] = useState(false); // 로그인 상태를 명시적으로 관리

  const { isSelected, isLoading, initializeHomepage } = useSchedule();

  useEffect(() => {
    setIsClient(true);
  }, []);

  useEffect(() => {
    if (isClient) {
      const loginDone = sessionStorage.getItem("kakaoLoginDone");
      if (loginDone) {
        setIsLoggedIn(true);
        // 로그인이 확인된 후에만 스케줄 상태 동기화를 시작합니다.
        console.log("로그인 확인됨, 스케줄 상태 초기화를 시작합니다.");
        initializeHomepage();
      } else {
        console.log("로그인되지 않음, 로그인 페이지로 이동합니다.");
        router.push('/auth/login');
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isClient, router]); // initializeHomepage는 useCallback이므로 의존성에 추가해도 안전합니다.

  // 초기 클라이언트 확인 또는 데이터 로딩 중일 때 로더 표시
  if (!isClient || (isLoggedIn && isLoading)) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">로딩 중...</p>
        </div>
      </div>
    );
  }

  // 위 useEffect에서 리디렉션이 발생하지만, 만약을 위한 최종 분기
  if (!isLoggedIn) {
    return <LoginScreen />;
  }

  return (
    <>
      {isSelected ? <ScheduleSummaryScreen /> : <HomeScreen />}
      <PWAInstaller />
    </>
  );
}