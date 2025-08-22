
import type { Schedule, Waypoint, MealSlot } from "@/types";
import { MealType } from "@/types";

// 팝업에서 사용할 타임라인 아이템의 타입 정의
export type TimelineItem = {
  type: "departure" | "waypoint" | "destination" | "meal_plan";
  time?: string;
  title: string;
  icon: string;
  color: string;
  description?: string;
  status: "pending" | "calculating" | "completed";
};

/**
 * 스케줄 데이터를 기반으로 팝업에 표시될 타임라인 아이템 배열을 생성합니다.
 * @param schedule - 변환할 스케줄 데이터
 * @returns 정렬된 TimelineItem 배열
 */
export const generateTimelineItems = (schedule: Schedule): TimelineItem[] => {
  const items: TimelineItem[] = [];

  // 1. 출발지 추가
  if (schedule.departure) {
    items.push({
      type: "departure",
      time: schedule.departureTime,
      title: schedule.departure.name,
      icon: "출발",
      color: "red",
      status: "completed",
    });
  }

  // 2. 경유지와 식사 슬롯을 시간순으로 정렬하기 위해 합침
  const intermediatePoints: any[] = [];
  if (schedule.waypoints) {
    intermediatePoints.push(...schedule.waypoints.map(w => ({...w, sortTime: w.arrivalTime || '00:00', itemType: 'waypoint'})));
  }

  // TODO: 더 정확한 시간 포맷 파싱 및 정렬 로직 필요
  intermediatePoints.sort((a, b) => a.sortTime.localeCompare(b.sortTime));

  // 3. 정렬된 경유지 및 식사 슬롯 추가
  intermediatePoints.forEach(point => {
    if (point.itemType === 'waypoint') {
        items.push({
            type: "waypoint",
            title: point.name,
            icon: "경유",
            color: "blue",
            status: "completed",
        });
    } else if (point.itemType === 'meal_slot') {
        const isMeal = point.mealType === MealType.MEAL;
        items.push({
            type: "meal_plan",
            time: point.scheduledTime,
            title: isMeal ? "식사" : "간식",
            description: `반경 ${point.radius / 1000}km 내`,
            icon: isMeal ? "🍽️" : "🍰",
            color: "orange",
            status: "calculating",
        });
    }
  });

  // 4. 도착지 추가
  if (schedule.destination) {
    items.push({
      type: "destination",
      title: schedule.destination.name,
      icon: "도착",
      color: "green",
      status: "completed",
    });
  }

  return items;
};
