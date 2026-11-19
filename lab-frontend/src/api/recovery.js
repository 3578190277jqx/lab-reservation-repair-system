import request from '@/utils/request'
export const recoveryOptions = () => request({url:'/user/recovery/options',method:'get',skipAuth:true})
export const sendVerificationCode = data => request({url:'/user/recovery/code',method:'post',data,skipAuth:data.purpose !== 'bind',timeout:30000})
export const resetPassword = data => request({url:'/user/recovery/reset',method:'post',data,skipAuth:true,timeout:30000})
export const bindContact = data => request({url:'/user/recovery/bind',method:'post',data,timeout:30000})
