import request from '@/utils/request'

// request 已解包 Result.data；调用方直接 await 登录结果或业务数据。
/** LoginRequest: { username, password }；登录失败不清除已有会话。 */
export function login(data) {
  return request({ url: '/user/login', method: 'post', data, skipAuth: true })
}

/** RegisterRequest: { username, password, realName, studentNo, phone?, email? }。 */
export function register(data) {
  return request({ url: '/user/register', method: 'post', data, skipAuth: true })
}

export function getUserInfo(config = {}) {
  return request({ ...config, url: '/user/info', method: 'get' })
}

export function logout() {
  return request({ url: '/user/logout', method: 'post' })
}

/** UserPageQuery: { current?, size?, username?, role?, status? }。 */
export function getUserPage(params) {
  return request({ url: '/user/page', method: 'get', params })
}

/** RoleUpdateRequest: { userId, role }；role 为 admin、lab_admin 或 student。 */
export function updateUserRole(data) {
  return request({ url: '/user/role', method: 'put', data })
}

export const updateUser = (id,data) => request({url:'/user/'+encodeURIComponent(id),method:'put',data})
