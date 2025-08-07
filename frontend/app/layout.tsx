import type React from "react"
import type { Metadata, Viewport } from "next"
import { Inter } from "next/font/google"
import Script from "next/script" // <--- Script 임포트 추가
import "./globals.css"

const inter = Inter({ subsets: ["latin"] })

export const metadata: Metadata = {
  title: "map.zip - 스케줄 기반 맛집 추천 서비스",
  description: "스케줄 기반 맛집 추천 서비스",
  manifest: "/manifest.json",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "map.zip",
  },
    generator: 'v0.dev'
}

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  themeColor: "#3b82f6",
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ko">
      <head>
        <link rel="apple-touch-icon" href="/icon-192x192.png" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="default" />
        <meta name="apple-mobile-web-app-title" content="map.zip" />
      </head>
      <body className={inter.className}>
        {/* 카카오 지도 API 스크립트를 body 태그 하단으로 이동하고 Script 컴포언트 사용 */}
        <Script 
          type="text/javascript" 
          src={`//dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.NEXT_PUBLIC_KAKAO_MAP_KEY}&libraries=services`}
        />
        <div className="min-h-screen bg-gray-100">
          <div className="mobile-container">{children}</div>
        </div>
      </body>
    </html>
  )
}
