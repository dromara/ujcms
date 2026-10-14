<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Upload } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import type { UploadFile, UploadUserFile } from 'element-plus';
import { perm } from '@/stores/useCurrentUser';
import { acceptToText, isAcceptedExtension } from '@/utils/upload';
import { enumParam, stringParam, useRouteState } from '@/composables/useRouteState';
import {
  deleteWechatMaterial,
  downloadWechatMaterial,
  queryWechatAccountList,
  queryWechatMaterialCount,
  queryWechatMaterialList,
  queryWechatMaterialVideo,
  uploadWechatMaterial,
} from '@/api/wechat';

defineOptions({
  name: 'WechatMaterialList',
});
const { t } = useI18n();
/** 微信永久素材的格式与大小限制：图片 10MB、语音 2MB（时长 60 秒内）、视频 10MB */
const IMAGE_RULE = { accept: '.bmp,.png,.jpeg,.jpg,.gif', maxSize: 10 * 1024 * 1024 };
const UPLOAD_RULES: Record<string, { accept: string; maxSize: number }> = {
  image: IMAGE_RULE,
  voice: { accept: '.mp3,.wma,.wav,.amr', maxSize: 2 * 1024 * 1024 },
  video: { accept: '.mp4', maxSize: 10 * 1024 * 1024 },
};
const accountList = ref<any[]>([]);
// 当前公众号存于 query.accountId（docs/spec/console-view-state），默认首个公众号：视图在刷新、页签往返时都会重建，
// 不记住就会悄悄回到首个公众号，运营者可能在错误的公众号上继续操作
const { accountId, type } = useRouteState({
  accountId: stringParam(() => accountList.value[0]?.id),
  type: enumParam('image', ['image', 'voice', 'video', 'news']),
});
const currentPage = ref<number>(1);
const total = ref<number>(0);
const counts = ref<any>({});
const data = ref<any[]>([]);
const loading = ref<boolean>(false);

const uploadVisible = ref<boolean>(false);
const uploadForm = ref<any>();
const uploadValues = ref<any>({});
const uploadFiles = ref<UploadUserFile[]>([]);
const buttonLoading = ref<boolean>(false);
const uploadRule = computed(() => UPLOAD_RULES[type.value] ?? IMAGE_RULE);
const uploadTip = computed(() => t(`wechatMaterial.tip.${type.value}`, { formats: acceptToText(uploadRule.value.accept), size: `${uploadRule.value.maxSize / 1024 / 1024} MB` }));

const previewUrl = ref<string>();
const videoVisible = ref<boolean>(false);
const videoInfo = ref<any>();

const fetchData = async () => {
  if (accountId.value == null) {
    data.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await queryWechatMaterialList({ accountId: accountId.value, type: type.value, page: currentPage.value });
    data.value = result.items ?? [];
    total.value = Number(result.totalCount ?? 0);
  } finally {
    loading.value = false;
  }
};
const fetchCount = async () => {
  if (accountId.value == null) {
    counts.value = {};
    return;
  }
  counts.value = await queryWechatMaterialCount(accountId.value);
};
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
  if (accountList.value.length > 0) {
    // 恢复出的公众号须仍在列表中（切换站点后整页重载而地址不变、公众号已删除），否则回落到首个
    if (!accountList.value.some((item) => item.id === accountId.value)) {
      accountId.value = accountList.value[0].id;
    }
    fetchData();
    fetchCount();
  }
};
onMounted(fetchAccountList);

const handleAccountChange = () => {
  currentPage.value = 1;
  fetchData();
  fetchCount();
};
const handleTypeChange = () => {
  currentPage.value = 1;
  fetchData();
};

const closePreview = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = undefined;
  }
};
onBeforeUnmount(closePreview);
const blobErrorMessage = async (error: any): Promise<string> => {
  const body = error?.response?.data;
  if (body instanceof Blob) {
    try {
      const message = JSON.parse(await body.text())?.message;
      if (message) return message;
    } catch {
      // 非 JSON 响应，使用默认错误信息
    }
  }
  return error?.message ?? String(error);
};
// 微信素材 URL 设有防盗链，<img> 带 Referer 只能得到占位图。大图经后端按 MediaID 取回，以 blob 显示
const handlePreview = async (row: any) => {
  if (accountId.value == null) return;
  loading.value = true;
  try {
    const blob = await downloadWechatMaterial({ accountId: accountId.value, mediaId: row.mediaId });
    closePreview();
    previewUrl.value = URL.createObjectURL(blob);
  } catch (error: any) {
    ElMessage.error(await blobErrorMessage(error));
  } finally {
    loading.value = false;
  }
};

// 微信素材列表不返回视频描述，按需调用“获取永久素材”接口查询，避免列表加载时逐条调用消耗接口额度
const handleVideoDetail = async (row: any) => {
  if (accountId.value == null) return;
  loading.value = true;
  try {
    videoInfo.value = await queryWechatMaterialVideo({ accountId: accountId.value, mediaId: row.mediaId });
    videoVisible.value = true;
  } finally {
    loading.value = false;
  }
};

const handleUpload = () => {
  uploadValues.value = {};
  uploadFiles.value = [];
  uploadVisible.value = true;
};
// accept 属性只过滤“选择文件”对话框，仍需按扩展名和大小校验，不符合要求的文件直接从列表移除
const handleFileChange = (file: UploadFile) => {
  if (file.status !== 'ready') return;
  const { accept, maxSize } = uploadRule.value;
  let message: string | undefined;
  if (!isAcceptedExtension(file.name, accept)) {
    message = t('error.fileType', { types: acceptToText(accept) });
  } else if ((file.size ?? 0) > maxSize) {
    message = t('error.fileMaxSize', { size: `${maxSize / 1024 / 1024} MB` });
  }
  if (message) {
    ElMessage.error(`${file.name}: ${message}`);
    uploadFiles.value = uploadFiles.value.filter((item) => item.uid !== file.uid);
  }
};
const handleUploadSubmit = async () => {
  if (accountId.value == null || uploadFiles.value.length === 0) return;
  await uploadForm.value?.validate();
  buttonLoading.value = true;
  let uploaded = 0;
  try {
    // 微信接口每次只接收一个文件，按选择顺序逐个上传；失败时停止，未上传的文件保留在列表中以便重试
    const files = uploadFiles.value;
    for (const file of files) {
      if (file.raw == null) continue;
      const formData = new FormData();
      formData.append('accountId', accountId.value);
      formData.append('type', type.value);
      formData.append('file', file.raw);
      if (uploadValues.value.title) formData.append('title', uploadValues.value.title);
      if (uploadValues.value.introduction) formData.append('introduction', uploadValues.value.introduction);
      await uploadWechatMaterial(formData);
      uploadFiles.value = uploadFiles.value.filter((item) => item.uid !== file.uid);
      uploaded += 1;
    }
    uploadVisible.value = false;
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
    if (uploaded > 0) {
      fetchData();
      fetchCount();
    }
  }
};
const handleDelete = async (mediaId: string) => {
  if (accountId.value == null) return;
  await deleteWechatMaterial({ accountId: accountId.value, mediaId });
  fetchData();
  fetchCount();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" :placeholder="$t('wechat.selectAccount')" class="w-48" @change="() => handleAccountChange()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-button type="primary" :disabled="accountId == null || type === 'news' || perm('wechatMaterial:upload')" :icon="Upload" @click="() => handleUpload()">
        {{ $t('wechatMaterial.op.upload') }}
      </el-button>
    </div>
    <el-alert v-if="accountList.length <= 0" :title="$t('wechat.noAccount')" type="info" :closable="false" class="mt-3" />
    <div class="mt-3 app-block">
      <el-tabs v-model="type" class="px-3" @tab-change="() => handleTypeChange()">
        <el-tab-pane v-for="item in ['image', 'voice', 'video', 'news']" :key="item" :name="item">
          <template #label>
            {{ $t(`wechatMaterial.type.${item}`) }}
            <span v-if="counts[item] != null" class="text-xs text-gray-secondary">({{ counts[item] }})</span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <!-- el-alert 宽度为 100%，外层加内边距使其与页签标题左右对齐 -->
      <div v-if="type === 'news'" class="px-3 pb-3">
        <el-alert :title="$t('wechatMaterial.newsTip')" type="info" :closable="false" show-icon />
      </div>
      <el-table v-loading="loading" :row-key="(row: any) => row.mediaId" :data="data">
        <el-table-column v-if="type === 'image'" :label="$t('wechatMaterial.preview')" width="120">
          <template #default="{ row }">
            <el-image :src="row.url" fit="contain" class="w-20 h-20 cursor-pointer" referrerpolicy="no-referrer" @click="() => handlePreview(row)" />
          </template>
        </el-table-column>
        <!-- 视频素材在微信中以上传时填写的视频标题作为名称 -->
        <el-table-column
          v-if="type !== 'news'"
          property="name"
          :label="type === 'video' ? $t('wechatMaterial.videoTitle') : $t('wechatMaterial.name')"
          :min-width="200"
          show-overflow-tooltip
        ></el-table-column>
        <el-table-column v-if="type === 'news'" :label="$t('wechatArticle.title')" :min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <a v-if="row.articles?.[0]?.url" :href="row.articles[0].url" target="_blank" class="text-primary">{{ row.articles?.[0]?.title }}</a>
            <span v-else>{{ row.articles?.[0]?.title }}</span>
            <el-tag v-if="(row.articles?.length ?? 0) > 1" size="small" class="ml-1">{{ row.articles.length }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column property="mediaId" label="MediaID" :min-width="220" show-overflow-tooltip></el-table-column>
        <el-table-column property="updateTime" :label="$t('wechatMaterial.updateTime')" width="170">
          <template #default="{ row }">{{ row.updateTime ? dayjs(row.updateTime).format('YYYY-MM-DD HH:mm:ss') : '' }}</template>
        </el-table-column>
        <el-table-column :label="$t('table.action')" :width="type === 'video' ? 130 : 100">
          <template #default="{ row }">
            <el-button v-if="type === 'video'" type="primary" size="small" link @click="() => handleVideoDetail(row)">{{ $t('detail') }}</el-button>
            <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(row.mediaId)">
              <template #reference>
                <el-button type="primary" :disabled="perm('wechatMaterial:delete')" size="small" link>{{ $t('delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        :total="total"
        :page-size="20"
        layout="total, prev, pager, next"
        size="small"
        background
        class="justify-end px-3 py-2"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
    <el-image-viewer v-if="previewUrl" :url-list="[previewUrl]" teleported hide-on-click-modal @close="() => closePreview()" />
    <el-dialog v-model="videoVisible" :title="$t('wechatMaterial.videoDetail')" :width="512">
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="$t('wechatMaterial.videoTitle')">{{ videoInfo?.title }}</el-descriptions-item>
        <el-descriptions-item :label="$t('wechatMaterial.videoIntroduction')">
          <span v-if="videoInfo?.description" class="whitespace-pre-wrap">{{ videoInfo.description }}</span>
          <!-- 微信“获取永久素材”接口对视频返回的 description 为空（2026-09 实测），上传时填写的描述不能读回 -->
          <span v-else class="text-gray-secondary">{{ $t('wechatMaterial.videoDescriptionUnavailable') }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('wechatMaterial.videoFile')">
          <!-- down_url 由微信临时签发，每次打开详情重新获取 -->
          <a v-if="videoInfo?.downUrl" :href="videoInfo.downUrl" target="_blank" rel="noopener noreferrer" class="text-primary">{{ $t('wechatMaterial.op.download') }}</a>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="() => (videoVisible = false)">{{ $t('close') }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="uploadVisible" :title="$t('wechatMaterial.op.upload')" :width="512">
      <el-form ref="uploadForm" :model="uploadValues" label-position="top">
        <el-form-item :label="$t(`wechatMaterial.type.${type}`)">
          <el-upload
            v-model:file-list="uploadFiles"
            class="w-full"
            :auto-upload="false"
            :accept="uploadRule.accept"
            :multiple="type !== 'video'"
            :limit="type === 'video' ? 1 : undefined"
            :on-change="handleFileChange"
          >
            <el-button>{{ $t('wechatMaterial.selectFile') }}</el-button>
            <template #tip>
              <div class="el-upload__tip">{{ uploadTip }}</div>
            </template>
          </el-upload>
        </el-form-item>
        <template v-if="type === 'video'">
          <el-form-item prop="title" :label="$t('wechatMaterial.videoTitle')" :rules="{ required: true, message: () => $t('v.required') }">
            <el-input v-model="uploadValues.title" maxlength="100"></el-input>
          </el-form-item>
          <el-form-item prop="introduction" :label="$t('wechatMaterial.videoIntroduction')">
            <el-input v-model="uploadValues.introduction" type="textarea" :rows="2" maxlength="200"></el-input>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="buttonLoading" :disabled="uploadFiles.length === 0" @click="() => handleUploadSubmit()">{{ $t('save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
