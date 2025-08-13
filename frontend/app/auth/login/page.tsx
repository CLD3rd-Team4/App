"use client"

import { useState } from "react"
import Logo from "@/components/common/Logo"

export default function LoginPage() {
  const [isLoading, setIsLoading] = useState(false)

  const handleKakaoLogin = () => {
    setIsLoading(true)
    const REST_API_KEY = process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID!
    const REDIRECT_URI = process.env.NEXT_PUBLIC_KAKAO_REDIRECT_URI!
    const KAKAO_AUTH_URL =
        `https://kauth.kakao.com/oauth/authorize?client_id=${REST_API_KEY}&redirect_uri=${REDIRECT_URI}&response_type=code`
    window.location.href = KAKAO_AUTH_URL
  }

  return (
      <div className="min-h-screen bg-gray-100 flex flex-col items-center justify-center px-4">
        <div className="w-full max-w-sm">
          <div className="text-center mb-16">
            <Logo size="large" />
            <p className="text-gray-600 mt-4 text-lg">장거리 이동 스케줄</p>
            <p className="text-gray-600 text-lg">맞춤형 맛집 추천</p>
          </div>

          {/* shadcn Button 대신 순수 button + 공식 이미지 */}
          <button
              type="button"
              onClick={handleKakaoLogin}
              disabled={isLoading}
              aria-label="카카오로 로그인"
              className="relative w-full p-0 h-auto bg-transparent border-0 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-black/30"
          >
            <img
                src="/kakao_login_large_wide.png"
                alt="카카오 로그인"
                className="w-full h-auto select-none"
                draggable={false}
            />
            {isLoading && (
                <div className="absolute inset-0 flex items-center justify-center bg-black/10 rounded-lg">
                  <div className="w-5 h-5 border-2 border-black border-t-transparent rounded-full animate-spin" />
                </div>
            )}
          </button>
        </div>
      </div>
  )
}
