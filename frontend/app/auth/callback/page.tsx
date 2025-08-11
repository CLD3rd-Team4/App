'use client';

import { Suspense, useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import api from '../../../lib/interceptor';

export const dynamic = 'force-dynamic';
export const revalidate = 0;

function KakaoCallbackInner() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const hasFetched = useRef(false); // 중복 방지

    useEffect(() => {
        if (hasFetched.current) return;
        hasFetched.current = true;

        const code = searchParams.get('code');
        if (!code) {
            router.replace('/auth/login');
            return;
        }

        // 이미 콜백 처리한 브라우저라면 세션 확인만
        if (sessionStorage.getItem('kakaoLoginDone')) {
            api
                .get('/auth/me/kakaoid')
                .then(() => router.push('/'))
                .catch(() => {
                    sessionStorage.removeItem('kakaoLoginDone');
                    router.push('/auth/login');
                });
            return;
        }

        // 최초 콜백 처리
        sessionStorage.setItem('kakaoLoginDone', 'true');

        api
            .post(`/auth/kakao/callback?code=${code}`)
            .then(() => router.push('/'))
            .catch(() => router.push('/auth/login'));
    }, [router]); // searchParams는 값 읽기만 하고, 실행은 1회로 고정

    return <div className="text-center mt-20">로그인 처리 중입니다...</div>;
}

export default function KakaoCallbackPage() {
    return (
        <Suspense fallback={<div className="text-center mt-20">로그인 처리 중입니다...</div>}>
            <KakaoCallbackInner />
        </Suspense>
    );
}