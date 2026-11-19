<script setup>
import EquipmentGlyph from '@/components/visual/EquipmentGlyph.vue'
import StateEmpty from '@/components/common/StateEmpty.vue'
import { actionNames } from '@/utils/workflow'
defineProps({
  resource: { type: String, required: true },
  role: { type: String, required: true },
  rows: { type: Array, required: true },
  columns: { type: Array, required: true },
  compact: Boolean,
  statusInfo: { type: Function, required: true },
  cellText: { type: Function, required: true },
  actionsFor: { type: Function, required: true },
  canManage: { type: Function, required: true },
})
const emit = defineEmits(['inspect', 'workflow', 'reservationDetail', 'edit', 'delete'])
</script>
<template>
  <div
    v-if="resource === 'equipment' && role === 'student'"
    class="student-device-catalog"
    :class="{ 'catalog-even': rows.length % 3 === 1 && rows.length > 1 }"
  >
    <article v-for="(row, index) in rows" :key="row.id" v-reveal class="discover-card">
      <div class="equipment-art" :class="`art-${index % 4}`">
        <EquipmentGlyph :name="row.name" /><span>{{ row.equipmentCode || '实验设备' }}</span>
      </div>
      <div class="discover-body">
        <div class="device-title">
          <h3>{{ row.name }}</h3>
          <el-tag :type="statusInfo(row.status)[2]" round>{{
            row.status === 1 ? '已有预约' : statusInfo(row.status)[1]
          }}</el-tag>
        </div>
        <p class="device-meta">
          <span>{{ row.model || '型号未登记' }}</span
          ><span>{{ row.labName || '实验室 #' + row.labId }}</span>
        </p>
        <p class="device-state-note">
          {{
            row.status === 1
              ? '可查看其他空闲时段并申请预约'
              : row.status === 2
                ? '维修期间暂停预约'
                : row.status === 3
                  ? '设备已退出使用'
                  : '查看占用时段，选择适合你的实验时间'
          }}
        </p>
        <el-button text class="device-detail-link" @click="emit('inspect', row)"
          >设备详情</el-button
        >
        <div class="device-actions">
          <el-button
            v-for="action in actionsFor(row)"
            :key="action"
            :type="action === 'apply' ? 'primary' : 'default'"
            @click="emit('workflow', action, row)"
            >{{ action === 'apply' ? '查看时段并预约' : '故障报修' }}</el-button
          >
        </div>
      </div>
    </article>
    <StateEmpty
      v-if="!rows.length"
      title="没有找到符合条件的设备"
      description="可以调整筛选条件后重新查询"
    />
  </div>
  <el-table v-else :data="rows" row-key="id" empty-text="没有符合条件的记录" class="resource-table"
    ><el-table-column
      v-for="column in columns"
      :key="`${resource}-${column[0]}`"
      :prop="column[0]"
      :label="column[1]"
      :min-width="column[2]"
      show-overflow-tooltip
      ><template #default="{ row }"
        ><span :class="{ 'table-primary': ['name', 'username', 'orderNo'].includes(column[0]) }">{{
          cellText(row, column[0])
        }}</span></template
      ></el-table-column
    ><el-table-column label="状态" width="110" :fixed="compact ? false : 'right'"
      ><template #default="{ row }"
        ><el-tag :type="statusInfo(row.status)[2]" effect="light" round>{{
          statusInfo(row.status)[1]
        }}</el-tag></template
      ></el-table-column
    ><el-table-column
      v-if="['lab', 'user', 'equipment', 'reservation', 'repair'].includes(resource)"
      label="操作"
      :width="resource === 'repair' ? 240 : 200"
      :fixed="compact ? false : 'right'"
      ><template #default="{ row }"
        ><el-button
          v-if="resource === 'reservation'"
          link
          type="primary"
          @click="emit('reservationDetail', row)"
          >进度详情</el-button
        ><template v-if="['lab', 'equipment', 'user'].includes(resource) && canManage(row)"
          ><el-button link type="primary" @click="emit('edit', row)">编辑</el-button
          ><el-button
            v-if="resource === 'user'"
            link
            type="primary"
            @click="emit('edit', row, 'role')"
            >角色</el-button
          ><el-button v-else link type="danger" @click="emit('delete', row)"
            >删除</el-button
          ></template
        ><el-button
          v-for="action in actionsFor(row)"
          :key="action"
          link
          :type="action === 'cancel' ? 'danger' : 'primary'"
          @click="emit('workflow', action, row)"
          >{{ actionNames[action] }}</el-button
        ><span v-if="!actionsFor(row).length && !canManage(row)" class="muted-text"
          >—</span
        ></template
      ></el-table-column
    ><template #empty
      ><StateEmpty
        title="没有符合条件的记录"
        description="可以调整筛选条件后重新查询"
        compact /></template
  ></el-table>
</template>
