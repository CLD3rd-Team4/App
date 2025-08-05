"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { TimePicker } from "@/components/ui/time-picker"
import { ArrowLeft } from "lucide-react"

interface MealTime {
  type: "식사" | "간식"
  time: string
  radius: "5km" | "10km" | "20km"
}

interface RequiredData {
  scheduleName: string
  departureTime: string
  arrivalTime: string
  estimatedArrivalTime: string
  targetMealTimes: MealTime[]
}

interface ScheduleCreateRequiredScreenProps {
  onNext: (data: RequiredData) => void
  onBack: () => void
  initialData?: RequiredData
}

export default function ScheduleCreateRequiredScreen({
  onNext,
  onBack,
  initialData,
}: ScheduleCreateRequiredScreenProps) {
  const [formData, setFormData] = useState<RequiredData>(
    initialData || {
      scheduleName: "",
      departureTime: "12:00",
      arrivalTime: "",
      estimatedArrivalTime: "18:30", // 백엔드에서 계산해서 받을 예정
      targetMealTimes: [],
    },
  )
  const [selectedAdjustment, setSelectedAdjustment] = useState<string>("")

  // 시간을 분으로 변환하는 함수
  const timeToMinutes = (time: string) => {
    const [hour, minute] = time.split(":").map(Number)
    return hour * 60 + minute
  }

  // 분을 시간으로 변환하는 함수
  const minutesToTime = (minutes: number) => {
    const hour = Math.floor(minutes / 60)
    const minute = minutes % 60
    return `${hour.toString().padStart(2, "0")}:${minute.toString().padStart(2, "0")}`
  }

  // 최소 시간 계산 (출발시간 또는 이전 식사시간의 5분 이후)
  const getMinTimeForMeal = (index: number) => {
    const departureMinutes = timeToMinutes(formData.departureTime)

    if (index === 0) {
      // 첫 번째 식사는 출발시간 + 5분 이후
      return minutesToTime(departureMinutes + 5)
    } else {
      // 이후 식사는 이전 식사시간 + 5분 이후
      const previousMealMinutes = timeToMinutes(formData.targetMealTimes[index - 1].time)
      return minutesToTime(previousMealMinutes + 5)
    }
  }

  const addMealTime = () => {
    const minTime = getMinTimeForMeal(formData.targetMealTimes.length)
    setFormData((prev) => ({
      ...prev,
      targetMealTimes: [...prev.targetMealTimes, { type: "식사", time: minTime, radius: "5km" }], // 기본 반경 5km 추가
    }))
  }

  const removeMealTime = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      targetMealTimes: prev.targetMealTimes.filter((_, i) => i !== index),
    }))
  }

  const updateMealTime = (index: number, field: keyof MealTime, value: string) => {
    if (field === "time") {
      const minTime = getMinTimeForMeal(index)
      const minMinutes = timeToMinutes(minTime)
      const selectedMinutes = timeToMinutes(value)

      if (selectedMinutes < minMinutes) {
        alert(`${index === 0 ? "출발시간" : "이전 식사시간"}의 5분 이후로 선택해주세요.`)
        return
      }
    }

    setFormData((prev) => ({
      ...prev,
      targetMealTimes: prev.targetMealTimes.map((meal, i) => (i === index ? { ...meal, [field]: value } : meal)),
    }))
  }

  const adjustArrivalTime = (adjustment: string) => {
    setSelectedAdjustment(adjustment)
    // TODO: 백엔드에서 계산된 예상 도착시간을 기준으로 조정
    const currentTime = new Date(`2024-01-01 ${formData.estimatedArrivalTime}`)
    let minutes = 0

    switch (adjustment) {
      case "+30분":
        minutes = 30
        break
      case "+1시간":
        minutes = 60
        break
      case "+2시간":
        minutes = 120
        break
    }

    currentTime.setMinutes(currentTime.getMinutes() + minutes)
    const newTime = `${currentTime.getHours().toString().padStart(2, "0")}:${currentTime.getMinutes().toString().padStart(2, "0")}`
    setFormData((prev) => ({ ...prev, arrivalTime: newTime }))
  }

  const handleNext = () => {
    if (formData.scheduleName && formData.departureTime) {
      onNext(formData)
    }
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm flex items-center">
        <Button onClick={onBack} variant="ghost" size="sm" className="mr-3">
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <h1 className="text-lg font-medium text-blue-600">필수 정보 입력</h1>
      </div>

      <div className="flex-1 pb-20 bg-gray-100 overflow-y-auto">
        <div className="p-4 space-y-6">
          <p className="text-sm text-gray-600">스케줄명을 입력해주세요.</p>

          {/* 스케줄명 */}
          <div>
            <Label htmlFor="scheduleName" className="text-base font-medium">
              스케줄명
            </Label>
            <Input
              id="scheduleName"
              value={formData.scheduleName}
              onChange={(e) => setFormData((prev) => ({ ...prev, scheduleName: e.target.value }))}
              placeholder="스케줄명을 입력하세요"
              className="mt-2"
            />
          </div>

          {/* 출발 시간 */}
          <TimePicker
            value={formData.departureTime}
            onChange={(time) => setFormData((prev) => ({ ...prev, departureTime: time }))}
            label="출발 시간"
          />

          {/* 도착 여유 시간 선택 */}
          <div className="bg-white rounded-lg border p-4">
            <div className="flex items-center justify-between mb-3">
              <span className="font-medium">도착 여유 시간 선택</span>
            </div>

            <div className="flex gap-2">
              {["+30분", "+1시간", "+2시간"].map((adjustment) => (
                <Button
                  key={adjustment}
                  onClick={() => adjustArrivalTime(adjustment)}
                  variant={selectedAdjustment === adjustment ? "default" : "outline"}
                  size="sm"
                  className={`flex-1 ${selectedAdjustment === adjustment ? "bg-blue-500 text-white" : ""}`}
                >
                  {adjustment}
                </Button>
              ))}
            </div>
          </div>

          {/* 식사 반경 */}
          {/* 이 부분은 각 식사 시간 카드 내부로 이동 */}

          {/* 목표 식사 시간 */}
          <div>
            <Label className="text-base font-medium">목표 식사 시간</Label>

            {formData.targetMealTimes.map((mealTime, index) => (
              <div key={index} className="bg-white rounded-lg border p-3 mt-3">
                <div className="flex items-center gap-2 mb-3">
                  <div className="flex gap-2">
                    <Button
                      onClick={() => updateMealTime(index, "type", "식사")}
                      variant={mealTime.type === "식사" ? "default" : "outline"}
                      size="sm"
                      className={mealTime.type === "식사" ? "bg-blue-500 text-white" : ""}
                    >
                      식사
                    </Button>
                    <Button
                      onClick={() => updateMealTime(index, "type", "간식")}
                      variant={mealTime.type === "간식" ? "default" : "outline"}
                      size="sm"
                      className={mealTime.type === "간식" ? "bg-blue-500 text-white" : ""}
                    >
                      간식
                    </Button>
                  </div>
                  <Button
                    onClick={() => removeMealTime(index)}
                    variant="outline"
                    size="sm"
                    className="ml-auto text-red-500"
                  >
                    삭제
                  </Button>
                </div>

                <TimePicker
                  value={mealTime.time}
                  onChange={(time) => updateMealTime(index, "time", time)}
                  label={`시간 선택 (${getMinTimeForMeal(index)} 이후)`}
                />

                {/* 개별 식사 반경 */}
                <div className="mt-3">
                  <Label className="text-sm font-medium">식사 반경</Label>
                  <div className="flex gap-2 mt-1">
                    {["5km", "10km", "20km"].map((radiusOption) => (
                      <Button
                        key={radiusOption}
                        onClick={() => updateMealTime(index, "radius", radiusOption as any)}
                        variant={mealTime.radius === radiusOption ? "default" : "outline"}
                        size="sm"
                        className={mealTime.radius === radiusOption ? "bg-blue-500 text-white" : ""}
                      >
                        {radiusOption}
                      </Button>
                    ))}
                  </div>
                </div>
              </div>
            ))}

            <Button
              onClick={addMealTime}
              variant="outline"
              size="sm"
              className="w-full mt-3 text-blue-600 border-blue-200 bg-transparent"
            >
              {formData.targetMealTimes.length === 0 ? "추가하기" : "식사 시간 추가"}
            </Button>
          </div>
        </div>
      </div>

      {/* Fixed Progress Bar and Next Button - 하단 네비게이션과 같은 높이로 조정 */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t z-50">
        <div className="max-w-md mx-auto p-4">
          <div className="flex items-center justify-between mb-3">
            <div className="flex-1 bg-gray-200 rounded-full h-2">
              <div className="bg-blue-500 h-2 rounded-full w-1/2"></div>
            </div>
            <span className="text-sm text-gray-600 ml-3">1/2</span>
          </div>
          <Button
            onClick={handleNext}
            disabled={!formData.scheduleName || !formData.departureTime}
            className="w-full bg-blue-500 hover:bg-blue-600 text-white py-2"
          >
            다음
          </Button>
        </div>
      </div>
    </div>
  )
}
