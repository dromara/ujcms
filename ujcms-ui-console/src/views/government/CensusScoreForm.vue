<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { queryCensus, queryCensusItems, queryCensusIndicators, updateCensusScore } from '@/api/census';

defineOptions({
  name: 'CensusScoreForm',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { t } = useI18n();
const bean = ref<any>({});
const items = ref<any[]>([]);
const criteriaMap = ref<Record<string, string>>({});
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);

watch(
  () => props.modelValue,
  async () => {
    if (props.modelValue && props.beanId != null) {
      loading.value = true;
      try {
        const [census, list, indicators] = await Promise.all([queryCensus(props.beanId), queryCensusItems(props.beanId), queryCensusIndicators()]);
        bean.value = census;
        items.value = list.map((item: any) => ({ ...item, score: Number(item.score), maxScore: Number(item.maxScore) }));
        criteriaMap.value = Object.fromEntries(indicators.map((it: any) => [it.code, it.criteria]));
      } finally {
        loading.value = false;
      }
    }
  },
);

const vetoItems = computed(() => items.value.filter((item) => item.type === 1));
const deductCategories = computed(() =>
  [1, 2, 3, 4]
    .map((category) => ({ category, items: items.value.filter((item) => item.type === 2 && item.category === category) }))
    .filter((group) => group.items.length > 0),
);
const bonusItems = computed(() => items.value.filter((item) => item.type === 3));

const categoryMax = (group: any): number => group.items.reduce((sum: number, item: any) => sum + item.maxScore, 0);
const categorySubtotal = (group: any): number => Math.min(group.items.reduce((sum: number, item: any) => sum + (item.score || 0), 0), categoryMax(group));

// 评分规则：任一单项否决触发即不合格；扣分得分=100-各类扣分（每类不超过类总分）；低于60分不合格；高于80分方可计入加分
const vetoTriggered = computed(() => vetoItems.value.some((item) => item.triggered));
const deductScore = computed(() => Math.max(0, Math.round((100 - deductCategories.value.reduce((sum, group) => sum + categorySubtotal(group), 0)) * 10) / 10));
const bonusTotal = computed(() => Math.round(bonusItems.value.reduce((sum, item) => sum + (item.score || 0), 0) * 10) / 10);
const bonusCounted = computed(() => deductScore.value > 80);
const finalScore = computed(() => Math.round((deductScore.value + (bonusCounted.value ? bonusTotal.value : 0)) * 10) / 10);
const pass = computed(() => !vetoTriggered.value && deductScore.value >= 60);

const handleSubmit = async () => {
  buttonLoading.value = true;
  try {
    await updateCensusScore({
      id: bean.value.id,
      items: items.value.map((item) => ({ code: item.code, score: item.score, triggered: item.triggered, remark: item.remark })),
    });
    ElMessage.success(t('success'));
    emit('update:modelValue', false);
    emit('finished');
  } finally {
    buttonLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    :title="`${$t('census.op.score')} - ${bean.name ?? ''}`"
    :model-value="modelValue"
    :close-on-click-modal="false"
    width="1080px"
    top="3vh"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <div v-loading="loading">
      <el-alert type="info" :closable="false" class="mb-3">{{ $t('census.score.tip') }}</el-alert>

      <h3 class="mb-1 mt-3 font-bold">{{ $t('census.indicator.type.1') }}</h3>
      <el-table :data="vetoItems" border size="small">
        <el-table-column property="code" :label="$t('census.indicator.code')" width="60"></el-table-column>
        <el-table-column property="name" :label="$t('census.indicator.name')" min-width="140" show-overflow-tooltip></el-table-column>
        <el-table-column :label="$t('census.indicator.criteria')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ criteriaMap[row.code] }}</template>
        </el-table-column>
        <el-table-column :label="$t('census.indicator.triggered')" width="90">
          <template #default="{ row }"><el-switch v-model="row.triggered" size="small" /></template>
        </el-table-column>
        <el-table-column :label="$t('census.indicator.remark')" min-width="240">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" type="textarea" :rows="1" autosize maxlength="500"></el-input></template>
        </el-table-column>
      </el-table>

      <template v-for="group in deductCategories" :key="group.category">
        <h3 class="mb-1 mt-3 font-bold">
          {{ $t('census.indicator.type.2') }} - {{ $t(`census.indicator.category.${group.category}`) }}（{{ categoryMax(group) }}）
          <span class="ml-2 text-sm font-normal text-gray-500">{{ $t('census.score.categorySubtotal') }}: {{ categorySubtotal(group) }}</span>
        </h3>
        <el-table :data="group.items" border size="small">
          <el-table-column property="code" :label="$t('census.indicator.code')" width="60"></el-table-column>
          <el-table-column property="name" :label="$t('census.indicator.name')" min-width="110" show-overflow-tooltip></el-table-column>
          <el-table-column :label="$t('census.indicator.criteria')" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ criteriaMap[row.code] }}</template>
          </el-table-column>
          <el-table-column property="maxScore" :label="$t('census.indicator.maxScore')" width="60"></el-table-column>
          <el-table-column :label="$t('census.indicator.score')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.score" :min="0" :max="row.maxScore" :step="0.5" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('census.indicator.remark')" min-width="200">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" type="textarea" :rows="1" autosize maxlength="500"></el-input></template>
          </el-table-column>
        </el-table>
      </template>

      <h3 class="mb-1 mt-3 font-bold">{{ $t('census.indicator.type.3') }}（30）</h3>
      <el-table :data="bonusItems" border size="small">
        <el-table-column property="code" :label="$t('census.indicator.code')" width="60"></el-table-column>
        <el-table-column :label="$t('census.indicator.name')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ $t(`census.indicator.category.${row.category}`) }} - {{ row.name }}</template>
        </el-table-column>
        <el-table-column :label="$t('census.indicator.criteria')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ criteriaMap[row.code] }}</template>
        </el-table-column>
        <el-table-column property="maxScore" :label="$t('census.indicator.maxScore')" width="60"></el-table-column>
        <el-table-column :label="$t('census.indicator.score')" width="130">
          <template #default="{ row }"><el-input-number v-model="row.score" :min="0" :max="row.maxScore" :step="0.5" size="small" /></template>
        </el-table-column>
        <el-table-column :label="$t('census.indicator.remark')" min-width="200">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" type="textarea" :rows="1" autosize maxlength="500"></el-input></template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <div class="flex items-center justify-between">
        <div class="space-x-3 text-left">
          <el-tag v-if="vetoTriggered" type="danger" disable-transitions>{{ $t('census.score.veto') }}</el-tag>
          <span>{{ $t('census.score.deduct') }}: <strong>{{ deductScore }}</strong></span>
          <span>
            {{ $t('census.score.bonus') }}: <strong>{{ bonusTotal }}</strong>
            <el-tooltip v-if="!bonusCounted" :content="$t('census.score.bonusNotCounted')" placement="top">
              <span class="text-orange-500">*</span>
            </el-tooltip>
          </span>
          <span>{{ $t('census.score.final') }}: <strong>{{ finalScore }}</strong></span>
          <el-tag :type="pass ? 'success' : 'danger'" disable-transitions>{{ $t(pass ? 'census.conclusion.1' : 'census.conclusion.2') }}</el-tag>
        </div>
        <div>
          <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
          <el-button type="primary" :disabled="perm('census:score')" :loading="buttonLoading" @click="() => handleSubmit()">{{ $t('submit') }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>
