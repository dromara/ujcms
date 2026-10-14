<script setup lang="ts">
import { computed, nextTick, ref, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { queryWechatFanList, queryWechatTagList, queryWechatTagUserList, sendWechatTemplate } from '@/api/wechat';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'WechatTemplateSendForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  accountId: { type: String, required: true },
  // 模板行数据 { templateId, title, content }
  template: { type: Object as PropType<any>, default: null },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const form = ref<any>();
const values = ref<any>({ data: {}, openids: [] });
// 已选粉丝的备注。微信不再提供昵称和头像，OpenID 无法辨认，有备注时随接收人标签一并显示
const fanRemarks = ref<Record<string, string>>({});
const buttonLoading = ref<boolean>(false);

// 模板内容中的占位符，形如 {{key.DATA}}
const PLACEHOLDER = /\{\{\s*([\w.]+?)\.DATA\s*\}\}/g;
// 模板内容片段：占位符片段带 slot，预览时高亮显示
interface Segment {
  text: string;
  slot?: string;
}
// 模板字段：name 为占位符名称（由微信或模板创建者指定，无法翻译），label 为占位符前同一行以冒号结尾的文字，没有时即为名称
interface Slot {
  name: string;
  label: string;
}

// 占位符前同一行的文字以冒号结尾时作为字段名称，如“预约时间：{{time4.DATA}}”得到“预约时间”
const labelOf = (before: string, name: string): string => {
  const line = before.substring(before.lastIndexOf('\n') + 1).trim();
  const match = /^(.{1,30}?)\s*[：:]$/.exec(line);
  return match?.[1]?.trim() ?? name;
};

const parsed = computed<{ segments: Segment[]; slots: Slot[] }>(() => {
  const content: string = props.template?.content ?? '';
  const segments: Segment[] = [];
  const slots: Slot[] = [];
  const regex = new RegExp(PLACEHOLDER.source, 'g');
  let last = 0;
  let match = regex.exec(content);
  while (match != null) {
    const before = content.substring(last, match.index);
    if (before) {
      segments.push({ text: before });
    }
    const name = match[1] ?? '';
    segments.push({ text: match[0], slot: name });
    if (!slots.some((slot) => slot.name === name)) {
      slots.push({ name, label: labelOf(before, name) });
    }
    last = match.index + match[0].length;
    match = regex.exec(content);
  }
  if (last < content.length) {
    segments.push({ text: content.substring(last) });
  }
  return { segments, slots };
});
const segments = computed<Segment[]>(() => parsed.value.segments);
const slots = computed<Slot[]>(() => parsed.value.slots);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      const data: Record<string, any> = {};
      slots.value.forEach(({ name }) => {
        data[name] = { value: '' };
      });
      values.value = { data, openids: [], url: undefined, appid: undefined, pagePath: undefined };
      fanRemarks.value = {};
      pickerTagId.value = '';
      nextTick(() => form.value?.clearValidate());
    }
  },
);
// 小程序页面路径依附于小程序AppID，AppID 清空后路径随之清空
watch(
  () => values.value.appid,
  (appid) => {
    if (!appid) {
      values.value.pagePath = undefined;
    }
  },
);

// 粉丝选择。微信只提供 OpenID，粉丝靠运营者自己设置的备注辨认、靠标签分组，因此可按标签筛选
const pickerVisible = ref<boolean>(false);
const pickerLoading = ref<boolean>(false);
const pickerPage = ref<number>(1);
const pickerTotal = ref<number>(0);
const pickerData = ref<any[]>([]);
const pickerSelection = ref<any[]>([]);
// 标签筛选，空串为全部粉丝
const pickerTagId = ref<string>('');
const tagList = ref<any[]>([]);
// 已加载标签的公众号ID，标签列表每个公众号只向微信取一次
const tagListAccountId = ref<string>();

const fetchTags = async () => {
  if (tagListAccountId.value === props.accountId) {
    return;
  }
  tagList.value = await queryWechatTagList(props.accountId);
  tagListAccountId.value = props.accountId;
};
const fetchFans = async () => {
  pickerLoading.value = true;
  try {
    if (pickerTagId.value) {
      // 标签粉丝接口不返回总数，以标签列表的粉丝数为准
      const result = await queryWechatTagUserList({ accountId: props.accountId, tagId: pickerTagId.value, page: pickerPage.value });
      pickerData.value = result.items ?? [];
      const tag = tagList.value.find((item) => String(item.id) === pickerTagId.value);
      pickerTotal.value = Number(tag?.count ?? pickerData.value.length);
    } else {
      const result = await queryWechatFanList({ accountId: props.accountId, type: 'all', page: pickerPage.value });
      pickerData.value = result.items ?? [];
      pickerTotal.value = Number(result.total ?? 0);
    }
  } finally {
    pickerLoading.value = false;
  }
};
const handlePickFans = () => {
  pickerPage.value = 1;
  pickerVisible.value = true;
  fetchTags();
  fetchFans();
};
const handlePickerFilter = () => {
  pickerPage.value = 1;
  fetchFans();
};
const handlePickerConfirm = () => {
  const openids: string[] = values.value.openids;
  pickerSelection.value.forEach((row) => {
    if (!openids.includes(row.openid) && openids.length < 100) {
      openids.push(row.openid);
    }
    if (row.remark) {
      fanRemarks.value[row.openid] = row.remark;
    }
  });
  pickerVisible.value = false;
};
const handleRemoveOpenid = (openid: string) => {
  values.value.openids = values.value.openids.filter((item: string) => item !== openid);
};

const handleSubmit = async () => {
  await form.value?.validate();
  buttonLoading.value = true;
  try {
    const { url, appid, pagePath, openids, data } = values.value;
    const result = await sendWechatTemplate({
      accountId: props.accountId,
      templateId: props.template.templateId,
      title: props.template.title,
      openids,
      url: url || undefined,
      miniProgram: appid ? { appid, pagePath } : undefined,
      // 微信已废弃字体颜色（color）参数，只发送内容
      data: slots.value.map(({ name }) => ({ name, value: data[name]?.value ?? '' })),
    });
    emit('update:modelValue', false);
    emit('finished');
    ElMessage.success(t('wechatTemplate.sendResult', { total: result.total, success: result.success, failed: result.failed }));
  } finally {
    buttonLoading.value = false;
  }
};
</script>

<template>
  <el-dialog :model-value="modelValue" :title="$t('wechatTemplate.op.send')" :width="768" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <el-form ref="form" :model="values" label-position="top">
      <!-- 模板预览：操作者需要看到每个字段在消息中的位置，占位符以主色高亮 -->
      <div class="mb-4 rounded border border-gray-200 bg-gray-50 px-3 py-2">
        <div class="font-bold text-gray-primary">{{ template?.title }}</div>
        <div class="mt-1 whitespace-pre-wrap text-gray-regular">
          <template v-for="(segment, index) in segments" :key="index">
            <span v-if="segment.slot" class="text-primary">{{ segment.text }}</span>
            <template v-else>{{ segment.text }}</template>
          </template>
        </div>
      </div>
      <el-form-item prop="openids" :rules="{ required: true, type: 'array', min: 1, message: () => $t('v.required') }">
        <template #label><label-tip message="wechatTemplate.openids" help /></template>
        <div class="w-full">
          <el-button @click="() => handlePickFans()">{{ $t('wechatTemplate.op.pickFans') }}</el-button>
          <span class="ml-2 text-xs text-gray-secondary">{{ values.openids.length }} / 100</span>
          <div v-if="values.openids.length > 0" class="mt-1">
            <el-tag v-for="openid in values.openids" :key="openid" closable size="small" class="mr-1 mb-1" @close="() => handleRemoveOpenid(openid)">
              {{ fanRemarks[openid] ? `${fanRemarks[openid]} (${openid})` : openid }}
            </el-tag>
          </div>
        </div>
      </el-form-item>
      <el-form-item v-for="slot in slots" :key="slot.name" :prop="`data.${slot.name}.value`" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label>
          {{ slot.label }}
          <span v-if="slot.label !== slot.name" class="ml-1 text-xs text-gray-secondary">{{ slot.name }}</span>
        </template>
        <el-input v-model="values.data[slot.name].value"></el-input>
      </el-form-item>
      <el-form-item prop="url">
        <template #label><label-tip message="wechatTemplate.url" help /></template>
        <el-input v-model="values.url" maxlength="300"></el-input>
      </el-form-item>
      <div class="grid grid-cols-2 gap-x-3">
        <el-form-item prop="appid">
          <template #label><label-tip message="wechatTemplate.appid" help /></template>
          <el-input v-model="values.appid" maxlength="64"></el-input>
        </el-form-item>
        <el-form-item prop="pagePath">
          <template #label><label-tip message="wechatTemplate.pagePath" help /></template>
          <el-input v-model="values.pagePath" maxlength="300" :disabled="!values.appid"></el-input>
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="buttonLoading" @click="() => handleSubmit()">{{ $t('wechatTemplate.op.send') }}</el-button>
    </template>
    <el-dialog v-model="pickerVisible" :title="$t('wechatTemplate.op.pickFans')" :width="640" append-to-body>
      <div class="mb-2 flex items-center gap-3">
        <el-select v-model="pickerTagId" class="w-48 flex-none" @change="() => handlePickerFilter()">
          <el-option :label="$t('wechatFan.type.all')" value=""></el-option>
          <el-option v-for="tag in tagList" :key="tag.id" :label="`${tag.name} (${tag.count})`" :value="String(tag.id)"></el-option>
        </el-select>
        <span class="flex-1 text-xs text-gray-secondary">{{ $t('wechatTemplate.pickFans.tip') }}</span>
      </div>
      <!-- 勾选结果在翻页、切换标签后保留（reserve-selection） -->
      <el-table v-loading="pickerLoading" :row-key="(row: any) => row.openid" :data="pickerData" max-height="360" @selection-change="(rows: any[]) => (pickerSelection = rows)">
        <el-table-column type="selection" width="38" reserve-selection></el-table-column>
        <el-table-column property="remark" :label="$t('wechatFan.remark')" width="160" show-overflow-tooltip></el-table-column>
        <el-table-column property="openid" label="OpenID" :min-width="220" show-overflow-tooltip></el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pickerPage"
        :total="pickerTotal"
        :page-size="20"
        layout="total, prev, pager, next"
        size="small"
        background
        class="justify-end px-3 py-2"
        @current-change="() => fetchFans()"
      ></el-pagination>
      <template #footer>
        <el-button @click="pickerVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :disabled="pickerSelection.length <= 0" @click="() => handlePickerConfirm()">{{ $t('ok') }}</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>
