<script setup lang="ts">
import { ref, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { currentUser } from '@/stores/useCurrentUser';
import { toTree, findTreeItem } from '@/utils/tree';
import { queryChannelList, batchMoveChannel } from '@/api/content';

defineOptions({
  name: 'ChannelMoveForm',
});
const props = defineProps({
  channelPermissions: { type: Array as PropType<string[]>, required: true },
});
const emit = defineEmits({ finished: null });
const visible = defineModel<boolean>();
const { t } = useI18n();

const treeLoading = ref<boolean>(false);
const submitting = ref<boolean>(false);
// 本次打开期间是否移动过：关闭时据此决定列表要不要刷新
const changed = ref<boolean>(false);
const srcTreeData = ref<any[]>([]);
const targetTreeData = ref<any[]>([]);
const srcTree = ref<any>();
const targetTree = ref<any>();
const moveType = ref<'inner' | 'before' | 'after'>('inner');
const error = ref<string>();

// 栏目权限（角色、组织的数据范围）：服务端据同一份清单拒绝，这里据它置灰
const allowed = (channel: any) => currentUser.allChannelPermission || props.channelPermissions.includes(channel.id);
const walk = (nodes: any[], visit: (node: any, inherited: boolean) => boolean, inherited = false) => {
  nodes.forEach((node) => walk(node.children ?? [], visit, visit(node, inherited)));
};
// 目标栏目不可选：已选原栏目及其子栏目；以及移动后的上级（之内为目标自身，之前、之后为目标的上级）不在栏目权限之内
const refreshTargetDisabled = () => {
  const checkedIds: string[] = srcTree.value?.getCheckedKeys() ?? [];
  walk(targetTreeData.value, (node, inSource) => {
    const within = inSource || checkedIds.includes(node.id);
    const newParent = moveType.value === 'inner' ? node : node.parent;
    node.disabled = within || (newParent != null && !allowed(newParent));
    return within;
  });
  const targetId = targetTree.value?.getCheckedKeys()[0];
  if (targetId != null && findTreeItem(targetTreeData.value, (item: any) => item.id === targetId)?.disabled) {
    targetTree.value.setCheckedKeys([]);
  }
};
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
    walk(srcTreeData.value, (node) => {
      node.disabled = !allowed(node);
      return false;
    });
    refreshTargetDisabled();
  } finally {
    treeLoading.value = false;
  }
};

watch(visible, async () => {
  if (visible.value) {
    changed.value = false;
    error.value = undefined;
    fetchData();
  }
});
watch(moveType, () => refreshTargetDisabled());
const handleSrcTreeCheck = () => {
  error.value = undefined;
  refreshTargetDisabled();
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
  submitting.value = true;
  try {
    await batchMoveChannel(srcIds, targetId, moveType.value);
    changed.value = true;
    ElMessage.success(t('success'));
    await fetchData();
  } finally {
    submitting.value = false;
  }
};
const handleClose = () => {
  if (changed.value) {
    emit('finished');
  }
};
</script>

<template>
  <el-dialog v-model="visible" :title="$t('channel.op.batchMove')" width="768" top="5vh" @close="handleClose">
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="text-lg text-center">{{ $t('channel.batchMove.srcChannel') }}</div>
        <el-tree ref="srcTree" v-loading="treeLoading" :data="srcTreeData" :props="{ label: 'name' }" node-key="id" show-checkbox check-strictly @check="handleSrcTreeCheck" />
      </el-col>
      <el-col :span="12">
        <div class="flex items-center justify-center">
          <div class="text-lg text-center">{{ $t('channel.batchMove.moveTo') }}</div>
          <el-select v-model="moveType" class="w-32 ml-2">
            <el-option v-for="item in ['inner', 'before', 'after']" :key="item" :label="$t(`channel.batchMove.moveTo.${item}`)" :value="item" />
          </el-select>
        </div>
        <el-tree ref="targetTree" v-loading="treeLoading" :data="targetTreeData" :props="{ label: 'name' }" node-key="id" show-checkbox check-strictly @check="handleSingleCheck" />
      </el-col>
    </el-row>
    <div class="mt-5">
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-2" />
      <el-button type="primary" :loading="submitting" @click="() => handleSubmit()">{{ $t('submit') }}</el-button>
    </div>
  </el-dialog>
</template>
