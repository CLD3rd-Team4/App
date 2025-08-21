"use client"

import { RefreshCw, X } from "lucide-react"
import React from "react"

type TimelineItem = {
  time?: string
  title: string
  description?: string
}

type Props = {
  isOpen: boolean
  onClose: () => void
  scheduleTitle: string
  statusText: string
  isProcessing?: boolean

  /** 새 옵션 */
  variant?: "timeline" | "location"

  /** timeline 모드 */
  timelineItems?: TimelineItem[]

  /** location 모드 */
  coordText?: string // 예: "37.598007, 126.931804"
}

export default function ScheduleProcessingPopup({
  isOpen,
  onClose,
  scheduleTitle,
  statusText,
  isProcessing = true,
  variant = "timeline",
  timelineItems = [],
  coordText = "",
}: Props) {
  if (!isOpen) return null

  const titleCls =
    "text-xl font-semibold " + (variant === "location" ? "text-center" : "")
  const Spinner = (
    <RefreshCw className={`w-5 h-5 ${isProcessing ? "animate-spin" : ""} text-gray-500`} />
  )

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

        {variant === "timeline" ? (
          <>
            {/* 일정 요약 */}
            <div className="px-6 pb-2">
              <ul className="divide-y">
                {(timelineItems || []).map((it, idx) => (
                  <li key={idx} className="py-3 flex items-start gap-3">
                    <div className="w-16 shrink-0 text-sm text-gray-500">
                      {it.time || ""}
                    </div>
                    <div className="flex-1">
                      <p className="font-medium">{it.title}</p>
                      {it.description && (
                        <p className="text-sm text-gray-600 mt-0.5">{it.description}</p>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
            {/* 하단 상태줄 */}
            <div className="px-6 py-4 border-t flex items-center gap-2">
              {Spinner}
              <span className="text-gray-700">{statusText}</span>
            </div>
          </>
        ) : (
          // location 모드: 중앙 정렬 + 현재 위치
          <div className="px-6 py-8 text-center">
            {coordText && (
              <div className="text-gray-700 mb-3">{coordText}</div>
            )}
            <div className="inline-flex items-center gap-2">
              {Spinner}
              <span className="text-gray-700">{statusText}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
