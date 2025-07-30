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
      // Schedule 타입에 맞게 데이터 재구성
      const scheduleData = {
        // 필수 정보
        title: requiredData.scheduleName,
        departureTime: requiredData.departureTime,
        arrivalTime: requiredData.arrivalTime,
        targetMealTimes: requiredData.targetMealTimes,
        mealRadius: requiredData.mealRadius,
        hasMeal: requiredData.targetMealTimes.length > 0,

        // 위치 정보 (Location/Waypoint 객체 전체를 전달)
        departure: locationData.departure,
        destination: locationData.destination,
        waypoints: locationData.waypoints || [],

        // 선택 정보
        companions: optionalData.companions,
        purpose: optionalData.travelPurpose,
        userRequirements: optionalData.userRequirements,

        // 기타
        tags: [], // 태그는 현재 수집하지 않으므로 빈 배열
      }

      // Omit<Schedule, "id"> 타입에 맞추기 위해 id가 없는 데이터 전달
      await createSchedule(scheduleData)
      router.push("/schedule")
    } catch (error) {
      console.error("스케줄 생성 실패:", error)
      // TODO: 사용자에게 에러 알림 표시 (예: toast)
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
