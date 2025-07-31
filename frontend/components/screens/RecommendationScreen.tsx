"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import { ArrowLeft, Star, ChevronDown, ChevronUp } from "lucide-react"
import { useSchedule } from "@/hooks/useSchedule"
import type { Restaurant } from "@/types"

interface MealSection {
  id: string
  title: string
  type: "식사" | "간식"
  index: number
  time: string
  restaurants: Restaurant[]
  previousSelection?: Restaurant
}

// 목데이터 - 백엔드 연동 전 테스트용
const MOCK_RESTAURANTS: Restaurant[] = [
  {
    id: "1",
    name: "맛있는 한식당",
    description: "전통 한식 전문점으로 정갈한 반찬과 깔끔한 맛",
    aiReason: "사용자의 비건 요구사항에 맞는 다양한 채식 메뉴 제공",
    rating: 4.2,
    distance: "1.2km",
    image: "/placeholder.svg?height=80&width=80",
  },
  {
    id: "2",
    name: "건강한 샐러드바",
    description: "신선한 채소와 건강식으로 유명한 샐러드 전문점",
    aiReason: "비건 친화적인 메뉴와 신선한 재료 사용",
    rating: 4.5,
    distance: "0.8km",
    image: "/placeholder.svg?height=80&width=80",
  },
  {
    id: "3",
    name: "이탈리안 파스타",
    description: "수제 파스타와 정통 이탈리아 요리 전문점",
    aiReason: "비건 파스타 옵션과 다양한 채식 메뉴 보유",
    rating: 4.0,
    distance: "2.1km",
    image: "/placeholder.svg?height=80&width=80",
  },
  {
    id: "4",
    name: "카페 브런치",
    description: "분위기 좋은 브런치 카페, 디저트와 커피가 맛있음",
    aiReason: "간식 시간에 적합한 비건 디저트와 음료 제공",
    rating: 4.3,
    distance: "1.5km",
    image: "/placeholder.svg?height=80&width=80",
  },
  {
    id: "5",
    name: "아시안 퓨전",
    description: "아시아 각국의 요리를 현대적으로 재해석한 퓨전 레스토랑",
    aiReason: "다양한 채식 아시아 요리와 건강한 재료 사용",
    rating: 4.1,
    distance: "1.8km",
    image: "/placeholder.svg?height=80&width=80",
  },
  {
    id: "6",
    name: "디저트 하우스",
    description: "수제 디저트와 케이크 전문점, 달콤한 간식의 천국",
    aiReason: "비건 디저트 옵션과 간식 시간에 완벽한 메뉴",
    rating: 4.4,
    distance: "1.0km",
    image: "/placeholder.svg?height=80&width=80",
  },
  {
    id: "7",
    name: "해산물 전문점",
    description: "신선한 해산물과 회를 전문으로 하는 레스토랑",
    aiReason: "지역 특산물을 활용한 신선한 해산물 메뉴",
    rating: 4.6,
    distance: "2.3km",
    image: "/placeholder.svg?height=80&width=80",
  },
  {
    id: "8",
    name: "고기구이 전문점",
    description: "숯불구이와 한우 전문점, 고품질 육류 제공",
    aiReason: "고품질 한우와 숯불구이의 깊은 맛",
    rating: 4.7,
    distance: "1.9km",
    image: "/placeholder.svg?height=80&width=80",
  },
  {
    id: "9",
    name: "중식당",
    description: "정통 중화요리와 딤섬을 맛볼 수 있는 중식당",
    aiReason: "다양한 중화요리와 합리적인 가격",
    rating: 4.2,
    distance: "1.4km",
    image: "/placeholder.svg?height=80&width=80",
  },
]

// 목데이터 - 이전 선택 테스트용
const MOCK_PREVIOUS_SELECTIONS = {
  "meal-1": {
    id: "prev-1",
    name: "이전에 선택한 한식당",
    description: "지난번에 선택했던 맛있는 한식당",
    aiReason: "이전 방문 기록을 바탕으로 한 추천",
    rating: 4.3,
    distance: "1.1km",
    image: "/placeholder.svg?height=80&width=80",
  },
  "snack-1": {
    id: "prev-2",
    name: "이전 선택 카페",
    description: "지난번에 선택했던 브런치 카페",
    aiReason: "이전 선택 기록을 바탕으로 한 추천",
    rating: 4.1,
    distance: "0.9km",
    image: "/placeholder.svg?height=80&width=80",
  },
}

export default function RecommendationScreen() {
  const router = useRouter()
  const { selectedSchedule, updateSelectedRestaurant } = useSchedule()
  const [mealSections, setMealSections] = useState<MealSection[]>([])
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set())
  const [selectedRestaurants, setSelectedRestaurants] = useState<{ [key: string]: Restaurant }>({})
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    loadRecommendations()
  }, [])

  const loadRecommendations = async () => {
    try {
      setIsLoading(true)

      // 목데이터 - 백엔드 연동 시 selectedSchedule.targetMealTimes 사용
      const targetMealTimes = [
        { type: "식사" as const, time: "12:00" },
        { type: "식사" as const, time: "18:00" },
        { type: "간식" as const, time: "15:00" },
      ]

      if (targetMealTimes.length === 0) {
        console.error("목표 식사 시간이 설정되지 않았습니다.")
        setIsLoading(false)
        return
      }

      // 목데이터 사용 - 백엔드 연동 시 주석 처리하고 아래 주석 해제
      const allRestaurants = MOCK_RESTAURANTS

      // 목표 식사 시간을 기반으로 섹션 생성
      const sections: MealSection[] = []

      // 식사와 간식을 시간순으로 정렬하여 섹션 생성
      const sortedMeals = targetMealTimes.sort((a, b) => a.time.localeCompare(b.time))

      let mealCount = 1
      let snackCount = 1

      sortedMeals.forEach((meal, index) => {
        // 각 섹션마다 다른 식당 3개씩 할당
        const startIndex = (index * 3) % allRestaurants.length
        const sectionRestaurants = [
          allRestaurants[startIndex],
          allRestaurants[(startIndex + 1) % allRestaurants.length],
          allRestaurants[(startIndex + 2) % allRestaurants.length],
        ]

        if (meal.type === "식사") {
          const sectionId = `meal-${mealCount}`
          sections.push({
            id: sectionId,
            title: `식사${mealCount}`,
            type: "식사",
            index: mealCount,
            time: meal.time,
            restaurants: sectionRestaurants,
            previousSelection: MOCK_PREVIOUS_SELECTIONS[sectionId as keyof typeof MOCK_PREVIOUS_SELECTIONS],
          })
          mealCount++
        } else {
          const sectionId = `snack-${snackCount}`
          sections.push({
            id: sectionId,
            title: `간식${snackCount}`,
            type: "간식",
            index: snackCount,
            time: meal.time,
            restaurants: sectionRestaurants,
            previousSelection: MOCK_PREVIOUS_SELECTIONS[sectionId as keyof typeof MOCK_PREVIOUS_SELECTIONS],
          })
          snackCount++
        }
      })

      setMealSections(sections)

      // 기본적으로 모든 섹션을 접힌 상태로 시작
      setExpandedSections(new Set())
    } catch (error) {
      console.error("추천 결과 로드 실패:", error)
    } finally {
      setIsLoading(false)
    }
  }

  const toggleSection = (sectionId: string) => {
    const newExpanded = new Set(expandedSections)
    if (newExpanded.has(sectionId)) {
      newExpanded.delete(sectionId)
    } else {
      newExpanded.add(sectionId)
    }
    setExpandedSections(newExpanded)
  }

  const handleRestaurantSelect = (sectionId: string, restaurant: Restaurant) => {
    setSelectedRestaurants((prev) => ({
      ...prev,
      [sectionId]: restaurant,
    }))
  }

  const formatTime = (time: string) => {
    const [hour, minute] = time.split(":")
    const h = Number.parseInt(hour)
    const period = h >= 12 ? "오후" : "오전"
    const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h
    return `${period} ${displayHour}:${minute}`
  }

  // 모든 섹션에서 선택이 완료되었는지 확인
  const isAllSectionsSelected = () => {
    return mealSections.every((section) => selectedRestaurants[section.id])
  }

  const handleComplete = () => {
    if (!isAllSectionsSelected()) {
      alert("모든 식사/간식 시간에 대해 식당을 선택해주세요.")
      return
    }

    const selectedList = Object.entries(selectedRestaurants).map(([sectionId, restaurant]) => ({
      sectionId,
      restaurant,
    }))

    // 목데이터 테스트용 - 로컬스토리지에 직접 저장
    const mockSchedule = {
      id: "mock-1",
      title: "테스트 스케줄",
      selectedRestaurants: selectedList,
      selectedRestaurant: selectedList[0].restaurant,
    }
    localStorage.setItem("selectedSchedule", JSON.stringify(mockSchedule))

    console.log("선택된 식당들:", selectedList) // 디버깅용
    router.push("/")
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center">
          <Button onClick={() => router.push("/")} variant="ghost" size="sm" className="mr-3">
            <ArrowLeft className="w-5 h-5" />
          </Button>
          <h1 className="text-lg font-medium">추천 결과</h1>
        </div>
        <Button
          onClick={handleComplete}
          disabled={!isAllSectionsSelected()}
          size="sm"
          className={`px-4 py-2 font-medium ${
            !isAllSectionsSelected()
              ? "bg-gray-300 text-gray-500 cursor-not-allowed"
              : "bg-blue-500 hover:bg-blue-600 text-white shadow-md"
          }`}
        >
          입력완료 ({Object.keys(selectedRestaurants).length}/{mealSections.length})
        </Button>
      </div>

      <div className="flex-1 content-with-bottom-nav">
        <div className="p-4">
          {isLoading ? (
            <div className="text-center py-8">
              <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
              <p className="text-gray-600">추천 결과를 불러오는 중...</p>
            </div>
          ) : mealSections.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-gray-600 mb-4">식사 시간이 설정되지 않았습니다.</p>
              <Button onClick={() => router.push("/schedule")} className="bg-blue-500 hover:bg-blue-600 text-white">
                스케줄 수정하기
              </Button>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="bg-blue-500 text-white p-4 rounded-lg">
                <h2 className="font-medium mb-2">추천 결과</h2>
                <p className="text-sm opacity-90">사용자의 이동경로와 선호도에 따라 추천된 장소입니다</p>
              </div>

              {mealSections.map((section) => (
                <div key={section.id} className="bg-white rounded-lg shadow-sm border">
                  {/* 토글 헤더 */}
                  <button
                    onClick={() => toggleSection(section.id)}
                    className="w-full p-4 flex items-center justify-between text-left hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                        <span className="text-sm font-medium text-blue-600">
                          {section.type === "식사" ? "🍽️" : "🍪"}
                        </span>
                      </div>
                      <div>
                        <span className="font-medium text-lg">{section.title}</span>
                        <span className="text-sm text-gray-500 ml-2">({formatTime(section.time)})</span>
                      </div>
                      {selectedRestaurants[section.id] && (
                        <span className="text-xs bg-green-100 text-green-600 px-2 py-1 rounded-full">선택완료</span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      {selectedRestaurants[section.id] && (
                        <span className="text-sm text-gray-600">{selectedRestaurants[section.id].name}</span>
                      )}
                      {expandedSections.has(section.id) ? (
                        <ChevronUp className="w-5 h-5 text-gray-400" />
                      ) : (
                        <ChevronDown className="w-5 h-5 text-gray-400" />
                      )}
                    </div>
                  </button>

                  {/* 토글 내용 */}
                  {expandedSections.has(section.id) && (
                    <div className="px-4 pb-4 border-t bg-gray-50">
                      <div className="space-y-3 pt-4">
                        {/* 이전 선택된 식당 표시 */}
                        {section.previousSelection && (
                          <div className="bg-green-50 p-4 rounded-lg border-2 border-green-200">
                            <div className="flex items-center gap-2 mb-3">
                              <span className="text-xs bg-green-500 text-white px-2 py-1 rounded-full font-medium">
                                이전 선택
                              </span>
                              <h3 className="font-medium">{section.previousSelection.name}</h3>
                            </div>
                            <div className="flex items-start gap-3">
                              <img
                                src={
                                  section.previousSelection.image ||
                                  "/placeholder.svg?height=60&width=60&query=restaurant" ||
                                  "/placeholder.svg" ||
                                  "/placeholder.svg"
                                }
                                alt={section.previousSelection.name}
                                className="w-16 h-16 rounded-lg object-cover flex-shrink-0"
                              />
                              <div className="flex-1 min-w-0">
                                <p className="text-sm text-gray-600 mb-2">{section.previousSelection.description}</p>
                                <p className="text-sm text-blue-600 mb-2">{section.previousSelection.aiReason}</p>
                                <div className="flex items-center justify-between">
                                  <div className="flex items-center gap-4">
                                    {section.previousSelection.rating && (
                                      <div className="flex items-center">
                                        <Star className="w-4 h-4 text-yellow-400 fill-current" />
                                        <span className="text-sm ml-1 font-medium">
                                          {section.previousSelection.rating}
                                        </span>
                                      </div>
                                    )}
                                    <span className="text-sm text-gray-500">
                                      거리: {section.previousSelection.distance || "1.2km"}
                                    </span>
                                  </div>
                                </div>
                                {/* 이전 선택 식당 선택 버튼 추가 */}
                                <Button
                                  onClick={() => handleRestaurantSelect(section.id, section.previousSelection!)}
                                  size="sm"
                                  className={`w-full mt-2 ${
                                    selectedRestaurants[section.id]?.id === section.previousSelection!.id
                                      ? "bg-green-500 hover:bg-green-600 text-white"
                                      : "bg-blue-500 hover:bg-blue-600 text-white"
                                  }`}
                                >
                                  {selectedRestaurants[section.id]?.id === section.previousSelection!.id
                                    ? "✓ 선택됨"
                                    : "다시 선택"}
                                </Button>
                              </div>
                            </div>
                          </div>
                        )}

                        {/* 새로운 추천 식당 목록 (카드형) */}
                        <div className="space-y-3">
                          <h4 className="font-medium text-gray-800">새로운 추천</h4>
                          {section.restaurants.map((restaurant) => (
                            <div
                              key={restaurant.id}
                              className={`bg-white border rounded-lg p-4 hover:border-blue-200 transition-all ${
                                selectedRestaurants[section.id]?.id === restaurant.id
                                  ? "border-blue-500 bg-blue-50"
                                  : ""
                              }`}
                            >
                              <div className="flex items-start gap-4">
                                {/* 음식점 이미지 */}
                                <img
                                  src={restaurant.image || "/placeholder.svg?height=80&width=80&query=restaurant"}
                                  alt={restaurant.name}
                                  className="w-20 h-20 rounded-lg object-cover flex-shrink-0"
                                />

                                <div className="flex-1 min-w-0">
                                  {/* 이름 */}
                                  <h3 className="font-semibold text-lg mb-1">{restaurant.name}</h3>

                                  {/* 한줄평 */}
                                  <p className="text-sm text-gray-600 mb-2">{restaurant.description}</p>

                                  {/* AI 추천 이유 */}
                                  <p className="text-sm text-blue-600 mb-3">{restaurant.aiReason}</p>

                                  {/* 별점과 거리 */}
                                  <div className="flex items-center justify-between mb-3">
                                    <div className="flex items-center gap-4">
                                      {restaurant.rating && (
                                        <div className="flex items-center">
                                          <Star className="w-4 h-4 text-yellow-400 fill-current" />
                                          <span className="text-sm ml-1 font-medium">{restaurant.rating}</span>
                                        </div>
                                      )}
                                      <span className="text-sm text-gray-500">거리: {restaurant.distance}</span>
                                    </div>
                                  </div>

                                  {/* 선택 버튼 */}
                                  <Button
                                    onClick={() => handleRestaurantSelect(section.id, restaurant)}
                                    size="sm"
                                    className={`w-full ${
                                      selectedRestaurants[section.id]?.id === restaurant.id
                                        ? "bg-green-500 hover:bg-green-600 text-white"
                                        : "bg-blue-500 hover:bg-blue-600 text-white"
                                    }`}
                                  >
                                    {selectedRestaurants[section.id]?.id === restaurant.id ? "✓ 선택됨" : "선택하기"}
                                  </Button>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ))}

              {/* 선택 상태 요약 */}
              {Object.keys(selectedRestaurants).length > 0 && (
                <div className="bg-blue-50 p-4 rounded-lg">
                  <h3 className="font-medium mb-2">선택된 식당</h3>
                  <div className="space-y-2">
                    {Object.entries(selectedRestaurants).map(([sectionId, restaurant]) => {
                      const section = mealSections.find((s) => s.id === sectionId)
                      return (
                        <div key={sectionId} className="flex items-center gap-2 text-sm">
                          <span className="font-medium">{section?.title}:</span>
                          <span>{restaurant.name}</span>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
