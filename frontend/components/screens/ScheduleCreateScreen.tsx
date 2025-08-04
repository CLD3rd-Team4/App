"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useSchedule } from "@/hooks/useSchedule"
import ScheduleCreateLocationScreen from "./ScheduleCreateLocationScreen"
import ScheduleCreateRequiredScreen from "./ScheduleCreateRequiredScreen"
import ScheduleCreateOptionalScreen from "./ScheduleCreateOptionalScreen"

type CreateStep = "location" | "required" | "optional"

export default function ScheduleCreateScreen({ isEdit = false, initialData = null }: { isEdit?: boolean, initialData?: any }) {
  const router = useRouter()
  const { createSchedule, updateSchedule } = useSchedule()
  const [currentStep, setCurrentStep] = useState<CreateStep>("location")

  // initialData를 기반으로 각 단계의 상태를 초기화합니다.
  const [locationData, setLocationData] = useState<any>(() => {
    if (!initialData) return null;
    return {
      departure: initialData.departure,
      waypoints: initialData.waypoints,
      destination: initialData.destination,
    };
  });

  const [requiredData, setRequiredData] = useState<any>(() => {
    if (!initialData) return null;
    return {
      scheduleName: initialData.title,
      departureTime: initialData.departureTime,
      targetMealTimes: initialData.mealSlots.map((ms: any) => ({
        type: ms.mealType === 'MEAL' ? '식사' : '간식',
        time: ms.scheduledTime,
        // 숫자 반경을 "Nkm" 형태의 문자열로 변환합니다.
        radius: `${ms.radius / 1000}km`,
      })),
    };
  });

  const [optionalData, setOptionalData] = useState<any>(() => {
    if (!initialData) return null;
    return {
      userRequirements: initialData.userNote,
      travelPurpose: initialData.purpose,
      companions: initialData.companions,
    };
  });

  const handleLocationNext = (data: any) => {
    setLocationData(data)
    setCurrentStep("required")
  }

  const handleRequiredNext = (data: any) => {
    setRequiredData(data)
    setCurrentStep("optional")
  }

  const handleOptionalComplete = async (finalOptionalData: any) => {
    try {
      const scheduleData = {
        userId: "test-user-123", // TODO: 실제 사용자 ID로 교체
        title: requiredData.scheduleName,
        departureTime: requiredData.departureTime,
        arrivalTime: initialData?.arrivalTime || "", // 수정 시 기존 도착 시간 유지
        mealSlots: requiredData.targetMealTimes.map((mt: any) => ({
          mealType: mt.type === '식사' ? 'MEAL' : 'SNACK',
          scheduledTime: mt.time,
          radius: (parseInt(mt.radius, 10) || 1) * 1000,
        })),
        departure: locationData.departure,
        waypoints: locationData.waypoints,
        destination: locationData.destination,
        userNote: finalOptionalData.userRequirements,
        purpose: finalOptionalData.travelPurpose,
        companions: finalOptionalData.companions,
      }

      if (isEdit && initialData?.id) { // initialData.id가 유효한지 확인
        const updatedScheduleData = { ...scheduleData, id: initialData.id };
        console.log("Updating schedule with data:", JSON.stringify(updatedScheduleData, null, 2));
        await updateSchedule(updatedScheduleData);
      } else if (!isEdit) {
        console.log("Creating schedule with data:", JSON.stringify(scheduleData, null, 2));
        await createSchedule(scheduleData)
      }
      
      router.push("/schedule")
    } catch (error) {
      console.error(isEdit ? "스케줄 수정 실패:" : "스케줄 생성 실패:", error)
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
        <ScheduleCreateOptionalScreen onComplete={handleOptionalComplete} onBack={handleBack} initialData={optionalData} />
      )}
    </>
  );
}
