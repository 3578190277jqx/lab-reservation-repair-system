<script setup>
import { useUserStore } from '@/stores/user'
import ResourceListView from './ResourceListView.vue'
import StudentEquipmentWorkspace from '@/components/equipment/StudentEquipmentWorkspace.vue'
const user = useUserStore()
</script>
<template>
  <StudentEquipmentWorkspace v-if="user.role === 'student'" />
  <ResourceListView v-else />
</template>
