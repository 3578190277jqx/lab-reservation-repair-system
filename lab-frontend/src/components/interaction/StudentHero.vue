<script setup>
import { ArrowDown, ArrowRight } from '@element-plus/icons-vue'

defineProps({ displayName: { type: String, default: '同学' }, date: { type: String, default: '' } })
const emit = defineEmits(['guide'])
</script>

<template>
  <section class="exploration-hero" aria-labelledby="exploration-title">
    <div class="hero-heading"><h1 id="exploration-title">实验设备预约</h1><p class="hero-welcome">{{ displayName }}<span>{{ date }}</span></p></div>
    <p class="hero-intro">查找设备、选择空闲时段，提交你的实验申请。</p>
    <div class="hero-choices">
      <RouterLink to="/equipment" class="hero-primary">查找并预约设备 <el-icon><ArrowRight /></el-icon></RouterLink>
      <button type="button" class="hero-secondary" @click="emit('guide')">了解使用流程 <el-icon><ArrowDown /></el-icon></button>
    </div>
  </section>
</template>

<style scoped>
.exploration-hero { display: flex; flex-direction: column; align-items: flex-start; gap: var(--sp-2); text-align: left; min-width: 0; padding: 0; }
.hero-heading { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2); width: 100%; flex-wrap: wrap; }
.hero-welcome { display: flex; flex-wrap: wrap; gap: var(--sp-2); color: var(--ink-500); font-size: var(--fs-sm); line-height: 1.6; }
.exploration-hero h1 { color: var(--ink-900); font-size: var(--fs-xl); line-height: 1.3; margin: 0; font-weight: 600; }
.hero-intro { color: var(--ink-500); font-size: var(--fs-sm); line-height: 1.8; }
.hero-choices { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-2); }
.hero-primary, .hero-secondary { display: inline-flex; justify-content: center; align-items: center; gap: var(--sp-1); min-height: 44px; padding: 10px 16px; border-radius: var(--r-sm); font-size: var(--fs-sm); font-weight: 500; transition: background-color 180ms ease, transform 220ms ease; }
.hero-primary { color: var(--surface); background: var(--ink-900); }
.hero-primary:hover { background: var(--ink-700); }
.hero-secondary { border: 1px solid var(--line); background: var(--surface); color: var(--ink-700); }
.hero-secondary:hover { background: var(--surface-muted); }
.hero-primary:active, .hero-secondary:active { transform: scale(.98); }
@media (max-width: 699px) {
  .hero-welcome { font-size: var(--fs-xs); }
  .exploration-hero h1 { font-size: var(--fs-lg); }
  .hero-choices { gap: var(--sp-1); }
  .hero-primary, .hero-secondary { padding-inline: 12px; }
}
</style>
