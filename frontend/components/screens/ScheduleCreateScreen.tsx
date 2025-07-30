"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useSchedule } from "@/hooks/useSchedule"
import ScheduleCreateLocationScreen from "./ScheduleCreateLocationScreen"
import ScheduleCreateRequiredScreen from "./ScheduleCreateRequiredScreen"
import ScheduleCreateOptionalScreen from "./ScheduleCreateOptionalScreen"

import { scheduleApi } from "@/services/api";
import { useToast } from "@/hooks/use-toast"; // toast 추가



type CreateStep = "location" | "required" | "optional"

export default function ScheduleCreateScreen() {
  const router = useRouter()
  const { createSchedule } = useSchedule()
  const { toast } = useToast()
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
      // API 요청을 위한 데이터 재구성 (schedule.proto에 맞게)
      const scheduleData = {
        user_id: "", // 백엔드에서 기본값 처리 예정
        title: requiredData.title, // `scheduleName` -> `title`
        departure_time: requiredData.departureTime,
        // `mealTimes`를 `meal_slots`로 변환
        meal_slots: requiredData.mealTimes.map((meal: any) => ({
          meal_type: meal.type === "식사" ? "MEAL" : "SNACK", // Enum 문자열로 전송
          scheduled_time: meal.time,
          radius: meal.radius,
        })),

        // 위치 정보
        departure: locationData.departure,
        destination: locationData.destination,
        waypoints: locationData.waypoints || [],

        // 선택 정보
        user_note: optionalData.userRequirements,
        purpose: optionalData.travelPurpose,
        companions: optionalData.companions,

        // 기타 필드 (proto에 정의된 경우)
        arrival_time: "", // 예상 도착 시간은 백엔드 계산
      };

      // `useSchedule`의 `createSchedule`가 아닌, `scheduleApi` 직접 호출
      // `useSchedule` 훅은 내부적으로 다른 데이터 구조를 사용할 수 있으므로, gRPC에 직접 매핑되는 api 함수 사용
      await scheduleApi.createSchedule(scheduleData);
      toast({ title: "성공", description: "스케줄이 성공적으로 생성되었습니다." });
      router.push("/schedule"); // 목록 화면으로 이동
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
