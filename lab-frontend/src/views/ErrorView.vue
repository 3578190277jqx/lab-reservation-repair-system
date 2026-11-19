<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, House } from '@element-plus/icons-vue'
const route = useRoute()
const router = useRouter()
const forbidden = computed(() => Number(route.meta.status) === 403)
</script>
<template><main class="standalone-state"><div class="state-card"><span class="error-number">{{ forbidden ? '403' : '404' }}</span><h1>{{ forbidden ? '当前账号无法访问此页面' : '这个页面暂时找不到了' }}</h1><p>{{ forbidden ? '你的账号没有此功能的访问权限。如需调整权限，请联系管理员。' : '页面地址可能有误，或内容已调整。可以返回工作台继续浏览。' }}</p><div class="state-actions"><el-button :icon="ArrowLeft" @click="router.back()">返回上一页</el-button><el-button type="primary" :icon="House" @click="router.replace('/home')">返回工作台</el-button></div></div></main></template>
