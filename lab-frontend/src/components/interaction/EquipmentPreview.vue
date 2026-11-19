<script setup>
import { computed, shallowRef, watch } from 'vue'
import { rowActions } from '@/utils/workflow'
import EquipmentGlyph from '@/components/visual/EquipmentGlyph.vue'

const props = defineProps({ equipment: { type: Object, default: null }, profile: { type: Object, default: null } })
const opened = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['action'])
const queuedAction = shallowRef(null)
const actions = computed(() => props.equipment ? rowActions('equipment', props.equipment, props.profile) : [])
const status = computed(() => ['可用', '已有预约', '维修中', '已报废'][props.equipment?.status] || '状态未登记')
const fields = computed(() => props.equipment ? [
  ['设备编码', props.equipment.equipmentCode],
  ['设备型号', props.equipment.model],
  ['所属实验室', props.equipment.labName || (props.equipment.labId ? '实验室 #' + props.equipment.labId : '')],
  ['所在位置', props.equipment.labLocation],
  ['制造商', props.equipment.manufacturer],
  ['购置日期', props.equipment.purchaseDate],
] : [])
watch(opened, value => { if (value) queuedAction.value = null })
function choose(action) {
  if (!actions.value.includes(action)) return
  queuedAction.value = { action, row: props.equipment }
  opened.value = false
}
function closed() {
  const choice = queuedAction.value
  queuedAction.value = null
  if (choice && actions.value.includes(choice.action)) emit('action', choice)
}
</script>

<template>
  <el-drawer v-model="opened" title="设备详情" size="min(520px, 100vw)" class="equipment-preview-dialog" append-to-body @closed="closed">
    <template v-if="equipment">
      <div class="preview-art"><EquipmentGlyph :name="equipment.name" /></div>
      <p class="preview-status">{{ status }}</p>
      <h2 class="preview-name">{{ equipment.name }}</h2>
      <p v-if="equipment.description" class="preview-description">{{ equipment.description }}</p>
      <dl class="preview-fields"><div v-for="[label, value] in fields" :key="label"><dt>{{ label }}</dt><dd>{{ value || '未登记' }}</dd></div></dl>
      <p class="preview-note">{{ equipment.status === 2 ? '维修期间暂停预约，可查看或提交故障信息。' : equipment.status === 3 ? '设备已退出使用，暂不支持预约和报修。' : '设备状态仅作参考，可预约时间以时段检查结果为准。' }}</p>
    </template>
    <template #footer>
      <div class="preview-actions"><el-button v-for="action in actions" :key="action" :type="action === 'apply' ? 'primary' : 'default'" @click="choose(action)">{{ action === 'apply' ? '查看时段并预约' : '故障报修' }}</el-button><el-button v-if="!actions.length" @click="opened = false">关闭详情</el-button></div>
    </template>
  </el-drawer>
</template>
