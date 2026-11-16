<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { GraphicComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, GraphicComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  // [{ name, value, color }]
  items: { type: Array, required: true },
  centerValue: { type: String, default: '' },
  centerLabel: { type: String, default: '' },
  height: { type: Number, default: 250 },
  dark: { type: Boolean, default: false }
})

function inkColor() {
  return props.dark ? '#93a398' : '#7c7a91'
}

function centerColor() {
  return props.dark ? '#9bc4a9' : '#2f4838'
}

function emptyColor() {
  return props.dark ? 'rgba(127, 176, 143, 0.12)' : '#eef4ee'
}

function borderColor() {
  return props.dark ? '#242e28' : '#fff'
}

const el = ref(null)
let chart = null

function render() {
  if (!chart) return
  const inkC = inkColor();
  const data = props.items
    .filter((i) => i.value > 0)
    .map((i) => ({ name: i.name, value: i.value, itemStyle: { color: i.color } }))
  const seriesData = data.length ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: emptyColor() } }]
  const option = {
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 14,
      textStyle: { color: inkC, fontSize: 12 }
    },
    graphic: props.centerValue
      ? [
          {
            type: 'text',
            left: 'center',
            top: '36%',
            style: { text: props.centerValue, fontSize: 26, fontWeight: 800, fill: centerColor() }
          },
          {
            type: 'text',
            left: 'center',
            top: '52%',
            style: { text: props.centerLabel, fontSize: 12, fill: inkC }
          }
        ]
      : [],
    series: [
      {
        type: 'pie',
        radius: ['56%', '78%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: borderColor(), borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        data: seriesData
      }
    ]
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

watch(() => [props.items, props.centerValue, props.centerLabel, props.dark], render, { deep: true })
</script>

<template>
  <div ref="el" :style="{ width: '100%', height: height + 'px' }"></div>
</template>
