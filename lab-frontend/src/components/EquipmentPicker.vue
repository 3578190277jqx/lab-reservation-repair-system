<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { getEquipmentPage } from '@/api/equipment'
import StateError from '@/components/common/StateError.vue'
import { getToken } from '@/utils/auth'

const props = defineProps({ modelValue: [String, Number], mode: String, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
const options = ref([]), loading = ref(false), error = ref(''), total = ref(0)
let keyword = '', current = 0, sequence = 0
async function load(query = '', more = false) {
  if (more && loading.value) return
  const token = getToken(), id = ++sequence
  if (!more) { keyword = query; current = 0; options.value = []; total.value = 0 }
  loading.value = true; error.value = ''
  const next = current + 1
  try {
    const data = await getEquipmentPage({ name: keyword || undefined, current: next, size: 20 })
    if (id !== sequence || token !== getToken()) return
    options.value = more ? [...options.value, ...data.records] : data.records
    current = next; total.value = Number(data.total)
  } catch (e) { if (id === sequence && token === getToken()) error.value = e.message }
  finally { if (id === sequence) loading.value = false }
}
const unavailable = (item) => props.mode === 'apply' ? ![0, 1].includes(item.status) : item.status === 3
onBeforeUnmount(() => { sequence++ })
</script>

<template>
  <el-select :model-value="modelValue" :disabled="disabled" filterable remote :remote-method="query => load(query)" :loading="loading" placeholder="输入设备名称搜索并选择" style="width:100%" @update:model-value="emit('update:modelValue', $event)" @visible-change="visible => visible && !options.length && load()">
    <el-option v-for="item in options" :key="item.id" :value="item.id" :disabled="unavailable(item)" :label="`${item.name} · ${item.equipmentCode || item.id}`">
      {{ item.name }} · {{ item.equipmentCode || item.id }}（{{ ['可用','已预约','维修中','已报废'][item.status] || '未知' }}）
    </el-option>
    <template #footer>
      <StateError v-if="error" :description="error" retry-label="重试" :disabled="loading" compact @retry="load(keyword, current > 0)" />
      <el-button v-else-if="options.length < total" text :loading="loading" @click="load(keyword, true)">加载更多设备</el-button>
    </template>
  </el-select>
</template>
