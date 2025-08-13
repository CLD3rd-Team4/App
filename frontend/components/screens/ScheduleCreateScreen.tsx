"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import useSchedule from "@/hooks/useSchedule"
import ScheduleCreateLocationScreen from "./ScheduleCreateLocationScreen"
import ScheduleCreateRequiredScreen from "./ScheduleCreateRequiredScreen"
import ScheduleCreateOptionalScreen from "./ScheduleCreateOptionalScreen"
import type { LocationData, RequiredFormData, OptionalFormData, SchedulePayload, Waypoint } from "@/types";
import { MealType } from "@/types";

type CreateStep = "location" | "required" | "optional"

export default function ScheduleCreateScreen({ isEdit = false, initialData = null }: { isEdit?: boolean, initialData?: any }) {
  const router = useRouter()
  const { createSchedule, updateSchedule } = useSchedule()
  const [currentStep, setCurrentStep] = useState<CreateStep>("location")

  const [locationData, setLocationData] = useState<LocationData | null>(null);
  const [requiredData, setRequiredData] = useState<any | null>(null);

  useEffect(() => {
    if (isEdit && initialData) {
      const location: LocationData = {
        departure: initialData.departure,
        destination: initialData.destination,
        waypoints: initialData.waypoints,
      };

      // ScheduleCreateRequiredScreen이 기대하는 타입에 맞게 데이터를 채웁니다.
      const required: any = {
        scheduleName: initialData.title,
        departureTime: initialData.departureTime,
        targetMealTimes: initialData.mealSlots.map((ms: any) => ({
          type: ms.mealType === MealType.MEAL ? '식사' : '간식',
          time: ms.scheduledTime,
          radius: `${ms.radius / 1000}km`,
        })),
        arrivalBufferMinutes: initialData.arrivalBufferMinutes,
        // 자식 컴포넌트가 요구하는 추가 필드를 초기화합니다.
        arrivalTime: initialData.calculatedArrivalTime || "", 
        estimatedArrivalTime: initialData.calculatedArrivalTime || "",
      };
      
      setLocationData(location);
      setRequiredData(required);
    }
  }, [isEdit, initialData]);

  const handleLocationNext = (data: LocationData) => {
    setLocationData(data)
    setCurrentStep("required")
  }

  const handleRequiredNext = (data: any) => {
    setRequiredData(data)
    setCurrentStep("optional")
  }

  const handleOptionalComplete = async (optionalData: any) => {
    if (!locationData || !requiredData) {
        alert("위치 정보 또는 필수 정보가 없습니다.");
        return;
    }

    try {
      // 데이터 변환 로직 (UI -> API)
      const companionsArray = optionalData.companions 
        ? (typeof optionalData.companions === 'string' 
            ? optionalData.companions.split(',').map((c: string) => c.trim()).filter(Boolean) 
            : Array.from(optionalData.companions))
        : [];

      const payload: SchedulePayload = {
        title: requiredData.scheduleName,
        departureTime: requiredData.departureTime,
        departure: locationData.departure!,
        destination: locationData.destination!,
        waypoints: locationData.waypoints?.filter(Boolean) as Waypoint[] || [],
        mealSlots: requiredData.targetMealTimes.map((mt: any) => ({
          mealType: mt.type === '식사' ? MealType.MEAL : MealType.SNACK,
          scheduledTime: mt.time,
          radius: parseInt(mt.radius.replace('km', '000'), 10),
        })),
        purpose: optionalData.travelPurpose,
        companions: companionsArray,
        userNote: optionalData.userRequirements,
        arrivalBufferMinutes: requiredData.arrivalBufferMinutes || 30, // 기본값 설정
      };

      if (isEdit && initialData?.id) {
        await updateSchedule(initialData.id, payload);
      } else {
        await createSchedule(payload);
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
        <ScheduleCreateRequiredScreen onNext={handleRequiredNext} onBack={handleBack} initialData={requiredData || undefined} />
      )}
      {currentStep === "optional" && (
        <ScheduleCreateOptionalScreen
          onComplete={handleOptionalComplete}
          onBack={handleBack}
        />
      )}
    </>
  );
}
