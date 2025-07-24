"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { ArrowLeft } from "lucide-react"

interface OptionalData {
  userRequirements: string
  travelPurpose: string
  companions: string[]
}

interface ScheduleCreateOptionalScreenProps {
  onComplete: (data: OptionalData) => void
  onBack: () => void
  initialData?: OptionalData
}

export default function ScheduleCreateOptionalScreen({
  onComplete,
  onBack,
  initialData,
}: ScheduleCreateOptionalScreenProps) {
  const [formData, setFormData] = useState<OptionalData>(
    initialData || {
      userRequirements: "",
      travelPurpose: "",
      companions: [],
    },
  )

  const companionOptions = ["혼자", "부모님", "연인", "지인", "자녀"]

  const toggleCompanion = (companion: string) => {
    setFormData((prev) => ({
      ...prev,
      companions: prev.companions.includes(companion)
        ? prev.companions.filter((c) => c !== companion)
        : [...prev.companions, companion],
    }))
  }

  const handleComplete = () => {
    onComplete(formData)
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm flex items-center">
        <Button onClick={onBack} variant="ghost" size="sm" className="mr-3">
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <h1 className="text-lg font-medium text-blue-600">선택 정보 입력</h1>
      </div>

      <div className="flex-1 pb-20 bg-gray-100 overflow-y-auto">
        <div className="p-4 space-y-6">
          <p className="text-sm text-gray-600">사용자 요구사항을 입력해주세요.</p>

          {/* 사용자 요구사항 */}
          <div>
            <Label htmlFor="userRequirements" className="text-base font-medium">
              사용자 요구사항
            </Label>
            <Textarea
              id="userRequirements"
              value={formData.userRequirements}
              onChange={(e) => setFormData((prev) => ({ ...prev, userRequirements: e.target.value }))}
              placeholder="예) 비건식을 원합니다"
              className="mt-2 min-h-[80px]"
            />
          </div>

          {/* 이동 목적 */}
          <div>
            <Label htmlFor="travelPurpose" className="text-base font-medium">
              이동 목적
            </Label>
            <Input
              id="travelPurpose"
              value={formData.travelPurpose}
              onChange={(e) => setFormData((prev) => ({ ...prev, travelPurpose: e.target.value }))}
              placeholder="예) 출장"
              className="mt-2"
            />
          </div>

          {/* 동행자 */}
          <div>
            <Label className="text-base font-medium">동행자</Label>
            <div className="flex flex-wrap gap-2 mt-2">
              {companionOptions.map((companion) => (
                <Button
                  key={companion}
                  onClick={() => toggleCompanion(companion)}
                  variant={formData.companions.includes(companion) ? "default" : "outline"}
                  size="sm"
                  className={formData.companions.includes(companion) ? "bg-blue-500 text-white" : ""}
                >
                  {companion}
                </Button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Fixed Progress Bar and Complete Button - 하단 네비게이션과 같은 높이로 조정 */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t z-50">
        <div className="max-w-md mx-auto p-4">
          <div className="flex items-center justify-between mb-3">
            <div className="flex-1 bg-gray-200 rounded-full h-2">
              <div className="bg-blue-500 h-2 rounded-full w-full"></div>
            </div>
            <span className="text-sm text-gray-600 ml-3">2/2</span>
          </div>
          <Button onClick={handleComplete} className="w-full bg-blue-500 hover:bg-blue-600 text-white py-2">
            완료
          </Button>
        </div>
      </div>
    </div>
  )
}
