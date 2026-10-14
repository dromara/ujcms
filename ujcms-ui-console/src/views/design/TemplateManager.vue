<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue';
import { onBeforeRouteLeave, useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { queryCurrentSite } from '@/api/system';
import { queryThemeList, type ThemeInfo } from '@/api/theme';
import { queryPreviewUrls } from '@/api/composition';
import { newTabPreviewBase } from '@/composables/usePreviewBridge';
import { openThemePreviewAt } from '@/views/design/themeUi';
import {
  uploadWebFileTemplateUrl,
  uploadZipWebFileTemplateUrl,
  queryWebFileTemplate,
  downloadZipWebFileTemplate,
  createWebFileTemplate,
  mkdirWebFileTemplate,
  updateWebFileTemplate,
  renameWebFileTemplate,
  copyWebFileTemplate,
  moveWebFileTemplate,
  deleteWebFileTemplate,
  queryWebFileTemplateList,
} from '@/api/file';
import WebFileList from '@/views/file/WebFileList.vue';
import TemplateWorkbench from './template/TemplateWorkbench.vue';
import { rootBadge, type RootBadge } from './template/dirRoles';

/**
 * 设计›模板（template-manager P2）：主题上下文的模板管理外壳。
 * 文件操作核心（浏览/编辑/改名/复制移动/ZIP 上下行/历史）全部来自共享的 WebFileList；
 * 本组件只负责模板专属语义——主题选择、渲染模式门控、原始目录进阶模式与模板预览。
 */
defineOptions({
  name: 'TemplateManager',
});

const { t } = useI18n();
const route = useRoute();

const site = ref<any>();
const themes = ref<ThemeInfo[]>([]);
const selectedTheme = ref<string>();
// 原始目录（globalPermission 专属）：整树浏览 /error/…、跨站子树等非常规路径，替代旧「全局数据」姿态
const rawMode = ref<boolean>(false);
const ready = ref<boolean>(false);

// 主题上下文浏览根：/{siteId}/{theme}（Site.getThemeKey 同构）——操作员不接触站点 ID
const browseRoot = computed<string>(() => (site.value != null && selectedTheme.value ? `/${site.value.id}/${selectedTheme.value}` : ''));
const selectedThemeInfo = computed<ThemeInfo | undefined>(() => themes.value.find((item) => item.themeName === selectedTheme.value));

// 深链（/design/template?theme={name}，主题画廊/详情的「模板」入口）：命中已装主题即选中
const applyThemeQuery = () => {
  const name = route.query.theme;
  if (typeof name === 'string' && name !== '' && themes.value.some((item) => item.themeName === name)) {
    selectedTheme.value = name;
    rawMode.value = false;
  }
};

onMounted(async () => {
  site.value = await queryCurrentSite();
  if (!perm('theme:list')) {
    try {
      themes.value = await queryThemeList(site.value.id);
    } catch {
      themes.value = [];
    }
  }
  if (themes.value.length <= 0) {
    // 无 theme:list 权限（webFileTemplate:* 单独授权）时退化为站点活动主题可选，能力面不缩水
    const fallback = [site.value.theme, site.value.mobileTheme].filter((name: string, index: number, arr: string[]) => name && arr.indexOf(name) === index);
    themes.value = fallback.map(
      (name: string) => ({ themeName: name, displayName: name, active: name === site.value.theme, activeMobile: name === site.value.mobileTheme }) as ThemeInfo,
    );
  }
  selectedTheme.value = themes.value.find((item) => item.active)?.themeName ?? themes.value[0]?.themeName;
  applyThemeQuery();
  ready.value = true;
});
onActivated(() => {
  if (ready.value) {
    applyThemeQuery();
  }
});

const themeOptionLabel = (theme: ThemeInfo): string => (theme.displayName && theme.displayName !== theme.themeName ? `${theme.displayName} (${theme.themeName})` : theme.themeName);

// 主题根条目的角色标签与开发目录/文件去强调（判定逻辑在 template/dirRoles.ts，与工作台文件树共用）：
// 仅主题根直下、非原始目录模式
const rowBadge = (row: any): RootBadge | undefined => (rawMode.value ? undefined : rootBadge(row, browseRoot.value));

// ----- 编辑工作台（template-manager P3）：浏览 ↔ 工作台同路由内切换，表格仍是管理面 -----
const workbenchFile = ref<any>(null);
const workbenchRef = ref<any>();
const webFileListRef = ref<any>();
// WebFileList 文件打开拦截：可编辑文本文件进工作台；其余（图片/ZIP 等）返回 false 走原编辑对话框
const handleOpenFile = (row: any): boolean => {
  if (row.editable !== true) {
    return false;
  }
  workbenchFile.value = row;
  return true;
};
const handleWorkbenchClose = () => {
  workbenchFile.value = null;
  // 工作台内的保存/恢复改变 lastModified，返回时刷新列表
  webFileListRef.value?.refresh?.();
};
// 路由离开守卫（Builder 先例）：工作台有未保存改动时确认
onBeforeRouteLeave(async () => {
  if (workbenchRef.value?.isDirty?.() !== true) {
    return true;
  }
  try {
    await ElMessageBox.confirm(t('webFile.template.leaveConfirm'), { type: 'warning' });
    return true;
  } catch {
    return false;
  }
});

// ----- 模板预览（preview-url-resolver P3）：主题与目录为外壳显式上下文，仅 templates/ 直下的 .html -----
// 解析端点权限面：pageRegion:show 或 theme:list 皆放行（两者俱无才禁用）
const previewDenied = computed<boolean>(() => perm('pageRegion:show') && perm('theme:list'));
const previewingId = ref<string>();
const templateNameOf = (row: any): string | undefined => {
  if (rawMode.value || row.fileType === 'DIRECTORY' || !browseRoot.value) return undefined;
  const prefix = `${browseRoot.value}/templates/`;
  const id = String(row.id ?? '');
  if (!id.startsWith(prefix) || !id.endsWith('.html')) return undefined;
  const name = id.slice(prefix.length, -'.html'.length);
  return name.includes('/') ? undefined : name;
};
const handlePreview = async (row: any) => {
  const template = templateNameOf(row);
  if (template == null || selectedTheme.value == null) return;
  previewingId.value = row.id;
  try {
    const result = await queryPreviewUrls({ template, limit: 1 });
    const path = result.candidates[0]?.url;
    if (path == null) {
      // 无候选：非页面模板（布局变体未被任何实体命中 / 表单模板无数据行等）
      ElMessage.info(t('webFile.preview.noCandidate'));
      return;
    }
    if (selectedThemeInfo.value?.active !== true) {
      // 非活动主题：以 ?_theme= 候选主题渲染试看打开（需预览令牌）
      try {
        await openThemePreviewAt(selectedTheme.value, path);
      } catch {
        ElMessage.error(t('theme.preview.tokenError'));
      }
    } else {
      window.open(`${newTabPreviewBase}${path}`, '_blank');
    }
  } finally {
    previewingId.value = undefined;
  }
};
</script>

<template>
  <div>
    <template v-if="ready">
      <!-- 浏览模式（管理面，P2 原样保留）：工作台打开时仅隐藏不卸载，返回即恢复浏览状态 -->
      <div v-show="workbenchFile == null">
        <div class="mb-3 flex items-center gap-2">
          <template v-if="!rawMode">
            <span class="font-medium">{{ $t('webFile.template.theme') }}</span>
            <el-select v-model="selectedTheme" filterable style="width: 280px">
              <el-option v-for="item in themes" :key="item.themeName" :value="item.themeName" :label="themeOptionLabel(item)">
                <span>{{ themeOptionLabel(item) }}</span>
                <el-tag v-if="item.active" type="success" size="small" class="ml-2">{{ $t('theme.active.desktop') }}</el-tag>
                <el-tag v-if="item.activeMobile" type="warning" size="small" class="ml-1">{{ $t('theme.active.mobile') }}</el-tag>
              </el-option>
            </el-select>
          </template>
          <el-checkbox v-if="currentUser.globalPermission" v-model="rawMode" class="!ml-auto" :label="$t('webFile.template.rawMode')" :border="true" />
        </div>
        <web-file-list
          v-if="rawMode || browseRoot"
          ref="webFileListRef"
          :key="rawMode ? '/' : browseRoot"
          type="Template"
          :open-file="handleOpenFile"
          :form-name="$t('menu.design.template')"
          :base-id="rawMode ? '/' : browseRoot"
          :breadcrumb-root="rawMode ? undefined : selectedTheme"
          :upload-web-file-url="uploadWebFileTemplateUrl"
          :upload-zip-web-file-url="uploadZipWebFileTemplateUrl"
          :query-web-file="queryWebFileTemplate"
          :download-zip-web-file="downloadZipWebFileTemplate"
          :create-web-file="createWebFileTemplate"
          :mkdir-web-file="mkdirWebFileTemplate"
          :update-web-file="updateWebFileTemplate"
          :rename-web-file="renameWebFileTemplate"
          :move-web-file="moveWebFileTemplate"
          :copy-web-file="copyWebFileTemplate"
          :delete-web-file="deleteWebFileTemplate"
          :query-web-file-list="queryWebFileTemplateList"
          :history="true"
        >
          <template #row-tag="{ row }">
            <el-tag v-if="rowBadge(row)" size="small" type="info" effect="plain" class="ml-2" :class="{ 'opacity-60': rowBadge(row)?.dev }">
              {{ $t(rowBadge(row)!.key) }}
            </el-tag>
          </template>
          <template #row-actions="{ row }">
            <el-button v-if="templateNameOf(row)" type="primary" :disabled="previewDenied" :loading="previewingId === row.id" size="small" link @click="() => handlePreview(row)">
              {{ $t('webFile.op.preview') }}
            </el-button>
          </template>
        </web-file-list>
      </div>
      <!-- 编辑工作台（P3）：文件树 + 全高编辑器；编辑/预览/历史/对比，管理操作留在浏览模式 -->
      <template-workbench
        v-if="workbenchFile != null"
        ref="workbenchRef"
        :root="rawMode ? '/' : browseRoot"
        :root-label="rawMode ? '/' : (selectedTheme ?? '/')"
        :structured="!rawMode"
        :initial-file="workbenchFile"
        :template-name-of="templateNameOf"
        :preview="handlePreview"
        :preview-disabled="previewDenied"
        :previewing-id="previewingId"
        @close="() => handleWorkbenchClose()"
      />
    </template>
  </div>
</template>
