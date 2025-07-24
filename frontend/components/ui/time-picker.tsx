"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ChevronDown, ChevronUp } from "lucide-react"

interface TimePickerProps {
  value: string
  onChange: (time: string) => void
  label: string
}

export function TimePicker({ value, onChange, label }: TimePickerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [selectedHour, setSelectedHour] = useState(Number.parseInt(value.split(":")[0]) || 12)
  const [selectedMinute, setSelectedMinute] = useState(Number.parseInt(value.split(":")[1]) || 0)

  const hours = Array.from({ length: 24 }, (_, i) => i)
  const minutes = Array.from({ length: 60 }, (_, i) => i)

  const handleTimeChange = (hour: number, minute: number) => {
    const timeString = `${hour.toString().padStart(2, "0")}:${minute.toString().padStart(2, "0")}`
    onChange(timeString)
    setSelectedHour(hour)
    setSelectedMinute(minute)
  }

  const formatTime = (time: string) => {
    const [hour, minute] = time.split(":")
    const h = Number.parseInt(hour)
    const period = h >= 12 ? "오후" : "오전"
    const displayHour = h === 0 ? 12 : h > 12 ? h - 12 : h
    return `${period} ${displayHour}:${minute}`
  }

  return (
    <div className="bg-white rounded-lg border p-4">
      <div className="flex items-center justify-between">
        <span className="font-medium">{label}</span>
        <Button onClick={() => setIsOpen(!isOpen)} variant="outline" size="sm" className="flex items-center gap-2">
          {formatTime(value)}
          <ChevronDown className={`w-4 h-4 transition-transform ${isOpen ? "rotate-180" : ""}`} />
        </Button>
      </div>

      {isOpen && (
        <div className="mt-4 bg-gray-50 rounded-lg p-4">
          <div className="flex gap-4 justify-center">
            {/* Hour Picker */}
            <div className="flex flex-col items-center">
              <Button
                onClick={() => handleTimeChange((selectedHour + 1) % 24, selectedMinute)}
                variant="ghost"
                size="sm"
              >
                <ChevronUp className="w-4 h-4" />
              </Button>
              <div className="text-2xl font-bold py-2 w-12 text-center">{selectedHour.toString().padStart(2, "0")}</div>
              <Button
                onClick={() => handleTimeChange((selectedHour - 1 + 24) % 24, selectedMinute)}
                variant="ghost"
                size="sm"
              >
                <ChevronDown className="w-4 h-4" />
              </Button>
            </div>

            <div className="text-2xl font-bold py-2">:</div>

            {/* Minute Picker */}
            <div className="flex flex-col items-center">
              <Button
                onClick={() => handleTimeChange(selectedHour, (selectedMinute + 5) % 60)}
                variant="ghost"
                size="sm"
              >
                <ChevronUp className="w-4 h-4" />
              </Button>
              <div className="text-2xl font-bold py-2 w-12 text-center">
                {selectedMinute.toString().padStart(2, "0")}
              </div>
              <Button
                onClick={() => handleTimeChange(selectedHour, (selectedMinute - 5 + 60) % 60)}
                variant="ghost"
                size="sm"
              >
                <ChevronDown className="w-4 h-4" />
              </Button>
            </div>
          </div>

          <Button onClick={() => setIsOpen(false)} className="w-full mt-4 bg-blue-500 hover:bg-blue-600 text-white">
            확인
          </Button>
        </div>
      )}
    </div>
  )
}
