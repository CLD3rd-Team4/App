'use client';

import { Suspense, useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

export const dynamic = 'force-dynamic';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'https://api.mapzip.shop';

function KakaoCallbackInner() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const once = useRef(false);

    useEffect(() => {
        if (once.current) return;
        once.current = true;

        const code = searchParams.get('code');
        if (!code) {
            router.replace('/auth/login?error=missing_code');
            return;
        }

        (async () => {
            try {
                const url = new URL(`${API_BASE}/auth/kakao/callback`);
                url.searchParams.set('code', code);

                const res = await fetch(url.toString(), {
                    method: 'POST',
                    credentials: 'include',
                });

                if (!res.ok) {
                    const ct = res.headers.get('content-type') || '';
                    const body = ct.includes('application/json')
                        ? JSON.stringify(await res.json()).slice(0, 400)
                        : (await res.text()).slice(0, 400);
                    console.error('[callback] failed:', res.status, body);
                    router.replace('/auth/login?error=callback_failed');
                    return;
                }

                sessionStorage.setItem('kakaoLoginDone', 'true');
                router.replace('/');
            } catch (e) {
                console.error('[callback] network error:', e);
                router.replace('/auth/login?error=callback_network');
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