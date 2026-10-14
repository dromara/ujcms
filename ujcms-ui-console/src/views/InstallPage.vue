<script setup lang="ts">
// 安装向导（install-flow §5 / D2）：backend-state.installed=false 时替代登录页渲染。
// 步骤：令牌 → 站点（请求预填，可编辑，D3）→ 管理员（SM2 与登录同路径）→ 主题与演示数据
// （来源 bundled|library|marketplace|upload，D30，均经主题包库解析）→ 完成。
// 每步均为幂等提交，中断后重进向导即续装；完成翻转后安装端点关闭，本页跳回登录。
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { Key, User, Lock, Plus } from '@element-plus/icons-vue';
import type { UploadFile } from 'element-plus';
import { sm2Encrypt } from '@/utils/sm';
import { queryClientPublicKey } from '@/api/login';
import type { SeedRunResult } from '@/api/theme';
import { notifySeedFailures, seedRunSummary } from '@/views/design/themeUi';
import {
  queryInstallState,
  verifyInstallToken,
  installSite,
  installAdmin,
  queryInstallThemeOptions,
  installTheme,
  installThemeUpload,
  installComplete,
} from '@/api/install';

const { t } = useI18n();
const router = useRouter();
const title = import.meta.env.VITE_APP_NAME || 'UJCMS';

const step = ref<number>(0);
const loading = ref<boolean>(false);
const error = ref<string>();
const notReady = ref<boolean>(false);
const templateStorageWarning = ref<string>();
// 令牌文件绝对路径（D49）：后端把随机令牌同时写入 WEB-INF/install-token.txt；预设令牌或写入失败时为空
const tokenFile = ref<string>();

const tokenForm = ref<any>();
const siteForm = ref<any>();
const adminForm = ref<any>();

const token = ref<string>('');
const site = ref<any>({ name: '', protocol: 'http', domain: '', port: undefined });
const admin = ref<any>({ username: 'admin', password: '', passwordConfirm: '', mustChangePassword: false });
const seedScope = ref<string>('all');
// 主题来源（D30）：bundled 预选；library/marketplace/upload 驱动同一 InstallService/包库管线
const themeSource = ref<string>('bundled');
const themeOptions = ref<any>();
const libraryRef = ref<string>('');
const marketplaceRef = ref<string>('');
const uploadFile = ref<File>();
// 主题步骤的种子导入结果：完成页只渲染可读摘要（原始计数结构不直接展示，避免误读为报错）
const seedRun = ref<SeedRunResult>();
const seedSummary = ref<string>();
const completeReport = ref<any>();

// 已提交步骤的只读回显：向导只进不退（每步即时落库，无草稿可回退），改错在登录后的站点设置 / 用户管理完成；
// 回显让操作员在下一步前即可确认已写入的值。密码不回显。
const siteAddress = computed<string>(() => {
  const port = site.value.port ? `:${site.value.port}` : '';
  return `${site.value.protocol}://${site.value.domain}${port}`;
});
const themeLabel = computed<string>(() => {
  switch (themeSource.value) {
    case 'library':
      return libraryRef.value;
    case 'marketplace':
      return marketplaceRef.value.trim();
    case 'upload':
      return uploadFile.value?.name ?? '';
    default:
      return themeOptions.value?.currentTheme ? `${t('install.theme.source.bundled')} (${themeOptions.value.currentTheme})` : t('install.theme.source.bundled');
  }
});
const recap = computed<{ label: string; value: string }[]>(() => {
  const items: { label: string; value: string }[] = [];
  if (step.value >= 2) {
    items.push({ label: t('install.siteName'), value: site.value.name || '-' });
    items.push({ label: t('install.recap.address'), value: siteAddress.value });
  }
  if (step.value >= 3) {
    items.push({ label: t('install.username'), value: admin.value.username });
  }
  if (step.value >= 4) {
    items.push({ label: t('install.theme.source'), value: themeLabel.value });
  }
  return items;
});

onMounted(async () => {
  try {
    const state = await queryInstallState();
    if (state.installed) {
      router.replace('/login');
      return;
    }
    if (state.state === 'NOT_INSTALLED') {
      notReady.value = true;
      return;
    }
    site.value.name = state.siteName ?? '';
    if (state.prefill) {
      site.value.protocol = state.prefill.protocol ?? 'http';
      site.value.domain = state.prefill.domain ?? '';
      site.value.port = state.prefill.port ?? undefined;
    }
    templateStorageWarning.value = state.templateStorageWarning ?? undefined;
    tokenFile.value = state.tokenFile ?? undefined;
  } catch {
    notReady.value = true;
  }
});

const withLoading = async (fn: () => Promise<void>) => {
  loading.value = true;
  error.value = undefined;
  try {
    await fn();
  } finally {
    loading.value = false;
  }
};

const handleToken = () => {
  tokenForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    await withLoading(async () => {
      try {
        await verifyInstallToken(token.value);
        step.value = 1;
      } catch {
        error.value = t('install.token.invalid');
      }
    });
  });
};

const handleSite = () => {
  siteForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    await withLoading(async () => {
      await installSite(token.value, {
        name: site.value.name || undefined,
        protocol: site.value.protocol,
        domain: site.value.domain || undefined,
        port: site.value.port || undefined,
      });
      step.value = 2;
    });
  });
};

const handleAdmin = () => {
  adminForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    await withLoading(async () => {
      const publicKey = await queryClientPublicKey();
      await installAdmin(token.value, {
        username: admin.value.username || undefined,
        password: admin.value.password ? sm2Encrypt(admin.value.password, publicKey) : undefined,
        mustChangePassword: admin.value.mustChangePassword,
      });
      step.value = 3;
      loadThemeOptions();
    });
  });
};

// 主题步骤可选项：包库存量下拉与市场开关。取不到不阻断向导（bundled/upload 仍可用）
const loadThemeOptions = async () => {
  try {
    themeOptions.value = await queryInstallThemeOptions(token.value);
  } catch {
    themeOptions.value = undefined;
  }
};

// auto-upload=false 时 ElementPlus 只触发 on-change（before-upload 不触发），校验挂在这里
const handleUploadSelect = (file: UploadFile) => {
  const raw = file.raw;
  if (raw == null) return;
  if (!raw.name.toLowerCase().endsWith('.zip')) return;
  uploadFile.value = raw;
};

const handleTheme = async () => {
  error.value = undefined;
  if (themeSource.value === 'library' && !libraryRef.value) {
    error.value = t('install.theme.ref.required');
    return;
  }
  if (themeSource.value === 'marketplace' && !marketplaceRef.value.trim()) {
    error.value = t('install.theme.ref.required');
    return;
  }
  if (themeSource.value === 'upload' && uploadFile.value == null) {
    error.value = t('install.theme.file.required');
    return;
  }
  await withLoading(async () => {
    let result;
    if (themeSource.value === 'upload') {
      const formData = new FormData();
      formData.append('file', uploadFile.value as File);
      formData.append('seedScope', seedScope.value);
      result = await installThemeUpload(token.value, formData);
    } else {
      result = await installTheme(token.value, {
        source: themeSource.value,
        ref: themeSource.value === 'library' ? libraryRef.value : themeSource.value === 'marketplace' ? marketplaceRef.value.trim() : undefined,
        seedScope: seedScope.value,
      });
    }
    seedRun.value = result.seed ?? undefined;
    seedSummary.value = seedRun.value
      ? t('install.seed.summary', { summary: seedRunSummary(seedRun.value, t) })
      : t('install.seed.skipped');
    const report = await installComplete(token.value);
    completeReport.value = report;
    step.value = 4;
    notifySeedFailures(seedRun.value, t);
  });
};

const handleToLogin = () => {
  // 安装完成后整页刷新进登录：清空探测缓存与任何中间状态
  window.location.href = window.location.pathname + '#/login';
  window.location.reload();
};
</script>
<template>
  <div class="h-full p-3 bg-gray-100">
    <h3 class="py-5 text-3xl font-bold text-center text-primary">{{ title }}</h3>
    <div class="mx-auto bg-white rounded shadow p-6 md:max-w-xl">
      <h4 class="mb-4 text-xl font-bold text-center">{{ $t('install.title') }}</h4>
      <el-alert v-if="notReady" :title="$t('install.notReady')" type="error" class="mb-3" :closable="false" show-icon />
      <template v-else>
        <el-steps :active="step" finish-status="success" align-center class="mb-6">
          <el-step :title="$t('install.step.token')" />
          <el-step :title="$t('install.step.site')" />
          <el-step :title="$t('install.step.admin')" />
          <el-step :title="$t('install.step.theme')" />
          <el-step :title="$t('install.step.complete')" />
        </el-steps>
        <el-alert v-if="error" :title="error" type="error" class="mb-3" :closable="false" show-icon />
        <el-alert
          v-if="templateStorageWarning"
          :title="$t('install.warning.templateStorage')"
          :description="templateStorageWarning"
          type="warning"
          class="mb-3"
          :closable="false"
          show-icon
        />

        <div v-if="recap.length > 0 && step < 4" class="mb-4 px-3 py-2 text-sm rounded bg-gray-100">
          <div class="mb-1 text-xs text-gray-secondary">{{ $t('install.recap.tip') }}</div>
          <div v-for="item in recap" :key="item.label" class="flex">
            <span class="text-gray-secondary shrink-0">{{ item.label }}{{ $t('punct.colon') }}</span>
            <span class="break-all">{{ item.value }}</span>
          </div>
        </div>

        <el-form v-if="step === 0" ref="tokenForm" :model="{ token }" @submit.prevent>
          <p class="mb-3 text-sm text-gray-secondary">{{ $t('install.token.tip') }}</p>
          <p v-if="tokenFile" class="mb-3 text-sm text-gray-secondary">
            {{ $t('install.token.file') }}
            <code class="break-all select-all">{{ tokenFile }}</code>
          </p>
          <el-form-item prop="token" :rules="[{ required: true, message: () => $t('v.required') }]">
            <el-input v-model="token" name="token" maxlength="64" :placeholder="$t('install.token')" :prefix-icon="Key" />
          </el-form-item>
          <el-button type="primary" native-type="submit" class="w-full" :loading="loading" @click.prevent="handleToken">
            {{ $t('install.next') }}
          </el-button>
        </el-form>

        <el-form v-if="step === 1" ref="siteForm" :model="site" label-width="120px" @submit.prevent>
          <el-form-item :label="$t('install.siteName')" prop="name">
            <el-input v-model="site.name" maxlength="50" />
          </el-form-item>
          <el-form-item :label="$t('install.protocol')" prop="protocol">
            <el-select v-model="site.protocol">
              <el-option label="http" value="http" />
              <el-option label="https" value="https" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('install.domain')" prop="domain" :rules="[{ required: true, message: () => $t('v.required') }]">
            <el-input v-model="site.domain" maxlength="100" />
          </el-form-item>
          <el-form-item :label="$t('install.port')" prop="port" :rules="{ type: 'number', min: 1, max: 65535, message: () => $t('v.range', { min: 1, max: 65535 }) }">
            <el-input v-model.number="site.port" />
            <div class="text-xs text-gray-secondary">{{ $t('install.port.tip') }}</div>
          </el-form-item>
          <el-button type="primary" native-type="submit" class="w-full" :loading="loading" @click.prevent="handleSite">
            {{ $t('install.next') }}
          </el-button>
        </el-form>

        <el-form v-if="step === 2" ref="adminForm" :model="admin" label-width="120px" @submit.prevent>
          <el-form-item :label="$t('install.username')" prop="username" :rules="[{ required: true, message: () => $t('v.required') }]">
            <el-input v-model="admin.username" maxlength="50" :prefix-icon="User" />
          </el-form-item>
          <el-form-item :label="$t('install.password')" prop="password" :rules="[{ required: true, message: () => $t('v.required') }]">
            <el-input v-model="admin.password" type="password" maxlength="64" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item
            :label="$t('install.passwordConfirm')"
            prop="passwordConfirm"
            :rules="[
              { required: true, message: () => $t('v.required') },
              {
                validator: (rule: any, value: any, callback: any) => {
                  if (value !== admin.password) callback($t('install.passwordNotMatch'));
                  else callback();
                },
                trigger: 'blur',
              },
            ]"
          >
            <el-input v-model="admin.passwordConfirm" type="password" maxlength="64" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-button type="primary" native-type="submit" class="w-full" :loading="loading" @click.prevent="handleAdmin">
            {{ $t('install.next') }}
          </el-button>
        </el-form>

        <div v-if="step === 3">
          <div class="mb-2 text-sm font-bold">{{ $t('install.theme.source') }}</div>
          <el-radio-group v-model="themeSource" class="block mb-4">
            <div class="mb-2">
              <el-radio value="bundled">{{ $t('install.theme.source.bundled') }}</el-radio>
              <div class="ml-6 text-xs text-gray-secondary">
                {{ $t('install.theme.source.bundled.tip') }}<template v-if="themeOptions?.currentTheme"> ({{ themeOptions.currentTheme }})</template>
              </div>
            </div>
            <div class="mb-2">
              <el-radio value="library" :disabled="!themeOptions?.library?.length">{{ $t('install.theme.source.library') }}</el-radio>
              <div v-if="themeSource === 'library'" class="ml-6 mt-1">
                <el-select v-model="libraryRef" class="w-full" :placeholder="$t('install.theme.library.placeholder')">
                  <el-option
                    v-for="pkg in themeOptions?.library ?? []"
                    :key="`${pkg.name}@${pkg.version}`"
                    :label="`${pkg.name}@${pkg.version} (${pkg.source})`"
                    :value="`${pkg.name}@${pkg.version}`"
                  />
                </el-select>
              </div>
            </div>
            <div class="mb-2">
              <el-radio value="marketplace" :disabled="themeOptions != null && !themeOptions.marketplace">
                {{ $t('install.theme.source.marketplace') }}
              </el-radio>
              <div v-if="themeSource === 'marketplace'" class="ml-6 mt-1">
                <el-input v-model="marketplaceRef" maxlength="120" :placeholder="$t('install.theme.marketplace.placeholder')" />
              </div>
            </div>
            <div>
              <el-radio value="upload">{{ $t('install.theme.source.upload') }}</el-radio>
              <div v-if="themeSource === 'upload'" class="ml-6 mt-1">
                <el-upload :on-change="handleUploadSelect" :auto-upload="false" :show-file-list="false" accept=".zip" drag>
                  <div class="flex flex-col items-center px-4 py-4">
                    <el-icon class="text-3xl text-primary"><Plus /></el-icon>
                    <div class="mt-1 text-sm">{{ uploadFile?.name ?? $t('install.theme.upload.placeholder') }}</div>
                  </div>
                </el-upload>
                <div class="text-xs text-gray-secondary">{{ $t('install.theme.upload.tip') }}</div>
              </div>
            </div>
          </el-radio-group>
          <div class="mb-2 text-sm font-bold">{{ $t('install.seedScope') }}</div>
          <el-radio-group v-model="seedScope" class="block mb-4">
            <div class="mb-2">
              <el-radio value="all">{{ $t('install.seedScope.all') }}</el-radio>
              <div class="ml-6 text-xs text-gray-secondary">{{ $t('install.seedScope.all.tip') }}</div>
            </div>
            <div>
              <el-radio value="none">{{ $t('install.seedScope.none') }}</el-radio>
              <div class="ml-6 text-xs text-gray-secondary">{{ $t('install.seedScope.none.tip') }}</div>
            </div>
          </el-radio-group>
          <p v-if="loading" class="mb-3 text-sm text-gray-secondary">{{ $t('install.theme.running') }}</p>
          <el-button type="primary" class="w-full" :loading="loading" @click="handleTheme">{{ $t('install.complete') }}</el-button>
        </div>

        <div v-if="step === 4">
          <el-result icon="success" :title="$t('install.completed')" :sub-title="$t('install.completed.tip')">
            <template #extra>
              <el-descriptions :column="1" border size="small" class="mb-4 text-left">
                <el-descriptions-item v-for="item in recap" :key="item.label" :label="item.label">{{ item.value }}</el-descriptions-item>
              </el-descriptions>
              <p v-if="seedSummary" class="mb-4 text-sm text-gray-secondary">{{ seedSummary }}</p>
              <el-alert v-if="completeReport?.warning" type="warning" :closable="false" show-icon class="mb-4 text-left" :title="completeReport.warning" />
              <el-button type="primary" @click="handleToLogin">{{ $t('install.toLogin') }}</el-button>
            </template>
          </el-result>
        </div>
      </template>
    </div>
  </div>
</template>
