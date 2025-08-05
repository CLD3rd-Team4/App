import axios from "axios"
import { toast } from "react-toastify"

const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_BASE_URL || "",
    withCredentials: true,
})

let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

// 토큰 재요청 기다리는 요청들 처리
function subscribeTokenRefresh(cb: (token: string) => void) {
    refreshSubscribers.push(cb)
}

function onTokenRefreshed(newToken: string) {
    refreshSubscribers.forEach((cb) => cb(newToken))
    refreshSubscribers = []
}

// 요청 인터셉터
api.interceptors.request.use((config) => {
    const token = localStorage.getItem("accessToken")
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// 응답 인터셉터
api.interceptors.response.use(
    (res) => res,
    async (error) => {
        const {
            config,
            response: { status, data },
        } = error

        const originalRequest = config

        // ===== JWT 관련 처리 =====
        if (status === 401 && data?.code === "TOKEN_EXPIRED") {
            if (!isRefreshing) {
                isRefreshing = true

                try {
                    const refreshToken = localStorage.getItem("refreshToken")
                    const res = await axios.post(
                        `${process.env.NEXT_PUBLIC_API_BASE_URL}/auth/refresh`,
                        {},
                        {
                            headers: {
                                Authorization: `Bearer ${refreshToken}`,
                            },
                        }
                    )

                    const newAccessToken = res.data.accessToken
                    localStorage.setItem("accessToken", newAccessToken)

                    onTokenRefreshed(newAccessToken)
                    isRefreshing = false

                    // 실패한 요청 재시도
                    return new Promise((resolve) => {
                        subscribeTokenRefresh((token) => {
                            originalRequest.headers.Authorization = `Bearer ${token}`
                            resolve(api(originalRequest))
                        })
                    })
                } catch (e) {
                    // 리프레시 토큰 만료 시
                    toast.warn("세션이 만료되었습니다. 다시 로그인해주세요.")
                    localStorage.removeItem("accessToken")
                    localStorage.removeItem("refreshToken")
                    window.location.href = "/login"
                    return
                }
            }

            // 이미 리프레시 중인 경우 대기
            return new Promise((resolve) => {
                subscribeTokenRefresh((token) => {
                    originalRequest.headers.Authorization = `Bearer ${token}`
                    resolve(api(originalRequest))
                })
            })
        }

        if (status === 401 && data?.code === "TOKEN_INVALID") {
            toast.warn("인증되지 않은 사용자입니다. 다시 로그인해주세요.")
            localStorage.removeItem("accessToken")
            localStorage.removeItem("refreshToken")
            window.location.href = "/login"
            return
        }

        // ===== 일반 에러 처리 =====
        if ([403, 404, 500, 503].includes(status)) {
            toast.error(data?.message || "문제가 발생했습니다. 홈으로 이동합니다.")
            window.location.href = "/"
            return
        }

        return Promise.reject(error)
    }
)

export default api
