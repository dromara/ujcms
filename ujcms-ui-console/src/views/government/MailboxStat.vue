<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Search, Download } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { saveAs } from 'file-saver';
import { queryMailboxStat } from '@/api/interaction';

defineOptions({
  name: 'MailboxStat',
});
const { t } = useI18n();
const loading = ref<boolean>(false);
const data = ref<any[]>([]);
const handlingDays = ref<number>(7);
const extendedDays = ref<number>(15);
const range = ref<any>([]);

const fetchData = async () => {
  loading.value = true;
  try {
    const result = await queryMailboxStat({ begin: range.value?.[0], end: range.value?.[1] });
    data.value = result.rows;
    handlingDays.value = result.handlingDays;
    extendedDays.value = result.extendedDays;
  } finally {
    loading.value = false;
  }
};

onMounted(async () => {
  range.value[0] = dayjs().subtract(1, 'month').add(1, 'day').startOf('day').toDate();
  range.value[1] = dayjs().startOf('day').toDate();
  fetchData();
});

const rowName = (row: any): string => (row.total ? t('mailbox.stat.totalRow') : (row.name ?? t('mailbox.stat.unclassified')));
const percent = (rate: number, count: number): string => (count > 0 ? `${(rate * 100).toFixed(1)}%` : '-');

const csvColumns = [
  'mailboxType',
  'received',
  'pending',
  'accepted',
  'rejected',
  'replied',
  'onTimeReplied',
  'onTimeRate',
  'overdue',
  'extended',
  'rated',
  'satisfied',
  'basicallySatisfied',
  'unsatisfied',
  'satisfactionRate',
];
const handleExport = () => {
  const header = csvColumns.map((key) => t(`mailbox.stat.${key}`)).join(',');
  const lines = data.value.map((row) =>
    [
      rowName(row),
      row.received,
      row.pending,
      row.accepted,
      row.rejected,
      row.replied,
      row.onTimeReplied,
      percent(row.onTimeRate, row.replied),
      row.overdue,
      row.extended,
      row.rated,
      row.satisfied,
      row.basicallySatisfied,
      row.unsatisfied,
      percent(row.satisfactionRate, row.rated),
    ].join(','),
  );
  const csv = [header, ...lines].join('\r\n');
  const begin = range.value?.[0] ? dayjs(range.value[0]).format('YYYYMMDD') : 'all';
  const end = range.value?.[1] ? dayjs(range.value[1]).format('YYYYMMDD') : 'all';
  saveAs(new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' }), `mailbox-stat_${begin}-${end}.csv`);
};
</script>

<template>
  <div>
    <div class="p-3 mt-3 app-block">
      <form class="flex">
        <div class="space-y-1">
          <el-date-picker v-model="range" type="daterange" />
        </div>
        <div>
          <el-button-group class="ml-2">
            <el-button native-type="submit" :icon="Search" @click.prevent="() => fetchData()">{{ $t('search') }}</el-button>
            <el-button :icon="Download" :disabled="data.length <= 0" @click.prevent="() => handleExport()">{{ $t('mailbox.stat.export') }}</el-button>
          </el-button-group>
        </div>
      </form>
      <div class="mt-2 text-xs text-gray-secondary">{{ $t('mailbox.stat.caption', { handlingDays, extendedDays }) }}</div>
      <div class="mt-2">
        <el-table ref="table" v-loading="loading" :data="data" :row-class-name="({ row }) => (row.total ? 'font-bold' : '')">
          <el-table-column :label="$t('mailbox.stat.mailboxType')" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ rowName(row) }}</template>
          </el-table-column>
          <el-table-column property="received" :label="$t('mailbox.stat.received')" min-width="70"></el-table-column>
          <el-table-column property="pending" :label="$t('mailbox.stat.pending')" min-width="70"></el-table-column>
          <el-table-column property="accepted" :label="$t('mailbox.stat.accepted')" min-width="70"></el-table-column>
          <el-table-column property="rejected" :label="$t('mailbox.stat.rejected')" min-width="70"></el-table-column>
          <el-table-column property="replied" :label="$t('mailbox.stat.replied')" min-width="70"></el-table-column>
          <el-table-column property="onTimeReplied" :label="$t('mailbox.stat.onTimeReplied')" min-width="80"></el-table-column>
          <el-table-column :label="$t('mailbox.stat.onTimeRate')" min-width="90">
            <template #default="{ row }">{{ percent(row.onTimeRate, row.replied) }}</template>
          </el-table-column>
          <el-table-column :label="$t('mailbox.stat.overdue')" min-width="80">
            <template #default="{ row }">
              <span :class="row.overdue > 0 ? 'text-error' : ''">{{ row.overdue }}</span>
            </template>
          </el-table-column>
          <el-table-column property="extended" :label="$t('mailbox.stat.extended')" min-width="70"></el-table-column>
          <el-table-column property="rated" :label="$t('mailbox.stat.rated')" min-width="70"></el-table-column>
          <el-table-column property="satisfied" :label="$t('mailbox.stat.satisfied')" min-width="70"></el-table-column>
          <el-table-column property="basicallySatisfied" :label="$t('mailbox.stat.basicallySatisfied')" min-width="80"></el-table-column>
          <el-table-column property="unsatisfied" :label="$t('mailbox.stat.unsatisfied')" min-width="80"></el-table-column>
          <el-table-column :label="$t('mailbox.stat.satisfactionRate')" min-width="90">
            <template #default="{ row }">{{ percent(row.satisfactionRate, row.rated) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>
