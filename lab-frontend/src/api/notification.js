import request from '@/utils/request'

export const getMyNotifications = (params) =>
  request({ url: '/notification/my', method: 'get', params })

export const getContactTodos = (params) =>
  request({ url: '/notification/contact-todos', method: 'get', params })

export const confirmNotification = (id) =>
  request({ url: `/notification/${encodeURIComponent(id)}/confirm`, method: 'post' })

export const markContacted = (id) =>
  request({ url: `/notification/${encodeURIComponent(id)}/contacted`, method: 'post' })
