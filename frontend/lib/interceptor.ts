import axios from "axios"

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

// 요청 인터셉터 - Gateway에서 JWT 검증 후 x-user-id 헤더를 자동 주입하므로 제거
        // Gateway에서 이미 JWT를 검증하고 x-user-id 헤더를 주입하므로 
        // 프론트엔드에서는 별도 처리 불필요 쿠키에 포함


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
        // 디버깅용 출력
        // console.error("API Error Status:", status)
        // console.error("API Error Message:", data?.error)
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
            alert("세션이 만료되었습니다. 다시 로그인해주세요.")
            window.location.href = "/login.html"
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
            alert("인증되지 않은 사용자입니다. 로그인해주세요.")
            window.location.href = "/login.html"
            return Promise.reject(error)
        }

        // Review 서버 관련 에러는 컴포넌트에서 처리하도록 그대로 전달
        if (config.url?.includes('/review/')) {
            return Promise.reject(error)
        }

        alert("문제가 발생했습니다. 홈으로 이동합니다.")
        window.location.href = "/"
        return Promise.reject(error)

    }
)

export default api
