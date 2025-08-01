"use client"

import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import BottomNavigation from "@/components/common/BottomNavigation"

export default function HomeScreen() {
  const router = useRouter()

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="flex-1 content-with-bottom-nav flex flex-col items-center justify-center px-4">
        <div className="text-center mb-8">
          <div className="w-32 h-32 bg-blue-100 rounded-full flex items-center justify-center mb-6 mx-auto">
            <div className="text-4xl">👤</div>
          </div>
          <h2 className="text-xl font-medium text-gray-800 mb-2">사용할 스케줄을</h2>
          <h2 className="text-xl font-medium text-gray-800 mb-2">생성하거나</h2>
          <h2 className="text-xl font-medium text-gray-800">선택해주세요.</h2>
        </div>

        <Button
          onClick={() => router.push("/schedule/")}
          className="bg-blue-500 hover:bg-blue-600 text-white px-8 py-3 rounded-lg"
        >
          스케줄 보기
        </Button>
      </div>

      <BottomNavigation currentTab="home" />
    </div>
  )
}
