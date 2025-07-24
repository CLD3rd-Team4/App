"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import Logo from "@/components/common/Logo"

export default function LoginPage() {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)

  const handleKakaoLogin = async () => {
    setIsLoading(true)
    try {
      // TODO: 실제 카카오 OAuth 연동
      // 현재는 더미 로그인 처리
      await new Promise((resolve) => setTimeout(resolve, 1000))

      // 로그인 상태 저장
      localStorage.setItem("isLoggedIn", "true")
      localStorage.setItem(
        "user",
        JSON.stringify({
          id: "1",
          name: "테스트 사용자",
          email: "test@kakao.com",
        }),
      )

      router.push("/")
    } catch (error) {
      console.error("로그인 실패:", error)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-16">
          <Logo size="large" />
          <p className="text-gray-600 mt-4 text-lg">장거리 이동 스케줄</p>
          <p className="text-gray-600 text-lg">맞춤형 맛집 추천</p>
        </div>

        <Button
          onClick={handleKakaoLogin}
          disabled={isLoading}
          className="w-full bg-yellow-400 hover:bg-yellow-500 text-black font-medium py-4 rounded-lg"
        >
          {isLoading ? (
            <div className="flex items-center justify-center">
              <div className="w-5 h-5 border-2 border-black border-t-transparent rounded-full animate-spin mr-2"></div>
              로그인 중...
            </div>
          ) : (
            <>
              <span className="mr-2">🟡</span>
              Kakao로 시작하기
            </>
          )}
        </Button>
      </div>
    </div>
  )
}
