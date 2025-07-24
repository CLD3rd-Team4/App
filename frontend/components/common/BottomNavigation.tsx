"use client"

import { useRouter } from "next/navigation"
import { Home, Calendar, Search, MapPin } from "lucide-react"

interface BottomNavigationProps {
  currentTab: "home" | "schedule" | "search" | "visited"
}

export default function BottomNavigation({ currentTab }: BottomNavigationProps) {
  const router = useRouter()

  const tabs = [
    { id: "home", label: "Home", icon: Home, path: "/" },
    { id: "schedule", label: "스케줄", icon: Calendar, path: "/schedule" },
    { id: "search", label: "추천결과", icon: Search, path: "/recommendations" },
    { id: "visited", label: "방문식당", icon: MapPin, path: "/visited" },
  ]

  return (
    <div className="bottom-nav">
      <div className="flex">
        {tabs.map((tab) => {
          const Icon = tab.icon
          const isActive = currentTab === tab.id

          return (
            <button
              key={tab.id}
              onClick={() => router.push(tab.path)}
              className={`flex-1 py-2 px-1 flex flex-col items-center justify-center ${
                isActive ? "text-blue-600" : "text-gray-500"
              }`}
            >
              <Icon className={`w-6 h-6 mb-1 ${isActive ? "text-blue-600" : "text-gray-500"}`} />
              <span className="text-xs">{tab.label}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
