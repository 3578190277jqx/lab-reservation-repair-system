import request from '@/utils/request'

const imagePathPattern = /^\/file\/images\/[1-9][0-9]{0,18}\/[0-9a-f]{32}\.(jpg|png)$/

/** 上传 JPEG/PNG File，最大5MiB；返回 UploadResult { imageUrl, fileName, size }。 */
export function uploadImage(file, onUploadProgress) {
  const data = new FormData()
  data.append('file', file)
  // 让浏览器根据 FormData 自动设置 multipart/form-data 及 boundary。
  return request({
    url: '/file/upload',
    method: 'post',
    data,
    timeout: 30000,
    onUploadProgress,
  })
}

/** 仅读取后端返回的本系统附件路径，携带登录令牌；响应由 request 直接返回 Blob。 */
export function getImageBlob(imageUrl, config = {}) {
  if (typeof imageUrl !== 'string' || imageUrl.trim() !== imageUrl || !imagePathPattern.test(imageUrl)) {
    return Promise.reject(new Error('图片地址必须是本系统上传接口返回的有效路径'))
  }
  return request({ ...config, url: imageUrl, method: 'get', responseType: 'blob' })
}
