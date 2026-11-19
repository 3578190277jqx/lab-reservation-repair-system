<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Calendar, Monitor, Refresh, Tools, TrendCharts } from '@element-plus/icons-vue'
import { init, use } from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import StateEmpty from '@/components/common/StateEmpty.vue'
import StateError from '@/components/common/StateError.vue'
import StateLoading from '@/components/common/StateLoading.vue'
import { getOverview, getRepairStatus, getReservationTrend, getEquipmentUsage, getLabRanking } from '@/api/stats'

use([LineChart, PieChart, BarChart, AriaComponent, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const dateRange = ref(null)
const filterError = ref('')
const busy = ref(false)
const overview = reactive({ data: null, loading: true, error: '' })
const trend = reactive({ data: null, loading: true, error: '' })
const repairs = reactive({ data: null, loading: true, error: '' })
const trendElement = ref(null)
const repairElement = ref(null)
const usageElement=ref(null),rankingElement=ref(null)
const usage=reactive({data:null,loading:true,error:''}),ranking=reactive({data:null,loading:true,error:''})
const hasUsage=computed(()=>usage.data?.xAxis?.length>0),hasRanking=computed(()=>ranking.data?.xAxis?.length>0)
let usageChart,rankingChart
const appliedParams = ref({})
let controller
let requestVersion = 0
let active = false
let trendChart
let repairChart
let observer
let resizeFrame = 0

const numberFormat = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 })
const isNumber = (value) => value !== null && value !== undefined && value !== ''
  && ['number', 'string'].includes(typeof value) && Number.isFinite(Number(value)) && Number(value) >= 0
const formatNumber = (value) => isNumber(value) ? numberFormat.format(Number(value)) : '—'
// A valid history containing zero is real data: draw its zero line instead of an empty-state picture.
const hasTrend = computed(() => Boolean(trend.data?.xAxis?.length && trend.data?.series?.length))
const hasRepairs = computed(() => Boolean(repairs.data?.data.some((item) => Number(item.value) > 0)))
const actualRange = computed(() => overview.data?.range || trend.data?.range || repairs.data?.range)
const rangeLabel = computed(() => actualRange.value
  ? `${actualRange.value.startDate} 至 ${actualRange.value.endDate}`
  : appliedParams.value.startDate ? `${appliedParams.value.startDate} 至 ${appliedParams.value.endDate}` : '最近 30 天')
const cards = computed(() => {
  const data = overview.data
  return [
    { label: '设备总数', value: formatNumber(data?.equipmentCount), icon: Monitor,
      detail: data ? `当前可用 ${formatNumber(data.availableEquipmentCount)} 台` : '查看设备资源规模' },
    { label: '预约申请', value: formatNumber(data?.reservationCount), icon: Calendar,
      detail: data ? `待审批 ${formatNumber(data.pendingReservationCount)} 次${isNumber(data.expiredReservationCount) ? ` · 审核超时 ${formatNumber(data.expiredReservationCount)} 次` : ''}` : '所选日期内的预约申请' },
    { label: '未完成报修', value: formatNumber(data?.openRepairOrderCount), icon: Tools,
      detail: data ? `报修共 ${formatNumber(data.repairOrderCount)} 单` : '所选日期内的未完成工单' },
    { label: '设备使用率', value: data ? `${formatNumber(data.equipmentUsageRate)}%` : '—', icon: TrendCharts,
      detail: data ? `实际使用 ${formatNumber(data.usedHours)} 小时` : '所选日期内的实际使用情况' },
  ]
})

function validateOverview(data) {
  const fields = ['labCount', 'equipmentCount', 'availableEquipmentCount', 'reservationCount',
    'pendingReservationCount', 'openRepairOrderCount', 'repairOrderCount', 'usedHours', 'equipmentUsageRate']
  if (!data || !fields.every((field) => isNumber(data[field]))) throw new Error('统计总览数据不完整，请重新加载。')
  return data
}

function validateTrend(data, label = '预约趋势') {
  if (!data || !Array.isArray(data.xAxis) || !Array.isArray(data.series)
    || !data.xAxis.every((day) => typeof day === 'string')
    || !data.series.every((series) => typeof series?.name === 'string' && Array.isArray(series.data)
      && series.data.length === data.xAxis.length && series.data.every(isNumber))) {
    throw new Error(`${label}数据不完整，请重新加载。`)
  }
  return data
}

function validateRepairs(data) {
  if (!data || !Array.isArray(data.data)
    || !data.data.every((item) => typeof item?.name === 'string' && isNumber(item.value))) {
    throw new Error('报修分布数据不完整，请重新加载。')
  }
  return data
}

function localDate(date = new Date()) { return new Date(date.getTime()+8*3600000).toISOString().slice(0,10) }

function disabledDate(date) {
  // DatePicker passes local-midnight calendar labels; compare that label with Beijing today.
  const day=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
  return date.getFullYear() < 1000 || day > localDate()
}

function applyFilter() {
  filterError.value = ''
  if (!dateRange.value?.length) return loadStatistics({})
  const [startDate, endDate] = dateRange.value
  const start = Date.parse(`${startDate}T00:00:00Z`)
  const end = Date.parse(`${endDate}T00:00:00Z`)
  if (dateRange.value.length !== 2 || !Number.isFinite(start) || !Number.isFinite(end)) {
    filterError.value = '请同时选择开始日期和结束日期。'
    return
  }
  if (start > end || endDate > localDate() || (end - start) / 86400000 >= 366 || startDate < '1000-01-01') {
    filterError.value = '日期范围最多 366 天，开始日期不能晚于结束日期，结束日期不能晚于今天。'
    return
  }
  return loadStatistics({ startDate, endDate })
}

function resetFilter() {
  dateRange.value = null
  filterError.value = ''
  return loadStatistics({})
}

function disposeCharts() {
  trendChart?.dispose()
  repairChart?.dispose()
  usageChart?.dispose();rankingChart?.dispose();usageChart=undefined;rankingChart=undefined
  trendChart = undefined
  repairChart = undefined
}

function renderCharts() {
  if (!active) return
  const styles = getComputedStyle(document.documentElement)
  const color = name => styles.getPropertyValue(name).trim()
  const colors = ['--brand-600', '--warning', '--info', '--danger', '--ink-500', '--violet', '--success'].map(color)
  const bar=(data,unit)=>({color: colors, textStyle: { color: color('--ink-500'), fontSize: 14 }, aria:{enabled:true},tooltip:{trigger:'axis',renderMode:'richText',valueFormatter:v=>formatNumber(v)+unit},grid:{left:24,right:20,top:30,bottom:70,containLabel:true},xAxis:{type:'category',data:data.xAxis,axisLabel:{rotate:25}},yAxis:{type:'value'},series:data.series.map(t=>({name:t.name,type:'bar',data:t.data.map(Number)}))})
  if(!usage.loading&&!usage.error&&hasUsage.value&&usageElement.value){usageChart ||= init(usageElement.value);usageChart.setOption(bar(usage.data,'%'),{notMerge:true})}
  if(!ranking.loading&&!ranking.error&&hasRanking.value&&rankingElement.value){rankingChart ||= init(rankingElement.value);rankingChart.setOption(bar(ranking.data,'次'),{notMerge:true})}
  if (!trend.loading && !trend.error && hasTrend.value && trendElement.value) {
    trendChart ||= init(trendElement.value)
    trendChart.setOption({
      color: colors,
      aria: { enabled: true },
      textStyle: { fontFamily: 'inherit', color: color('--ink-500') },
      tooltip: { trigger: 'axis', renderMode: 'richText', valueFormatter: (value) => `${formatNumber(value)} 次` },
      legend: { type: 'scroll', bottom: 0, itemWidth: 14, itemHeight: 8, textStyle: { color: color('--ink-500') } },
      grid: { left: 16, right: 20, top: 24, bottom: 52, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: trend.data.xAxis,
        axisLine: { lineStyle: { color: color('--line') } }, axisTick: { show: false },
        axisLabel: { formatter: (value) => value.slice(5), hideOverlap: true } },
      yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: color('--line'), type: 'dashed' } } },
      series: trend.data.series.map((series, index) => ({
        name: series.name, type: 'line', data: series.data.map(Number), smooth: false,
        showSymbol: trend.data.xAxis.length <= 14, symbolSize: 5,
        lineStyle: { width: index === 0 ? 3 : 2 },
        ...(index === 0 ? { areaStyle: { opacity: 0.08 } } : {}),
        emphasis: { focus: 'series' },
      })),
    }, { notMerge: true })
  }
  if (!repairs.loading && !repairs.error && hasRepairs.value && repairElement.value) {
    repairChart ||= init(repairElement.value)
    repairChart.setOption({
      color: ['--warning', '--info', '--violet', '--brand-600'].map(color),
      aria: { enabled: true },
      textStyle: { fontFamily: 'inherit', color: color('--ink-500') },
      tooltip: { trigger: 'item', renderMode: 'richText', formatter: '{b}\n{c} 单 · {d}%' },
      legend: { type: 'scroll', bottom: 0, itemWidth: 10, itemHeight: 10,
        data: repairs.data.data.map((item) => item.name), textStyle: { color: color('--ink-500') } },
      series: [{ name: '报修工单', type: 'pie', radius: ['45%', '68%'], center: ['50%', '43%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: color('--surface'), borderWidth: 4, borderRadius: 6 },
        label: { show: false }, emphasis: { label: { show: true, fontSize: 14, fontWeight: 600, formatter: '{b}\n{c} 单' } },
        data: repairs.data.data.map((item) => ({ name: item.name, value: Number(item.value) })),
      }],
    }, { notMerge: true })
  }
}

function resizeCharts() {
  if (!active) return
  cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => {
    if (trendElement.value?.clientWidth) trendChart?.resize()
    if (repairElement.value?.clientWidth) repairChart?.resize()
    if(usageElement.value?.clientWidth)usageChart?.resize()
    if(rankingElement.value?.clientWidth)rankingChart?.resize()
  })
}

async function loadStatistics(params = appliedParams.value) {
  if (busy.value || !active) return
  appliedParams.value = { ...params }
  const version = ++requestVersion
  controller?.abort()
  controller = new AbortController()
  const config = { signal: controller.signal, silent: true }
  busy.value = true
  disposeCharts()
  for (const state of [overview, trend, repairs,usage,ranking]) Object.assign(state, { data: null, loading: true, error: '' })
  const requests = [
    [overview, getOverview, validateOverview],
    [trend, getReservationTrend, validateTrend],
    [repairs, getRepairStatus, validateRepairs],
    [usage,getEquipmentUsage,data => validateTrend(data, '设备使用率')],
    [ranking,getLabRanking,data => validateTrend(data, '实验室排行')],
  ]
  await Promise.allSettled(requests.map(async ([state, request, validate]) => {
    try {
      const data = validate(await request(appliedParams.value, config))
      if (active && version === requestVersion) state.data = data
    } catch (error) {
      if (active && version === requestVersion && !config.signal.aborted) {
        state.error = error.message || '加载失败，请稍后重试。'
      }
    } finally {
      if (active && version === requestVersion) {
        state.loading = false
        await nextTick()
        renderCharts()
      }
    }
  }))
  if (active && version === requestVersion) busy.value = false
}

onMounted(() => {
  active = true
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(resizeCharts)
    observer.observe(trendElement.value)
    observer.observe(repairElement.value)
    observer.observe(usageElement.value)
    observer.observe(rankingElement.value)
  }
  window.addEventListener('resize', resizeCharts)
  loadStatistics()
})

onBeforeUnmount(() => {
  active = false
  requestVersion += 1
  controller?.abort()
  observer?.disconnect()
  window.removeEventListener('resize', resizeCharts)
  cancelAnimationFrame(resizeFrame)
  disposeCharts()
})
</script>

<template>
  <div class="page-stack">
    <header class="page-heading">
      <div>
        <h1>统计分析</h1>
        <p class="page-description">从资源使用到维护进展，让实验室的每一天都有据可循。</p>
      </div>
      <el-button :icon="Refresh" :loading="busy" @click="loadStatistics()">刷新统计</el-button>
    </header>

    <section class="surface-card stats-filter-card" aria-label="统计日期筛选">
      <div class="filters-bar">
        <span>统计日期</span>
        <el-date-picker v-model="dateRange" type="daterange" popper-class="lab-range-picker" value-format="YYYY-MM-DD"
          start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至"
          :disabled="busy" :disabled-date="disabledDate" unlink-panels clearable aria-label="统计日期范围" />
        <el-button type="primary" :disabled="busy" @click="applyFilter">查询</el-button>
        <el-button :disabled="busy" @click="resetFilter">最近 30 天</el-button>
      </div>
      <p class="muted-text">支持最长 366 天的统计。设备总数与可用数量反映当前状态，其余指标按所选日期统计。</p>
      <el-alert v-if="filterError" :title="filterError" type="warning" :closable="false" show-icon />
    </section>

    <section aria-label="统计总览" :aria-busy="overview.loading">
      <div class="card-heading">
        <div><h2>运行总览</h2><p class="muted-text">{{ rangeLabel }}</p></div>
        <span v-if="overview.data" class="muted-text">可见实验室 {{ formatNumber(overview.data.labCount) }} 间</span>
      </div>
      <StateError v-if="overview.error" title="总览加载失败" :description="overview.error" :disabled="busy" compact @retry="loadStatistics()" />
      <div class="stats-grid">
        <article v-for="card in cards" :key="card.label" class="stat-card">
          
          <p class="stat-label">{{ card.label }}</p>
          <StateLoading v-if="overview.loading" label="正在加载指标…" :rows="1" compact />
          <template v-else>
            <div class="stat-value">{{ card.value }}</div>
            <p class="muted-text">{{ overview.error ? '暂不可用，请刷新重试' : card.detail }}</p>
          </template>
        </article>
      </div>
    </section>

    <div class="chart-grid">
      <section class="surface-card chart-card" :aria-busy="usage.loading">
        <div class="card-heading"><div><h2>设备使用率排行</h2><p class="muted-text">统计区间内实际使用时长占设备存在时长的比例。</p></div></div>
        <StateLoading v-if="usage.loading" class="chart-empty" label="正在加载设备使用率…" :rows="5" />
        <StateError v-else-if="usage.error" class="chart-empty" title="设备使用率加载失败" :description="usage.error" :disabled="busy" @retry="loadStatistics()" />
        <StateEmpty v-else-if="!hasUsage" class="chart-empty" title="暂无设备数据" description="当前管理范围内没有可统计的设备。" />
        <div v-show="!usage.loading && !usage.error && hasUsage" ref="usageElement" class="chart-canvas" aria-label="设备使用率柱状图"></div>
      </section>
      <section class="surface-card chart-card" :aria-busy="ranking.loading">
        <div class="card-heading"><div><h2>实验室预约排行</h2><p class="muted-text">按所选区间预约申请数排序，包含当前状态不同的申请。</p></div></div>
        <StateLoading v-if="ranking.loading" class="chart-empty" label="正在加载实验室排行…" :rows="5" />
        <StateError v-else-if="ranking.error" class="chart-empty" title="实验室排行加载失败" :description="ranking.error" :disabled="busy" @retry="loadStatistics()" />
        <StateEmpty v-else-if="!hasRanking" class="chart-empty" title="暂无实验室数据" description="当前管理范围内没有可统计的实验室。" />
        <div v-show="!ranking.loading && !ranking.error && hasRanking" ref="rankingElement" class="chart-canvas" aria-label="实验室预约排行柱状图"></div>
      </section>
    </div>

    <div class="chart-grid">
      <section class="surface-card chart-card" :aria-busy="trend.loading">
        <div class="card-heading"><div><h2>预约趋势</h2><p class="muted-text">按申请日期查看预约数量与审批状态</p></div></div>
        <StateLoading v-if="trend.loading" class="chart-empty" label="正在加载预约趋势…" :rows="5" />
        <StateError v-else-if="trend.error" class="chart-empty" title="预约趋势加载失败" :description="trend.error" :disabled="busy" @retry="loadStatistics()" />
        <StateEmpty v-else-if="!hasTrend" class="chart-empty" title="暂无预约记录" description="所选日期内没有预约申请，可调整日期范围查看。" />
        <div v-show="!trend.loading && !trend.error && hasTrend" ref="trendElement" class="chart-canvas" aria-label="逐日预约数量折线图"></div>
      </section>
      <section class="surface-card chart-card" :aria-busy="repairs.loading">
        <div class="card-heading"><div><h2>报修状态分布</h2><p class="muted-text">所选日期内提交的工单当前处理状态</p></div></div>
        <StateLoading v-if="repairs.loading" class="chart-empty" label="正在加载报修分布…" :rows="5" />
        <StateError v-else-if="repairs.error" class="chart-empty" title="报修分布加载失败" :description="repairs.error" :disabled="busy" @retry="loadStatistics()" />
        <StateEmpty v-else-if="!hasRepairs" class="chart-empty" title="暂无报修记录" description="所选日期内没有报修工单。" />
        <div v-show="!repairs.loading && !repairs.error && hasRepairs" ref="repairElement" class="chart-canvas" aria-label="报修工单状态环形图"></div>
      </section>
    </div>
    <p v-if="actualRange?.effectiveEndTime" class="muted-text">使用时长统计截至 {{ actualRange.effectiveEndTime }}。统计范围依据当前账号的实验室管理权限确定。</p>
  </div>
</template>
