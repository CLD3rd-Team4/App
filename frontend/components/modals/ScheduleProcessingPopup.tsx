"use client"

import { RefreshCw, X } from "lucide-react"
import React from "react"

type TimelineItem = {
  time?: string
  title: string
  // ...필요시 확장
}

type Props = {
  isOpen: boolean
  onClose: () => void
  scheduleTitle: string
  timelineItems: TimelineItem[]
  statusText: string
  isProcessing?: boolean

  /** ⬇️ 새 옵션들 */
  titleAlign?: "left" | "center"
  statusAlign?: "left" | "center"
  showLocation?: boolean
  coordText?: string // 예: "현재 위치: 37.598007, 126.931804"
}

export default function ScheduleProcessingPopup({
  isOpen,
  onClose,
  scheduleTitle,
  timelineItems,
  statusText,
  isProcessing = true,
  titleAlign = "left",
  statusAlign = "left",
  showLocation = false,
  coordText = "",
}: Props) {
  if (!isOpen) return null

  const titleCls =
    "text-xl font-semibold " + (titleAlign === "center" ? "text-center" : "")
  const statusBoxCls =
    "px-6 py-4 " + (statusAlign === "center" ? "flex flex-col items-center text-center" : "")

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center">
      <div className="w-[90%] max-w-xl bg-white rounded-2xl shadow-lg overflow-hidden">
        <div className="p-5 relative">
          <button
            aria-label="닫기"
            onClick={onClose}
            className="absolute right-4 top-4 text-gray-400 hover:text-gray-600"
          >
            <X className="w-5 h-5" />
          </button>
          <h2 className={titleCls}>{scheduleTitle}</h2>
        </div>

        {/* 본문: 타임라인이 있으면 기존 표시, 없으면 상태만 중앙 표시 */}
        {timelineItems?.length ? (
          <div className="px-6 pb-4">
            {/* 타임라인 렌더 (필요 시 기존 코드 유지) */}
          </div>
        ) : (
          <div className={statusBoxCls}>
            {showLocation && coordText && (
              <div className="text-gray-600 mb-2">{coordText}</div>
            )}
            <div className="flex items-center gap-2">
              <RefreshCw className={`w-5 h-5 ${isProcessing ? "animate-spin" : ""} text-gray-500`} />
              <span className="text-gray-700">{statusText}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
