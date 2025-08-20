'use client';

import type React from "react";
import { useEffect, useState, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { reviewApi, APIError } from '@/services/api';
import { Button } from "@/components/ui/button";
import { ArrowLeft, Star, Edit, Save, X } from "lucide-react";
import { Textarea } from "@/components/ui/textarea";

// 상세 페이지의 실제 콘텐츠
function ReviewDetailContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const restaurantId = searchParams.get('restaurantId');
  const reviewId = searchParams.get('reviewId');

  const [review, setReview] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // 수정 관련 상태
  const [isEditing, setIsEditing] = useState(false);
  const [editedRating, setEditedRating] = useState(0);
  const [editedContent, setEditedContent] = useState("");

  useEffect(() => {
    if (restaurantId && reviewId) {
      const fetchReviewDetail = async () => {
        try {
          setIsLoading(true);
          const response = await reviewApi.getReview(restaurantId, reviewId);
          if (response.success && response.data) {
            setReview(response.data);
            setEditedRating(response.data.rating);
            setEditedContent(response.data.content);
          } else {
            setError(response.message || '리뷰 정보를 찾을 수 없습니다.');
          }
        } catch (err: any) {
          setError('리뷰 정보를 불러오는데 실패했습니다.');
          console.error(err);
        } finally {
          setIsLoading(false);
        }
      };
      fetchReviewDetail();
    }
  }, [restaurantId, reviewId]);

  const handleDelete = async () => {
    if (!restaurantId || !reviewId) return;

    if (window.confirm("정말로 이 리뷰를 삭제하시겠습니까?")) {
      try {
        await reviewApi.deleteReview(restaurantId, reviewId);
        alert("리뷰가 삭제되었습니다.");
        router.push("/visited");
      } catch (err: any) {
        alert(`리뷰 삭제에 실패했습니다: ${err.message || '알 수 없는 오류'}`);
        console.error(err);
      }
    }
  };
  
  const handleSave = async () => {
    if (!restaurantId || !reviewId) return;

    try {
      const updatedData = { rating: editedRating, content: editedContent };
      await reviewApi.updateReview(restaurantId, reviewId, updatedData);
      alert("리뷰가 수정되었습니다.");
      // 상태를 다시 로드하거나 로컬에서 업데이트
      setReview({ ...review, rating: editedRating, content: editedContent });
      setIsEditing(false);
    } catch (err: any) {
      alert(`리뷰 수정에 실패했습니다: ${err.message || '알 수 없는 오류'}`);
      console.error(err);
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-screen">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-600">리뷰 정보 로딩 중...</p>
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

  if (!review) {
    return (
      <div className="flex justify-center items-center h-screen">
        <p>리뷰 정보를 찾을 수 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-white p-4 shadow-sm flex items-center justify-between">
        <div className="flex items-center">
          <Button onClick={() => router.back()} variant="ghost" size="sm" className="mr-3">
            <ArrowLeft className="w-5 h-5" />
          </Button>
          <h1 className="text-lg font-medium">리뷰 상세</h1>
        </div>
        {isEditing ? (
            <Button onClick={() => setIsEditing(false)} variant="ghost" size="sm"><X className="w-4 h-4" /></Button>
        ) : (
            <Button onClick={() => setIsEditing(true)} variant="ghost" size="sm"><Edit className="w-4 h-4" /></Button>
        )}
      </div>

      <div className="flex-1 p-4 space-y-4">
        <div className="bg-white rounded-lg p-4 shadow-sm">
          <h2 className="text-lg font-medium mb-2">{review.restaurantName}</h2>
          <p className="text-sm text-gray-600 mb-2">{review.restaurantAddress}</p>
          <div className="flex items-center">
            {[1, 2, 3, 4, 5].map((star) => (
              <Star
                key={star}
                onClick={() => isEditing && setEditedRating(star)}
                className={`w-5 h-5 ${
                  star <= (isEditing ? editedRating : review.rating)
                    ? "text-yellow-400 fill-current"
                    : "text-gray-300"
                } ${isEditing ? 'cursor-pointer' : ''}`}
              />
            ))}
          </div>
        </div>

        <div className="bg-white rounded-lg p-4 shadow-sm">
          <h3 className="font-medium mb-3">리뷰 내용</h3>
          {isEditing ? (
            <Textarea 
              value={editedContent} 
              onChange={(e) => setEditedContent(e.target.value)}
              className="min-h-[150px]"
            />
          ) : (
            <p className="text-gray-700 leading-relaxed">{review.content}</p>
          )}
        </div>

        <div className="flex gap-3">
          {isEditing ? (
            <Button onClick={handleSave} className="w-full bg-blue-500 hover:bg-blue-600 text-white">
              <Save className="w-4 h-4 mr-2" /> 저장하기
            </Button>
          ) : (
            <Button onClick={handleDelete} variant="outline" className="w-full text-red-600 border-red-200">
              삭제하기
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

// Suspense로 감싸서 useSearchParams 사용 준비
export default function ReviewDetailPage() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <ReviewDetailContent />
    </Suspense>
  );
}
