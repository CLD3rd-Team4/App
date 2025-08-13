'use client';

import { Suspense, useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import api from '../../../lib/interceptor';

// export const dynamic = 'force-dynamic';

function KakaoCallbackInner() {
    const router = useRouter();
    const hasFetched = useRef(false); // StrictMode 중복 방지

    useEffect(() => {
        if (hasFetched.current) return;
        hasFetched.current = true;

        const urlParams = new URLSearchParams(window.location.search);
        const code = urlParams.get('code');
        if (!code) {
            router.replace('/auth/login?error=missing_code');
            return;
        }

        // 이미 콜백 처리한 브라우저라면 세션 확인만
        if (sessionStorage.getItem('kakaoLoginDone')) {
            api.get('/auth/me/kakaoid')
                .then(() => router.replace('/')) // 뒤로가기로 콜백 안 돌아오게 replace
                .catch(() => {
                    sessionStorage.removeItem('kakaoLoginDone');
                    router.replace('/auth/login?error=session_check_failed');
                });
            return;
        }

        // 최초 콜백 처리
        sessionStorage.setItem('kakaoLoginDone', 'true');

        api.post(`/auth/kakao/callback?code=${code}`)
            .then(() => router.replace('/'))
            .catch(() => router.replace('/auth/login?error=callback_failed'));
    }, [router]);

    return <div className="text-center mt-20">로그인 처리 중입니다…</div>;
}

export default function KakaoCallbackPage() {
    return (
        <Suspense fallback={<div className="text-center mt-20">로그인 처리 중입니다…</div>}>
            <KakaoCallbackInner />
        </Suspense>
    );
}