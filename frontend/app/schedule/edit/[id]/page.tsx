"use client"

import { useState, useEffect } from "react"
import { useRouter, useParams } from "next/navigation"
import { useSchedule } from "@/hooks/useSchedule"
import ScheduleCreateLocationScreen from "@/components/screens/ScheduleCreateLocationScreen"
import ScheduleCreateRequiredScreen from "@/components/screens/ScheduleCreateRequiredScreen"
import ScheduleCreateOptionalScreen from "@/components/screens/ScheduleCreateOptionalScreen"
import type { Schedule } from "@/types"

type EditStep = "location" | "required" | "optional"

export default function ScheduleEditPage() {
  const router = useRouter()
  const params = useParams()
  const { schedules, updateSchedule } = useSchedule()
  const [currentStep, setCurrentStep] = useState<EditStep>("location")
  const [scheduleData, setScheduleData] = useState<Schedule | null>(null)
  const [locationData, setLocationData] = useState<any>(null)
  const [requiredData, setRequiredData] = useState<any>(null)

  useEffect(() => {
    const schedule = schedules.find((s) => s.id === params.id)
    if (schedule) {
      setScheduleData(schedule)
      setLocationData({
        departure: schedule.departure,
        destination: schedule.destination,
        waypoints: schedule.waypoints || [],
      })
      setRequiredData({
        scheduleName: schedule.title,
        departureTime: schedule.departureTime,
        arrivalTime: schedule.arrivalTime,
        estimatedArrivalTime: "18:30", // 백엔드에서 계산
        mealRadius: schedule.mealRadius || "5km",
        targetMealTimes: schedule.targetMealTimes || [],
      })
    }
  }, [params.id, schedules])

  const handleLocationNext = (data: any) => {
    setLocationData(data)
    setCurrentStep("required")
  }

  const handleRequiredNext = (data: any) => {
    setRequiredData(data)
    setCurrentStep("optional")
  }

  const handleOptionalComplete = async (optionalData: any) => {
    try {
      const updatedScheduleData = {
        ...scheduleData!,
        title: requiredData.scheduleName,
        departure: locationData.departure,
        destination: locationData.destination,
        waypoints: locationData.waypoints,
        departureTime: requiredData.departureTime,
        arrivalTime: requiredData.arrivalTime,
        hasMeal: requiredData.targetMealTimes.length > 0,
        companions: optionalData.companions,
        purpose: optionalData.travelPurpose,
        userRequirements: optionalData.userRequirements,
        mealRadius: requiredData.mealRadius,
        targetMealTimes: requiredData.targetMealTimes,
      }

      await updateSchedule(updatedScheduleData)
      router.push("/schedule")
    } catch (error) {
      console.error("스케줄 수정 실패:", error)
    }
  }

  const handleBack = () => {
    if (currentStep === "required") {
      setCurrentStep("location")
    } else if (currentStep === "optional") {
      setCurrentStep("required")
    }
  }

  if (!scheduleData) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">로딩 중...</p>
        </div>
      </div>
    )
  }

  return (
    <>
      {currentStep === "location" && (
        <ScheduleCreateLocationScreen onNext={handleLocationNext} initialData={locationData} isEdit={true} />
      )}
      {currentStep === "required" && (
        <ScheduleCreateRequiredScreen onNext={handleRequiredNext} onBack={handleBack} initialData={requiredData} />
      )}
      {currentStep === "optional" && (
        <ScheduleCreateOptionalScreen
          onComplete={handleOptionalComplete}
          onBack={handleBack}
          initialData={{
            userRequirements: scheduleData.userRequirements || "",
            travelPurpose: scheduleData.purpose || "",
            companions: scheduleData.companions || [],
          }}
        />
      )}
    </>
  )
}
