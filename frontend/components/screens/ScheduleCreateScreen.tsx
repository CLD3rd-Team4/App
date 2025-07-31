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
        title: requiredData.scheduleName,
        departure: locationData.departure,
        destination: locationData.destination,
        waypoints: locationData.waypoints,
        departureTime: requiredData.departureTime,
        arrivalTime: requiredData.arrivalTime,
        hasMeal: requiredData.targetMealTimes.length > 0,
        companions: optionalData.companions,
        purpose: optionalData.travelPurpose,
        tags: [],
        userRequirements: optionalData.userRequirements,
        mealRadius: requiredData.mealRadius,
        targetMealTimes: requiredData.targetMealTimes,
      }

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
