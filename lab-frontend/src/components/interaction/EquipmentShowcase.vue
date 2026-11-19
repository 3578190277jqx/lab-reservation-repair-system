<script setup>
import { computed, useTemplateRef } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import EquipmentGlyph from '@/components/visual/EquipmentGlyph.vue'
import { useHorizontalRail } from '@/composables/useHorizontalRail'

const props = defineProps({ records: { type: Array, required: true } })
const emit = defineEmits(['inspect', 'apply'])
const track = useTemplateRef('track')
const { bounds, visibleRange, scroll, keydown } = useHorizontalRail(track, computed(() => props.records.length))
</script>

<template>
  <section class="equipment-showcase" aria-label="可用设备展示">
    <p id="equipment-rail-help" class="showcase-help">左右滑动浏览设备，也可以用下方箭头切换。</p>
    <div ref="track" class="showcase-track" role="region" aria-label="设备浏览轨道" aria-describedby="equipment-rail-help" tabindex="0" @keydown="keydown">
      <article v-for="(row, index) in records" :key="row.id" class="showcase-card discover-card" :aria-label="row.name">
        <div class="equipment-art" :class="`art-${index % 4}`"><EquipmentGlyph :name="row.name" /><span>{{ row.equipmentCode || '实验设备' }}</span></div>
        <div class="discover-body"><div class="device-title"><h3>{{ row.name }}</h3><el-tag type="success" round>可用</el-tag></div><p class="device-meta"><span>{{ row.model || '型号未登记' }}</span><span>{{ row.labName || ('实验室 #' + row.labId) }}</span></p>
          <el-button text class="device-detail-link" @click="emit('inspect', row)">进一步了解 <el-icon><ArrowRight /></el-icon></el-button>
          <el-button type="primary" plain @click="emit('apply', row)">查看时段并预约 <el-icon><ArrowRight /></el-icon></el-button>
        </div>
      </article>
    </div>
    <div class="showcase-controls"><span class="showcase-range" role="status" aria-live="polite" aria-atomic="true">{{ visibleRange }}</span><div><button type="button" aria-label="浏览上一台设备" :disabled="bounds.start" @click="scroll(-1)"><el-icon><ArrowLeft /></el-icon></button><button type="button" aria-label="浏览下一台设备" :disabled="bounds.end" @click="scroll(1)"><el-icon><ArrowRight /></el-icon></button></div></div>
  </section>
</template>

<style scoped>
.equipment-showcase { min-width: 0; }
.showcase-help { color: var(--ink-500); font-size: var(--fs-sm); margin: 0 0 14px; line-height: 1.7; }
.showcase-track { display: flex; gap: 22px; overflow-x: auto; overflow-y: hidden; overscroll-behavior-x: contain; scroll-snap-type: x proximity; scrollbar-width: thin; scrollbar-color: var(--brand-200) transparent; padding: 4px 0 14px; border-radius: var(--r-md); }
.showcase-track:focus-visible { outline: 3px solid var(--brand-600); outline-offset: 5px; }
.showcase-card { flex: 0 0 clamp(280px, 30vw, 365px); scroll-snap-align: start; scroll-margin-inline: 0; }
.showcase-card .equipment-art { height: 140px; }
.showcase-card .discover-body { padding: 20px 22px 22px; }
.showcase-card .discover-body h3 { font-size: var(--fs-md); }
.showcase-card .discover-body > .el-button:last-child { margin-top: 14px; min-height: 44px; }
.showcase-card .device-detail-link { margin-top: 12px; padding-top: 14px; min-height: 48px; }
.showcase-card .device-detail-link .el-icon { margin-left: 4px; }
.showcase-controls { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 60px; }
.showcase-range { font-size: var(--fs-sm); color: var(--ink-500); line-height: 1.7; }
.showcase-controls > div { display: flex; gap: 10px; }
.showcase-controls button { width: 44px; height: 44px; border-radius: var(--r-round); border: 0; display: grid; place-items: center; background: var(--surface-muted); color: var(--ink-700); font-size: var(--fs-lg); transition: background-color 180ms ease, transform 180ms ease; }
.showcase-controls button:hover:not(:disabled) { background: var(--brand-100); }
.showcase-controls button:active:not(:disabled) { transform: scale(.96); }
.showcase-controls button:disabled { opacity: .35; cursor: default; }
@media (hover: hover) and (pointer: fine) { .showcase-card:hover { transform: none; box-shadow: none; } }
@media (max-width: 699px) {
  .showcase-track { gap: 16px; }
  .showcase-card { flex-basis: 88%; min-width: 0; }
  .showcase-card .equipment-art { height: 140px; }
  .showcase-card .discover-body { padding: 22px; }
  .showcase-card .discover-body h3 { font-size: var(--fs-md); }
  .showcase-range { max-width: 17em; font-size: var(--fs-xs); }
}
</style>
