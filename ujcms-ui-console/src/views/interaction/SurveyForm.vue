<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { Plus, Minus, Grid, Folder, FolderOpened } from '@element-plus/icons-vue';
import draggable from 'vuedraggable';
import dayjs from 'dayjs';
import { useI18n } from 'vue-i18n';
import { pageSizes, pageLayout } from '@/utils/common';
import { querySurvey, createSurvey, updateSurvey, deleteSurvey, querySurveyOptionFeedbackPage, querySurveyItemFeedbackPage, updateSurveyItemFeedback } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'SurveyForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const focus = ref<any>();
const values = ref<any>({});

let itemsIndex = -1;
const addNewItem = (index: number) => {
  itemsIndex -= 1;
  values.value.items.splice(index, 0, { id: itemsIndex, display: true, multiple: false, essay: false, options: [{ id: -1, count: 0 }] });
};
let optionsIndex = -1;
const addNewOption = (item: any, index: number) => {
  optionsIndex -= 1;
  item.options.splice(index, 0, { id: optionsIndex, count: 0 });
};

const optionFeedbackVisible = ref<boolean>(false);
const optionFeedbackLoading = ref<boolean>(false);
const optionFeedbackPageSize = ref<number>(10);
const optionFeedbackCurrentPage = ref<number>(1);
const optionFeedbackTotal = ref<number>(0);
const optionFeedbackData = ref<any>([]);
const surveyOptionId = ref<string>('0');

const fetchOptionFeedbackData = async () => {
  optionFeedbackLoading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await querySurveyOptionFeedbackPage({
      optionId: surveyOptionId.value,
      page: optionFeedbackCurrentPage.value,
      pageSize: optionFeedbackPageSize.value,
    });
    optionFeedbackTotal.value = totalElements;
    optionFeedbackData.value = content;
  } finally {
    optionFeedbackLoading.value = false;
  }
};
const openOptionFeedback = async (optionId: string) => {
  surveyOptionId.value = optionId;
  optionFeedbackCurrentPage.value = 1;
  fetchOptionFeedbackData();
  optionFeedbackVisible.value = true;
};

const itemFeedbackVisible = ref<boolean>(false);
const itemFeedbackLoading = ref<boolean>(false);
const itemFeedbackPageSize = ref<number>(10);
const itemFeedbackCurrentPage = ref<number>(1);
const itemFeedbackTotal = ref<number>(0);
const itemFeedbackData = ref<any>([]);
const surveyItemId = ref<string>('0');

const fetchItemFeedbackData = async () => {
  itemFeedbackLoading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await querySurveyItemFeedbackPage({
      itemId: surveyItemId.value,
      page: itemFeedbackCurrentPage.value,
      pageSize: itemFeedbackPageSize.value,
    });
    itemFeedbackTotal.value = totalElements;
    itemFeedbackData.value = content;
  } finally {
    itemFeedbackLoading.value = false;
  }
};
const openItemFeedback = async (itemId: string) => {
  surveyItemId.value = itemId;
  itemFeedbackCurrentPage.value = 1;
  fetchItemFeedbackData();
  itemFeedbackVisible.value = true;
};
const updateAnswer = async (itemFeedback: any) => {
  itemFeedbackLoading.value = true;
  try {
    await updateSurveyItemFeedback(itemFeedback);
    ElMessage.success(t('success'));
  } finally {
    itemFeedbackLoading.value = false;
  }
};

const essayChange = (checked: boolean, item: any) => {
  if (!checked && item.options.length <= 0) {
    item.options[0] = { id: -1, count: 0 };
  }
};

const beforeValidate = (values: any) => {
  values.items?.forEach((item: any) => {
    item.display = true;
    if (item.essay) {
      item.options = [];
    }
  });
};
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.interaction.survey')"
    :query-bean="querySurvey"
    :create-bean="createSurvey"
    :update-bean="updateSurvey"
    :delete-bean="deleteSurvey"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="
      () => ({ maxChoice: 0, mode: 1, interval: 0, total: 0, enabled: true, items: [{ id: -1, display: true, multiple: false, essay: false, options: [{ id: -1, count: 0 }] }] })
    "
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    :before-validate="beforeValidate"
    perms="survey"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-row>
        <el-col :span="24">
          <el-form-item prop="title" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="survey.title" /></template>
            <el-input ref="focus" v-model="values.title" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="description">
            <template #label><label-tip message="survey.description" /></template>
            <el-input v-model="values.description" type="textarea" :rows="3" maxlength="1000"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="startDate">
            <template #label><label-tip message="survey.startDate" help /></template>
            <el-date-picker v-model="values.startDate" type="datetime" class="w-full"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="endDate">
            <template #label><label-tip message="survey.endDate" help /></template>
            <el-date-picker v-model="values.endDate" type="datetime" class="w-full"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="mode" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="survey.mode" help /></template>
            <el-select v-model="values.mode" class="w-full"><el-option v-for="n in [1, 2, 3]" :key="n" :value="n" :label="$t(`vote.mode.${n}`)" /> </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="interval" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="survey.interval" help /></template>
            <el-input-number v-model="values.interval" :min="0" :max="32767" class="w-48" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="created">
            <template #label><label-tip message="survey.created" /></template>
            <el-date-picker v-model="values.created" type="datetime" class="w-full" disabled></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="times">
            <template #label><label-tip message="survey.times" /></template>
            <el-input-number :model-value="values.times" disabled class="w-48" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="enabled">
            <template #label><label-tip message="survey.enabled" /></template>
            <el-switch v-model="values.enabled" />
          </el-form-item>
        </el-col>
      </el-row>
      <div class="text-base">{{ $t('survey.items') }}</div>
      <el-form-item label-width="0px">
        <draggable v-model="values.items" :animation="250" handle=".item-handle" item-key="id" :component-data="{ class: 'divide-y divide-dashed w-full' }">
          <template #item="{ element: item, index: itemIndex }">
            <div>
              <div class="flex items-center my-4">
                <el-icon class="text-2xl align-middle cursor-move item-handle"><Grid /></el-icon>
                <el-button class="ml-2" :icon="(item.display ?? true) ? FolderOpened : Folder" circle @click="item.display = !(item.display ?? true)"></el-button>
                <el-button :icon="Plus" circle @click="addNewItem(itemIndex + 1)"></el-button>
                <el-button :icon="Minus" circle :disabled="values.items.length <= 1" @click="() => (values.items = values.items.filter((it) => it.id !== item.id))"></el-button>
                <div class="ml-2">{{ itemIndex + 1 }}.</div>
                <el-form-item class="ml-2 grow" :prop="`items.${itemIndex}.title`" :rules="{ required: true, message: () => $t('v.required') }">
                  <el-input v-model="item.title" maxlength="1000"></el-input>
                </el-form-item>
                <el-checkbox v-model="item.multiple" class="ml-5" :label="$t('surveyItem.multiple')" />
                <el-checkbox v-model="item.essay" :label="$t('surveyItem.essay')" @change="(checked: any) => essayChange(checked, item)" />
                <el-button class="ml-3" type="primary" size="small" plain :disabled="!item.essay" @click="() => openItemFeedback(item.id)">
                  {{ $t('surveyItem.feedback') }}
                </el-button>
              </div>
              <draggable
                v-if="(item.display ?? true) && !item.essay"
                v-model="item.options"
                :animation="250"
                handle=".option-handle"
                item-key="id"
                :component-data="{ class: ['ml-20'] }"
              >
                <template #item="{ element: option, index: optionIndex }">
                  <div class="flex items-center my-4">
                    <el-icon class="text-2xl align-middle cursor-move option-handle"><Grid /></el-icon>
                    <el-button class="ml-2" :icon="Plus" circle @click="addNewOption(item, optionIndex + 1)"></el-button>
                    <el-button
                      :icon="Minus"
                      circle
                      :disabled="item.options.length <= 1"
                      @click="() => (item.options = item.options.filter((it) => it.id !== option.id))"
                    ></el-button>
                    <div class="ml-2">{{ String.fromCharCode(65 + optionIndex) }}.</div>
                    <el-form-item class="ml-2 grow" :prop="`items.${itemIndex}.options.${optionIndex}.title`" :rules="{ required: true, message: () => $t('v.required') }">
                      <el-input v-model="option.title" maxlength="300"></el-input>
                    </el-form-item>
                    <el-form-item class="ml-3" :prop="`items.${itemIndex}.options.${optionIndex}.count`" :rules="{ required: true, message: () => $t('v.required') }">
                      <el-input v-model="option.count" class="w-48">
                        <template #prepend>{{ $t('surveyOption.count') }}</template>
                      </el-input>
                    </el-form-item>
                    <el-button class="ml-3" type="primary" size="small" plain @click="() => openOptionFeedback(option.id)">{{ $t('surveyOption.detail') }}</el-button>
                  </div>
                </template>
              </draggable>
            </div>
          </template>
        </draggable>
      </el-form-item>
    </template>
  </dialog-form>
  <el-dialog v-model="optionFeedbackVisible" :title="$t('surveyOption.detail')" width="98%" top="16px">
    <template #default>
      <el-table v-loading="optionFeedbackLoading" :data="optionFeedbackData" class="w-full">
        <el-table-column prop="feedback.ip" :label="$t('surveyFeedback.ip')" />
        <el-table-column prop="feedback.cookie" :label="$t('surveyFeedback.cookie')" />
        <el-table-column prop="feedback.user.username" :label="$t('surveyFeedback.user')" />
        <el-table-column prop="feedback.created" :label="$t('surveyFeedback.created')" :formatter="(row) => dayjs(row.feedback.created).format('YYYY-MM-DD HH:mm:ss')" />
      </el-table>
      <el-pagination
        v-model:current-page="optionFeedbackCurrentPage"
        v-model:page-size="optionFeedbackPageSize"
        :total="optionFeedbackTotal"
        :page-sizes="pageSizes"
        :layout="pageLayout"
        class="justify-end px-3 py-2"
        size="small"
        background
        @size-change="() => fetchOptionFeedbackData()"
        @current-change="() => fetchOptionFeedbackData()"
      ></el-pagination>
    </template>
  </el-dialog>
  <el-dialog v-model="itemFeedbackVisible" :title="$t('surveyItem.feedback')" width="98%" top="16px">
    <template #default>
      <div v-loading="itemFeedbackLoading">
        <div class="space-y-2">
          <div class="flex space-x-4 font-bold text-secondary">
            <div class="w-full">{{ $t('surveyFeedback.ip') }}</div>
            <div class="w-full">{{ $t('surveyFeedback.cookie') }}</div>
            <div class="w-full">{{ $t('surveyFeedback.user') }}</div>
            <div class="w-full">{{ $t('surveyFeedback.created') }}</div>
          </div>
          <div v-for="item in itemFeedbackData" :key="item.id">
            <div class="flex pt-2 space-x-4 border-t">
              <el-input :model-value="item.feedback.ip" disabled />
              <el-input :model-value="item.feedback.cookie" disabled />
              <el-input :model-value="item.feedback.user?.username" disabled />
              <el-input :model-value="dayjs(item.feedback.created).format('YYYY-MM-DD HH:mm:ss')" disabled />
            </div>
            <div class="flex mt-2">
              <el-input v-model="item.answer" :autosize="{ minRows: 1, maxRows: 8 }" type="textarea" class="grow-1" required />
              <el-button type="primary" class="ml-2" @click="() => updateAnswer(item)">{{ $t('save') }}</el-button>
            </div>
          </div>
        </div>
        <el-pagination
          v-model:current-page="itemFeedbackCurrentPage"
          v-model:page-size="itemFeedbackPageSize"
          :total="itemFeedbackTotal"
          :page-sizes="pageSizes"
          :layout="pageLayout"
          class="justify-end px-3 py-2 mt-2"
          size="small"
          background
          @size-change="() => fetchItemFeedbackData()"
          @current-change="() => fetchItemFeedbackData()"
        ></el-pagination>
      </div>
    </template>
  </el-dialog>
</template>
