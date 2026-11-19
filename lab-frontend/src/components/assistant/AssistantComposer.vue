<script setup>
defineProps({ modelValue: { type: String, default: '' }, busy: Boolean, error: { type: String, default: '' } })
defineEmits(['update:modelValue', 'send', 'clear', 'edit'])
</script>
<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <el-form label-position="top" @submit.prevent="$emit('send')">
    <el-form-item label="你的问题或追问">
      <el-input :model-value="modelValue" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="例如：设备黑屏，帮我整理报修描述 / 再简短一点" :disabled="busy" @update:model-value="$emit('update:modelValue', $event); $emit('edit')" />
    </el-form-item>
    <div class="composer-actions"><el-button type="primary" native-type="submit" :loading="busy">{{ busy ? '正在回答…' : '发送问题' }}</el-button><el-button @click="$emit('clear')">开始新对话</el-button></div>
  </el-form>
  <p class="conversation-hint">连续追问会携带最近3轮成功问答。当前页最多显示10轮，开始新对话或离开页面会清除记录。</p>
</template>
<style scoped>
.composer-actions{display:flex;flex-wrap:wrap;gap:10px}.composer-actions .el-button{margin:0}
.conversation-hint{font-size:13px;line-height:1.8;color:var(--el-text-color-secondary)}
</style>
