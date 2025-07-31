"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useRouter } from "next/navigation"
import { ArrowLeft, Plus, Minus } from "lucide-react"

interface LocationData {
  departure: string
  destination: string
  waypoints: string[]
}

interface ScheduleCreateLocationScreenProps {
  onNext: (data: LocationData) => void
  initialData?: LocationData
  isEdit?: boolean
}

export default function ScheduleCreateLocationScreen({
  onNext,
  initialData,
  isEdit = false,
}: ScheduleCreateLocationScreenProps) {
  const router = useRouter()
  const [formData, setFormData] = useState<LocationData>(
    initialData || {
      departure: "",
      destination: "",
      waypoints: [],
    },
  )
  const [showLocationInfo, setShowLocationInfo] = useState(false)

  useEffect(() => {
    if (formData.destination) {
      setShowLocationInfo(true)
    }
  }, [formData.destination])

  const handleDepartureChange = (value: string) => {
    setFormData((prev) => ({ ...prev, departure: value }))
    // TODO: 외부 지도 API 연동 지점
    // 예: searchLocation(value)
  }

  const handleDestinationChange = (value: string) => {
    setFormData((prev) => ({ ...prev, destination: value }))
    if (value.trim()) {
      setShowLocationInfo(true)
      // TODO: 외부 지도 API 연동 지점
      // 예: searchLocation(value)
    }
  }

  const addWaypoint = () => {
    setFormData((prev) => ({
      ...prev,
      waypoints: [...prev.waypoints, ""],
    }))
  }

  const removeWaypoint = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      waypoints: prev.waypoints.filter((_, i) => i !== index),
    }))
  }

  const updateWaypoint = (index: number, value: string) => {
    setFormData((prev) => ({
      ...prev,
      waypoints: prev.waypoints.map((wp, i) => (i === index ? value : wp)),
    }))
    // TODO: 외부 지도 API 연동 지점
    // 예: searchLocation(value)
  }

  const handleComplete = () => {
    if (formData.departure && formData.destination) {
      onNext(formData)
    }
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm flex items-center">
        <Button onClick={() => router.back()} variant="ghost" size="sm" className="mr-3">
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <h1 className="text-lg font-medium">지역명 입력</h1>
      </div>

      <div className="flex-1 bg-gray-100">
        {/* Map Area - 외부 API 연동 준비 */}
        <div className="h-64 bg-gray-200 relative">
          {/* TODO: 외부 지도 API 컴포넌트로 교체 */}
          <img
            src="https://hebbkx1anhila5yf.public.blob.vercel-storage.com/image-2dEuIvD7siA9sGPwNTHKsq4vzTXrse.png"
            alt="지도"
            className="w-full h-full object-cover"
          />
        </div>

        {/* Location Input Section */}
        <div className="bg-white p-4 space-y-3">
          <div className="flex items-center gap-3">
            <div className="w-3 h-3 bg-blue-500 rounded-full"></div>
            <Input
              value={formData.departure}
              onChange={(e) => handleDepartureChange(e.target.value)}
              placeholder="출발지"
              className="flex-1"
            />
          </div>

          {formData.departure && (
            <>
              {/* 경유지들 - 출발지와 도착지 사이에 위치 */}
              {formData.waypoints.map((waypoint, index) => (
                <div key={index} className="flex items-center gap-3">
                  <Button
                    onClick={() => removeWaypoint(index)}
                    size="sm"
                    variant="outline"
                    className="w-8 h-8 p-0 text-red-500"
                  >
                    <Minus className="w-4 h-4" />
                  </Button>
                  <Input
                    value={waypoint}
                    onChange={(e) => updateWaypoint(index, e.target.value)}
                    placeholder="경유지 입력"
                    className="flex-1"
                  />
                </div>
              ))}

              <div className="flex items-center gap-3">
                <div className="w-3 h-3 bg-red-500 rounded-full"></div>
                <Input
                  value={formData.destination}
                  onChange={(e) => handleDestinationChange(e.target.value)}
                  placeholder="도착지"
                  className="flex-1"
                />
                <Button onClick={addWaypoint} size="sm" variant="outline" className="w-8 h-8 p-0 bg-transparent">
                  <Plus className="w-4 h-4" />
                </Button>
              </div>
            </>
          )}
        </div>

        {/* Location Info Section */}
        {showLocationInfo && formData.destination && (
          <div className="bg-blue-50 p-4 m-4 rounded-lg">
            <h3 className="font-medium mb-2">컴셀된 지역명</h3>
            <p className="text-sm text-gray-600 mb-4">컴셀된 지역 추천</p>
            <div className="flex gap-2">
              <Button onClick={addWaypoint} size="sm" variant="outline" className="flex-1 bg-transparent">
                경유지 추가
              </Button>
              <Button onClick={handleComplete} size="sm" className="flex-1 bg-blue-500 hover:bg-blue-600 text-white">
                입력완료
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
