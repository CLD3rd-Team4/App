"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useSchedule } from "@/hooks/useSchedule"
import ScheduleCreateLocationScreen from "./ScheduleCreateLocationScreen"
import ScheduleCreateRequiredScreen from "./ScheduleCreateRequiredScreen"
import ScheduleCreateOptionalScreen from "./ScheduleCreateOptionalScreen"

type CreateStep = "location" | "required" | "optional"

export default function ScheduleCreateScreen() {
  const router = useRouter()
  const { createSchedule } = useSchedule()
  const [currentStep, setCurrentStep] = useState<CreateStep>("location")
  const [locationData, setLocationData] = useState<any>(null)
  const [requiredData, setRequiredData] = useState<any>(null)

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
      const scheduleData = {
        userId: "test-user-123",
        title: requiredData.scheduleName,
        departureTime: requiredData.departureTime,
        arrivalTime: "", // 항상 빈 문자열로 전송
                mealSlots: requiredData.targetMealTimes.map((mt: any) => ({
          mealType: mt.type === '식사' ? 'MEAL' : 'SNACK',
          scheduledTime: mt.time,
          radius: (parseInt(mt.radius, 10) || 1) * 1000, // 각 식사 시간의 반경 사용
        })),
        departure: locationData.departure,
        waypoints: locationData.waypoints,
        destination: locationData.destination,
        userNote: optionalData.userRequirements,
        purpose: optionalData.travelPurpose,
        companions: optionalData.companions,
      }

      console.log("Creating schedule with data:", JSON.stringify(scheduleData, null, 2));

      await createSchedule(scheduleData)
      router.push("/schedule")
    } catch (error) {
      console.error("스케줄 생성 실패:", error)
    }
  }

  const handleBack = () => {
    if (currentStep === "required") {
      setCurrentStep("location")
    } else if (currentStep === "optional") {
      setCurrentStep("required")
    }
  }

  return (
    <>
      {currentStep === "location" && (
        <ScheduleCreateLocationScreen onNext={handleLocationNext} initialData={locationData} />
      )}
      {currentStep === "required" && (
        <ScheduleCreateRequiredScreen onNext={handleRequiredNext} onBack={handleBack} initialData={requiredData} />
      )}
      {currentStep === "optional" && (
        <ScheduleCreateOptionalScreen onComplete={handleOptionalComplete} onBack={handleBack} />
      )}
    </>
  )
}
