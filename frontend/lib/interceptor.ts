import axios from "axios"
import { toast } from "react-toastify"

const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_BASE_URL || "",
    withCredentials: true,
})

let isRefreshing = false

type Subscriber = {
    resolve: (value?: any) => void
    reject: (error?: any) => void
}

let refreshSubscribers: Subscriber[] = []

function subscribeTokenRefresh(
    resolve: (value?: any) => void,
    reject: (error?: any) => void
) {
    refreshSubscribers.push({ resolve, reject })
}

function onTokenRefreshed() {
    refreshSubscribers.forEach(({ resolve }) => resolve())
    refreshSubscribers = []
}

function onRefreshFailed(error: any) {
    refreshSubscribers.forEach(({ reject }) => reject(error))
    refreshSubscribers = []
}

// 응답 인터셉터
api.interceptors.response.use(
    (res) => res,
    async (error) => {
        const {
        config,
        response: { status, data },
        } = error

        const originalRequest = config

        if (status === 401 && data?.error === "TOKEN_EXPIRED") {
        if (!isRefreshing) {
            isRefreshing = true

            try {
            // 리프레시 토큰으로 액세스 토큰 갱신
            await axios.post(
                `${process.env.NEXT_PUBLIC_API_BASE_URL}/auth/token/refresh`,
                {},
                { withCredentials: true }
            )

            isRefreshing = false
            onTokenRefreshed()

            // 원래 요청 재시도
            return api(originalRequest)
            } catch (e) {
            isRefreshing = false
            onRefreshFailed(e)
            toast.warn("세션이 만료되었습니다. 다시 로그인해주세요.")
            window.location.href = "/login"
            return Promise.reject(e)
            }
        }

        // 이미 리프레시 중인 경우 대기
        return new Promise((resolve, reject) => {
            subscribeTokenRefresh(
            () => resolve(api(originalRequest)),
            (err) => reject(err)
            )
        })
        }

        if (status === 401 && data?.error === "TOKEN_INVALID") {
            toast.warn("인증되지 않은 사용자입니다. 다시 로그인해주세요.")
            window.location.href = "/login"
            return Promise.reject(error)
        }


        toast.error("문제가 발생했습니다. 홈으로 이동합니다.")
        window.location.href = "/"
        return Promise.reject(error)

    }
)

export default api
