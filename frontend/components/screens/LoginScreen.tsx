"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import Logo from "@/components/common/Logo"

export default function LoginScreen() {
  const [isLoading, setIsLoading] = useState(false)

  const handleKakaoLogin = async () => {
    setIsLoading(true)
    try {
      const REST_API_KEY = process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID!
      const REDIRECT_URI = process.env.NEXT_PUBLIC_KAKAO_REDIRECT_URI!
      const KAKAO_AUTH_URL = `https://kauth.kakao.com/oauth/authorize?client_id=${REST_API_KEY}&redirect_uri=${REDIRECT_URI}&response_type=code`
  
      window.location.href = KAKAO_AUTH_URL
    } catch (error) {
      console.error("로그인 실패:", error)
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-16">
          <Logo size="large" />
          <p className="text-gray-600 mt-4">스케줄 기반 맛집 추천 서비스</p>
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
