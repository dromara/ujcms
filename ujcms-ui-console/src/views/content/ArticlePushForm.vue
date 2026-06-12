<script setup lang="ts">
import { ref, watch, toRefs, PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { toTree, disableParentTree, disableTree } from '@/utils/tree';
import { queryArticle, queryChannelList, internalPushArticle, externalPushArticle } from '@/api/content';
import { querySiteList } from '@/api/system';

defineOptions({
  name: 'ArticleInternalPushForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  type: { type: String as PropType<'internal' | 'external'>, default: 'external' },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { beanId, type, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const form = ref<any>();
const bean = ref<any>({});
const values = ref<any>({});
const buttonLoading = ref<boolean>(false);

const siteList = ref<any[]>([]);
const channelData = ref<any[]>([]);
const channelTree = ref<any>();

const fetchArticle = async () => {
  if (beanId?.value != null) {
    bean.value = await queryArticle(beanId.value);
  }
};
const fetchChannelData = async (siteId?: string) => {
  const params = type.value === 'internal' ? { isArticlePermission: true } : { siteId };
  channelData.value = disableParentTree(toTree(await queryChannelList(params)));
  channelData.value = disableTree(channelData.value, [...bean.value.destList.map((item: any) => item.channel.id), bean.value.channel.id]);
};
const fetchSiteList = async () => {
  siteList.value = await querySiteList();
  siteList.value = toTree(siteList.value);
  siteList.value = disableTree(siteList.value, [bean.value.siteId]);
};

watch(visible, async () => {
  if (visible.value) {
    await fetchArticle();
    if (type.value === 'external') {
      fetchSiteList();
    } else {
      fetchChannelData();
    }
    values.value = { ids: [bean.value.id], type: 3 };
  }
});

const handleChannel = () => {
  if (channelTree.value != null) {
    values.value.channelIds = [...channelTree.value.getCheckedNodes(), ...channelTree.value.getHalfCheckedNodes()].map((item) => item.id);
    form.value.validate();
  }
};

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      if (type.value === 'internal') {
        await internalPushArticle(values.value);
      } else {
        await externalPushArticle(values.value);
      }
      emit('finished');
      emit('update:modelValue', false);
      ElMessage.success(t('success'));
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <el-drawer :title="$t(`article.op.${type}Push`)" :model-value="modelValue" :size="414" @update:model-value="(event) => $emit('update:modelValue', event)">
    <template #default>
      <el-form ref="form" :model="values" label-position="top">
        <el-form-item>
          <el-input :model-value="bean.title" disabled />
        </el-form-item>
        <el-form-item prop="type">
          <el-radio-group v-model="values.type">
            <el-radio-button v-for="n in [1, 3]" :key="n" :value="n">{{ t(`article.type.${n}`) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="type === 'external'" prop="siteId" :rules="[{ required: true, message: () => $t('v.required') }]">
          <el-tree-select
            v-model="values.siteId"
            :data="siteList"
            node-key="id"
            :props="{ label: 'name', disabled: 'disabled' }"
            :render-after-expand="false"
            check-strictly
            class="w-full"
            @change="(value: string) => fetchChannelData(value)"
          />
        </el-form-item>
        <el-form-item prop="channelIds" :rules="[{ required: true, message: () => $t('v.required') }]">
          <el-tree
            ref="channelTree"
            :data="channelData"
            node-key="id"
            :props="{ label: 'name' }"
            class="w-full border rounded"
            check-strictly
            default-expand-all
            show-checkbox
            check-on-click-node
            @check="() => handleChannel()"
          />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <div class="flex items-center justify-between">
        <div></div>
        <div>
          <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
          <el-button type="primary" :loading="buttonLoading" @click="() => handleSubmit()">{{ $t('save') }}</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>
