'use client';

import { useEffect, useState, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { scheduleApi } from '@/services/api';
import ScheduleCreateScreen from '@/components/screens/ScheduleCreateScreen';
import type { Schedule } from '@/types';

function EditPageContent() {
  const searchParams = useSearchParams();
  const id = searchParams.get('id');
  const [initialData, setInitialData] = useState<Schedule | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id) {
      const fetchScheduleDetail = async () => {
        try {
          setIsLoading(true);
          // userId는 JWT 토큰에서 자동으로 추출
          const data = await scheduleApi.getScheduleDetail(id);
          setInitialData(data.schedule);
        } catch (err) {
          setError('스케줄 정보를 불러오는데 실패했습니다.');
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

export default function ScheduleEditPage() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <EditPageContent />
    </Suspense>
  );
}
