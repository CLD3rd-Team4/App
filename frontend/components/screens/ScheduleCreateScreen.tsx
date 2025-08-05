"use client"

import { useState, useEffect } from "react"
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

  // 각 단계의 상태를 초기화합니다.
  const [locationData, setLocationData] = useState<any>(null);
  const [requiredData, setRequiredData] = useState<any>(null);
  const [optionalData, setOptionalData] = useState<any>(null);

  // initialData prop이 비동기적으로 업데이트될 때 상태를 동기화하는 useEffect
  useEffect(() => {
    if (initialData) {
      setLocationData({
        departure: initialData.departure,
        waypoints: initialData.waypoints,
        destination: initialData.destination,
      });
      setRequiredData({
        scheduleName: initialData.title,
        departureTime: initialData.departureTime,
        targetMealTimes: initialData.mealSlots.map((ms: any) => ({
          type: ms.mealType === 'MEAL' ? '식사' : '간식',
          time: ms.scheduledTime,
          radius: ms.radius / 1000, // m -> km
        })),
      });
      setOptionalData({
        userRequirements: initialData.userNote,
        travelPurpose: initialData.purpose,
        companions: initialData.companions,
      });
    }
  }, [initialData]);

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
        arrivalTime: initialData?.arrivalTime || "",
        mealSlots: requiredData.targetMealTimes.map((mt: any) => ({
          mealType: mt.type === '식사' ? 'MEAL' : 'SNACK',
          scheduledTime: mt.time,
          radius: (parseFloat(mt.radius) || 1) * 1000, // km -> m
        })),
        departure: locationData.departure,
        waypoints: locationData.waypoints,
        destination: locationData.destination,
        userNote: finalOptionalData.userRequirements,
        purpose: finalOptionalData.travelPurpose,
        companions: finalOptionalData.companions,
      };

      if (isEdit && initialData?.id) {
        const finalScheduleData = {
          ...scheduleData,
          id: initialData.id,
          hasMeal: initialData.hasMeal || false, // 기본값으로 false
          tags: initialData.tags || [],         // 기본값으로 빈 배열
        };
        await updateSchedule(finalScheduleData);
      } else {
        await createSchedule(scheduleData);
      }
      
      router.push("/schedule");
    } catch (error) {
      console.error(isEdit ? "스케줄 수정 실패:" : "스케줄 생성 실패:", error);
      alert(isEdit ? "스케줄 수정에 실패했습니다." : "스케줄 생성에 실패했습니다.");
    }
  };

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
        <ScheduleCreateOptionalScreen
          onComplete={handleOptionalComplete}
          onBack={handleBack}
          initialData={optionalData}
          isEdit={isEdit} // isEdit prop 전달
        />
      )}
    </>
  );
}
