<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  // x 轴标签：['11/03', ...]
  labels: { type: Array, required: true },
  // [{ name, data:[...], color, type: 'line'|'bar' }]
  series: { type: Array, required: true },
  height: { type: Number, default: 250 }
})

const el = ref(null)
let chart = null

function render() {
  if (!chart) return
  const series = props.series.map((s) => ({
    name: s.name,
    type: s.type || 'line',
    data: s.data,
    smooth: true,
    itemStyle: { color: s.color },
    lineStyle: { width: 2.5, color: s.color },
    barWidth: '42%',
    itemStyle: s.type === 'bar' ? { borderRadius: [6, 6, 0, 0], color: s.color } : {}
  }))
  const option = {
    tooltip: { trigger: 'axis' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 14,
      textStyle: { color: '#8a9a8e', fontSize: 12 }
    },
    grid: { left: 40, right: 24, top: 28, bottom: 44 },
    xAxis: {
      type: 'category',
      data: props.labels,
      axisLine: { lineStyle: { color: '#e8e2d4' } },
      axisTick: { show: false },
      axisLabel: { color: '#8a9a8e', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#8a9a8e', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0eae0' } }
    },
    series
  }
  chart.setOption(option, true)
}

function resize() {
  if (chart) chart.resize()
}

onMounted(() => {
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})

watch(() => [props.labels, props.series], render, { deep: true })
</script>

<template>
  <div ref="el" :style="{ width: '100%', height: height + 'px' }"></div>
</template>
