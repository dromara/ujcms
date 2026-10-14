<script setup lang="ts">
// 主题包库（install-flow §6 P3 / D5）：全局包列表 + 上传入库 + 安装到站点 + 删除。
// 包是站点无关制品——安装到任意站点零重传；作为「设计›主题」页的包库页签挂载，
// 页签可见性 theme:package（D26 面板可见性语义不变），安装沿用 theme:install。
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Delete, Plus, Refresh, Upload } from '@element-plus/icons-vue';
import type { UploadFile } from 'element-plus';
import { perm } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { querySiteList } from '@/api/system';
import { queryThemePackageList, uploadThemePackage, installThemePackage, deleteThemePackage, type ThemePackageInfo } from '@/api/themePackage';
import type { ActivateTarget, SeedScope } from '@/api/theme';

defineOptions({
  name: 'ThemePackageList',
});

// siteId：画廊当前选中站点，作为「安装到站点」的缺省目标；包表本身是全局面，不随站点过滤
const props = defineProps<{ siteId?: string | number }>();
// installed：装包成功后通知画廊刷新已装列表（同 MarketplaceGallery 约定）
const emit = defineEmits<{ (e: 'installed'): void }>();

const { t } = useI18n();
const currentSiteStore = useCurrentSiteStore();

const data = ref<ThemePackageInfo[]>([]);
const loading = ref<boolean>(false);
const sites = ref<any[]>([]);

const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await queryThemePackageList();
  } finally {
    loading.value = false;
  }
};
const fetchSites = async () => {
  sites.value = await querySiteList();
};
onMounted(async () => {
  fetchData();
  fetchSites();
});

const sourceTagType = (source: string): 'primary' | 'success' | 'warning' | 'info' => {
  if (source === 'marketplace') return 'primary';
  if (source === 'export') return 'success';
  if (source === 'bundled') return 'warning';
  // upload / registered（目录登记，install-flow §14）→ info
  return 'info';
};

const formatSize = (size: string): string => {
  const bytes = Number(size);
  if (!Number.isFinite(bytes)) return size;
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${bytes} B`;
};

// ----- 上传入库 -----
const uploadVisible = ref<boolean>(false);
const uploadFile = ref<File>();
const uploading = ref<boolean>(false);

const openUpload = () => {
  uploadFile.value = undefined;
  uploadVisible.value = true;
};
// auto-upload=false 时 ElementPlus 只触发 on-change（before-upload 不触发），校验挂在这里
const handleUploadSelect = (file: UploadFile) => {
  const raw = file.raw;
  if (raw == null) return;
  if (!/\.zip$/i.test(raw.name)) {
    ElMessage.error(t('theme.error.notZip'));
    return;
  }
  uploadFile.value = raw;
};
const handleUploadConfirm = async () => {
  if (uploadFile.value == null) return;
  uploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', uploadFile.value);
    await uploadThemePackage(formData);
    uploadVisible.value = false;
    ElMessage.success(t('success'));
    fetchData();
  } finally {
    uploading.value = false;
  }
};

// ----- 安装到站点 -----
const installVisible = ref<boolean>(false);
const installing = ref<boolean>(false);
const installPackage = ref<ThemePackageInfo>();
const installForm = ref<{
  siteId?: string | number;
  themeName: string;
  activate: boolean;
  activateTarget: ActivateTarget;
  seedScope: SeedScope;
}>({ themeName: '', activate: false, activateTarget: 'desktop', seedScope: 'composition' });

const openInstall = (row: ThemePackageInfo) => {
  installPackage.value = row;
  installForm.value = {
    siteId: props.siteId ?? currentSiteStore.currentSiteId ?? sites.value[0]?.id,
    themeName: row.name,
    activate: false,
    activateTarget: 'desktop',
    seedScope: 'composition',
  };
  installVisible.value = true;
};
const handleInstallConfirm = async () => {
  if (installPackage.value == null || installForm.value.siteId == null) return;
  installing.value = true;
  try {
    await installThemePackage({
      packageId: installPackage.value.id,
      siteId: installForm.value.siteId,
      themeName: installForm.value.themeName.trim() || undefined,
      activate: installForm.value.activate ? installForm.value.activateTarget : undefined,
      seedScope: installForm.value.seedScope,
    });
    installVisible.value = false;
    ElMessage.success(t('success'));
    emit('installed');
  } finally {
    installing.value = false;
  }
};

// ----- 删除 -----
const handleDelete = async (row: ThemePackageInfo) => {
  await deleteThemePackage(row.id);
  ElMessage.success(t('success'));
  fetchData();
};
</script>

<template>
  <div>
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <el-button :disabled="perm('theme:package')" type="primary" :icon="Upload" @click="openUpload">
          {{ $t('themePackage.op.upload') }}
        </el-button>
        <el-button :icon="Refresh" @click="fetchData">{{ $t('refresh') }}</el-button>
      </div>
      <span class="text-xs text-gray-500">{{ $t('themePackage.tip') }}</span>
    </div>
    <div class="app-block">
      <el-table v-loading="loading" :data="data">
        <el-table-column property="name" :label="$t('themePackage.name')" min-width="140" show-overflow-tooltip></el-table-column>
        <el-table-column property="version" :label="$t('themePackage.version')" width="110" show-overflow-tooltip></el-table-column>
        <el-table-column property="source" :label="$t('themePackage.source')" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceTagType(row.source)">{{ $t(`themePackage.source.${row.source}`) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column property="marketplaceSlug" :label="$t('themePackage.slug')" min-width="120" show-overflow-tooltip></el-table-column>
        <el-table-column property="size" :label="$t('themePackage.size')" width="100" align="right">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column property="sha256" :label="$t('themePackage.sha256')" width="140">
          <template #default="{ row }">
            <el-tooltip :content="row.sha256" placement="top">
              <span class="font-mono text-xs">{{ row.sha256.substring(0, 12) }}…</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column property="created" :label="$t('themePackage.created')" width="170" show-overflow-tooltip></el-table-column>
        <el-table-column :label="$t('table.action')" width="160">
          <template #default="{ row }">
            <el-button type="primary" size="small" link :disabled="perm('theme:install')" @click="() => openInstall(row as ThemePackageInfo)">
              {{ $t('themePackage.op.install') }}
            </el-button>
            <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(row as ThemePackageInfo)">
              <template #reference>
                <el-button type="danger" size="small" link :disabled="perm('theme:package')" :icon="Delete">{{ $t('delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <div class="py-3 text-gray-400">{{ $t('themePackage.empty') }}</div>
        </template>
      </el-table>
    </div>

    <!-- 上传入库：只入库不安装（安装是独立动作） -->
    <el-dialog v-model="uploadVisible" :title="$t('themePackage.op.upload')" width="480" :close-on-click-modal="false">
      <el-alert :title="$t('themePackage.upload.tip')" type="info" :closable="false" class="mb-3" />
      <el-upload :on-change="handleUploadSelect" :auto-upload="false" :show-file-list="false" accept=".zip" drag>
        <div class="flex flex-col items-center px-4 py-6">
          <el-icon class="text-4xl text-primary"><Plus /></el-icon>
          <div class="mt-2">{{ uploadFile?.name ?? $t('theme.zip.placeholder') }}</div>
        </div>
      </el-upload>
      <template #footer>
        <el-button @click="uploadVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="uploading" :disabled="uploadFile == null" @click="handleUploadConfirm">
          {{ $t('themePackage.op.upload') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 安装到站点：同一包装到任意站点，零重传（§6） -->
    <el-dialog v-model="installVisible" :title="$t('themePackage.op.install')" width="540" :close-on-click-modal="false">
      <el-form label-width="120">
        <el-form-item :label="$t('themePackage.name')">
          <span>{{ installPackage?.name }}@{{ installPackage?.version }}</span>
        </el-form-item>
        <el-form-item :label="$t('theme.site')" required>
          <el-select v-model="installForm.siteId" filterable class="w-full">
            <el-option v-for="s in sites" :key="s.id" :value="s.id" :label="s.name" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('theme.themeName')" required>
          <el-input v-model="installForm.themeName" maxlength="50" />
        </el-form-item>
        <el-form-item :label="$t('theme.seed.import')">
          <el-radio-group v-model="installForm.seedScope">
            <el-radio value="all">{{ $t('theme.seed.scope.all') }}</el-radio>
            <el-radio value="composition">{{ $t('theme.seed.scope.composition') }}</el-radio>
            <el-radio value="none">{{ $t('theme.seed.scope.none') }}</el-radio>
          </el-radio-group>
          <div class="text-xs text-gray-500">{{ $t('theme.seed.import.tip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('theme.install.activate')">
          <el-switch v-model="installForm.activate" />
        </el-form-item>
        <el-form-item v-if="installForm.activate" :label="$t('theme.activate.target')">
          <el-radio-group v-model="installForm.activateTarget">
            <el-radio value="desktop">{{ $t('theme.activate.target.desktop') }}</el-radio>
            <el-radio value="mobile">{{ $t('theme.activate.target.mobile') }}</el-radio>
            <el-radio value="both">{{ $t('theme.activate.target.both') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="installVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="installing" :disabled="installForm.siteId == null || !installForm.themeName.trim()" @click="handleInstallConfirm">
          {{ $t('themePackage.op.install') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
