"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useRouter } from "next/navigation"
import { Search } from "lucide-react"
import BottomNavigation from "@/components/common/BottomNavigation"

export default function SearchPage() {
  const router = useRouter()
  const [searchQuery, setSearchQuery] = useState("")

  const handleSearch = () => {
    if (searchQuery.trim()) {
      // TODO: 검색 기능 구현
      console.log("검색:", searchQuery)
    }
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm">
        <h1 className="text-lg font-medium">추천 검색</h1>
      </div>

      <div className="flex-1 content-with-bottom-nav">
        <div className="p-4">
          <div className="bg-white p-4 rounded-lg shadow-sm mb-4">
            <div className="flex gap-2">
              <Input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="장소나 음식점을 검색하세요"
                className="flex-1"
                onKeyPress={(e) => e.key === "Enter" && handleSearch()}
              />
              <Button onClick={handleSearch} className="bg-blue-500 hover:bg-blue-600 text-white">
                <Search className="w-4 h-4" />
              </Button>
            </div>
          </div>

          <div className="text-center py-8">
            <p className="text-gray-600">검색어를 입력해주세요</p>
          </div>
        </div>
      </div>

      <BottomNavigation currentTab="search" />
    </div>
  )
}
