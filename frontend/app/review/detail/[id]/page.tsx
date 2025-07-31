import ReviewDetailPageClient from "./ReviewDetailPageClient"

export async function generateStaticParams() {
  // 가능한 모든 리뷰 ID를 미리 생성
  const ids = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10"]

  return ids.map((id) => ({
    id: id,
  }))
}

export default function ReviewDetailPage() {
  return <ReviewDetailPageClient />
}
