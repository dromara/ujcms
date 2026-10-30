<script setup lang="ts">
import { computed, onMounted, ref, toRefs, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { saveAs } from 'file-saver';
import type { UploadFile } from 'element-plus';
import { handleBlobError } from '@/utils/request';
import { sm2Encrypt } from '@/utils/sm';
import { toTree } from '@/utils/tree';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { currentUser, hasPermission } from '@/stores/useCurrentUser';
import { queryClientPublicKey } from '@/api/login';
import { downloadUserImportTemplate, importUser, previewUserImport, queryGroupList, queryOrgList, queryRoleList } from '@/api/user';
import LabelTip from '@/components/LabelTip.vue';

// 用户批量导入（docs/spec/user-management/plan.md，商业版 T1）。三步：选文件与默认值 → 预检（只读）→ 执行（当场创建，
// 返回结果报告）。服务端不在预检与执行之间保存文件：执行时把同一份文件再传一次，重新核对。规则与批量录入、单个新增相同。
// 默认角色（D8）只在操作员另有权限设置的权限时显示；没有这个权限时表格里的角色列也不读
defineOptions({
  name: 'UserImportDialog',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  // 列表里选中的组织：作为默认组织的初值
  org: { type: Object, default: null },
  showGlobalData: { type: Boolean, required: true },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const MAX_ISSUES = 200;

const { modelValue: visible, showGlobalData } = toRefs(props);
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const step = ref<'select' | 'preview' | 'done'>('select');
const file = ref<File>();
const upload = ref<any>();
const form = ref<any>();
const values = ref<{ orgId?: string; groupId?: string; password: string; roleIds: string[] }>({ password: '', roleIds: [] });
const orgList = ref<any[]>([]);
const groupList = ref<any[]>([]);
const roleList = ref<any[]>([]);
const canGrantRoles = hasPermission('user:updatePermission');
const newUserRank = currentUser.rank + 1;
const publicKey = ref<string>('');
const loading = ref<boolean>(false);
const preview = ref<any>();
const report = ref<any>();

const issues = computed<any[]>(() => (step.value === 'done' ? (report.value?.issues ?? []) : (preview.value?.issues ?? [])));
const issueCount = computed<number>(() => {
  if (step.value === 'done') {
    return (report.value?.skipped ?? 0) + (report.value?.failed ?? 0);
  }
  return (preview.value?.skipped ?? 0) + (preview.value?.invalid ?? 0);
});

const reset = () => {
  step.value = 'select';
  file.value = undefined;
  preview.value = undefined;
  report.value = undefined;
  upload.value?.clearFiles();
};

onMounted(async () => {
  publicKey.value = await queryClientPublicKey();
});
watch(visible, async () => {
  if (!visible.value) {
    return;
  }
  reset();
  loading.value = true;
  try {
    const [flatOrgList, groups, roles] = await Promise.all([
      queryOrgList({ current: !showGlobalData.value }),
      queryGroupList(),
      canGrantRoles ? queryRoleList() : Promise.resolve([]),
    ]);
    orgList.value = toTree(flatOrgList);
    groupList.value = groups;
    roleList.value = roles;
  } finally {
    loading.value = false;
  }
  values.value = { orgId: props.org?.id, groupId: groupList.value.find((item) => item.type === 2)?.id, password: '', roleIds: [] };
  form.value?.clearValidate();
});

const options = () => ({
  orgId: values.value.orgId,
  groupId: values.value.groupId,
  password: values.value.password.trim() ? sm2Encrypt(values.value.password, publicKey.value) : undefined,
  roleIds: canGrantRoles ? values.value.roleIds : undefined,
});
const handleFileChange = (uploadFile: UploadFile) => {
  file.value = uploadFile.raw;
};
const handleFileRemove = () => {
  file.value = undefined;
};
const handleTemplate = async () => {
  try {
    const { blob, filename } = await downloadUserImportTemplate();
    saveAs(blob, filename);
  } catch (e) {
    await handleBlobError(e);
  }
};
const handlePreview = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    if (file.value == null) {
      ElMessage.warning(t('user.import.fileRequired'));
      return;
    }
    loading.value = true;
    try {
      preview.value = await previewUserImport(file.value, options());
      step.value = 'preview';
    } finally {
      loading.value = false;
    }
  });
};
const handleStart = async () => {
  if (file.value == null) {
    return;
  }
  loading.value = true;
  try {
    report.value = await importUser(file.value, options());
    step.value = 'done';
    emit('finished');
  } finally {
    loading.value = false;
  }
};
// 回到第一步重新选文件：上传组件随这一步重建，列表是空的——原来那份文件也一并放掉
const handleBack = () => {
  file.value = undefined;
  preview.value = undefined;
  step.value = 'select';
};
</script>

<template>
  <el-dialog :title="$t('user.op.import')" :model-value="modelValue" :width="760" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <div v-loading="loading">
      <!-- 第一步：文件与默认值 -->
      <template v-if="step === 'select'">
        <div class="mb-4 text-xs leading-5 text-gray-secondary">
          {{ $t('user.import.tip') }}
          <el-button type="primary" link size="small" @click="() => handleTemplate()">{{ $t('user.import.template') }}</el-button>
        </div>
        <el-form ref="form" :model="values" label-position="top" @submit.prevent>
          <el-form-item :label="$t('user.import.file')" required>
            <el-upload ref="upload" class="w-full" :auto-upload="false" :limit="1" accept=".xlsx" :on-change="handleFileChange" :on-remove="handleFileRemove">
              <el-button>{{ $t('user.import.selectFile') }}</el-button>
            </el-upload>
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item prop="orgId" :label="$t('user.import.org')">
                <el-tree-select
                  v-model="values.orgId"
                  :data="orgList"
                  node-key="id"
                  :default-expanded-keys="orgList.map((item) => item.id)"
                  :props="{ label: 'name' }"
                  :render-after-expand="false"
                  check-strictly
                  clearable
                  class="w-full"
                />
                <div class="text-xs leading-5 text-gray-secondary">{{ $t('user.import.org.tip') }}</div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="groupId" :label="$t('user.import.group')">
                <el-select v-model="values.groupId" clearable class="w-full">
                  <el-option v-for="item in groupList" :key="item.id" :value="item.id" :label="item.name" :disabled="item.type !== 2"></el-option>
                </el-select>
                <div class="text-xs leading-5 text-gray-secondary">{{ $t('user.import.group.tip') }}</div>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item
            prop="password"
            :label="$t('user.import.password')"
            :rules="[
              {
                min: sysConfig.security.passwordMinLength,
                max: sysConfig.security.passwordMaxLength,
                message: () => $t('user.error.passwordLength', { min: sysConfig.security.passwordMinLength, max: sysConfig.security.passwordMaxLength }),
              },
              { pattern: new RegExp(sysConfig.security.passwordPattern), message: () => $t(`user.error.passwordPattern.${sysConfig.security.passwordStrength}`) },
            ]"
          >
            <el-input v-model="values.password" :maxlength="sysConfig.security.passwordMaxLength" show-password autocomplete="new-password" class="w-full"></el-input>
            <div class="text-xs leading-5 text-gray-secondary">{{ $t('user.import.password.tip') }}</div>
          </el-form-item>
          <el-form-item v-if="canGrantRoles" prop="roleIds" :label="$t('user.import.roles')">
            <template #label><label-tip :label="$t('user.import.roles')" :tooltip="$t('user.role.tooltip')" help /></template>
            <el-select v-model="values.roleIds" multiple clearable class="w-full">
              <el-option v-for="item in roleList" :key="item.id" :value="item.id" :label="`${item.name}(${item.rank})`" :disabled="item.rank < newUserRank"></el-option>
            </el-select>
            <div class="text-xs leading-5 text-gray-secondary">{{ $t('user.import.roles.tip') }}</div>
          </el-form-item>
        </el-form>
      </template>

      <!-- 第二步：预检结果 -->
      <template v-else-if="step === 'preview' && preview != null">
        <div class="flex flex-wrap items-center gap-2 mb-3">
          <el-tag type="info" disable-transitions>{{ $t('user.import.result.total', { count: preview.total }) }}</el-tag>
          <el-tag type="success" disable-transitions>{{ $t('user.import.result.valid', { count: preview.valid }) }}</el-tag>
          <el-tag v-if="preview.skipped > 0" type="warning" disable-transitions>{{ $t('user.import.result.skipped', { count: preview.skipped }) }}</el-tag>
          <el-tag v-if="preview.invalid > 0" type="danger" disable-transitions>{{ $t('user.import.result.invalid', { count: preview.invalid }) }}</el-tag>
        </div>
        <el-alert
          v-if="preview.unknownHeaders?.length > 0"
          type="warning"
          :closable="false"
          show-icon
          class="mb-3"
          :title="$t('user.import.unknownHeaders', { headers: preview.unknownHeaders.join($t('punct.enum')) })"
        />
      </template>

      <!-- 第三步：结果 -->
      <template v-else-if="step === 'done' && report != null">
        <el-alert
          :type="report.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
          class="mb-3"
          :title="$t('user.import.done', { created: report.created, skipped: report.skipped, failed: report.failed })"
        />
      </template>

      <!-- 被跳过、有问题的行：预检与执行结果共用 -->
      <template v-if="step !== 'select' && issues.length > 0">
        <div class="mb-1 text-sm">
          {{ $t('user.import.issues') }}
          <span v-if="issueCount > issues.length" class="ml-2 text-xs text-gray-secondary">{{ $t('user.import.issues.more', { max: MAX_ISSUES }) }}</span>
        </div>
        <el-table :data="issues" size="small" max-height="320" border>
          <el-table-column property="row" :label="$t('user.import.row')" width="70" />
          <el-table-column property="username" :label="$t('user.username')" min-width="150" show-overflow-tooltip />
          <el-table-column property="reason" :label="$t('user.import.reason')" min-width="280" show-overflow-tooltip />
        </el-table>
      </template>
    </div>
    <template #footer>
      <template v-if="step === 'select'">
        <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="loading" :disabled="file == null" @click="() => handlePreview()">{{ $t('user.import.preview') }}</el-button>
      </template>
      <template v-else-if="step === 'preview'">
        <el-button @click="() => handleBack()">{{ $t('user.import.back') }}</el-button>
        <el-button type="primary" :loading="loading" :disabled="!preview || preview.valid <= 0" @click="() => handleStart()">
          {{ $t('user.import.start', { count: preview?.valid ?? 0 }) }}
        </el-button>
      </template>
      <template v-else>
        <el-button @click="() => reset()">{{ $t('user.import.again') }}</el-button>
        <el-button type="primary" @click="() => $emit('update:modelValue', false)">{{ $t('close') }}</el-button>
      </template>
    </template>
  </el-dialog>
</template>
