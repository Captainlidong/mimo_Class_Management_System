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
  height: { type: Number, default: 250 }
})

const el = ref(null)
let chart = null

function render() {
  if (!chart) return
  const data = props.items
    .filter((i) => i.value > 0)
    .map((i) => ({ name: i.name, value: i.value, itemStyle: { color: i.color } }))
  const seriesData = data.length ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: '#f6e7ee' } }]
  const option = {
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 14,
      textStyle: { color: '#7c7a91', fontSize: 12 }
    },
    graphic: props.centerValue
      ? [
          {
            type: 'text',
            left: 'center',
            top: '36%',
            style: { text: props.centerValue, fontSize: 26, fontWeight: 800, fill: '#f43f7a' }
          },
          {
            type: 'text',
            left: 'center',
            top: '52%',
            style: { text: props.centerLabel, fontSize: 12, fill: '#7c7a91' }
          }
        ]
      : [],
    series: [
      {
        type: 'pie',
        radius: ['56%', '78%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
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

watch(() => [props.items, props.centerValue, props.centerLabel], render, { deep: true })
</script>

<template>
  <div ref="el" :style="{ width: '100%', height: height + 'px' }"></div>
</template>
