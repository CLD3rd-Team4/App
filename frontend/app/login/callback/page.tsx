"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { useEffect, useRef } from "react"

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
            fetch("http://localhost:8080/oauth2/me/kakaoid", {
                credentials: "include",
            })
                .then((res) => {
                    if (!res.ok) throw new Error("인증 실패");
                    return res.text();
                })
                .then((text) => {
                    console.log("인증 확인:", text);
                    router.push("/");
                })
                .catch(() => {
                    sessionStorage.removeItem("kakaoLoginDone");
                    console.log("쿠키 없고 인증도 안됨");
                    router.push("/login");
                });
            return;
        }

        sessionStorage.setItem("kakaoLoginDone", "true");

        fetch(`http://localhost:8080/auth/kakao/callback?code=${code}`, {
            credentials: "include",
        })
            .then((res) => {
                if (!res.ok) throw new Error("로그인 실패");
                return res.json();
            })
            .then((data) => {
                console.log("서버 응답 메시지:", data.message);
                router.push("/");
            })
            .catch((err) => {
                console.error("로그인 실패", err);
                router.push("/login");
            });

    }, [searchParams, router]);

    return <div className="text-center mt-20">로그인 처리 중입니다...</div>;
}
