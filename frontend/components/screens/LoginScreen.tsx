"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/hooks/useAuth"
import Logo from "@/components/common/Logo"

export default function LoginScreen() {
  const { login } = useAuth()
  const [isLoading, setIsLoading] = useState(false)

  const handleKakaoLogin = async () => {
    setIsLoading(true)
    try {
      // TODO: REST API 연동 - Kakao 로그인
      await login("kakao")
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
          <p className="text-gray-600 mt-4">부제 적성</p>
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
              <span className="mr-2">💬</span>
              Kakao 시작하기
            </>
          )}
        </Button>
      </div>
    </div>
  )
}
