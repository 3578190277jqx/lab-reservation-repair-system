<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/stores/user'
import { useRoute } from 'vue-router'
import { getToken } from '@/utils/auth'
import { getReservationDetail } from '@/api/reservation'
import { reservationStatusName, reservationTimeline } from '@/utils/reservation-detail'
const visible=ref(false), busy=ref(false), error=ref(''), row=ref(null)
const user=useUserStore(), route=useRoute();let version=0, recordId
const events=computed(()=>row.value?reservationTimeline(row.value):[])
function reset(){version++;visible.value=false;row.value=null;error.value='';busy.value=false}
async function load(){const id=++version,token=getToken();row.value=null;error.value='';busy.value=true
 try{const result=await getReservationDetail(recordId);if(id===version&&visible.value&&token===getToken())row.value=result}
 catch(e){if(id===version&&token===getToken())error.value=e.message}
 finally{if(id===version)busy.value=false}}
function open(row){reset();recordId=row.id;visible.value=true;load()}
watch([()=>user.token,()=>user.role,()=>route.fullPath],reset);onBeforeUnmount(reset);defineExpose({open})
</script>
<template><el-dialog v-model="visible" title="预约进度详情" width="620px" style="max-width:calc(100vw - 28px)" @closed="reset">
 <div v-loading="busy" style="min-height:100px"><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-button v-if="error" @click="load">重新加载</el-button>
 <template v-if="row"><el-descriptions :column="1" border>
 <el-descriptions-item label="预约编号">#{{row.id}}</el-descriptions-item>
 <el-descriptions-item label="设备">{{row.equipmentName || ('设备 #'+row.equipmentId)}}</el-descriptions-item>
 <el-descriptions-item label="实验室">{{row.labName || '未提供'}} · {{row.labLocation || '位置未登记'}}</el-descriptions-item>
 <el-descriptions-item label="状态">{{reservationStatusName(row.status)}}</el-descriptions-item>
 <el-descriptions-item label="预约时段">{{row.startTime}} 至 {{row.endTime}}（北京时间）</el-descriptions-item>
 <el-descriptions-item label="用途">{{row.purpose}}</el-descriptions-item></el-descriptions>
 <h3>处理进度</h3><el-timeline><el-timeline-item v-for="(event,i) in events" :key="i" :timestamp="event.time"><strong>{{event.title}}</strong><p style="white-space:pre-wrap;overflow-wrap:anywhere">{{event.content}}</p></el-timeline-item></el-timeline>
 <el-empty v-if="!events.length" description="暂无进度记录"/></template></div>
 <template #footer><el-button @click="reset">关闭</el-button></template></el-dialog></template>
