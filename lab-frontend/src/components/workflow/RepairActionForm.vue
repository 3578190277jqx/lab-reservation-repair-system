<script setup>
const props = defineProps({ controller: { type: Object, required: true } })
const { action, row, busy, uploading, preview, fileName, form, chooseImage, removeImage } =
  props.controller
</script>

<template>
  <template v-if="action === 'submit'">
    <el-form-item label="故障描述" required
      ><el-input
        v-model="form.description"
        type="textarea"
        :rows="4"
        maxlength="2000"
        show-word-limit
        placeholder="请描述设备故障现象、发生时间等信息"
    /></el-form-item>
    <el-form-item label="故障图片（选填）">
      <div class="workflow-upload">
        <label class="image-picker"
          ><input
            type="file"
            accept="image/jpeg,image/png"
            :disabled="busy || uploading"
            @change="chooseImage"
          />{{ uploading ? '正在上传…' : form.imageUrl ? '更换图片' : '选择图片' }}</label
        ><span class="workflow-hint">JPEG / PNG，最大5MiB</span>
        <div v-if="preview" class="uploaded-image">
          <img :src="preview" alt="故障图片预览" /><span>{{ fileName }}</span
          ><el-button text type="danger" @click="removeImage">移除图片</el-button>
        </div>
      </div>
    </el-form-item>
    <el-form-item
      ><el-checkbox v-model="form.analyzeImage" :disabled="!form.imageUrl"
        >请求 AI 图片辅助提示</el-checkbox
      ></el-form-item
    >
    <p class="workflow-hint">AI 结果仅供参考；服务未启用或识别失败时仍可正常提交报修。</p>
  </template>
  <template v-if="['accept', 'start', 'complete'].includes(action)">
    <p>工单 {{ row.orderNo || row.id }} · 设备 {{ row.equipmentId }}</p>
    <p>{{ row.description }}</p>
    <el-alert
      v-if="action === 'accept'"
      title="受理确认故障后，设备进入维修中，受影响的预约会由系统取消。"
      type="warning"
      :closable="false"
      class="workflow-alert"
    />
    <el-form-item
      :label="action === 'complete' ? '维修结果' : '操作说明（选填）'"
      :required="action === 'complete'"
      ><el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit
    /></el-form-item>
    <el-form-item v-if="action === 'complete'" label="维修费用（元）" required
      ><el-input v-model="form.repairCost" inputmode="decimal" placeholder="0.00"
    /></el-form-item>
  </template>
</template>

<style scoped>
.workflow-alert {
  margin-bottom: 18px;
}
.workflow-hint {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}
.workflow-upload {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.image-picker {
  border: 1px solid #0b807e;
  color: #087f7b;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
}
.image-picker input {
  position: absolute;
  inset: 0;
  opacity: 0;
  width: 100%;
  cursor: pointer;
}
.uploaded-image {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  overflow-wrap: anywhere;
}
.uploaded-image img {
  width: 86px;
  height: 86px;
  object-fit: contain;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}
</style>
