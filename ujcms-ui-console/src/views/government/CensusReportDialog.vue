<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { queryCensus, queryCensusItems } from '@/api/census';

defineOptions({
  name: 'CensusReportDialog',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
defineEmits({ 'update:modelValue': null });

const bean = ref<any>({});
const items = ref<any[]>([]);
const loading = ref<boolean>(false);

watch(
  () => props.modelValue,
  async () => {
    if (props.modelValue && props.beanId != null) {
      loading.value = true;
      try {
        [bean.value, items.value] = await Promise.all([queryCensus(props.beanId), queryCensusItems(props.beanId)]);
      } finally {
        loading.value = false;
      }
    }
  },
);

const vetoItems = computed(() => items.value.filter((item) => item.type === 1));
const deductItems = computed(() => items.value.filter((item) => item.type === 2));
const bonusItems = computed(() => items.value.filter((item) => item.type === 3));
const formatDate = (value?: string): string => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '');
</script>

<template>
  <el-dialog
    :title="`${$t('census.op.report')} - ${bean.name ?? ''}`"
    :model-value="modelValue"
    width="1080px"
    top="3vh"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <div v-loading="loading">
      <el-alert
        v-if="bean.conclusion != null || bean.veto"
        :type="bean.conclusion === 1 ? 'success' : 'error'"
        :title="`${$t('census.conclusion')}: ${bean.conclusion != null ? $t(`census.conclusion.${bean.conclusion}`) : $t('census.conclusion.none')}${bean.veto ? ' - ' + $t('census.score.veto') : ''}`"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-alert v-if="bean.status === 2 && bean.errorInfo" type="error" :closable="false" class="mb-3">
        <pre class="max-h-40 overflow-auto text-xs">{{ bean.errorInfo }}</pre>
      </el-alert>
      <el-alert v-if="bean.remark" type="warning" :title="bean.remark" :closable="false" class="mb-3" />

      <el-descriptions :column="4" border size="small" class="mb-3">
        <el-descriptions-item :label="$t('census.status')">{{ bean.status != null ? $t(`census.status.${bean.status}`) : '' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.user')">{{ bean.user?.username }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.beginDate')">{{ formatDate(bean.beginDate) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.endDate')">{{ formatDate(bean.endDate) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.homeOk')">
          <el-tag :type="bean.homeOk ? 'success' : 'danger'" size="small" disable-transitions>{{ $t(bean.homeOk ? 'yes' : 'no') }}</el-tag>
          <span v-if="bean.homeStatus != null" class="ml-1">HTTP {{ bean.homeStatus }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('census.latestPublishDate')">{{ formatDate(bean.latestPublishDate) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.deductScore')">{{ bean.deductScore }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.bonusScore')">
          {{ bean.bonusScore }}<span v-if="bean.finalScore != null" class="ml-2 font-bold">{{ $t('census.finalScore') }}: {{ bean.finalScore }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions :column="4" border size="small" class="mb-3">
        <el-descriptions-item :label="$t('census.channelCount')">{{ bean.channelCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.blankChannelCount')">{{ bean.blankChannelCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.staleChannelCount')">{{ bean.staleChannelCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.articleCount')">{{ bean.articleCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.errorWordCount')">{{ bean.errorWordCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.sensitiveWordCount')">{{ bean.sensitiveWordCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.linkCount')">{{ bean.linkCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('census.deadLinkCount')">{{ bean.deadLinkCount }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="items.length > 0">
        <h3 class="mb-1 font-bold">{{ $t('census.indicator.type.1') }}</h3>
        <el-table :data="vetoItems" border size="small" class="mb-3">
          <el-table-column property="code" :label="$t('census.indicator.code')" width="60"></el-table-column>
          <el-table-column property="name" :label="$t('census.indicator.name')" min-width="160" show-overflow-tooltip></el-table-column>
          <el-table-column :label="$t('census.indicator.triggered')" width="90">
            <template #default="{ row }">
              <el-tag :type="row.triggered ? 'danger' : 'success'" size="small" disable-transitions>{{ $t(row.triggered ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="remark" :label="$t('census.indicator.remark')" min-width="300" show-overflow-tooltip></el-table-column>
        </el-table>

        <h3 class="mb-1 font-bold">{{ $t('census.indicator.type.2') }}（100）</h3>
        <el-table :data="deductItems" border size="small" class="mb-3">
          <el-table-column property="code" :label="$t('census.indicator.code')" width="60"></el-table-column>
          <el-table-column :label="$t('census.indicator.name')" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ $t(`census.indicator.category.${row.category}`) }} - {{ row.name }}</template>
          </el-table-column>
          <el-table-column property="maxScore" :label="$t('census.indicator.maxScore')" width="60"></el-table-column>
          <el-table-column property="score" :label="$t('census.indicator.score')" width="80">
            <template #default="{ row }">
              <span :class="Number(row.score) > 0 ? 'font-bold text-red-500' : ''">{{ row.score }}</span>
            </template>
          </el-table-column>
          <el-table-column property="remark" :label="$t('census.indicator.remark')" min-width="260" show-overflow-tooltip></el-table-column>
        </el-table>

        <h3 class="mb-1 font-bold">{{ $t('census.indicator.type.3') }}（30）</h3>
        <el-table :data="bonusItems" border size="small">
          <el-table-column property="code" :label="$t('census.indicator.code')" width="60"></el-table-column>
          <el-table-column :label="$t('census.indicator.name')" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ $t(`census.indicator.category.${row.category}`) }} - {{ row.name }}</template>
          </el-table-column>
          <el-table-column property="maxScore" :label="$t('census.indicator.maxScore')" width="60"></el-table-column>
          <el-table-column property="score" :label="$t('census.indicator.score')" width="80">
            <template #default="{ row }">
              <span :class="Number(row.score) > 0 ? 'font-bold text-green-600' : ''">{{ row.score }}</span>
            </template>
          </el-table-column>
          <el-table-column property="remark" :label="$t('census.indicator.remark')" min-width="260" show-overflow-tooltip></el-table-column>
        </el-table>
      </template>
    </div>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
    </template>
  </el-dialog>
</template>
