"use client"

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { scheduleApi } from "@/services/api";
import ScheduleCreateScreen from "@/components/screens/ScheduleCreateScreen";
import type { Schedule } from "@/types";

export default function ScheduleEditPage() {
  const params = useParams();
  const id = params.id as string;
  const [initialData, setInitialData] = useState<Schedule | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id) {
      const fetchScheduleDetail = async () => {
        try {
          setIsLoading(true);
          // TODO: 실제 userId로 교체 필요
          const data = await scheduleApi.getScheduleDetail(id, "test-user-123");
          console.log("API Response:", JSON.stringify(data, null, 2)); // 응답 데이터 확인
          setInitialData(data.schedule);
        } catch (err) {
          setError("스케줄 정보를 불러오는데 실패했습니다.");
          console.error(err);
        } finally {
          setIsLoading(false);
        }
      };
      fetchScheduleDetail();
    }
  }, [id]);

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-screen">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">스케줄 정보 로딩 중...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex justify-center items-center h-screen">
        <p className="text-red-500">{error}</p>
      </div>
    );
  }

  if (!initialData) {
    return (
      <div className="flex justify-center items-center h-screen">
        <p>스케줄 정보를 찾을 수 없습니다.</p>
      </div>
    );
  }

  return <ScheduleCreateScreen isEdit={true} initialData={initialData} />;
}