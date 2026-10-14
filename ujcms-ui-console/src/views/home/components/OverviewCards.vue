<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { queryContentStat } from '@/api/personal';
import { isMenuRouteVisible } from '../menuLeaves';

/**
 * 概览：文章 / 栏目 / 用户 / 附件的总数、最近 30 天新增（芯片）与按日柱状迷你图；对应叶子可见时整卡可点。
 * 版式（console-home D10）：总数与迷你图并排成一组靠左（不靠右：贴着卡片右缘的小图与数字之间空一截，像没做完），
 * 图 20px 高、落在数字基线上、只占数字的下半高，横向撑满数字右侧余宽（宽屏上四张卡各 470px，固定 128px 的小字形让每卡
 * 右半边空着；柱子随卡宽变粗，窄屏上缩回小字形），与数字间留 16px（12px 时首根柱子贴着数字，读成一个词）；数字占固定最小宽 112px（等宽数字，容得下“88,888”），
 * 文章、附件数以万计而栏目、用户只有两位数，四张卡的柱图仍从同一条竖线起——柱图宽度不随数字位数变，占半宽的做法解决不了这点；说明行写明
 * “最近30天新增”再跟芯片，没有新增时写“最近30天无新增”、不出芯片也不画图——栏目、用户本就少有增减，
 * 一枚孤零零的“0”让人不知所指。窗口与流量卡同为 30 天。芯片用 EP 的 success 标签（与最近更新的状态标签同一套）。
 * 画柱不画线：按日新增是离散事件（多数日子为零、偶有尖峰），30 根细柱说得实在，也与下方流量卡的连续折线
 * 形态不同——之前的填充折线撑满卡宽，四张卡成了流量图的缩略图（2026-09-22 评审）。
 * 内联 SVG（四张卡各起一个 ECharts 实例不值当）：柱色走 tailwind 的 fill-primary，随主题色变化；
 * 基线是 gray-300 细线（gray-200 在 32px 高的字形里看不见），让零日也有延展；非零小值至少 2 个单位高，免得 1 在 78 旁边看不见。
 */
defineOptions({
  name: 'OverviewCards',
});
const router = useRouter();
const ITEMS = [
  { key: 'article', route: 'ArticleList' },
  { key: 'channel', route: 'ChannelList' },
  { key: 'user', route: 'UserList' },
  { key: 'attachment', route: 'AttachmentList' },
].map((item) => ({ ...item, linkable: isMenuRouteVisible(item.route) }));
const stat = ref<any>({});
const loading = ref<boolean>(true);
const recentOf = (key: string): number => Number(stat.value[key]?.last30day ?? 0);
// 迷你柱图：30 天按日新增，viewBox 128×20（20px 高，落在数字下半部；32px 高时柱子与数字一样高，抢了标题），横向拉伸到余宽；
// 柱宽为步长之半、间隙与柱同宽（3/4 步长时宽屏上柱子约 9px、间隙 3px，成了一排板块；2/3 步长配 96px 宽则像条形码），
// 纵向按最大值缩放，顶部留 2；全零不画
const SPARK_W = 128;
const SPARK_H = 20;
const SPARK_PAD = 2;
const SPARK_MIN = 2;
const sparkValues = (daily: unknown): number[] => (Array.isArray(daily) ? daily : []).map((v) => Number(v ?? 0));
const sparkBars = (daily: unknown): { x: number; y: number; w: number; h: number }[] => {
  const values = sparkValues(daily);
  if (values.length < 2 || values.every((v) => v <= 0)) return [];
  const max = Math.max(...values, 1);
  const step = SPARK_W / values.length;
  const width = step / 2;
  const scale = (SPARK_H - SPARK_PAD) / max;
  return values.flatMap((v, i) => {
    if (v <= 0) return [];
    const h = Math.max(v * scale, SPARK_MIN);
    return [{ x: i * step, y: SPARK_H - h, w: width, h }];
  });
};
onMounted(async () => {
  try {
    stat.value = await queryContentStat();
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div v-loading="loading" class="grid grid-cols-2 md:grid-cols-4 gap-3">
    <component
      :is="item.linkable ? 'button' : 'div'"
      v-for="item in ITEMS"
      :key="item.key"
      :type="item.linkable ? 'button' : undefined"
      class="app-block p-4 text-left"
      :class="{ 'cursor-pointer hover:border-primary': item.linkable }"
      @click="() => item.linkable && router.push({ name: item.route })"
    >
      <div class="text-sm text-gray-secondary">{{ $t(`home.overview.${item.key}`) }}</div>
      <div class="mt-1 flex items-end gap-4 h-9">
        <span class="min-w-28 text-3xl font-medium leading-none tabular-nums text-gray-primary">{{ $n(stat[item.key]?.total ?? 0) }}</span>
        <svg v-if="sparkBars(stat[item.key]?.daily).length" class="flex-1 min-w-0 h-5" :viewBox="`0 0 ${SPARK_W} ${SPARK_H}`" preserveAspectRatio="none" aria-hidden="true">
          <line x1="0" :y1="SPARK_H" :x2="SPARK_W" :y2="SPARK_H" class="stroke-gray-300" stroke-width="1" vector-effect="non-scaling-stroke" />
          <rect v-for="bar in sparkBars(stat[item.key]?.daily)" :key="bar.x" :x="bar.x" :y="bar.y" :width="bar.w" :height="bar.h" class="fill-primary" />
        </svg>
      </div>
      <div class="mt-2 flex items-center gap-1.5 text-xs text-gray-secondary">
        <template v-if="recentOf(item.key) > 0">
          <span>{{ $t('home.overview.recent') }}</span>
          <el-tag type="success" size="small" round class="tabular-nums">+{{ $n(recentOf(item.key)) }}</el-tag>
        </template>
        <span v-else>{{ $t('home.overview.recentNone') }}</span>
      </div>
    </component>
  </div>
</template>
