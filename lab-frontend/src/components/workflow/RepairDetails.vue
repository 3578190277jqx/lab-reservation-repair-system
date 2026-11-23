<script setup>
const props = defineProps({ controller: { type: Object, required: true } })
const {
  row,
  preview,
  records,
  detailsLoading,
  imageError,
  recordError,
  loadDetails,
  repairStatusNames,
} = props.controller
</script>

<template>
  <div v-loading="detailsLoading" class="order-details">
    <el-descriptions :column="1" border
      ><el-descriptions-item label="工单编号">{{ row.orderNo || row.id }}</el-descriptions-item
      ><el-descriptions-item label="设备">{{
        row.equipmentName || '设备 #' + row.equipmentId
      }}</el-descriptions-item
      ><el-descriptions-item label="实验室">{{ row.labName || '未提供' }}</el-descriptions-item
      ><el-descriptions-item label="状态">{{ repairStatusNames[row.status] }}</el-descriptions-item
      ><el-descriptions-item label="故障描述">{{ row.description }}</el-descriptions-item
      ><el-descriptions-item label="AI 提示">{{
        row.aiSuggestion || '未提供辅助提示'
      }}</el-descriptions-item></el-descriptions
    >
    <img v-if="preview" :src="preview" alt="报修附件" class="order-image" />
    <el-alert
      v-if="imageError"
      :title="`图片加载失败：${imageError}`"
      type="warning"
      :closable="false"
    />
    <h3>处理记录</h3>
    <el-alert v-if="recordError" :title="recordError" type="error" :closable="false" />
    <el-timeline v-else
      ><el-timeline-item
        v-for="record in records"
        :key="record.id"
        :timestamp="record.operationTime"
        ><strong>{{ repairStatusNames[record.toStatus] }}</strong>
        <p>{{ record.content }}</p>
        <small
          >操作人 ID：{{ record.operatorId
          }}<template v-if="record.repairCost != null">
            · 费用：{{ record.repairCost }} 元</template
          ></small
        ></el-timeline-item
      ></el-timeline
    >
    <el-empty
      v-if="!detailsLoading && !recordError && !records.length"
      description="暂无处理记录"
      :image-size="60"
    />
    <el-button v-if="recordError || imageError" text @click="loadDetails">重新加载详情</el-button>
  </div>
</template>

<style scoped>
.order-image {
  max-width: 100%;
  max-height: 260px;
  display: block;
  margin: 18px auto;
  object-fit: contain;
}
.order-details h3 {
  margin: 22px 0 16px;
}
</style>
