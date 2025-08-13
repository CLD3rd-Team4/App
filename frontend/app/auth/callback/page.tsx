'use client';

import { Suspense, useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import api from '../../../lib/interceptor'; // 그대로 두셔도 됩니다(아래에서는 사용 안 함)

export const dynamic = 'force-dynamic';

function KakaoCallbackInner() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const hasFetched = useRef(false); // StrictMode 중복 방지

    useEffect(() => {
        if (hasFetched.current) return;
        hasFetched.current = true;

        const code = searchParams.get('code');
        if (!code) {
            router.replace('/auth/login?error=missing_code');
            return;
        }
        console.log('카카오 로그인 code:', code);

        // 이미 콜백 처리한 브라우저라면 세션 확인만 (원하시면 일단 주석 처리하고 테스트하세요)
        if (sessionStorage.getItem('kakaoLoginDone')) {
            api.get('/auth/me/kakaoid', { withCredentials: true })
                .then(() => router.replace('/'))
                .catch(() => {
                    sessionStorage.removeItem('kakaoLoginDone');
                    router.replace('/auth/login?error=session_check_failed');
                });
            return;
        }

        // 최초 콜백 처리
        sessionStorage.setItem('kakaoLoginDone', 'true');

        // 인터셉터 우회: 동일 오리진 fetch 사용
        (async () => {
            try {
                const res = await fetch("/auth/kakao/callback", {
                    method: "POST",
                    credentials: "include",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        code,
                        redirectUri: process.env.NEXT_PUBLIC_KAKAO_REDIRECT_URI, // 예: https://www.mapzip.shop/auth/callback.html
                    }),
                });

                console.log("callback status:", res.status);

                if (!res.ok) {
                    const text = await res.text().catch(() => "");
                    console.error("callback failed:", res.status, text);
                    router.replace("/auth/login?error=callback_failed");
                    return;
                }

                router.replace("/");
            } catch (e) {
                console.error("callback network error:", e);
                router.replace("/auth/login?error=callback_network");
            }
        })();
    }, [router, searchParams]);

    return <div className="text-center mt-20">로그인 처리 중입니다…</div>;
}

export default function KakaoCallbackPage() {
    return (
        <Suspense fallback={<div className="text-center mt-20">로그인 처리 중입니다…</div>}>
            <KakaoCallbackInner />
        </Suspense>
    );
}