
import { X, RefreshCw } from "lucide-react";
import type { TimelineItem } from "@/lib/timeline";

interface ScheduleProcessingPopupProps {
  isOpen: boolean;
  onClose: () => void;
  scheduleTitle: string;
  timelineItems: TimelineItem[];
  statusText: string;
  isProcessing: boolean;
}

export default function ScheduleProcessingPopup({
  isOpen,
  onClose,
  scheduleTitle,
  timelineItems,
  statusText,
  isProcessing,
}: ScheduleProcessingPopupProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg max-w-md w-full max-h-[60vh] overflow-hidden flex flex-col">
        <div className="bg-white border-b p-4 flex items-center justify-between rounded-t-lg">
          <h2 className="text-lg font-medium">{scheduleTitle}</h2>
        </div>
        <div className="flex-1 overflow-y-auto p-4" style={{ scrollbarWidth: "thin" }}>
          <div className="space-y-3 pb-4">
            {timelineItems.map((item, index) => (
              <div key={index} className={`flex items-center gap-3 ${item.type === "meal_plan" ? "bg-orange-50 rounded-lg p-3 -mx-3" : ""}`}>
                <div className={`w-8 h-8 ${
                    item.color === "red" ? "bg-red-100" :
                    item.color === "blue" ? "bg-blue-100" :
                    item.color === "orange" ? "bg-orange-500" : "bg-green-100"
                  } rounded-full flex items-center justify-center relative`}>
                  {item.status === "calculating" && <RefreshCw className={`w-4 h-4 animate-spin ${item.color === "orange" ? "text-white" : "text-blue-600"}`} />}
                  {item.status === "completed" && <span className={`text-sm font-medium ${
                        item.color === "orange" ? "text-white" :
                        item.color === "red" ? "text-red-600" :
                        item.color === "blue" ? "text-blue-600" : "text-green-600"
                      }`}>{item.icon}</span>}
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <p className="text-sm text-gray-500">{item.time || (item.status === "calculating" ? "검색 중..." : "")}</p>
                    {item.status === "calculating" && (
                      <div className="flex space-x-1">
                        <div className="w-1 h-1 rounded-full animate-bounce bg-orange-500"></div>
                        <div className="w-1 h-1 rounded-full animate-bounce bg-orange-500" style={{ animationDelay: "0.1s" }}></div>
                        <div className="w-1 h-1 rounded-full animate-bounce bg-orange-500" style={{ animationDelay: "0.2s" }}></div>
                      </div>
                    )}
                  </div>
                  <p className="font-medium">{item.title}</p>
                  {item.description && <p className="text-sm text-gray-600">{item.description}</p>}
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="p-4 border-t">
          <div className="flex items-center justify-center gap-2 text-gray-600">
            {isProcessing && <RefreshCw className="w-5 h-5 animate-spin" />}
            <span className="text-sm">{statusText}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
