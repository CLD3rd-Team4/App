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

// OptionalData 타입을 OptionalScreen에서 가져오거나 여기에 정의
interface OptionalData {
  userRequirements: string;
  travelPurpose: string;
  companions: string[];
}

export default function ScheduleCreateScreen({ isEdit = false, initialData = null }: { isEdit?: boolean, initialData?: any }) {
  const router = useRouter()
  const { createSchedule, updateSchedule } = useSchedule()
  const [currentStep, setCurrentStep] = useState<CreateStep>("location")
  
  const [editId, setEditId] = useState<string | null>(null);
  const [locationData, setLocationData] = useState<LocationData | null>(null);
  const [requiredData, setRequiredData] = useState<any | null>(null);
  const [optionalData, setOptionalData] = useState<OptionalData | null>(null);

  useEffect(() => {
    if (isEdit && initialData) {
      setEditId(initialData.id);

      const location: LocationData = {
        departure: initialData.departure,
        destination: initialData.destination,
        waypoints: initialData.waypoints,
      };

      const required: any = {
        scheduleName: initialData.title,
        departureTime: initialData.departureTime,
        targetMealTimes: initialData.mealSlots.map((ms: any) => ({
          type: Number(ms.mealType) === MealType.MEAL ? '식사' : '간식',
          time: ms.scheduledTime,
          radius: `${ms.radius / 1000}km`,
        })),
        arrivalBufferMinutes: initialData.arrivalBufferMinutes,
        arrivalTime: initialData.calculatedArrivalTime || "", 
        estimatedArrivalTime: initialData.calculatedArrivalTime || "",
      };

      const optional: OptionalData = {
        userRequirements: initialData.userNote || "",
        travelPurpose: initialData.purpose || "",
        companions: initialData.companions || [],
      };
      
      setLocationData(location);
      setRequiredData(required);
      setOptionalData(optional);
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

  const handleOptionalComplete = async (optionalDataFromChild: OptionalData) => {
    if (!locationData || !requiredData) {
        alert("위치 정보 또는 필수 정보가 없습니다.");
        return;
    }

    try {
      // optionalDataFromChild.companions는 항상 string[] 타입이므로, 타입 검사 로직을 단순화합니다.
      const companionsArray = optionalDataFromChild.companions || [];

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
        purpose: optionalDataFromChild.travelPurpose,
        companions: companionsArray,
        userNote: optionalDataFromChild.userRequirements,
        arrivalBufferMinutes: requiredData.arrivalBufferMinutes || 30,
      };

      if (isEdit && editId) {
        await updateSchedule(editId, payload);
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
          initialData={optionalData || undefined}
          isEdit={isEdit}
        />
      )}
    </>
  );
}