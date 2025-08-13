"use client";

import { Suspense, useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";

export const dynamic = "force-dynamic";

// 게이트웨이 라우팅이 잡히기 전엔 API 호스트로 보냄
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "https://api.mapzip.shop";

function KakaoCallbackInner() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const once = useRef(false); // StrictMode 중복 방지

    useEffect(() => {
        if (once.current) return;
        once.current = true;

        const code = searchParams.get("code");
        if (!code) {
            router.replace("/auth/login?error=missing_code");
            return;
        }

        (async () => {
            try {
                const res = await fetch(`${API_BASE}/auth/kakao/callback`, {
                    method: "POST",
                    credentials: "include",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        code,
                        redirectUri: process.env.NEXT_PUBLIC_KAKAO_REDIRECT_URI,
                    }),
                });

                if (!res.ok) {
                    const ct = res.headers.get("content-type") || "";
                    const body = ct.includes("application/json")
                        ? JSON.stringify(await res.json()).slice(0, 400)
                        : (await res.text()).slice(0, 400);
                    console.error("[callback] failed:", res.status, body);
                    router.replace("/auth/login?error=callback_failed");
                    return;
                }

                // 성공한 뒤에만 done 플래그 기록 (실패 시 재시도 가능)
                sessionStorage.setItem("kakaoLoginDone", "true");

                router.replace("/");
            } catch (e) {
                console.error("[callback] network error:", e);
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