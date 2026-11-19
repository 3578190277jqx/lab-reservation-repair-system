import request from '@/utils/request'

// 后端映射本身是 /api/health；配合 request 的 /api baseURL 和开发代理移除一层 /api。
export function getHealth() {
  return request({ url: '/api/health', method: 'get', skipAuth: true })
}
