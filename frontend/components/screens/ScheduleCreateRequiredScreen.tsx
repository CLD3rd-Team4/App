"use client"

import { useState, useEffect } from "react"
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
  mealDurationMinutes: number
}

interface ScheduleCreateRequiredScreenProps {
  onNext: (data: RequiredData) => void
  onBack: () => void
  initialData?: Partial<RequiredData>
}

export default function ScheduleCreateRequiredScreen({
  onNext,
  onBack,
  initialData,
}: ScheduleCreateRequiredScreenProps) {
  const [formData, setFormData] = useState<RequiredData>(() => ({
    scheduleName: "",
    departureTime: "12:00",
    arrivalTime: "",
    estimatedArrivalTime: "18:30",
    targetMealTimes: [],
    mealDurationMinutes: 30, // 기본 30분으로 설정
    ...initialData,
  }))

  // 컴포넌트 마운트 시 기본 식사 시간을 하나 추가합니다.
  useEffect(() => {
    // initialData가 없고, 식사 시간이 비어있을 때만 실행
    if (!initialData?.targetMealTimes && formData.targetMealTimes.length === 0) {
      addMealTime()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []) // 빈 배열은 마운트 시 한 번만 실행되도록 보장합니다.

  // 시간을 분으로 변환하는 함수
  const timeToMinutes = (time: string) => {
    const [hour, minute] = time.split(":").map(Number)
    return hour * 60 + minute
  }

  // 분을 시간으로 변환하는 함수
  const minutesToTime = (minutes: number) => {
    const hour = Math.floor(minutes / 60)
    const minute = minutes % 60
    return `${hour.toString().padStart(2, "0")}:${minute
      .toString()
      .padStart(2, "0")}`
  }

  // 최소 시간 계산 (출발시간 또는 이전 식사시간의 5분 이후)
  const getMinTimeForMeal = (index: number) => {
    const departureMinutes = timeToMinutes(formData.departureTime)

    if (index === 0) {
      return minutesToTime(departureMinutes + 5)
    } else {
      const previousMealMinutes = timeToMinutes(
        formData.targetMealTimes[index - 1].time,
      )
      return minutesToTime(previousMealMinutes + 5)
    }
  }

  // 출발시간 변경 시 식사시간들 자동 조정
  const handleDepartureTimeChange = (newDepartureTime: string) => {
    setFormData((prev) => {
      const newDepartureMinutes = timeToMinutes(newDepartureTime)

      const updatedMealTimes = prev.targetMealTimes.map((meal, index) => {
        const currentMealMinutes = timeToMinutes(meal.time)
        const minRequiredMinutes = newDepartureMinutes + 5 + index * 5

        if (currentMealMinutes < minRequiredMinutes) {
          return {
            ...meal,
            time: minutesToTime(minRequiredMinutes),
          }
        }
        return meal
      })

      return {
        ...prev,
        departureTime: newDepartureTime,
        targetMealTimes: updatedMealTimes,
      }
    })
  }

  const addMealTime = () => {
    const minTime = getMinTimeForMeal(formData.targetMealTimes.length)
    setFormData((prev) => ({
      ...prev,
      targetMealTimes: [
        ...prev.targetMealTimes,
        { type: "식사", time: minTime, radius: "5km" },
      ],
    }))
  }

  const removeMealTime = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      targetMealTimes: prev.targetMealTimes.filter((_, i) => i !== index),
    }))
  }

  const updateMealTime = (
    index: number,
    field: keyof MealTime,
    value: string,
  ) => {
    if (field === "time") {
      const newTimeMinutes = timeToMinutes(value)
      const minTime = getMinTimeForMeal(index)
      const minMinutes = timeToMinutes(minTime)

      // 최소 시간보다 이른 경우 그냥 최소 시간으로 설정 (경고 없음)
      const finalTime = newTimeMinutes < minMinutes ? minTime : value

      setFormData((prev) => ({
        ...prev,
        targetMealTimes: prev.targetMealTimes.map((meal, i) => {
          if (i === index) {
            return { ...meal, time: finalTime }
          }
          // 이후 식사시간들도 필요시 자동 조정
          if (i > index) {
            const currentMealMinutes = timeToMinutes(meal.time)
            const previousMealMinutes = timeToMinutes(
              prev.targetMealTimes[i - 1].time,
            )
            const adjustedPreviousMinutes =
              i - 1 === index
                ? timeToMinutes(finalTime)
                : previousMealMinutes
            const minRequiredMinutes = adjustedPreviousMinutes + 5

            if (currentMealMinutes < minRequiredMinutes) {
              return { ...meal, time: minutesToTime(minRequiredMinutes) }
            }
          }
          return meal
        }),
      }))
      return
    }

    setFormData((prev) => ({
      ...prev,
      targetMealTimes: prev.targetMealTimes.map((meal, i) =>
        i === index ? { ...meal, [field]: value } : meal,
      ),
    }))
  }

  const handleDurationChange = (minutes: number) => {
    setFormData((prev) => ({ ...prev, mealDurationMinutes: minutes }))
  }

  const handleNext = () => {
    if (!formData.scheduleName || !formData.departureTime) {
      alert("스케줄명과 출발 시간을 입력해주세요.")
      return
    }

    if (formData.targetMealTimes.length === 0) {
      alert("식사 시간은 최소 1개 이상 추가해야 합니다.")
      return
    }

    if (!formData.mealDurationMinutes) {
      alert("식사 소요시간을 선택해주세요.")
      return
    }

    // Final validation for meal time sequence
    for (let i = 0; i < formData.targetMealTimes.length; i++) {
      const mealTime = formData.targetMealTimes[i].time
      const minTime = getMinTimeForMeal(i)

      if (timeToMinutes(mealTime) < timeToMinutes(minTime)) {
        const errorMessage =
          i === 0
            ? `첫 번째 식사 시간(${mealTime})은 출발 시간 이후인 ${minTime}부터 설정 가능합니다.`
            : `식사 시간(${mealTime})은 이전 식사 시간 이후인 ${minTime}부터 설정 가능합니다.`
        alert(errorMessage)
        return
      }
    }

    onNext(formData)
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm flex items-center">
        <Button onClick={onBack} variant="ghost" size="sm" className="mr-3">
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <h1 className="text-lg font-medium text-blue-600">필수 정보 입력</h1>
      </div>

      <div className="flex-1 pb-32 bg-gray-100 overflow-y-auto">
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
              onChange={(e) =>
                setFormData((prev) => ({ ...prev, scheduleName: e.target.value }))
              }
              placeholder="스케줄명을 입력하세요"
              className="mt-2"
            />
          </div>

          {/* 출발 시간 */}
          <TimePicker
            value={formData.departureTime}
            onChange={handleDepartureTimeChange}
            label="출발 시간"
          />

          {/* 식사 소요시간 */}
          <div className="bg-white rounded-lg border p-4">
            <div className="flex items-center justify-between mb-3">
              <span className="font-medium">식사 소요시간</span>
            </div>

            <div className="flex gap-2">
              {[
                { label: "30분", minutes: 30 },
                { label: "1시간", minutes: 60 },
                { label: "2시간", minutes: 120 },
              ].map(({ label, minutes }) => (
                <Button
                  key={label}
                  onClick={() => handleDurationChange(minutes)}
                  variant={
                    formData.mealDurationMinutes === minutes
                      ? "default"
                      : "outline"
                  }
                  size="sm"
                  className={`flex-1 ${
                    formData.mealDurationMinutes === minutes
                      ? "bg-blue-500 text-white"
                      : ""
                  }`}
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>

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
                      className={
                        mealTime.type === "식사"
                          ? "bg-blue-500 text-white"
                          : ""
                      }
                    >
                      식사
                    </Button>
                    <Button
                      onClick={() => updateMealTime(index, "type", "간식")}
                      variant={mealTime.type === "간식" ? "default" : "outline"}
                      size="sm"
                      className={
                        mealTime.type === "간식"
                          ? "bg-blue-500 text-white"
                          : ""
                      }
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
                        onClick={() =>
                          updateMealTime(index, "radius", radiusOption as any)
                        }
                        variant={
                          mealTime.radius === radiusOption ? "default" : "outline"
                        }
                        size="sm"
                        className={
                          mealTime.radius === radiusOption
                            ? "bg-blue-500 text-white"
                            : ""
                        }
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
              disabled={formData.targetMealTimes.length >= 3}
            >
              {formData.targetMealTimes.length === 0
                ? "추가하기"
                : "식사 시간 추가"}
            </Button>
          </div>
        </div>
      </div>

      {/* Fixed Progress Bar and Next Button */}
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
            disabled={
              !formData.scheduleName ||
              !formData.departureTime ||
              formData.targetMealTimes.length === 0 ||
              !formData.mealDurationMinutes
            }
            className="w-full bg-blue-500 hover:bg-blue-600 text-white py-2"
          >
            다음
          </Button>
        </div>
      </div>
    </div>
  )
}
