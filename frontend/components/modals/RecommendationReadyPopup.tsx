
interface RecommendationReadyPopupProps {
  isOpen: boolean;
  onViewResults: () => void;
  onGoBack: () => void;
}

export default function RecommendationReadyPopup({
  isOpen,
  onViewResults,
  onGoBack,
}: RecommendationReadyPopupProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg max-w-sm w-full">
        <div className="p-6 text-center">
          <div className="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <span className="text-2xl">🍽️</span>
          </div>
          <h3 className="text-lg font-medium mb-2">추천 결과 준비 완료!</h3>
          <p className="text-gray-600 mb-6">맞춤형 식당 추천이 준비되었습니다.</p>
          <div className="space-y-3">
            <button
              onClick={onViewResults}
              className="w-full bg-blue-500 hover:bg-blue-600 text-white py-3 px-4 rounded-lg font-medium"
            >
              추천 결과 보기
            </button>
            <button
              onClick={onGoBack}
              className="w-full border border-gray-300 hover:bg-gray-50 text-gray-700 py-2 px-4 rounded-lg font-medium"
            >
              이전으로
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
