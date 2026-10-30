<script setup lang="ts">
import { computed, onMounted, ref, toRefs, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { currentUser, hasPermission } from '@/stores/useCurrentUser';
import { sm2Encrypt } from '@/utils/sm';
import { toTree } from '@/utils/tree';
import { queryClientPublicKey } from '@/api/login';
import { batchCreateUser, queryGroupList, queryOrgList, queryRoleList } from '@/api/user';
import LabelTip from '@/components/LabelTip.vue';

// 批量录入（docs/spec/user-management/plan.md）：一个表格，一行一个用户，统一的组织、用户组与初始密码。
// 规则在服务端（UserBatchService，与单个新增相同）：任何一行有问题就一个也不建，问题逐行显示在表格里，改正后重新提交。
// 从 Excel 复制的多行多列粘贴到任一单元格时，按制表符、换行铺到后面的列与行里。
// 角色（D8）只在操作员另有权限设置的权限时显示：与“新增 + 权限设置”两步等价，等级高于新用户的角色不能选
defineOptions({
  name: 'UserBatchForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  // 列表里选中的组织：作为组织的初值
  org: { type: Object, default: null },
  showGlobalData: { type: Boolean, required: true },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { modelValue: visible, showGlobalData } = toRefs(props);
const { t } = useI18n();
const sysConfig = useSysConfigStore();

// 与服务端 UserController.BATCH_MAX_ROWS 一致
const MAX_ROWS = 200;
const INITIAL_ROWS = 5;
const COLUMNS = ['username', 'realName', 'mobile', 'email', 'password'] as const;
type Column = (typeof COLUMNS)[number];
interface Row {
  username: string;
  realName: string;
  mobile: string;
  email: string;
  password: string;
  reason?: string;
}
const emptyRow = (): Row => ({ username: '', realName: '', mobile: '', email: '', password: '' });
const isBlank = (row: Row): boolean => COLUMNS.every((column) => row[column].trim() === '');

const form = ref<any>();
const values = ref<{ orgId?: string; groupId?: string; password: string; roleIds: string[] }>({ password: '', roleIds: [] });
const rows = ref<Row[]>([]);
const orgList = ref<any[]>([]);
const groupList = ref<any[]>([]);
const roleList = ref<any[]>([]);
// 给角色须另有权限设置的权限；新用户的等级是操作员的下一级，角色的等级不能高于它
const canGrantRoles = hasPermission('user:updatePermission');
const newUserRank = currentUser.rank + 1;
const publicKey = ref<string>('');
const loading = ref<boolean>(false);
const filled = computed<number>(() => rows.value.filter((row) => !isBlank(row)).length);

const fetchOrgList = async () => {
  orgList.value = toTree(await queryOrgList({ current: !showGlobalData.value }));
};
const fetchGroupList = async () => {
  groupList.value = await queryGroupList();
};
const fetchRoleList = async () => {
  roleList.value = canGrantRoles ? await queryRoleList() : [];
};
const reset = () => {
  values.value = { orgId: props.org?.id, groupId: groupList.value.find((item) => item.type === 2)?.id, password: '', roleIds: [] };
  rows.value = Array.from({ length: INITIAL_ROWS }, emptyRow);
};

onMounted(async () => {
  publicKey.value = await queryClientPublicKey();
});
watch(visible, async () => {
  if (!visible.value) {
    return;
  }
  loading.value = true;
  try {
    await Promise.all([fetchOrgList(), fetchGroupList(), fetchRoleList()]);
  } finally {
    loading.value = false;
  }
  reset();
  form.value?.clearValidate();
});

const addRows = (count: number) => {
  for (let i = 0; i < count; i++) {
    rows.value.push(emptyRow());
  }
};
const removeRow = (index: number) => {
  rows.value.splice(index, 1);
  if (rows.value.length === 0) {
    addRows(1);
  }
};
const clearRows = () => {
  rows.value = Array.from({ length: INITIAL_ROWS }, emptyRow);
};
// 粘贴多行多列：从当前单元格起铺开；一行一个值时按换行分行，一行多个值时按制表符（Excel）或逗号分列
const handlePaste = (event: ClipboardEvent, rowIndex: number, column: Column) => {
  const text = event.clipboardData?.getData('text/plain') ?? '';
  if (!/[\t\r\n]/.test(text)) {
    return;
  }
  event.preventDefault();
  const lines = text.replace(/\r\n?/g, '\n').split('\n');
  if (lines.length > 1 && lines[lines.length - 1] === '') {
    lines.pop();
  }
  const start = COLUMNS.indexOf(column);
  lines.forEach((line, i) => {
    const cells = line.includes('\t') ? line.split('\t') : line.split(/[,，;；]/);
    while (rows.value.length <= rowIndex + i) {
      rows.value.push(emptyRow());
    }
    const target = rows.value[rowIndex + i];
    cells.forEach((cell, j) => {
      const key = COLUMNS[start + j];
      if (key != null) {
        target[key] = cell.trim();
      }
    });
    target.reason = undefined;
  });
};

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    const entries = rows.value.filter((row) => !isBlank(row));
    if (entries.length === 0) {
      ElMessage.warning(t('user.batch.empty'));
      return;
    }
    if (entries.length > MAX_ROWS) {
      ElMessage.warning(t('user.batch.tooMany', { max: MAX_ROWS }));
      return;
    }
    rows.value.forEach((row) => (row.reason = undefined));
    loading.value = true;
    try {
      const encrypt = (plain: string): string | undefined => (plain.trim() ? sm2Encrypt(plain, publicKey.value) : undefined);
      const { created, issues } = await batchCreateUser({
        orgId: values.value.orgId,
        groupId: values.value.groupId,
        password: encrypt(values.value.password),
        roleIds: canGrantRoles ? values.value.roleIds : undefined,
        users: entries.map((row) => ({
          username: row.username.trim(),
          realName: row.realName.trim() || undefined,
          mobile: row.mobile.trim() || undefined,
          email: row.email.trim() || undefined,
          password: encrypt(row.password),
        })),
      });
      if (issues.length > 0) {
        // 服务端的行号是提交的行（已去掉空行）的序号
        issues.forEach((issue: { row: number; reason: string }) => {
          const entry = entries[issue.row - 1];
          if (entry != null) {
            entry.reason = issue.reason;
          }
        });
        ElMessage.warning(t('user.batch.hasIssues', { count: issues.length }));
        return;
      }
      ElMessage.success(t('user.batch.done', { count: created }));
      emit('finished');
      emit('update:modelValue', false);
    } finally {
      loading.value = false;
    }
  });
};
</script>

<template>
  <el-dialog :title="$t('user.op.batchCreate')" :model-value="modelValue" :width="1080" top="5vh" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <div v-loading="loading">
      <div class="mb-3 text-xs leading-5 text-gray-secondary">{{ $t('user.batch.tip') }}</div>
      <el-form ref="form" :model="values" label-position="top" @submit.prevent>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item prop="orgId" :label="$t('user.org')" :rules="{ required: true, message: () => $t('v.required') }">
              <el-tree-select
                v-model="values.orgId"
                :data="orgList"
                node-key="id"
                :default-expanded-keys="orgList.map((item) => item.id)"
                :props="{ label: 'name' }"
                :render-after-expand="false"
                check-strictly
                class="w-full"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item prop="groupId" :label="$t('user.group')" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="user.group" help /></template>
              <el-select v-model="values.groupId" class="w-full">
                <el-option v-for="item in groupList" :key="item.id" :value="item.id" :label="item.name" :disabled="item.type !== 2"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              prop="password"
              :label="$t('user.batch.password')"
              :rules="[
                {
                  min: sysConfig.security.passwordMinLength,
                  max: sysConfig.security.passwordMaxLength,
                  message: () => $t('user.error.passwordLength', { min: sysConfig.security.passwordMinLength, max: sysConfig.security.passwordMaxLength }),
                },
                { pattern: new RegExp(sysConfig.security.passwordPattern), message: () => $t(`user.error.passwordPattern.${sysConfig.security.passwordStrength}`) },
              ]"
            >
              <template #label><label-tip message="user.batch.password" help /></template>
              <el-input v-model="values.password" :maxlength="sysConfig.security.passwordMaxLength" show-password autocomplete="new-password"></el-input>
            </el-form-item>
          </el-col>
          <el-col v-if="canGrantRoles" :span="24">
            <el-form-item prop="roleIds">
              <template #label><label-tip message="user.role" help /></template>
              <el-select v-model="values.roleIds" multiple clearable class="w-full">
                <el-option v-for="item in roleList" :key="item.id" :value="item.id" :label="`${item.name}(${item.rank})`" :disabled="item.rank < newUserRank"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div class="mb-2 flex items-center gap-2">
        <el-button :icon="Plus" size="small" @click="() => addRows(1)">{{ $t('user.batch.addRow') }}</el-button>
        <el-button size="small" @click="() => addRows(5)">{{ $t('user.batch.addRows', { count: 5 }) }}</el-button>
        <el-button size="small" @click="() => clearRows()">{{ $t('user.batch.clear') }}</el-button>
      </div>
      <el-table :data="rows" size="small" max-height="420" border :row-class-name="({ row }) => (row.reason != null ? 'user-batch-issue' : '')">
        <el-table-column type="index" :label="$t('user.batch.index')" width="56" />
        <el-table-column :label="`${$t('user.username')} *`" min-width="150">
          <template #default="{ row, $index }">
            <el-input v-model="row.username" maxlength="30" @paste="(event: ClipboardEvent) => handlePaste(event, $index, 'username')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('user.realName')" min-width="120">
          <template #default="{ row, $index }">
            <el-input v-model="row.realName" maxlength="50" @paste="(event: ClipboardEvent) => handlePaste(event, $index, 'realName')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('user.mobile')" min-width="140">
          <template #default="{ row, $index }">
            <el-input v-model="row.mobile" maxlength="50" @paste="(event: ClipboardEvent) => handlePaste(event, $index, 'mobile')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('user.email')" min-width="180">
          <template #default="{ row, $index }">
            <el-input v-model="row.email" maxlength="50" @paste="(event: ClipboardEvent) => handlePaste(event, $index, 'email')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('user.batch.rowPassword')" min-width="140">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.password"
              :maxlength="sysConfig.security.passwordMaxLength"
              show-password
              autocomplete="new-password"
              @paste="(event: ClipboardEvent) => handlePaste(event, $index, 'password')"
            />
          </template>
        </el-table-column>
        <el-table-column :label="$t('user.batch.issue')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-danger">{{ row.reason }}</span>
          </template>
        </el-table-column>
        <el-table-column width="48" align="center">
          <template #default="{ $index }">
            <el-button type="danger" :icon="Delete" size="small" link @click="() => removeRow($index)" />
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="loading" :disabled="filled <= 0" @click="() => handleSubmit()">{{ $t('user.batch.submit', { count: filled }) }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.el-table .user-batch-issue td) {
  background-color: var(--el-color-danger-light-9);
}
</style>
