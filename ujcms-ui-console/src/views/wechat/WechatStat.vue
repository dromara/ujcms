<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import echarts, { type ECOption } from '@/utils/echarts';
import { queryWechatAccountList, queryWechatStatArticle, queryWechatStatInterface, queryWechatStatUser } from '@/api/wechat';
import { enumParam, numberParam, stringParam, useRouteState } from '@/composables/useRouteState';

defineOptions({
  name: 'WechatStat',
});
const { t } = useI18n();
const accountList = ref<any[]>([]);
// 当前公众号存于 query.accountId（docs/spec/console-view-state），默认首个公众号：视图在刷新、页签往返时都会重建，
// 不记住就会悄悄回到首个公众号，运营者可能在错误的公众号上继续操作
// 页签与时间跨度同为导航上下文，一并记住
const { accountId, tab, days } = useRouteState({
  accountId: stringParam(() => accountList.value[0]?.id),
  tab: enumParam('user', ['user', 'article', 'interface']),
  days: numberParam(7, [7, 30, 90]),
});
const loading = ref<boolean>(false);
const chartRef = shallowRef<HTMLElement>();

// 图文分析总跨度上限30天，其余90天（与后端跨度校验一致）
const dayOptions = computed<number[]>(() => (tab.value === 'article' ? [7, 30] : [7, 30, 90]));
// 恢复出的组合可能不合法（手改地址得到 tab=article&days=90），与切换页签时同样收敛到 30 天
if (!dayOptions.value.includes(days.value)) {
  days.value = 30;
}

// 统计数据为 T+1，最新数据到昨天
const buildParams = () => {
  const end = dayjs().subtract(1, 'day');
  const begin = end.subtract(days.value - 1, 'day');
  return { accountId: accountId.value, begin: begin.format('YYYY-MM-DD'), end: end.format('YYYY-MM-DD') };
};

const buildOption = (list: any[]): ECOption => {
  const dates = list.map((item) => item.date);
  const symbol = list.length > 30 ? 'none' : 'circle';
  if (tab.value === 'user') {
    const series = ['newUser', 'cancelUser', 'netUser', 'cumulateUser'];
    return {
      tooltip: { trigger: 'axis' },
      legend: { top: 0, data: series.map((name) => t(`wechatStat.user.${name}`)) },
      grid: { left: 16, right: 24, top: 40, bottom: 8, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, axisTick: { show: false }, data: dates },
      yAxis: { type: 'value', minInterval: 1 },
      series: series.map((name) => ({
        name: t(`wechatStat.user.${name}`),
        type: 'line',
        symbol,
        data: list.map((item) => Number(item[name])),
      })),
    };
  }
  if (tab.value === 'article') {
    // 发表内容概况（微信 getbizsummary）：人数、条数类指标为折线，发表篇数为柱状
    const series = ['readUser', 'shareUser', 'likeUser', 'zaikanUser', 'commentCount', 'collectionUser', 'redirectOriPageUser'];
    return {
      tooltip: { trigger: 'axis' },
      legend: { top: 0, data: [...series, 'sendPageCount'].map((name) => t(`wechatStat.article.${name}`)) },
      grid: { left: 16, right: 24, top: 40, bottom: 8, containLabel: true },
      xAxis: { type: 'category', axisTick: { show: false }, data: dates },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        ...series.map((name) => ({
          name: t(`wechatStat.article.${name}`),
          type: 'line' as const,
          symbol,
          data: list.map((item) => Number(item[name])),
        })),
        { name: t('wechatStat.article.sendPageCount'), type: 'bar', data: list.map((item) => Number(item.sendPageCount)) },
      ],
    };
  }
  // 接口分析：次数为折线/柱状，耗时（毫秒）使用右侧坐标轴
  return {
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      data: ['callbackCount', 'failCount', 'avgTimeCost', 'maxTimeCost'].map((name) => t(`wechatStat.interface.${name}`)),
    },
    grid: { left: 16, right: 24, top: 40, bottom: 8, containLabel: true },
    xAxis: { type: 'category', axisTick: { show: false }, data: dates },
    yAxis: [
      { type: 'value', minInterval: 1 },
      { type: 'value', minInterval: 1, name: 'ms', splitLine: { show: false } },
    ],
    series: [
      { name: t('wechatStat.interface.callbackCount'), type: 'line', symbol, data: list.map((item) => Number(item.callbackCount)) },
      { name: t('wechatStat.interface.failCount'), type: 'bar', data: list.map((item) => Number(item.failCount)) },
      { name: t('wechatStat.interface.avgTimeCost'), type: 'line', symbol, yAxisIndex: 1, data: list.map((item) => Number(item.avgTimeCost)) },
      { name: t('wechatStat.interface.maxTimeCost'), type: 'line', symbol, yAxisIndex: 1, data: list.map((item) => Number(item.maxTimeCost)) },
    ],
  };
};

const fetchChart = async () => {
  if (accountId.value == null) {
    return;
  }
  loading.value = true;
  try {
    const params = buildParams();
    let list: any[];
    if (tab.value === 'user') {
      list = await queryWechatStatUser(params);
    } else if (tab.value === 'article') {
      list = await queryWechatStatArticle(params);
    } else {
      list = await queryWechatStatInterface(params);
    }
    await nextTick();
    const chartDom = chartRef.value;
    if (chartDom == null) {
      return;
    }
    let chart = echarts.getInstanceByDom(chartDom);
    if (chart == null) {
      chart = echarts.init(chartDom);
    }
    chart.setOption(buildOption(list), true);
  } finally {
    loading.value = false;
  }
};
const handleResize = () => {
  const chartDom = chartRef.value;
  if (chartDom != null) {
    echarts.getInstanceByDom(chartDom)?.resize();
  }
};
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
  if (accountList.value.length > 0) {
    // 恢复出的公众号须仍在列表中（切换站点后整页重载而地址不变、公众号已删除），否则回落到首个
    if (!accountList.value.some((item) => item.id === accountId.value)) {
      accountId.value = accountList.value[0].id;
    }
    fetchChart();
  }
};
onMounted(() => {
  fetchAccountList();
  window.addEventListener('resize', handleResize);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
});

const handleTabChange = () => {
  if (!dayOptions.value.includes(days.value)) {
    days.value = 30;
  }
  fetchChart();
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" :placeholder="$t('wechat.selectAccount')" class="w-48" @change="() => fetchChart()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-radio-group v-model="days" @change="() => fetchChart()">
        <el-radio-button v-for="n in dayOptions" :key="n" :value="n">{{ $t(`wechatStat.last${n}day`) }}</el-radio-button>
      </el-radio-group>
    </div>
    <el-alert v-if="accountList.length <= 0" :title="$t('wechat.noAccount')" type="info" :closable="false" class="mt-3" />
    <div class="mt-3 app-block">
      <el-tabs v-model="tab" class="px-3" @tab-change="() => handleTabChange()">
        <el-tab-pane v-for="item in ['user', 'article', 'interface']" :key="item" :name="item" :label="$t(`wechatStat.tab.${item}`)"></el-tab-pane>
      </el-tabs>
      <div v-loading="loading" class="p-3">
        <div ref="chartRef" class="h-96"></div>
      </div>
    </div>
  </div>
</template>
