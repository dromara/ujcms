<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import type { ECharts } from 'echarts/core';
import echarts, { type ECOption } from '@/utils/echarts';
import { queryTrendStat, querySourceTypeStat } from '@/api/stat';
import HomeCard from './HomeCard.vue';

/**
 * 流量：今日 PV / UV（较昨日同时段的增减）+ 最近 30 天按日的 PV / UV 折线 + 30 天访问来源。
 * 跨度 ≥ 14 天的 trend-stat 返回按日行，且当天的日行每分钟更新，今日 KPI 直接取自同一批数据；
 * 增减另取一次小时行（昨日 0 点 → 现在），今日与昨日各累加到当前小时——拿半天比昨日全天，早上永远是 -100%。
 * UV 按小时累加两边同样偏大（跨小时的访客重复计），比值仍可比；PV 精确。
 * 图表颜色取自 EP 主色变量（主题色改变后视图重建时重新读取）；尺寸用 ResizeObserver（侧栏折叠、分栏切换不触发 window.resize）。
 *
 * 版式（console-home D9）：左右两栏以 gray-100 竖线分隔，两栏顶部各是同一样式的 HTML 小标题（KPI 标签 / 访问来源），
 * 不用 ECharts 的画布标题——字号、颜色、基线都与旁边的 HTML 文字对不上。KPI 行兼作折线的图例：标签前的色点与系列同色，
 * 折线不再另画一行居中的图例（那是用另一套词把 KPI 行再说一遍）。环图中心是 30 天浏览量合计，图例是带数字与占比的 HTML 列表
 * （ECharts 图例只有名字），二者按同一色序着色。坐标轴收成细线：gray-200 虚线网格（gray-100 在普通屏上看不见）、无 y 轴线、次要灰标签；提示框走卡片的边线、不带阴影。
 * 卡片标题右侧的“最近30天”说明折线与环图的时段（两个大数是今日的）。
 */
defineOptions({
  name: 'TrafficCard',
});
const { t, n } = useI18n();
const trendEl = ref<HTMLElement>();
const sourceEl = ref<HTMLElement>();
const loading = ref<boolean>(true);
const kpi = ref({ todayPv: 0, todayUv: 0 });
// 同时段累计 [今日, 昨日]
const sameTime = ref({ pv: [0, 0], uv: [0, 0] });
// 30 天来源，按浏览量降序；色点与环图按同一色序
const sourceItems = ref<{ name: string; value: number; color: string }[]>([]);

// 色序（主色链由深到浅）：HTML 色点引用变量名，图表在初始化时读取变量值。折线 PV / UV 取前两色；
// 来源类型至多四种（内部链接 / 直接访问 / 外部链接 / 搜索引擎）
const PALETTE = [
  ['--el-color-primary', '#409eff'],
  ['--el-color-primary-light-3', '#79bbff'],
  ['--el-color-primary-light-5', '#a0cfff'],
  ['--el-color-primary-light-7', '#c6e2ff'],
];
const paletteVar = (index: number): string => `var(${PALETTE[index % PALETTE.length][0]})`;
// 轴线、网格与提示框边线取控制台的边线灰（tailwind gray-200 / 300 的字面量：面色不走变量，console-shell S6）
const GRAY_200 = '#e5e7eb';
const GRAY_300 = '#d1d5db';

// 较昨日同时段的百分比；昨日为零没有基数，不显示（“—”）
const deltaOf = (today: number, yesterday: number): number | null => (yesterday > 0 ? ((today - yesterday) / yesterday) * 100 : null);
const deltaText = (delta: number | null): string => (delta == null ? '—' : `${delta > 0 ? '+' : ''}${n(Math.round(delta * 10) / 10)}%`);
// 增长绿、下降红（Element Plus 的 success / danger 语义，与控制台其它处一致）
const deltaClass = (delta: number | null): string => (delta == null || delta === 0 ? 'text-gray-secondary' : delta > 0 ? 'text-success' : 'text-danger');
const kpiItems = computed(() => [
  { key: 'todayPv', color: paletteVar(0), value: kpi.value.todayPv, delta: deltaOf(sameTime.value.pv[0], sameTime.value.pv[1]) },
  { key: 'todayUv', color: paletteVar(1), value: kpi.value.todayUv, delta: deltaOf(sameTime.value.uv[0], sameTime.value.uv[1]) },
]);
const sourceTotal = computed(() => sourceItems.value.reduce((sum, item) => sum + item.value, 0));
const percentText = (value: number): string => (sourceTotal.value > 0 ? `${n(Math.round((value / sourceTotal.value) * 1000) / 10)}%` : '—');

let charts: ECharts[] = [];
let observer: ResizeObserver | undefined;

const cssColor = (name: string, fallback: string): string => getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
// 提示框：白底、gray-200 边线、无阴影，与卡片同一套边线
const tooltipChrome = (textColor: string) => ({
  backgroundColor: '#fff',
  borderColor: GRAY_200,
  borderWidth: 1,
  padding: [8, 12],
  textStyle: { color: textColor, fontSize: 12 },
  extraCssText: 'box-shadow: none; border-radius: 6px;',
});

const initChart = (el: HTMLElement | undefined, option: ECOption): void => {
  if (el == null) return;
  const chart = echarts.getInstanceByDom(el) ?? echarts.init(el);
  chart.setOption(option, true);
  charts.push(chart);
  observer?.observe(el);
};

onMounted(async () => {
  observer = new ResizeObserver(() => charts.forEach((chart) => chart.resize()));
  const [trend, source, hourly] = await Promise.allSettled([
    queryTrendStat({ begin: dayjs().startOf('day').subtract(29, 'day').format(), end: dayjs().format() }),
    querySourceTypeStat({ begin: dayjs().subtract(30, 'day').format('YYYY-MM-DD'), end: dayjs().format('YYYY-MM-DD') }),
    // 24～48 小时的跨度返回小时行
    queryTrendStat({ begin: dayjs().startOf('day').subtract(1, 'day').format(), end: dayjs().format() }),
  ]);
  const rows: any[] = trend.status === 'fulfilled' ? (trend.value ?? []) : [];
  const sources: any[] = source.status === 'fulfilled' ? (source.value ?? []) : [];
  const rowOf = (day: dayjs.Dayjs) => rows.find((row) => dayjs(row.date).isSame(day, 'day'));
  const today = rowOf(dayjs());
  kpi.value = { todayPv: Number(today?.pvCount ?? 0), todayUv: Number(today?.uvCount ?? 0) };
  const hours: any[] = hourly.status === 'fulfilled' ? (hourly.value ?? []) : [];
  const hour = dayjs().hour();
  const sumTo = (day: dayjs.Dayjs, field: string): number =>
    hours.filter((row) => dayjs(row.date).isSame(day, 'day') && dayjs(row.date).hour() <= hour).reduce((sum, row) => sum + Number(row[field] ?? 0), 0);
  sameTime.value = {
    pv: [sumTo(dayjs(), 'pvCount'), sumTo(dayjs().subtract(1, 'day'), 'pvCount')],
    uv: [sumTo(dayjs(), 'uvCount'), sumTo(dayjs().subtract(1, 'day'), 'uvCount')],
  };
  sourceItems.value = [...sources]
    .sort((a, b) => Number(b.pvCount) - Number(a.pvCount))
    .map((item, index) => ({ name: t(`visitSource.type.${item.name}`), value: Number(item.pvCount), color: paletteVar(index) }));
  loading.value = false;
  await nextTick();

  const palette = PALETTE.map(([name, fallback]) => cssColor(name, fallback));
  const [primary, light3, light5] = palette;
  const textRegular = cssColor('--el-text-color-regular', '#606266');
  const textSecondary = cssColor('--el-text-color-secondary', '#909399');
  initChart(trendEl.value, {
    tooltip: { trigger: 'axis', axisPointer: { lineStyle: { color: GRAY_300 } }, ...tooltipChrome(textRegular) },
    grid: { left: 0, right: 8, top: 12, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: GRAY_200 } },
      axisLabel: { color: textSecondary, margin: 12 },
      data: rows.map((row) => dayjs(row.date).format('MM-DD')),
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: textSecondary },
      splitLine: { lineStyle: { type: 'dashed', color: GRAY_200 } },
    },
    series: [
      {
        name: t('visit.pv'),
        type: 'line',
        smooth: true,
        symbol: 'none',
        color: primary,
        // PV 下方从主色淡到白的渐变面
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: light5 },
            { offset: 1, color: '#fff' },
          ]),
          opacity: 0.6,
        },
        data: rows.map((row) => Number(row.pvCount)),
      },
      { name: t('visit.uv'), type: 'line', smooth: true, symbol: 'none', color: light3, data: rows.map((row) => Number(row.uvCount)) },
    ],
  });
  initChart(sourceEl.value, {
    tooltip: { trigger: 'item', valueFormatter: (value: any) => n(Number(value)), ...tooltipChrome(textRegular) },
    color: palette,
    series: [
      {
        name: t('home.trafficSource'),
        type: 'pie',
        radius: ['60%', '86%'],
        itemStyle: { borderRadius: 3, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: sourceItems.value.map((item) => ({ value: item.value, name: item.name })),
      },
    ],
  });
});
onBeforeUnmount(() => {
  observer?.disconnect();
  charts.forEach((chart) => chart.dispose());
  charts = [];
});
</script>

<template>
  <home-card :title="$t('home.traffic')">
    <template #action>
      <span class="text-xs text-gray-secondary">{{ $t('home.traffic.period') }}</span>
    </template>
    <div v-loading="loading" class="flex">
      <div class="w-2/3 pr-6">
        <div class="flex gap-10">
          <div v-for="item in kpiItems" :key="item.key">
            <div class="flex items-center text-xs text-gray-secondary">
              <span class="w-2 h-2 mr-1.5 rounded-full" :style="{ backgroundColor: item.color }"></span>
              {{ $t(`home.traffic.${item.key}`) }}
            </div>
            <div class="mt-0.5 text-2xl font-medium leading-8 tabular-nums text-gray-primary">{{ $n(item.value) }}</div>
            <i18n-t keypath="home.traffic.vsYesterday" tag="div" class="text-xs tabular-nums text-gray-secondary">
              <template #delta>
                <span :class="deltaClass(item.delta)">{{ deltaText(item.delta) }}</span>
              </template>
            </i18n-t>
          </div>
        </div>
        <div ref="trendEl" class="h-64 mt-3"></div>
      </div>
      <div class="flex flex-col w-1/3 pl-6 border-l border-gray-100">
        <div class="text-xs text-gray-secondary">{{ $t('home.trafficSource') }}</div>
        <!-- 环图与列表并排；列表放不下最小宽度（12rem）时换到环图下方：内容驱动的折行，不加断点（1280 宽的笔记本并排放不下，1440 起放得下） -->
        <div class="flex flex-wrap items-center content-center justify-center flex-1 gap-x-6 gap-y-4 min-h-[10rem]">
          <div class="relative w-36 h-36 shrink-0">
            <div ref="sourceEl" class="w-full h-full"></div>
            <!-- 环心：30 天浏览量合计（来源之和）；不拦截指针，环上的提示框照常 -->
            <div class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
              <span class="text-lg font-medium leading-6 tabular-nums text-gray-primary">{{ $n(sourceTotal) }}</span>
              <span class="text-xs text-gray-secondary">{{ $t('home.traffic.sourceTotal') }}</span>
            </div>
          </div>
          <ul v-if="sourceItems.length > 0" class="flex-1 min-w-[12rem]">
            <li v-for="item in sourceItems" :key="item.name" class="flex items-center h-7 text-sm">
              <span class="w-2 h-2 mr-2 rounded-full shrink-0" :style="{ backgroundColor: item.color }"></span>
              <span class="flex-1 min-w-0 truncate text-gray-regular">{{ item.name }}</span>
              <span class="ml-3 tabular-nums text-gray-primary">{{ $n(item.value) }}</span>
              <span class="w-12 text-right text-xs tabular-nums text-gray-secondary">{{ percentText(item.value) }}</span>
            </li>
          </ul>
          <div v-else-if="!loading" class="flex-1 min-w-[12rem] text-sm text-gray-secondary">{{ $t('home.trafficSource.empty') }}</div>
        </div>
      </div>
    </div>
  </home-card>
</template>
