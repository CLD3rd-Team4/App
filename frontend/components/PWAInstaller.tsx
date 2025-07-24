"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Download, X } from "lucide-react"

export default function PWAInstaller() {
  const [deferredPrompt, setDeferredPrompt] = useState<any>(null)
  const [showInstallPrompt, setShowInstallPrompt] = useState(false)

  useEffect(() => {
    const handler = (e: Event) => {
      e.preventDefault()
      setDeferredPrompt(e)
      setShowInstallPrompt(true)
    }

    window.addEventListener("beforeinstallprompt", handler)

    // 서비스 워커 등록
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker
        .register("/sw.js")
        .then((registration) => {
          console.log("SW registered: ", registration)
        })
        .catch((registrationError) => {
          console.log("SW registration failed: ", registrationError)
        })
    }

    return () => {
      window.removeEventListener("beforeinstallprompt", handler)
    }
  }, [])

  const handleInstall = async () => {
    if (!deferredPrompt) return

    deferredPrompt.prompt()
    const { outcome } = await deferredPrompt.userChoice

    if (outcome === "accepted") {
      setDeferredPrompt(null)
      setShowInstallPrompt(false)
    }
  }

  const handleDismiss = () => {
    setShowInstallPrompt(false)
    setDeferredPrompt(null)
  }

  if (!showInstallPrompt) return null

  return (
    <div className="fixed bottom-20 left-4 right-4 bg-white rounded-lg shadow-lg border p-4 z-50">
      <div className="flex items-start justify-between mb-3">
        <div>
          <h3 className="font-medium text-gray-900">앱으로 설치하기</h3>
          <p className="text-sm text-gray-600">홈 화면에 추가하여 더 편리하게 사용하세요</p>
        </div>
        <Button onClick={handleDismiss} variant="ghost" size="sm" className="p-1">
          <X className="w-4 h-4" />
        </Button>
      </div>
      <div className="flex gap-2">
        <Button onClick={handleDismiss} variant="outline" size="sm" className="flex-1 bg-transparent">
          나중에
        </Button>
        <Button
          onClick={handleInstall}
          size="sm"
          className="flex-1 bg-blue-500 hover:bg-blue-600 text-white flex items-center gap-2"
        >
          <Download className="w-4 h-4" />
          설치
        </Button>
      </div>
    </div>
  )
}
