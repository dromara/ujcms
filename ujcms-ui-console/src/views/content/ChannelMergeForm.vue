<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { toTree } from '@/utils/tree';
import { queryChannelList, batchMergeChannel } from '@/api/content';

defineOptions({
  name: 'ChannelMergeForm',
});
defineEmits({ 'update:modelValue': null, finished: null });
const visible = defineModel<boolean>();
const { t } = useI18n();

const treeLoading = ref<boolean>(false);
const srcTreeData = ref<any[]>([]);
const targetTreeData = ref<any[]>([]);
const srcTree = ref<any>();
const targetTree = ref<any>();
const error = ref<string>();

const fetchData = async () => {
  treeLoading.value = true;
  try {
    const srcChannels = await queryChannelList();
    const targetChannels: any[] = [];
    srcChannels.forEach((item: any) => {
      targetChannels.push({ ...item });
    });
    srcTreeData.value = toTree(srcChannels);
    targetTreeData.value = toTree(targetChannels);
  } finally {
    treeLoading.value = false;
  }
};

watch(visible, async () => {
  if (visible.value) {
    fetchData();
  }
});
const disableCheckedChannel = (channel: any, disabled: boolean, checkedIds: string[]) => {
  disabled = disabled || checkedIds.includes(channel.id);
  channel.disabled = disabled;

  channel.children?.forEach((item: any) => {
    disableCheckedChannel(item, disabled, checkedIds);
  });
};
const handleSrcTreeCheck = () => {
  error.value = undefined;
  const checkedIds = srcTree.value.getCheckedKeys();
  if (checkedIds.includes(targetTree.value.getCheckedKeys()[0])) {
    targetTree.value.setCheckedKeys([]);
  }
  targetTreeData.value.forEach((item: any) => {
    disableCheckedChannel(item, false, checkedIds);
  });
};
const handleSingleCheck = (obj: any, status: any) => {
  error.value = undefined;
  if (status.checkedKeys.length > 1) {
    targetTree.value.setCheckedKeys([obj.id]);
  }
};
const handleSubmit = async () => {
  const srcIds = srcTree.value.getCheckedKeys();
  if (srcIds.length <= 0) {
    error.value = t('channel.error.srcChannelRequired');
    return;
  }
  const targetId = targetTree.value.getCheckedKeys()[0];
  if (targetId == null) {
    error.value = t('channel.error.moveToChannelRequired');
    return;
  }
  await batchMergeChannel(srcIds, targetId);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <el-dialog v-model="visible" :title="$t('channel.op.batchMerge')" width="768" top="5vh" @close="$emit('finished')">
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="text-lg text-center">{{ $t('channel.batchMerge.srcChannel') }}</div>
        <el-tree ref="srcTree" v-loading="treeLoading" :data="srcTreeData" :props="{ label: 'name' }" node-key="id" show-checkbox @check="handleSrcTreeCheck" />
      </el-col>
      <el-col :span="12">
        <div class="flex items-center justify-center">
          <div class="text-lg text-center">{{ $t('channel.batchMerge.mergeTo') }}</div>
        </div>
        <el-tree ref="targetTree" v-loading="treeLoading" :data="targetTreeData" :props="{ label: 'name' }" node-key="id" show-checkbox check-strictly @check="handleSingleCheck" />
      </el-col>
    </el-row>
    <div class="mt-5">
      <el-alert v-if="error" :title="error" type="error" :closeable="false" class="mb-2" />
      <el-button type="primary" @click="() => handleSubmit()">{{ $t('submit') }}</el-button>
    </div>
  </el-dialog>
</template>
