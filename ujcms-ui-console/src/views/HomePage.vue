<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import { useI18n } from 'vue-i18n';
import { Document, Files, FolderOpened, User, UserFilled, BellFilled } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import duration from 'dayjs/plugin/duration';
import echarts, { ECOption } from '@/utils/echarts';
import { currentUser, hasPermission } from '@/stores/useCurrentUser';
import { queryContentStat } from '@/api/personal';
import { queryTrendStat, queryVisitorStat, querySourceTypeStat } from '@/api/stat';
import { queryArticlePendingCount, queryArticleRejectCount, queryFormPendingCount, queryFormRejectCount } from '@/api/content';
import { queryMessageBoardUnreviewedCount } from '@/api/interaction';

const { t, n } = useI18n();
dayjs.extend(duration);

const trendChart = shallowRef<HTMLElement>();
const initTrendChart = async () => {
  const list = await queryTrendStat({ begin: dayjs().startOf('day').subtract(1, 'day').format(), end: dayjs().endOf('day').format() });
  const option: ECOption = {
    tooltip: { trigger: 'axis' },
    legend: { data: [t('visitTrend.yesterdayPv'), t('visitTrend.todayPv')] },
    grid: { left: 16, right: 16, top: 40, bottom: 0, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, axisTick: { show: false }, data: Array.from(Array(24).keys()) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: t('visitTrend.yesterdayPv'),
        type: 'line',
        symbol: 'circle',
        color: '#a0cfff',
        data: list.filter((item) => dayjs(item.date) < dayjs().startOf('day')).map((item) => Number(item.pvCount)),
      },
      {
        name: t('visitTrend.todayPv'),
        type: 'line',
        symbol: 'circle',
        color: '#409eff',
        areaStyle: { color: '#ecf5ff' },
        data: list.filter((item) => dayjs(item.date) >= dayjs().startOf('day')).map((item) => Number(item.pvCount)),
      },
    ],
  };
  const chartDom = trendChart.value;
  if (chartDom == null) {
    return;
  }
  let chart = echarts.getInstanceByDom(chartDom);
  if (chart == null) {
    chart = echarts.init(chartDom);
  }
  chart.setOption(option);
  window.addEventListener('resize', function () {
    chart?.resize();
  });
};

const sourceTypeChart = shallowRef<HTMLElement>();
const initSourceTypeChart = async () => {
  const list = await querySourceTypeStat({ begin: dayjs().subtract(30, 'day').format('YYYY-MM-DD'), end: dayjs().format('YYYY-MM-DD') });
  const option: ECOption = {
    title: { text: t('menu.stat.visitSource'), textStyle: { color: '#909399', fontWeight: 'normal', fontSize: 16 } },
    legend: { type: 'scroll', orient: 'vertical', right: '10%', top: 16, bottom: 16 },
    tooltip: { trigger: 'item', valueFormatter: (value: any) => n(value) },
    series: [
      {
        name: t('menu.stat.visitSource'),
        type: 'pie',
        radius: ['44%', '80%'],
        center: ['40%', '54%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: false } },
        labelLine: { show: false },
        data: list.map((item) => ({ value: Number(item.pvCount), name: t(`visitSource.type.${item.name}`) })),
      },
    ],
  };
  const chartDom = sourceTypeChart.value;
  if (chartDom == null) {
    return;
  }
  let chart = echarts.getInstanceByDom(chartDom);
  if (chart == null) {
    chart = echarts.init(chartDom);
  }
  chart.setOption(option);
  window.addEventListener('resize', function () {
    chart?.resize();
  });
};

const newVisitor = ref<any>({});
const oldVisitor = ref<any>({});
const fetchVisitorStat = async () => {
  const visitorStat = await queryVisitorStat({ begin: dayjs().subtract(30, 'day').format('YYYY-MM-DD'), end: dayjs().format('YYYY-MM-DD') });
  newVisitor.value = visitorStat['newVisitor'];
  oldVisitor.value = visitorStat['oldVisitor'];
  newVisitor.value.pvCount = Number(newVisitor.value.pvCount);
  oldVisitor.value.pvCount = Number(oldVisitor.value.pvCount);
  newVisitor.value.uvCount = Number(newVisitor.value.uvCount);
  oldVisitor.value.uvCount = Number(oldVisitor.value.uvCount);
  const totalUvCount = newVisitor.value.uvCount + oldVisitor.value.uvCount;
  if (totalUvCount > 0) {
    newVisitor.value.proportion = (newVisitor.value.uvCount * 100) / totalUvCount;
    oldVisitor.value.proportion = (oldVisitor.value.uvCount * 100) / totalUvCount;
  } else {
    newVisitor.value.proportion = 50;
    oldVisitor.value.proportion = 50;
  }
};

const contentStat = ref<any>({});
const fetchContentStat = async () => {
  contentStat.value = await queryContentStat();
};

const pendingArticle = ref<number>(0);
const fetchPendingArticle = async () => {
  if (currentUser.epRank >= 1) {
    pendingArticle.value = await queryArticlePendingCount();
  }
};
const rejectedArticle = ref<number>(0);
const fetchRejectArticle = async () => {
  if (currentUser.epRank >= 1) {
    rejectedArticle.value = await queryArticleRejectCount();
  }
};
const unreviewedMessageBoard = ref<number>(0);
const fetchUnreviewedMessageBoard = async () => {
  unreviewedMessageBoard.value = await queryMessageBoardUnreviewedCount();
};
const pendingForm = ref<number>(0);
const fetchPendingForm = async () => {
  if (currentUser.epRank >= 3) {
    pendingForm.value = await queryFormPendingCount();
  }
};
const rejectedForm = ref<number>(0);
const fetchRejectForm = async () => {
  if (currentUser.epRank >= 3) {
    rejectedForm.value = await queryFormRejectCount();
  }
};

onMounted(async () => {
  if (hasPermission('articleReview:list')) {
    fetchPendingArticle();
    fetchRejectArticle();
  }
  if (hasPermission('formReview:list')) {
    fetchPendingForm();
    fetchRejectForm();
  }
  if (hasPermission('messageBoard:list')) {
    fetchUnreviewedMessageBoard();
  }
  initTrendChart();
  initSourceTypeChart();
  fetchVisitorStat();
  fetchContentStat();
});
</script>

<template>
  <div>
    <el-row :gutter="12">
      <el-col v-if="pendingArticle > 0" :span="6">
        <div class="mb-3 shadow-md bg-warning-lighter">
          <div class="flex items-center justify-between px-4 py-3 text-xl text-warning">
            <div class="flex items-center">
              <el-icon><BellFilled /></el-icon>
              <el-link class="ml-1 text-base" type="warning" :underline="false" @click="() => $router.push({ path: '/content/article-review' })">
                {{ $t('todo.pendingArticle') }}
              </el-link>
            </div>
            <div>
              <el-link class="ml-1 text-xl" type="warning" :underline="false" @click="() => $router.push({ path: '/content/article-review' })">
                {{ pendingArticle }}
              </el-link>
            </div>
          </div>
        </div>
      </el-col>
      <el-col v-if="rejectedArticle > 0" :span="6">
        <div class="mb-3 shadow-md bg-warning-lighter">
          <div class="flex items-center justify-between px-4 py-3 text-xl text-warning">
            <div class="flex items-center">
              <el-icon><BellFilled /></el-icon>
              <el-link class="ml-1 text-base" type="warning" :underline="false" @click="() => $router.push({ path: '/content/article', query: { status: 22 } })">
                {{ $t('todo.rejectedArticle') }}
              </el-link>
            </div>
            <div>
              <el-link class="ml-1 text-xl" type="warning" :underline="false" @click="() => $router.push({ path: '/content/article', query: { status: 22 } })">
                {{ rejectedArticle }}
              </el-link>
            </div>
          </div>
        </div>
      </el-col>
      <el-col v-if="pendingForm > 0" :span="6">
        <div class="mb-3 shadow-md bg-warning-lighter">
          <div class="flex items-center justify-between px-4 py-3 text-xl text-warning">
            <div class="flex items-center">
              <el-icon><BellFilled /></el-icon>
              <el-link class="ml-1 text-base" type="warning" :underline="false" @click="() => $router.push({ path: '/content/form-review' })">
                {{ $t('todo.pendingForm') }}
              </el-link>
            </div>
            <div>
              <el-link class="ml-1 text-xl" type="warning" :underline="false" @click="() => $router.push({ path: '/content/form-review' })">
                {{ pendingForm }}
              </el-link>
            </div>
          </div>
        </div>
      </el-col>
      <el-col v-if="rejectedForm > 0" :span="6">
        <div class="mb-3 shadow-md bg-warning-lighter">
          <div class="flex items-center justify-between px-4 py-3 text-xl text-warning">
            <div class="flex items-center">
              <el-icon><BellFilled /></el-icon>
              <el-link class="ml-1 text-base" type="warning" :underline="false" @click="() => $router.push({ path: '/content/form', query: { status: 22 } })">
                {{ $t('todo.rejectedForm') }}
              </el-link>
            </div>
            <div>
              <el-link class="ml-1 text-xl" type="warning" :underline="false" @click="() => $router.push({ path: '/content/form', query: { status: 22 } })">
                {{ rejectedForm }}
              </el-link>
            </div>
          </div>
        </div>
      </el-col>
      <el-col v-if="unreviewedMessageBoard > 0" :span="6">
        <div class="mb-3 shadow-md bg-warning-lighter">
          <div class="flex items-center justify-between px-4 py-3 text-xl text-warning">
            <div class="flex items-center">
              <el-icon><BellFilled /></el-icon>
              <el-link class="ml-1 text-base" type="warning" :underline="false" @click="() => $router.push({ path: '/interaction/message-board', query: { status: 1 } })">
                {{ $t('todo.unreviewedMessageBoard') }}
              </el-link>
            </div>
            <div>
              <el-link class="ml-1 text-xl" type="warning" :underline="false" @click="() => $router.push({ path: '/interaction/message-board', query: { status: 1 } })">
                {{ unreviewedMessageBoard }}
              </el-link>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="12">
      <el-col :span="6">
        <div class="p-3 app-block">
          <div class="text-sm text-gray-secondary">{{ $t('contentStat.article') }}</div>
          <div class="flex items-end justify-between mt-1">
            <div class="text-3xl text-primary">{{ contentStat.article?.total }}</div>
            <div class="flex items-center justify-center w-12 h-12 text-xl text-white rounded bg-primary">
              <el-icon><Document /></el-icon>
            </div>
          </div>
          <div class="flex justify-between pt-2 mt-3 text-xs border-t text-gray-regular">
            <div>{{ $t('contentStat.last7day') }}</div>
            <div>{{ contentStat.article?.last7day }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="p-3 app-block">
          <div class="text-sm text-gray-secondary">{{ $t('contentStat.channel') }}</div>
          <div class="flex items-end justify-between mt-1">
            <div class="text-3xl text-primary">{{ contentStat.channel?.total }}</div>
            <div class="flex items-center justify-center w-12 h-12 text-xl text-white rounded bg-warning">
              <el-icon><FolderOpened /></el-icon>
            </div>
          </div>
          <div class="flex justify-between pt-2 mt-3 text-xs border-t text-gray-regular">
            <div>{{ $t('contentStat.last7day') }}</div>
            <div>{{ contentStat.channel?.last7day }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="p-3 app-block">
          <div class="text-sm text-gray-secondary">{{ $t('contentStat.user') }}</div>
          <div class="flex items-end justify-between mt-1">
            <div class="text-3xl text-primary">{{ contentStat.user?.total }}</div>
            <div class="flex items-center justify-center w-12 h-12 text-xl text-white rounded bg-success">
              <el-icon><User /></el-icon>
            </div>
          </div>
          <div class="flex justify-between pt-2 mt-3 text-xs border-t text-gray-regular">
            <div>{{ $t('contentStat.last7day') }}</div>
            <div>{{ contentStat.user?.last7day }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="p-3 app-block">
          <div class="text-sm text-gray-secondary">{{ $t('contentStat.attachment') }}</div>
          <div class="flex items-end justify-between mt-1">
            <div class="text-3xl text-primary">{{ contentStat.attachment?.total }}</div>
            <div class="flex items-center justify-center w-12 h-12 text-xl text-white rounded bg-danger">
              <el-icon><Files /></el-icon>
            </div>
          </div>
          <div class="flex justify-between pt-2 mt-3 text-xs border-t text-gray-regular">
            <div>{{ $t('contentStat.last7day') }}</div>
            <div>{{ contentStat.attachment?.last7day }}</div>
          </div>
        </div>
      </el-col>
    </el-row>
    <div class="px-3 py-5 mt-3 app-block">
      <div ref="trendChart" class="h-64"></div>
    </div>
    <el-row :gutter="12">
      <el-col :span="12">
        <div class="flex p-3 mt-3 justify-evenly app-block">
          <div class="h-64 w-60 text-primary">
            <div class="mt-2 text-center">{{ $t('visitVisitor.newVisitor') }}</div>
            <div class="flex items-center justify-center mt-2">
              <el-icon class="text-5xl"><UserFilled /></el-icon>
              <span class="text-3xl">
                <span v-if="newVisitor.uvCount > 0">{{ $n(newVisitor.proportion ?? 0, 'decimal') }}</span>
                <span v-else>-</span>%
              </span>
            </div>
            <div class="mt-4 space-y-2 text-sm">
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visitVisitor.pv') }}</el-col>
                <el-col :span="12">{{ newVisitor.pvCount }}</el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visitVisitor.uv') }}</el-col>
                <el-col :span="12">{{ newVisitor.uvCount }}</el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visit.bounceRate') }}</el-col>
                <el-col :span="12">
                  <span v-if="newVisitor.uvCount > 0">{{ $n((newVisitor.bounceCount * 100) / newVisitor.uvCount, 'decimal') }}%</span>
                  <span v-else>-</span>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visit.averageDuration') }}</el-col>
                <el-col :span="12">
                  <span v-if="newVisitor.uvCount > 0">{{ dayjs.duration(newVisitor.duration / newVisitor.uvCount, 'seconds').format('HH:mm:ss') }}</span>
                  <span v-else>-</span>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visit.averagePv') }}</el-col>
                <el-col :span="12">
                  <span v-if="newVisitor.uvCount > 0">{{ $n(newVisitor.pvCount / newVisitor.uvCount, 'decimal') }}</span>
                  <span v-else>-</span>
                </el-col>
              </el-row>
            </div>
          </div>
          <div class="h-64 w-60 text-gray-secondary">
            <div class="mt-2 text-center">{{ $t('visitVisitor.oldVisitor') }}</div>
            <div class="flex items-center justify-center mt-2">
              <el-icon class="text-5xl"><UserFilled /></el-icon>
              <span class="text-3xl">
                <span v-if="oldVisitor.uvCount > 0">{{ $n(oldVisitor.proportion ?? 0, 'decimal') }}</span>
                <span v-else>-</span>%
              </span>
            </div>
            <div class="mt-4 space-y-2 text-sm">
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visitVisitor.pv') }}</el-col>
                <el-col :span="12">{{ oldVisitor.pvCount }}</el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visitVisitor.uv') }}</el-col>
                <el-col :span="12">{{ oldVisitor.uvCount }}</el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visit.bounceRate') }}</el-col>
                <el-col :span="12">
                  <span v-if="oldVisitor.uvCount > 0">{{ $n((oldVisitor.bounceCount * 100) / oldVisitor.uvCount, 'decimal') }}%</span>
                  <span v-else>-</span>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visit.averageDuration') }}</el-col>
                <el-col :span="12">
                  <span v-if="oldVisitor.uvCount > 0">{{ dayjs.duration(oldVisitor.duration / oldVisitor.uvCount, 'seconds').format('HH:mm:ss') }}</span>
                  <span v-else>-</span>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12" class="text-right">{{ $t('visit.averagePv') }}</el-col>
                <el-col :span="12">
                  <span v-if="oldVisitor.uvCount > 0">{{ $n(oldVisitor.pvCount / oldVisitor.uvCount, 'decimal') }}</span>
                  <span v-else>-</span>
                </el-col>
              </el-row>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="p-3 mt-3 app-block">
          <div ref="sourceTypeChart" class="w-full h-64"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>
