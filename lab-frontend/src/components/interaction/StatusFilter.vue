<script setup>
defineProps({
  items: { type: Array, required: true },
  modelValue: { type: [String, Number], default: '' },
  disabled: Boolean,
})
const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <div class="status-filter" role="group" aria-label="按状态筛选">
    <button type="button" :aria-pressed="modelValue === '' || modelValue == null" :disabled="disabled" @click="emit('update:modelValue', '')">全部状态</button>
    <button v-for="item in items" :key="item[0]" type="button" :aria-pressed="modelValue === item[0]" :disabled="disabled" @click="emit('update:modelValue', item[0])">{{ item[1] }}</button>
  </div>
</template>
