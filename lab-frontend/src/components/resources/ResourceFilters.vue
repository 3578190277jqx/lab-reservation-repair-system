<script setup>
import { Search } from '@element-plus/icons-vue'
import StatusFilter from '@/components/interaction/StatusFilter.vue'
import { roleNames } from '@/resources'
defineProps({
  config: { type: Object, required: true },
  resource: { type: String, required: true },
  loading: Boolean,
})
const keyword = defineModel('keyword', { type: String, default: '' })
const status = defineModel('status', { type: [String, Number], default: '' })
const role = defineModel('role', { type: String, default: '' })
const emit = defineEmits(['search', 'reset', 'statusChange'])
</script>
<template>
  <el-form class="filters-bar" label-position="top" @submit.prevent="emit('search')">
    <el-form-item :label="config.searchLabel" class="keyword-filter">
      <el-input
        v-model="keyword"
        clearable
        :placeholder="config.searchPlaceholder"
        :prefix-icon="Search"
        :maxlength="config.numeric ? 16 : 100"
        :inputmode="config.numeric ? 'numeric' : 'text'"
      />
    </el-form-item>
    <el-form-item v-if="resource === 'user'" label="角色"
      ><el-select v-model="role" placeholder="全部角色" clearable
        ><el-option
          v-for="(label, key) in roleNames"
          :key="key"
          :label="label"
          :value="key" /></el-select
    ></el-form-item>
    <div class="filter-actions">
      <el-button type="primary" :icon="Search" native-type="submit" :loading="loading"
        >查询</el-button
      ><el-button :disabled="loading" @click="emit('reset')">重置</el-button>
    </div>
  </el-form>
  <StatusFilter
    :items="config.statuses"
    :model-value="status"
    :disabled="loading"
    @update:model-value="emit('statusChange', $event)"
  />
</template>
