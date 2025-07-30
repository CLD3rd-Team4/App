"use client"

import { useState, useEffect, useRef } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useRouter } from "next/navigation"
import { ArrowLeft, Plus, Minus, X } from "lucide-react"
import type { LocationInfo, LocationData } from "@/types"
import { scheduleApi } from "@/services/api" // scheduleApi 임포트
import { useToast } from "@/hooks/use-toast" // useToast 임포트

interface ScheduleCreateLocationScreenProps {
  onNext: (scheduleId: string) => void // scheduleId를 받도록 수정
  initialData?: LocationData
  isEdit?: boolean
}

export default function ScheduleCreateLocationScreen({
  onNext,
  initialData,
  isEdit = false,
}: ScheduleCreateLocationScreenProps) {
  const router = useRouter()
  const { toast } = useToast()
  const mapContainer = useRef<HTMLDivElement>(null)
  const map = useRef<any>(null)
  const markers = useRef<any[]>([])

  const [formData, setFormData] = useState<LocationData>(
    initialData || {
      departure: null,
      destination: null,
      waypoints: [],
    },
  )

  const [searchKeyword, setSearchKeyword] = useState("")
  const [searchResults, setSearchResults] = useState<any[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const [isLoading, setIsLoading] = useState(false) // 로딩 상태 추가
  const [showSearchResults, setShowSearchResults] = useState(false)
  const [activeField, setActiveField] = useState<"departure" | "destination" | number | null>(null)

  // 지도 초기화
  useEffect(() => {
    if (!mapContainer.current || !window.kakao) return

    const mapOption = {
      center: new window.kakao.maps.LatLng(37.566826, 126.9786567),
      level: 8,
    }
    map.current = new window.kakao.maps.Map(mapContainer.current, mapOption)

    // 초기 데이터가 있으면 마커 표시
    if (initialData?.departure) addMarker(initialData.departure, "blue")
    if (initialData?.destination) addMarker(initialData.destination, "red")
    initialData?.waypoints?.forEach((wp) => wp && addMarker(wp, "yellow"))
  }, [])

  // 모든 마커 제거
  const clearMarkers = () => {
    markers.current.forEach((marker) => marker.setMap(null))
    markers.current = []
  }

  // 마커 추가
  const addMarker = (locationInfo: LocationInfo, color: "blue" | "red" | "yellow" = "blue") => {
    if (!map.current) return

    const markerPosition = new window.kakao.maps.LatLng(locationInfo.lat, locationInfo.lng)

    const imageSrc =
      color === "blue"
        ? "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_blue.png"
        : color === "red"
          ? "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png"
          : "https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_yellow.png"

    const imageSize = new window.kakao.maps.Size(24, 35)
    const markerImage = new window.kakao.maps.MarkerImage(imageSrc, imageSize)

    const marker = new window.kakao.maps.Marker({
      position: markerPosition,
      image: markerImage,
    })

    marker.setMap(map.current)
    markers.current.push(marker)
    map.current.setCenter(markerPosition)
  }

  // 장소 검색
  const searchPlaces = async () => {
    if (!searchKeyword.trim()) return
    setIsSearching(true)

    const ps = new window.kakao.maps.services.Places()

    ps.keywordSearch(searchKeyword, (data: any[], status: any) => {
      setIsSearching(false)
      if (status === window.kakao.maps.services.Status.OK) {
        setSearchResults(data.slice(0, 8))
        setShowSearchResults(true)
      } else {
        setSearchResults([])
        setShowSearchResults(true)
      }
    })
  }

  // 검색 시작
  const startSearch = (field: "departure" | "destination" | number) => {
    setActiveField(field)
    setSearchKeyword("")
    setSearchResults([])
    setShowSearchResults(true)
  }

  // 검색 종료
  const endSearch = () => {
    setActiveField(null)
    setSearchKeyword("")
    setSearchResults([])
    setShowSearchResults(false)
  }

  // 장소 선택
  const selectLocation = (place: any) => {
    const locationInfo: LocationInfo = {
      name: place.place_name,
      address: place.address_name,
      lat: Number.parseFloat(place.y),
      lng: Number.parseFloat(place.x),
    }

    if (activeField === "departure") {
      setFormData((prev) => ({ ...prev, departure: locationInfo }))
      clearMarkers()
      addMarker(locationInfo, "blue")
      // 기존 마커들 다시 추가
      if (formData.destination) addMarker(formData.destination, "red")
      formData.waypoints.forEach((wp) => wp && addMarker(wp, "yellow"))
    } else if (activeField === "destination") {
      setFormData((prev) => ({ ...prev, destination: locationInfo }))
      addMarker(locationInfo, "red")
    } else if (typeof activeField === "number") {
      setFormData((prev) => ({
        ...prev,
        waypoints: prev.waypoints.map((wp, i) => (i === activeField ? locationInfo : wp)),
      }))
      addMarker(locationInfo, "yellow")
    }

    endSearch()
  }

  // 경유지 추가
  const addWaypoint = () => {
    setFormData((prev) => ({
      ...prev,
      waypoints: [...prev.waypoints, null as any],
    }))
  }

  // 경유지 제거
  const removeWaypoint = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      waypoints: prev.waypoints.filter((_, i) => i !== index),
    }))

    // 마커 새로고침
    setTimeout(() => {
      clearMarkers()
      if (formData.departure) addMarker(formData.departure, "blue")
      if (formData.destination) addMarker(formData.destination, "red")
      formData.waypoints.filter((_, i) => i !== index).forEach((wp) => wp && addMarker(wp, "yellow"))
    }, 0)
  }

  // 완료 처리
  const handleComplete = async () => {
    if (formData.departure && formData.destination && !isLoading) {
      setIsLoading(true)
      try {
        const response = await scheduleApi.createInitialSchedule(formData);
        if (response && response.scheduleId) {
          toast({
            title: "스케줄 생성 성공",
            description: "기본 스케줄이 생성되었습니다. 상세 정보를 입력해주세요.",
          })
          onNext(response.scheduleId); // 생성된 ID를 다음 단계로 전달
        } else {
          throw new Error("Invalid response from server");
        }
      } catch (error) {
        console.error("스케줄 생성 실패", error);
        toast({
          title: "오류",
          description: "스케줄 생성에 실패했습니다. 잠시 후 다시 시도해주세요.",
          variant: "destructive",
        })
      } finally {
        setIsLoading(false)
      }
    }
  }

  return (
    <div className="h-screen bg-white flex flex-col relative">
      {/* 지도 */}
      <div className="flex-1 relative">
        <div ref={mapContainer} className="w-full h-full bg-gray-200"></div>

        {/* 헤더 */}
        <div className="absolute top-0 left-0 right-0 bg-white/90 backdrop-blur-sm px-4 py-3 flex items-center z-30">
          <Button onClick={() => router.back()} variant="ghost" size="sm" className="mr-2 p-2">
            <ArrowLeft className="w-5 h-5" />
          </Button>
          <h1 className="text-lg font-medium">
            {activeField === "departure"
              ? "출발지 입력"
              : activeField === "destination"
                ? "도착지 입력"
                : typeof activeField === "number"
                  ? "경유지 입력"
                  : "지역명 입력"}
          </h1>
          <Button onClick={() => router.back()} variant="ghost" size="sm" className="ml-auto p-2">
            <X className="w-5 h-5" />
          </Button>
        </div>

        {/* 위치 입력 오버레이 */}
        {!showSearchResults && (
          <div className="absolute top-20 left-4 right-4 z-20">
            <div className="bg-white rounded-lg shadow-lg overflow-hidden">
              {/* 출발지 */}
              <div
                className="flex items-center gap-3 p-4 border-b cursor-pointer hover:bg-gray-50"
                onClick={() => startSearch("departure")}
              >
                <div className="flex items-center gap-2">
                  <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
                  <span className="text-sm text-gray-600">출발지</span>
                </div>
                <div className="flex-1 text-sm">
                  {formData.departure ? formData.departure.name : "출발지를 선택해주세요"}
                </div>
              </div>

              {/* 경유지들 */}
              {formData.waypoints.map((waypoint, index) => (
                <div key={index} className="flex items-center border-b">
                  <Button
                    onClick={() => removeWaypoint(index)}
                    size="sm"
                    variant="ghost"
                    className="p-2 text-red-500 hover:bg-red-50 ml-2"
                  >
                    <Minus className="w-4 h-4" />
                  </Button>
                  <div
                    className="flex-1 flex items-center gap-3 p-4 cursor-pointer hover:bg-gray-50"
                    onClick={() => startSearch(index)}
                  >
                    <div className="flex items-center gap-2">
                      <div className="w-2 h-2 bg-yellow-500 rounded-full"></div>
                      <span className="text-sm text-gray-600">경유지</span>
                    </div>
                    <div className="flex-1 text-sm">{waypoint ? waypoint.name : `경유지 ${index + 1}`}</div>
                  </div>
                </div>
              ))}

              {/* 도착지 */}
              <div className="flex items-center">
                <div
                  className="flex-1 flex items-center gap-3 p-4 cursor-pointer hover:bg-gray-50"
                  onClick={() => startSearch("destination")}
                >
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 bg-red-500 rounded-full"></div>
                    <span className="text-sm text-gray-600">도착지</span>
                  </div>
                  <div className="flex-1 text-sm">
                    {formData.destination ? formData.destination.name : "도착지를 선택해주세요"}
                  </div>
                </div>
                <Button
                  onClick={addWaypoint}
                  size="sm"
                  variant="ghost"
                  className="p-2 text-blue-600 hover:bg-blue-50 mr-2"
                >
                  <Plus className="w-4 h-4" />
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 검색 결과 바텀 시트 */}
      {showSearchResults && (
        <div className="absolute inset-0 bg-black/20 z-40">
          <div className="absolute bottom-0 left-0 right-0 bg-white rounded-t-2xl max-h-[70vh] flex flex-col">
            {/* 검색창 */}
            <div className="p-4 border-b">
              <div className="relative">
                <Input
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  placeholder="검색할 지역 주소"
                  onKeyPress={(e) => e.key === "Enter" && searchPlaces()}
                  className="text-sm"
                  autoFocus
                />
                {isSearching && (
                  <div className="absolute right-3 top-1/2 transform -translate-y-1/2">
                    <div className="w-4 h-4 border-2 border-gray-300 border-t-blue-600 rounded-full animate-spin"></div>
                  </div>
                )}
              </div>
            </div>

            {/* 검색 결과 헤더 */}
            <div className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-50">검색된 지역명</div>

            {/* 검색 결과 */}
            <div className="flex-1 overflow-y-auto">
              {searchResults.length > 0 ? (
                searchResults.map((place, index) => (
                  <div key={index} className="p-4 border-b border-gray-100">
                    <div className="mb-3">
                      <div className="font-medium text-sm mb-1">{place.place_name}</div>
                      <div className="text-xs text-gray-500">{place.address_name}</div>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        onClick={() => selectLocation(place)}
                        size="sm"
                        className="bg-blue-500 hover:bg-blue-600 text-white text-xs px-3 py-1 h-auto"
                      >
                        {activeField === "departure" ? "출발" : activeField === "destination" ? "도착" : "경유지 추가"}
                      </Button>
                    </div>
                  </div>
                ))
              ) : (
                <div className="flex items-center justify-center py-12 text-gray-400 text-sm">
                  {searchKeyword ? "검색 결과가 없습니다" : "지역명을 검색해보세요"}
                </div>
              )}
            </div>

            {/* 취소 버튼 */}
            <div className="p-4 border-t">
              <Button onClick={endSearch} variant="outline" className="w-full bg-transparent">
                취소
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* 완료 버튼 */}
      {!showSearchResults && formData.departure && formData.destination && (
        <div className="absolute bottom-6 left-4 right-4 z-30">
          <Button
            onClick={handleComplete}
            disabled={isLoading} // 로딩 중 버튼 비활성화
            className="w-full bg-blue-500 hover:bg-blue-600 text-white py-3 text-sm font-medium rounded-lg shadow-lg"
          >
            {isLoading ? "생성 중..." : "입력완료"}
          </Button>
        </div>
      )}
    </div>
  )
}
