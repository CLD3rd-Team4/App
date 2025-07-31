"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { TimePicker } from "@/components/ui/time-picker"
import { ArrowLeft } from "lucide-react"

// 1. 데이터 구조 변경
interface MealTime {
  type: "식사" | "간식"
  time: string
  radius: number // 개별 반경 (단위: 미터)
}

interface RequiredData {
  title: string // 'scheduleName'에서 변경
  departureTime: string
  mealTimes: MealTime[] // 'targetMealTimes'에서 변경
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
  // 2. 상태 관리 로직 수정
  const [formData, setFormData] = useState<RequiredData>(
    initialData || {
      title: "",
      departureTime: "12:00",
      // mealTimes 배열에 기본 항목 1개 추가
      mealTimes: [{ type: "식사", time: "12:05", radius: 5000 }],
    },
  )

  // 시간을 분으로 변환하는 함수
  const timeToMinutes = (time: string) => {
    if (!time) return 0;
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
      return minutesToTime(departureMinutes + 5)
    } else {
      const previousMealTime = formData.mealTimes[index - 1]?.time;
      if (!previousMealTime) return minutesToTime(departureMinutes + 5); // 이전 시간 없으면 출발시간 기준
      const previousMealMinutes = timeToMinutes(previousMealTime)
      return minutesToTime(previousMealMinutes + 5)
    }
  }


  const handleMealTimeChange = (index: number, field: keyof MealTime, value: any) => {
    // 시간 유효성 검사
    if (field === "time") {
      const minTime = getMinTimeForMeal(index)
      if (value < minTime) {
        alert(`${index === 0 ? "출발시간" : "이전 식사시간"}의 5분 이후로 선택해주세요.`)
        return
      }
    }

    const newMealTimes = [...formData.mealTimes]
    newMealTimes[index] = { ...newMealTimes[index], [field]: value }
    setFormData((prev) => ({ ...prev, mealTimes: newMealTimes }))
  }

  const addMealTime = () => {
    const minTime = getMinTimeForMeal(formData.mealTimes.length);
    setFormData((prev) => ({
      ...prev,
      mealTimes: [
        ...prev.mealTimes,
        { type: "식사", time: minTime, radius: 5000 },
      ],
    }))
  }

  const removeMealTime = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      mealTimes: prev.mealTimes.filter((_, i) => i !== index),
    }))
  }

  const handleNext = () => {
    if (formData.title && formData.departureTime) {
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
            <Label htmlFor="title" className="text-base font-medium">
              스케줄명
            </Label>
            <Input
              id="title"
              value={formData.title}
              onChange={(e) => setFormData((prev) => ({ ...prev, title: e.target.value }))}
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

          {/* 목표 식사 시간 */}
          <div>
            <Label className="text-base font-medium">목표 식사 시간</Label>

            {formData.mealTimes.map((mealTime, index) => (
              <div key={index} className="bg-white rounded-lg border p-3 mt-3 space-y-4">
                {/* 식사/간식 타입 및 삭제 버튼 */}
                <div className="flex items-center gap-2">
                  <div className="flex gap-2">
                    <Button
                      onClick={() => handleMealTimeChange(index, "type", "식사")}
                      variant={mealTime.type === "식사" ? "default" : "outline"}
                      size="sm"
                      className={mealTime.type === "식사" ? "bg-blue-500 text-white" : ""}
                    >
                      식사
                    </Button>
                    <Button
                      onClick={() => handleMealTimeChange(index, "type", "간식")}
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

                {/* 시간 선택 */}
                <TimePicker
                  value={mealTime.time}
                  onChange={(time) => handleMealTimeChange(index, "time", time)}
                  label={`시간 선택 (${getMinTimeForMeal(index)} 이후)`}
                />

                {/* 3. UI 로직 연결: 개별 반경 선택 */}
                <div>
                  <Label className="text-sm text-gray-500">식사 반경</Label>
                  <div className="flex gap-2 mt-2">
                    {[5000, 10000, 20000].map((radius) => (
                      <Button
                        key={radius}
                        onClick={() => handleMealTimeChange(index, "radius", radius)}
                        variant={mealTime.radius === radius ? "default" : "outline"}
                        size="sm"
                        className={mealTime.radius === radius ? "bg-blue-500 text-white" : ""}
                      >
                        {radius / 1000}km
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
              {formData.mealTimes.length === 0 ? "추가하기" : "식사 시간 추가"}
            </Button>
          </div>
        </div>
      </div>

      {/* 하단 버튼 */}
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
            disabled={!formData.title || !formData.departureTime}
            className="w-full bg-blue-500 hover:bg-blue-600 text-white py-2"
          >
            다음
          </Button>
        </div>
      </div>
    </div>
  )
}
