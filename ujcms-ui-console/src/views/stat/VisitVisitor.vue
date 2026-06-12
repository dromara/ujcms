<script setup lang="ts">
import { onMounted, ref } from 'vue';
import dayjs from 'dayjs';
import duration from 'dayjs/plugin/duration';
import { UserFilled } from '@element-plus/icons-vue';
import { queryVisitorStat } from '@/api/stat';

dayjs.extend(duration);

const dateRange = ref<string>('last30day');
const getBeginByDateRange = (range: string): string | undefined => {
  switch (range) {
    case 'today':
      return dayjs().format('YYYY-MM-DD');
    case 'yesterday':
      return dayjs().subtract(1, 'day').format('YYYY-MM-DD');
    case 'last7day':
      return dayjs().subtract(6, 'day').format('YYYY-MM-DD');
    case 'last30day':
      return dayjs().subtract(29, 'day').format('YYYY-MM-DD');
    case 'last3month':
      return dayjs().subtract(3, 'month').format('YYYY-MM-DD');
    case 'last6month':
      return dayjs().subtract(6, 'month').format('YYYY-MM-DD');
    case 'lastYear':
      return dayjs().subtract(1, 'year').format('YYYY-MM-DD');
    default:
      return undefined;
  }
};

const getEndByDateRange = (range: string): string => {
  if (range === 'yesterday') {
    return dayjs().subtract(1, 'day').format('YYYY-MM-DD');
  }
  return dayjs().format('YYYY-MM-DD');
};

const newVisitor = ref<any>({});
const oldVisitor = ref<any>({});
const fetchVisitorStat = async (range: string) => {
  const visitorStat = await queryVisitorStat({ begin: getBeginByDateRange(range), end: getEndByDateRange(range) });
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

onMounted(async () => {
  fetchVisitorStat(dateRange.value);
});
</script>

<template>
  <div>
    <div class="p-3 mt-3 app-block">
      <div>
        <el-radio-group v-model="dateRange" @change="(value: any) => fetchVisitorStat(value)">
          <el-radio-button v-for="item in ['today', 'yesterday', 'last7day', 'last30day', 'last3month', 'last6month', 'lastYear', 'all']" :key="item" :value="item">
            {{ $t(`visit.${item}`) }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="flex p-3 mt-3 justify-evenly app-block">
        <div class="h-64 w-60 text-primary">
          <div class="mt-2 text-center">{{ $t('visitVisitor.newVisitor') }}</div>
          <div class="flex items-center justify-center mt-2">
            <el-icon class="text-5xl"><UserFilled /></el-icon>
            <span class="text-3xl">
              <span v-if="newVisitor.uvCount > 0">{{ $n(newVisitor.proportion ?? 0, 'decimal') }}</span>
              <span v-else>0</span>%
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
              <span v-else>0</span>%
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
                <span v-if="newVisitor.uvCount > 0">{{ dayjs.duration(oldVisitor.duration / oldVisitor.uvCount, 'seconds').format('HH:mm:ss') }}</span>
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
    </div>
  </div>
</template>
