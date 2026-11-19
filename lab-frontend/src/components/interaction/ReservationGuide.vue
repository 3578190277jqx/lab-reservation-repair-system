<script setup>
import { useTemplateRef } from 'vue'
import { Plus } from '@element-plus/icons-vue'

const guide = useTemplateRef('guide')
const firstStep = useTemplateRef('firstStep')
function open() {
  if (!guide.value) return
  firstStep.value.open = true
  guide.value.focus({ preventScroll: true })
  guide.value.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
}
defineExpose({ open })
</script>

<template>
  <section ref="guide" class="reservation-guide" tabindex="-1" aria-labelledby="reservation-guide-title">
    <div class="guide-heading"><span>从查找到使用</span><h2 id="reservation-guide-title">做好准备，<br />专注下一场实验。</h2><p>预约、签到、报修，每一步都有入口。</p><RouterLink to="/equipment">开始查找设备 <span aria-hidden="true">→</span></RouterLink></div>
    <div class="guide-steps">
      <details ref="firstStep"><summary><span class="guide-number">01</span><span>选设备，查看可预约时段</span><el-icon><Plus /></el-icon></summary><div class="guide-answer"><p>进入“找设备”，查看设备详情与占用时段，填写时间和实验用途。待审核与已通过的预约都会占用相应时段。</p><RouterLink to="/equipment">查找设备 →</RouterLink></div></details>
      <details><summary><span class="guide-number">02</span><span>等待审核，按时到场签到</span><el-icon><Plus /></el-icon></summary><div class="guide-answer"><p>在“我的预约”查看审核结果。通过后，请在预约开始后的30分钟内、且结束前签到；无法到场时请提前取消。</p><RouterLink to="/reservations">查看我的预约 →</RouterLink></div></details>
      <details><summary><span class="guide-number">03</span><span>发现故障，提交信息并跟进</span><el-icon><Plus /></el-icon></summary><div class="guide-answer"><p>在设备页提交故障描述，可附上图片。工单受理、维修和完成进度可在“我的报修”查看；AI提示仅供参考。</p><RouterLink to="/repairs">查看我的报修 →</RouterLink></div></details>
    </div>
  </section>
</template>

<style scoped>
.reservation-guide { display: grid; grid-template-columns: .9fr 1.1fr; gap: 60px; padding: 54px 0 30px; scroll-margin-top: 150px; border-top: 1px solid #e2e5e3; }
.reservation-guide:focus-visible { outline: 2px solid #23887d; outline-offset: 8px; border-radius: 8px; }
.guide-heading > span { color: #5e7468; font-size: 13px; }
.guide-heading h2 { color: #1d1d1f; font-size: clamp(28px, 3.2vw, 42px); line-height: 1.25; letter-spacing: -.04em; margin: 16px 0; }
.guide-heading p { color: #6e6e73; font-size: 14px; line-height: 1.8; }
.guide-heading > a { display: inline-flex; align-items: center; min-height: 44px; gap: 8px; margin-top: 16px; color: #1c6659; font-size: 14px; }
.guide-steps details { border-bottom: 1px solid #dce2de; }
.guide-steps summary { list-style: none; cursor: pointer; display: flex; align-items: center; gap: 16px; min-height: 82px; color: #26352c; font-size: 16px; font-weight: 500; }
.guide-steps summary::-webkit-details-marker { display: none; }
.guide-number { color: #6f8175; font-size: 12px; font-variant-numeric: tabular-nums; font-weight: 400; }
.guide-steps summary > .el-icon { margin-left: auto; font-size: 19px; flex-shrink: 0; transition: transform 220ms ease; }
.guide-steps details[open] summary > .el-icon { transform: rotate(45deg); }
.guide-answer { padding: 0 0 18px 32px; color: #626c65; font-size: 14px; line-height: 1.9; }
.guide-answer p { margin: 0; }
.guide-answer a { display: inline-flex; align-items: center; min-height: 44px; color: #1c6659; margin-top: 8px; }
@media (max-width: 768px) {
  .reservation-guide { grid-template-columns: 1fr; gap: 20px; padding-top: 36px; }
  .guide-heading h2 { font-size: 32px; }
  .guide-steps summary { gap: 12px; font-size: 14px; min-height: 74px; }
  .guide-answer { padding-left: 26px; font-size: 13px; }
}
</style>
