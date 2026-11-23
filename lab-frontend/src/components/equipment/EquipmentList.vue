<script setup>
import EquipmentGlyph from '@/components/visual/EquipmentGlyph.vue'
import StateEmpty from '@/components/common/StateEmpty.vue'
defineProps({ rows: Array, selectedId: [String, Number], locked: Boolean })
defineEmits(['select'])
const statusNames = ['可用', '已有预约', '维修中', '已报废']
</script>
<template>
  <ul v-if="rows.length" class="equipment-ledger" aria-label="设备清单">
    <li v-for="row in rows" :key="row.id" :class="{ selected: selectedId === row.id }">
      <div class="device-mark"><EquipmentGlyph :name="row.name" /></div>
      <div class="device-copy">
        <div class="device-title">
          <h3>{{ row.name }}</h3>
          <span class="device-state" :class="`state-${row.status}`">{{
            statusNames[row.status] || '状态未登记'
          }}</span>
        </div>
        <p>{{ row.model || '型号未登记' }}</p>
        <p>
          {{ row.labName || `实验室 #${row.labId}`
          }}<span v-if="row.labLocation"> · {{ row.labLocation }}</span>
        </p>
        <small>{{ row.equipmentCode || `设备 #${row.id}` }}</small>
      </div>
      <el-button
        :type="selectedId === row.id ? 'primary' : 'default'"
        :plain="selectedId === row.id"
        :disabled="locked"
        :aria-pressed="selectedId === row.id"
        :aria-label="`查看${row.name}排期`"
        @click="$emit('select', row)"
        >{{ selectedId === row.id ? '正在查看' : '查看排期' }}</el-button
      >
    </li>
  </ul>
  <StateEmpty
    v-else
    title="没有符合条件的设备"
    description="试试其他设备名称，或重置筛选条件。"
    compact
  />
</template>
<style scoped>
.equipment-ledger {
  padding: 0;
  margin: 0;
  list-style: none;
}
li {
  display: flex;
  gap: var(--sp-2);
  padding: var(--sp-2);
  align-items: center;
  border-bottom: 1px solid var(--line);
  border-left: 3px solid transparent;
}
li:last-child {
  border-bottom: 0;
}
li.selected {
  background: var(--brand-50);
  border-left-color: var(--brand-600);
}
.device-mark {
  width: 48px;
  flex: 0 0 48px;
  color: var(--brand-600);
}
.device-mark svg {
  width: 100%;
  display: block;
}
.device-copy {
  min-width: 0;
  flex: 1;
}
.device-title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 16px;
}
h3 {
  margin: 0;
  font-size: var(--fs-md);
  color: var(--ink-900);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
p {
  margin: 4px 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
small {
  color: var(--ink-500);
  font-size: var(--fs-xs);
}
.device-state {
  font-size: var(--fs-sm);
  color: var(--ink-500);
  white-space: nowrap;
}
.state-0 {
  color: var(--success);
}
.state-1 {
  color: var(--info);
}
.state-2 {
  color: var(--warning);
}
@media (max-width: 699px) {
  li {
    flex-wrap: wrap;
    gap: var(--sp-1);
    padding: var(--sp-2);
  }
  .device-mark {
    width: 40px;
    flex-basis: 40px;
  }
  .device-copy {
    flex-basis: calc(100% - 48px);
  }
  li > .el-button {
    margin-left: 48px;
    min-height: 44px;
  }
}
</style>
