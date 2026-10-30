<script setup lang="ts">
import { computed, ref, toRefs, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { batchUpdateUserRoles, queryRoleList } from '@/api/user';
import LabelTip from '@/components/LabelTip.vue';

// 批量添加 / 移除角色（docs/spec/user-management/plan.md D9）：给列表里选中的用户统一加上或去掉几个角色，各人其余的角色不动。
// 添加时，等级高于任一选中用户的角色不能选（与权限设置相同）；移除时只列出选中用户持有的角色。
// 规则在服务端（UserBatchService.planRoles，与单个权限设置相同）：任何一个用户有问题就一个也不改，问题逐个列出，去掉这些用户后可以再试
defineOptions({
  name: 'UserRolesForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  mode: { type: String as PropType<'add' | 'remove'>, required: true },
  // 列表里选中的用户：id、username、rank、roleList
  users: { type: Array as PropType<any[]>, required: true },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { modelValue: visible, mode, users: selected } = toRefs(props);
const { t } = useI18n();

// 对话框自己的一份选中用户：有问题的用户可以从这一次操作里去掉，不动列表的选择
const targets = ref<any[]>([]);
const roleIds = ref<string[]>([]);
const roleList = ref<any[]>([]);
const issues = ref<any[]>([]);
const loading = ref<boolean>(false);
const isRemove = computed<boolean>(() => mode.value === 'remove');
// 添加：角色的等级不能高于任何一个选中用户，即角色的等级数值须不小于选中用户里最大的等级数值
const maxRank = computed<number>(() => Math.max(0, ...targets.value.map((user) => Number(user.rank ?? 0))));
const options = computed<any[]>(() => {
  if (!isRemove.value) {
    return roleList.value.map((role) => ({ ...role, disabled: role.rank < maxRank.value }));
  }
  // 移除：选中用户持有的角色，带持有人数
  const holders = new Map<string, { role: any; count: number }>();
  targets.value.forEach((user) => {
    (user.roleList ?? []).forEach((role: any) => {
      const entry = holders.get(role.id);
      if (entry != null) {
        entry.count += 1;
      } else {
        holders.set(role.id, { role, count: 1 });
      }
    });
  });
  return [...holders.values()].map(({ role, count }) => ({ ...role, count }));
});

watch(visible, async () => {
  if (!visible.value) {
    return;
  }
  targets.value = [...selected.value];
  roleIds.value = [];
  issues.value = [];
  if (!isRemove.value) {
    loading.value = true;
    try {
      roleList.value = await queryRoleList();
    } finally {
      loading.value = false;
    }
  }
});

const handleSubmit = async () => {
  if (roleIds.value.length === 0) {
    ElMessage.warning(t('user.roles.empty'));
    return;
  }
  loading.value = true;
  try {
    const { updated, unchanged, issues: result } = await batchUpdateUserRoles({
      ids: targets.value.map((user) => user.id),
      roleIds: roleIds.value,
      action: mode.value,
    });
    if (result.length > 0) {
      issues.value = result;
      ElMessage.warning(t('user.roles.hasIssues', { count: result.length }));
      return;
    }
    ElMessage.success(t('user.roles.done', { updated, unchanged }));
    emit('finished');
    emit('update:modelValue', false);
  } finally {
    loading.value = false;
  }
};
const dropIssues = () => {
  const ids = new Set(issues.value.map((issue) => issue.userId));
  targets.value = targets.value.filter((user) => !ids.has(user.id));
  issues.value = [];
};
</script>

<template>
  <el-dialog :title="$t(isRemove ? 'user.roles.remove' : 'user.roles.add')" :model-value="modelValue" :width="720" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <div v-loading="loading">
      <div class="mb-3 text-xs leading-5 text-gray-secondary">{{ $t(isRemove ? 'user.roles.remove.tip' : 'user.roles.add.tip') }}</div>
      <el-form label-position="top" @submit.prevent>
        <el-form-item :label="$t('user.roles.selected', { count: targets.length })">
          <div class="flex max-h-32 flex-wrap gap-1 overflow-y-auto">
            <el-tag v-for="user in targets" :key="user.id" size="small" type="info">{{ user.username }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item>
          <template #label><label-tip message="user.role" help /></template>
          <el-checkbox-group v-model="roleIds">
            <!-- 列表行里的角色只有名称，没有等级：移除时只显示名称 -->
            <el-checkbox v-for="item in options" :key="item.id" :value="item.id" :disabled="item.disabled">
              {{ item.rank != null ? `${item.name}(${item.rank})` : item.name }}
              <span v-if="isRemove" class="ml-1 text-xs text-gray-secondary">{{ $t('user.roles.holders', { count: item.count }) }}</span>
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="isRemove && options.length === 0" class="text-xs text-gray-secondary">{{ $t('user.roles.noRoles') }}</div>
        </el-form-item>
      </el-form>
      <template v-if="issues.length > 0">
        <el-alert type="warning" :closable="false" show-icon class="mb-2">
          <template #title>
            {{ $t('user.roles.hasIssues', { count: issues.length }) }}
            <el-button type="primary" size="small" link class="ml-2" @click="() => dropIssues()">{{ $t('user.roles.dropIssues') }}</el-button>
          </template>
        </el-alert>
        <el-table :data="issues" size="small" max-height="240" border>
          <el-table-column property="username" :label="$t('user.username')" min-width="150" show-overflow-tooltip />
          <el-table-column property="reason" :label="$t('user.roles.reason')" min-width="320" show-overflow-tooltip />
        </el-table>
      </template>
    </div>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="loading" :disabled="targets.length === 0 || roleIds.length === 0" @click="() => handleSubmit()">
        {{ $t(isRemove ? 'user.roles.remove.submit' : 'user.roles.add.submit', { count: targets.length }) }}
      </el-button>
    </template>
  </el-dialog>
</template>
