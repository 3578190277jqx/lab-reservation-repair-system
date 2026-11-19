import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login, logout as logoutApi, getUserInfo } from '@/api/user'
import { clearSession, getToken, saveSession } from '@/utils/auth'

const ROLES = ['admin', 'lab_admin', 'student']

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const user = ref(null)
  const loading = ref(false)
  const errorMessage = ref('')
  const role = computed(() => user.value?.role || '')
  const displayName = computed(() => user.value?.realName || user.value?.username || '用户')
  const isAdmin = computed(() => role.value === 'admin')
  const isManager = computed(() => ['admin', 'lab_admin'].includes(role.value))
  let generation = 0
  let profileToken = ''
  let profileRequest = null

  function resetSession() {
    generation += 1
    clearSession()
    token.value = ''
    user.value = null
    loading.value = false
    errorMessage.value = ''
    profileToken = ''
    profileRequest = null
  }

  function staleSession() {
    const error = new Error('登录状态已变化，请重试')
    error.code = 'ERR_AUTH_CHANGED'
    return error
  }

  function fetchUserInfo({ force = false } = {}) {
    const currentToken = getToken()
    token.value = currentToken
    if (!currentToken) {
      const error = new Error('请先登录')
      error.status = 401
      return Promise.reject(error)
    }
    if (!force && profileToken === currentToken && user.value) return Promise.resolve(user.value)
    if (profileRequest?.token === currentToken) return profileRequest.promise
    const version = generation
    const pending = { token: currentToken, promise: null }
    pending.promise = getUserInfo({ silent: true }).then((profile) => {
      if (version !== generation || currentToken !== getToken()) throw staleSession()
      if (!profile || !ROLES.includes(profile.role) || profile.status !== 1) {
        const error = new Error('当前账号信息无效，请重新登录')
        error.status = 403
        throw error
      }
      user.value = profile
      profileToken = currentToken
      errorMessage.value = ''
      return profile
    }).finally(() => {
      if (profileRequest === pending) profileRequest = null
    })
    profileRequest = pending
    return pending.promise
  }

  async function signIn(credentials) {
    resetSession()
    const version = generation
    loading.value = true
    try {
      const result = await login({ username: credentials.username.trim(), password: credentials.password })
      if (version !== generation) throw staleSession()
      saveSession({ token: result.token, expiresIn: result.expiresIn })
      token.value = getToken()
      // 使用携带Token的 /user/info 获取权威角色；不相信本地缓存的角色和菜单。
      return await fetchUserInfo({ force: true })
    } catch (error) {
      if (version === generation) errorMessage.value = error.message || '登录失败'
      throw error
    } finally {
      if (version === generation) loading.value = false
    }
  }

  async function logout() {
    const currentToken = getToken()
    let revoked = true
    try { if (currentToken) await logoutApi() } catch { revoked = false }
    finally {
      if (getToken() === currentToken || !getToken()) resetSession()
    }
    return revoked
  }

  return { token, user, role, displayName, isAdmin, isManager, loading, errorMessage,
    signIn, fetchUserInfo, logout, resetSession }
})
