import ScheduleEditClientPage from "./ScheduleEditClientPage"

// 정적 생성을 위한 함수들
export async function generateStaticParams() {
  // 가능한 모든 ID를 미리 생성 (실제로는 API에서 가져와야 함)
  const ids = ["1", "2", "3", "4", "5"] // 예시 ID들

  return ids.map((id) => ({
    id: id,
  }))
}

export default function ScheduleEditPage() {
  return <ScheduleEditClientPage />
}
