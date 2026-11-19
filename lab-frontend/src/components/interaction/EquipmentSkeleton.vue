<script setup>
import StateLoading from '@/components/common/StateLoading.vue'
defineProps({ count: { type: Number, default: 4 } })
</script>

<template>
  <StateLoading class="equipment-loading" label="正在加载设备">
  <div class="equipment-skeleton-grid">
    <div v-for="item in count" :key="item" class="equipment-skeleton" aria-hidden="true">
      <el-skeleton>
        <template #template><el-skeleton-item variant="image" class="skeleton-art" /><div class="skeleton-copy"><el-skeleton-item variant="h3" /><el-skeleton-item variant="text" /><el-skeleton-item variant="text" /><el-skeleton-item variant="button" /></div></template>
      </el-skeleton>
    </div>
  </div>
  </StateLoading>
</template>
