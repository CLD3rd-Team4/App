"use client";

import { useState, useCallback } from "react";
import Logo from "@/components/common/Logo";

export default function LoginPage() {
  const [isLoading, setIsLoading] = useState(false);

  const handleKakaoLogin = useCallback(() => {
    setIsLoading(true);

    const REST_API_KEY = process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID;
    const REDIRECT_URI = process.env.NEXT_PUBLIC_KAKAO_REDIRECT_URI;
    if (!REST_API_KEY || !REDIRECT_URI) {
      console.error("Kakao env 누락", { REST_API_KEY, REDIRECT_URI });
      setIsLoading(false);
      return;
    }

    const url = new URL("https://kauth.kakao.com/oauth/authorize");
    url.searchParams.set("client_id", REST_API_KEY);
    url.searchParams.set("redirect_uri", REDIRECT_URI);
    url.searchParams.set("response_type", "code");

    window.location.assign(url.toString());
  }, []);

  return (
      <div className="min-h-screen bg-gray-100 flex flex-col items-center justify-center px-4">
        <div className="w-full max-w-sm">
          <div className="text-center mb-16">
            <Logo size="large" />
            <p className="text-gray-600 mt-4 text-lg">장거리 이동 스케줄</p>
            <p className="text-gray-600 text-lg">맞춤형 맛집 추천</p>
          </div>

          <button
              type="button"
              onClick={handleKakaoLogin}
              disabled={isLoading}
              aria-label="카카오로 로그인"
              className="relative block mx-auto w-[280px] sm:w-[320px] p-0 bg-transparent border-0 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-black/30"
          >
            <img
                src="/kakao_login_large_wide.png"
                alt="카카오 로그인"
                className="block w-full h-auto select-none"
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
  );
}