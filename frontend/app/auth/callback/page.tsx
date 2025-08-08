"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { useEffect, useRef } from "react"
import api from "../../../lib/interceptor"

export default function KakaoCallbackPage() {
    const router = useRouter()
    const searchParams = useSearchParams()
    const hasFetched = useRef(false) // 중복 방지 플래그

    useEffect(() => {
        if (hasFetched.current) return;
        hasFetched.current = true;

        console.log("카카오 로그인 useEffect 실행됨");

        const code = searchParams.get("code");
        console.log("code:", code);
        if (!code) return;

        if (sessionStorage.getItem("kakaoLoginDone")) {
            api.get("/oauth2/me/kakaoid")
                .then((res) => {
                    console.log("인증 확인:", res.data);
                    router.push("/");
                })
                .catch((err) => {
                    console.log("인증 실패 또는 쿠키 없음", err);
                    sessionStorage.removeItem("kakaoLoginDone");
                    router.push("/auth/login");
                });
            return;
        }

        sessionStorage.setItem("kakaoLoginDone", "true");

        api.post(`/auth/kakao/callback?code=${code}`)
            .then((res) => {
                console.log("서버 응답 메시지:", res.data.message);
                router.push("/"); // 로그인 성공
            })
            .catch((err) => {
                console.error("로그인 실패", err)
                router.push("/auth/login")
            });

    }, [searchParams, router]);

    return <div className="text-center mt-20">로그인 처리 중입니다...</div>;
}
