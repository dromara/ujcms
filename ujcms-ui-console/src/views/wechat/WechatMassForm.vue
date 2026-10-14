<script setup lang="ts">
import { nextTick, ref, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { previewWechatMass, queryWechatDraftList, queryWechatTagList, sendWechatMass } from '@/api/wechat';
import LabelTip from '@/components/LabelTip.vue';
import WechatItemsTag from './WechatItemsTag.vue';

defineOptions({
  name: 'WechatMassForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  accountList: { type: Array as PropType<any[]>, required: true },
  accountId: { type: String, default: null },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const form = ref<any>();
const values = ref<any>({});
const tagList = ref<any[]>([]);
const previewOpenid = ref<string>('');
// 选定的草稿（含各篇封面、标题、摘要和微信临时预览链接），只用于在窗口中展示
const pickedDraft = ref<any>(null);
const buttonLoading = ref<boolean>(false);
const previewLoading = ref<boolean>(false);

const fetchTagList = async () => {
  tagList.value = values.value.accountId ? await queryWechatTagList(values.value.accountId) : [];
};
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      values.value = {
        accountId: props.accountId ?? props.accountList[0]?.id,
        target: 0,
        tagId: undefined,
        msgType: 'mpnews',
        mediaId: undefined,
        title: undefined,
        content: undefined,
        ignoreReprint: false,
      };
      previewOpenid.value = '';
      pickedDraft.value = null;
      fetchTagList();
      nextTick(() => form.value?.clearValidate());
    }
  },
);
const handleAccountChange = () => {
  values.value.tagId = undefined;
  values.value.mediaId = undefined;
  values.value.title = undefined;
  pickedDraft.value = null;
  fetchTagList();
};

// 草稿选择
const pickerVisible = ref<boolean>(false);
const pickerLoading = ref<boolean>(false);
const pickerPage = ref<number>(1);
const pickerTotal = ref<number>(0);
const pickerData = ref<any[]>([]);
const pickerCurrent = ref<any>(null);

const fetchDrafts = async () => {
  pickerLoading.value = true;
  try {
    const result = await queryWechatDraftList({ accountId: values.value.accountId, page: pickerPage.value });
    pickerData.value = result.items ?? [];
    pickerTotal.value = Number(result.total ?? 0);
  } finally {
    pickerLoading.value = false;
  }
};
const handlePickDraft = () => {
  pickerPage.value = 1;
  pickerCurrent.value = null;
  pickerVisible.value = true;
  fetchDrafts();
};
const handlePickerConfirm = () => {
  values.value.mediaId = pickerCurrent.value.mediaId;
  values.value.title = pickerCurrent.value.articles?.[0]?.title;
  pickedDraft.value = pickerCurrent.value;
  pickerVisible.value = false;
};

const handlePreview = async () => {
  await form.value?.validate();
  previewLoading.value = true;
  try {
    const { accountId, msgType, mediaId, content } = values.value;
    await previewWechatMass({ accountId, msgType, mediaId, content, openid: previewOpenid.value });
    ElMessage.success(t('wechatMass.previewSuccess'));
  } finally {
    previewLoading.value = false;
  }
};

const handleSubmit = async () => {
  await form.value?.validate();
  await ElMessageBox.confirm(t('wechatMass.sendConfirm'), { type: 'warning' });
  buttonLoading.value = true;
  try {
    const { accountId, target, tagId, msgType, mediaId, title, content, ignoreReprint } = values.value;
    const result = await sendWechatMass({
      accountId,
      target,
      tagId: target === 1 ? tagId : undefined,
      tagName: target === 1 ? tagList.value.find((item) => item.id === tagId)?.name : undefined,
      msgType,
      mediaId,
      title,
      content,
      ignoreReprint,
    });
    emit('update:modelValue', false);
    emit('finished');
    if (result.status === 2) {
      ElMessage.warning(t('wechatMass.sendFailed', { error: result.error }));
    } else {
      ElMessage.success(t('wechatMass.sendSubmitted'));
    }
  } finally {
    buttonLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('wechatMass.op.send')"
    :width="768"
    @update:model-value="(event: boolean) => $emit('update:modelValue', event)"
  >
    <el-form ref="form" :model="values" label-position="top">
      <el-form-item prop="accountId" :label="$t('wechatMass.account')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-select v-model="values.accountId" class="w-full" @change="() => handleAccountChange()">
          <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="target" :label="$t('wechatMass.target')">
        <el-radio-group v-model="values.target">
          <el-radio v-for="n in [0, 1]" :key="n" :value="n">{{ $t(`wechatMass.target.${n}`) }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="values.target === 1" prop="tagId" :label="$t('wechatMass.tag')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-select v-model="values.tagId" class="w-full">
          <el-option v-for="item in tagList" :key="item.id" :label="`${item.name} (${item.count})`" :value="item.id"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="msgType" :label="$t('wechatMass.msgType')">
        <el-radio-group v-model="values.msgType">
          <el-radio value="mpnews">{{ $t('wechatMass.msgType.mpnews') }}</el-radio>
          <el-radio value="text">{{ $t('wechatMass.msgType.text') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="values.msgType === 'mpnews'">
        <el-form-item prop="mediaId" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="wechatMass.mediaId" help /></template>
          <div class="w-full">
            <el-button @click="() => handlePickDraft()">{{ $t('wechatMass.op.pickDraft') }}</el-button>
            <!-- 选定草稿的封面、标题、摘要：群发按草稿原样发送，这里只能查看不能修改；“网页预览”打开微信的临时链接 -->
            <div v-if="pickedDraft" class="mt-2 flex items-start gap-3 rounded border border-gray-200 p-2">
              <el-image v-if="pickedDraft.articles?.[0]?.thumbUrl" :src="pickedDraft.articles[0].thumbUrl" fit="cover" class="h-16 w-16 shrink-0" referrerpolicy="no-referrer" />
              <div class="min-w-0 flex-1 leading-normal">
                <div class="flex min-w-0 items-center">
                  <span class="min-w-0 truncate font-bold" :title="pickedDraft.articles?.[0]?.title">{{ pickedDraft.articles?.[0]?.title }}</span>
                  <wechat-items-tag :items="pickedDraft.articles ?? []" />
                </div>
                <div v-if="pickedDraft.articles?.[0]?.digest" class="mt-1 truncate text-xs text-gray-secondary" :title="pickedDraft.articles[0].digest">
                  {{ pickedDraft.articles[0].digest }}
                </div>
                <div class="mt-1 text-xs text-gray-secondary">
                  <a v-if="pickedDraft.articles?.[0]?.url" :href="pickedDraft.articles[0].url" target="_blank" class="text-primary mr-2" :title="$t('wechatDraft.preview.tooltip')">
                    {{ $t('wechatDraft.op.preview') }}
                  </a>
                  <span>{{ pickedDraft.mediaId }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item prop="ignoreReprint">
          <el-checkbox v-model="values.ignoreReprint">
            <label-tip message="wechatMass.ignoreReprint" help />
          </el-checkbox>
        </el-form-item>
      </template>
      <el-form-item
        v-else
        prop="content"
        :label="$t('wechatMass.content')"
        :rules="{ required: true, message: () => $t('v.required') }"
      >
        <el-input v-model="values.content" type="textarea" :rows="5" maxlength="600" show-word-limit></el-input>
      </el-form-item>
      <el-form-item>
        <template #label><label-tip message="wechatMass.preview" help /></template>
        <div class="flex w-full gap-2">
          <el-input v-model="previewOpenid" :placeholder="$t('wechatMass.previewOpenid')" class="flex-1"></el-input>
          <el-button :loading="previewLoading" :disabled="!previewOpenid || perm('wechatMass:preview')" @click="() => handlePreview()">
            {{ $t('wechatMass.op.preview') }}
          </el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="buttonLoading" @click="() => handleSubmit()">{{ $t('wechatMass.op.send') }}</el-button>
    </template>
    <el-dialog v-model="pickerVisible" :title="$t('wechatMass.op.pickDraft')" :width="640" append-to-body>
      <el-table
        v-loading="pickerLoading"
        :row-key="(row: any) => row.mediaId"
        :data="pickerData"
        max-height="360"
        highlight-current-row
        @current-change="(row: any) => (pickerCurrent = row)"
      >
        <el-table-column :label="$t('wechatMass.draftCover')" width="90">
          <template #default="{ row }">
            <el-image v-if="row.articles?.[0]?.thumbUrl" :src="row.articles[0].thumbUrl" fit="cover" class="w-14 h-14" referrerpolicy="no-referrer" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('wechatMass.draftTitle')" :min-width="220">
          <template #default="{ row }">
            <div class="flex min-w-0 items-center">
              <span class="min-w-0 truncate" :title="row.articles?.[0]?.title">{{ row.articles?.[0]?.title }}</span>
              <wechat-items-tag :items="row.articles ?? []" />
            </div>
          </template>
        </el-table-column>
        <el-table-column property="mediaId" label="MediaID" :min-width="180" show-overflow-tooltip></el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pickerPage"
        :total="pickerTotal"
        :page-size="20"
        layout="total, prev, pager, next"
        size="small"
        background
        class="justify-end px-3 py-2"
        @current-change="() => fetchDrafts()"
      ></el-pagination>
      <template #footer>
        <el-button @click="pickerVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :disabled="pickerCurrent == null" @click="() => handlePickerConfirm()">{{ $t('ok') }}</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>
