"use client"

import { useState, useEffect, createContext, useContext } from "react"
import { authApi } from "@/services/api"
import type { User } from "@/types"

interface AuthContextType {
  user: User | null
  isAuthenticated: boolean
  login: (provider: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function useAuth() {
  const [user, setUser] = useState<User | null>(null)
  const [isAuthenticated, setIsAuthenticated] = useState(false)

  useEffect(() => {
    // 로컬 스토리지에서 사용자 정보 확인
    const savedUser = localStorage.getItem("user")
    if (savedUser) {
      try {
        const userData = JSON.parse(savedUser)
        setUser(userData)
        setIsAuthenticated(true)
      } catch (error) {
        console.error("사용자 정보 파싱 실패:", error)
      }
    }
  }, [])

  const context = useContext(AuthContext)
  if (context) {
    return context
  }

  const login = async (provider: string) => {
    try {
      // TODO: REST API 연동 - 로그인
      const userData = await authApi.login(provider)
      setUser(userData)
      setIsAuthenticated(true)
      localStorage.setItem("user", JSON.stringify(userData))
    } catch (error) {
      console.error("로그인 실패:", error)
      throw error
    }
  }

  const logout = async () => {
    try {
      // TODO: REST API 연동 - 로그아웃
      await authApi.logout()
      setUser(null)
      setIsAuthenticated(false)
      localStorage.removeItem("user")
    } catch (error) {
      console.error("로그아웃 실패:", error)
      throw error
    }
  }

  return { user, isAuthenticated, login, logout }
}
