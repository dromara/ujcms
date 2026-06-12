<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { toTree } from '@/utils/tree';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import {
  querySiteSettings,
  updateSiteBaseSettings,
  updateSiteWatermarkSettings,
  updateSiteEditorSettings,
  updateSiteMessageBoardSettings,
  updateSiteCustomsSettings,
  queryCurrentSiteThemeList,
  queryModelList,
} from '@/api/config';
import { queryOrgList } from '@/api/user';
import { perm, hasPermission, currentUser } from '@/stores/useCurrentUser';
import LabelTip from '@/components/LabelTip.vue';
import { ImageUpload } from '@/components/Upload';
import FieldItem from '@/views/config/components/FieldItem.vue';

defineOptions({
  name: 'SiteSettings',
});
defineEmits({ 'update:modelValue': null, finished: null });

const { t } = useI18n();
const currentSiteStore = useCurrentSiteStore();
const form = ref<any>();
const site = ref<any>();
const values = ref<any>({});
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const orgList = ref<any[]>([]);
const themeList = ref<string[]>([]);
const modelList = ref<any[]>([]);
const modelId = ref<string>();
const model = computed(() => modelList.value.find((item) => item.id === modelId.value));
const fields = computed(() => JSON.parse(model.value?.customs || '[]'));

const types: string[] = [];
if (hasPermission('siteSettings:base:update')) {
  types.push('base');
}
if (hasPermission('siteSettings:watermark:update')) {
  types.push('watermark');
}
if (hasPermission('siteSettings:editor:update') && (currentUser.epRank >= 2 || currentUser.epDisplay)) {
  types.push('editor');
}
if (hasPermission('siteSettings:messageBoard:update')) {
  types.push('messageBoard');
}
if (hasPermission('siteSettings:customs:update')) {
  types.push('customs');
}
const type = ref<string>(types[0]);

const switchType = () => {
  if (type.value === 'watermark') {
    values.value = site.value.watermark;
  } else if (type.value === 'editor') {
    values.value = site.value.editor;
    if (values.value.typesetting == null) {
      values.value.typesetting = {
        fontFamily: '',
        fontSize: '',
        lineHeight: '',
        textIndent: false,
        imageCenterAlign: true,
        tableFullWidth: true,
        emptyLine: 'one',
        halfWidthCharConversion: true,
      };
    }
  } else if (type.value === 'messageBoard') {
    values.value = site.value.messageBoard;
  } else if (type.value === 'customs') {
    values.value = site.value.customs;
  } else {
    values.value = site.value;
  }
};

watch(type, () => switchType());

// const tabClick = (paneName?: string | number) => {};

const fetchThemeList = async () => {
  themeList.value = await queryCurrentSiteThemeList();
};
const fetchOrgList = async () => {
  orgList.value = toTree(await queryOrgList());
};
const fetchModelList = async () => {
  modelList.value = await queryModelList({ type: 'site' });
};
const fetchSiteSetting = async () => {
  site.value = await querySiteSettings();
  modelId.value = site.value.modelId;
};
onMounted(async () => {
  loading.value = true;
  try {
    await Promise.all([fetchThemeList(), fetchOrgList(), fetchModelList(), fetchSiteSetting()]);
    switchType();
  } finally {
    loading.value = false;
  }
});
const load = async () => {
  loading.value = true;
  try {
    await fetchSiteSetting();
  } finally {
    loading.value = false;
  }
};
const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      if (type.value === 'watermark') {
        await updateSiteWatermarkSettings(values.value);
      } else if (type.value === 'editor') {
        await updateSiteEditorSettings(values.value);
      } else if (type.value === 'messageBoard') {
        await updateSiteMessageBoardSettings(values.value);
      } else if (type.value === 'customs') {
        await updateSiteCustomsSettings(values.value);
      } else {
        await updateSiteBaseSettings(values.value);
      }
      currentSiteStore.currentSite = await querySiteSettings();
      ElMessage.success(t('success'));
    } finally {
      buttonLoading.value = false;
    }
    load();
  });
};
</script>

<template>
  <el-container>
    <el-aside width="180px" class="pr-3">
      <el-tabs v-model="type" tab-position="left" stretch class="bg-white">
        <el-tab-pane v-for="tp in types" :key="tp" :name="tp" :label="$t(`site.settings.${tp}`)"></el-tab-pane>
      </el-tabs>
    </el-aside>
    <el-main class="p-3 app-block">
      <el-form ref="form" v-loading="loading" :model="values" label-width="150px">
        <template v-if="type === 'watermark'">
          <el-row>
            <el-col :span="24">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.watermark.enabled" help /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="overlay" :label="$t('site.watermark.overlay')" :rules="values.enabled ? { required: true, message: () => $t('v.required') } : {}">
                <image-upload v-model="values.overlay"></image-upload>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="position" :label="$t('site.watermark.position')" :rules="{ required: true, message: () => $t('v.required') }">
                <el-radio-group v-model="values.position">
                  <div class="watermark-position">
                    <el-radio v-for="n in 9" :key="n" :value="n" :title="$t(`site.watermark.position.${n}`)"><span></span></el-radio>
                  </div>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="dissolve" :label="$t('site.watermark.dissolve')" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.watermark.dissolve" help /></template>
                <el-slider v-model="values.dissolve" :min="0" :max="100" show-input></el-slider>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="minWidth" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.watermark.minWidth" help /></template>
                <el-input v-model.number="values.minWidth" :min="1" :max="65535"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="minHeight" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.watermark.minHeight" help /></template>
                <el-input v-model.number="values.minHeight" :min="1" :max="65535"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'editor' && currentUser.epRank < 2">
          <el-alert type="warning" :closable="false" :show-icon="true">
            <!-- eslint-disable-next-line vue/no-v-html -->
            <template #title><span v-html="$t('error.enterprise')"></span></template>
          </el-alert>
        </template>
        <template v-else-if="type === 'editor' && currentUser.epRank >= 2">
          <el-divider>{{ $t('site.editor.typesetting') }}</el-divider>
          <el-row>
            <el-col :span="12">
              <el-form-item prop="typesetting.fontFamily">
                <template #label><label-tip message="site.editor.typesetting.fontFamily" /></template>
                <el-select v-model="values.typesetting.fontFamily">
                  <el-option
                    v-for="item in [
                      { value: '', label: '默认' },
                      { value: 'SimSun', label: '宋体' },
                      { value: 'Microsoft YaHei', label: '微软雅黑' },
                      { value: 'SimKai,KaiTi', label: '楷体' },
                      { value: 'SimHei', label: '黑体' },
                      { value: 'SimLi,LiSu', label: '隶书' },
                      { value: 'andale mono,times', label: 'Andale Mono' },
                      { value: 'arial,helvetica,sans-serif', label: 'Arial' },
                      { value: 'arial black,avant garde', label: 'Arial Black' },
                      { value: 'comic sans ms,sans-serif', label: 'Comic Sans MS' },
                      { value: 'helvetica', label: 'Helvetica' },
                      { value: 'impact,chicago', label: 'Impact' },
                      { value: 'times new roman,times', label: 'Times New Roman' },
                    ]"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.fontSize">
                <template #label><label-tip message="site.editor.typesetting.fontSize" /></template>
                <el-select v-model="values.typesetting.fontSize">
                  <el-option
                    v-for="item in [
                      { value: '', label: '默认' },
                      { value: '12px', label: '小五' },
                      { value: '14px', label: '五号' },
                      { value: '16px', label: '小四' },
                      { value: '18px', label: '四号' },
                      { value: '20px', label: '小三' },
                      { value: '22px', label: '三号' },
                      { value: '24px', label: '小二' },
                      { value: '28px', label: '二号' },
                    ]"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.lineHeight">
                <template #label><label-tip message="site.editor.typesetting.lineHeight" /></template>
                <el-select v-model="values.typesetting.lineHeight">
                  <el-option
                    v-for="item in [
                      { value: '', label: '默认' },
                      { value: '1.0', label: '1.0' },
                      { value: '1.1', label: '1.1' },
                      { value: '1.2', label: '1.2' },
                      { value: '1.3', label: '1.3' },
                      { value: '1.4', label: '1.4' },
                      { value: '1.5', label: '1.5' },
                      { value: '2.0', label: '2.0' },
                    ]"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.emptyLine">
                <template #label><label-tip message="site.editor.typesetting.emptyLine" /></template>
                <el-select v-model="values.typesetting.emptyLine">
                  <el-option
                    v-for="item in [
                      { value: 'remain', label: '保留空行' },
                      { value: 'one', label: '合并为一行' },
                      { value: 'remove', label: '删除空行' },
                    ]"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.textIndent">
                <template #label><label-tip message="site.editor.typesetting.textIndent" /></template>
                <el-switch v-model="values.typesetting.textIndent" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.imageCenterAlign">
                <template #label><label-tip message="site.editor.typesetting.imageCenterAlign" /></template>
                <el-switch v-model="values.typesetting.imageCenterAlign" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.tableFullWidth">
                <template #label><label-tip message="site.editor.typesetting.tableFullWidth" /></template>
                <el-switch v-model="values.typesetting.tableFullWidth" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="typesetting.halfWidthCharConversion">
                <template #label><label-tip message="site.editor.typesetting.halfWidthCharConversion" /></template>
                <el-switch v-model="values.typesetting.halfWidthCharConversion" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'messageBoard'">
          <el-row>
            <el-col :span="12">
              <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.messageBoard.enabled" /></template>
                <el-switch v-model="values.enabled"></el-switch>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="loginRequired" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.messageBoard.loginRequired" /></template>
                <el-switch v-model="values.loginRequired"></el-switch>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="type === 'customs'">
          <el-row>
            <el-col v-for="field in fields" :key="field.code" :span="field.double ? 12 : 24">
              <el-form-item :prop="field.code" :rules="field.required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="field.name" /></template>
                <field-item v-model="values[field.code]" v-model:model-key="values[field.code + 'Key']" :field="field"></field-item>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else>
          <el-row>
            <el-col :span="12">
              <el-form-item prop="name" :label="$t('site.name')" :rules="{ required: true, message: () => $t('v.required') }">
                <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="protocol" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.protocol" help /></template>
                <el-select v-model="values.protocol" class="w-full">
                  <el-option v-for="item in ['http', 'https']" :key="item" :label="item" :value="item"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="domain"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  { pattern: /^[a-z0-9-.]*$/, message: () => $t('site.error.domainPattern') },
                  { pattern: /^(?!(uploads|templates|WEB-INF|cp)$).*/i, message: () => $t('site.error.excludesPattern') },
                ]"
              >
                <template #label><label-tip message="site.domain" help /></template>
                <el-input v-model="values.domain" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                prop="subDir"
                :rules="[
                  { pattern: /^[\w-]*$/, message: () => $t('site.error.subDirPattern') },
                  { pattern: /^(?!(uploads|templates|WEB-INF|cp)$).*/i, message: () => $t('site.error.excludesPattern') },
                ]"
              >
                <template #label><label-tip message="site.subDir" help /></template>
                <el-input v-model="values.subDir" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="theme" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.theme" help /></template>
                <el-select v-model="values.theme" class="w-full">
                  <el-option v-for="item in themeList" :key="item" :label="item" :value="item"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="mobileTheme" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.mobileTheme" help /></template>
                <el-select v-model="values.mobileTheme" class="w-full">
                  <el-option v-for="item in themeList" :key="item" :label="item" :value="item"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="modelId" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.model" help /></template>
                <el-select v-model="values.modelId" class="w-full">
                  <el-option v-for="item in modelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="logo">
                <template #label><label-tip message="site.logo" help /></template>
                <image-upload v-model="values.logo"></image-upload>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="seoTitle">
                <template #label><label-tip message="site.seoTitle" help /></template>
                <el-input v-model="values.seoTitle" maxlength="150"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="seoKeywords">
                <template #label><label-tip message="site.seoKeywords" help /></template>
                <el-input v-model="values.seoKeywords" maxlength="150"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item prop="seoDescription">
                <template #label><label-tip message="site.seoDescription" help /></template>
                <el-input v-model="values.seoDescription" type="textarea" :rows="3" maxlength="1000"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="pageSize" :rules="{ required: true, message: () => $t('v.required') }">
                <template #label><label-tip message="site.pageSize" help /></template>
                <el-input-number v-model="values.pageSize" :min="1" :max="200"></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item prop="status">
                <template #label><label-tip message="site.status" help /></template>
                <el-radio-group v-model="values.status">
                  <el-radio v-for="n in [0, 1]" :key="n" :value="n">{{ $t(`site.status.${n}`) }}</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <div v-if="type !== 'editor' || currentUser.epRank >= 1">
          <el-button :disabled="perm(`siteSettings:${type}:update`)" :loading="buttonLoading" type="primary" native-type="submit" @click.prevent="handleSubmit">
            {{ $t('save') }}
          </el-button>
        </div>
      </el-form>
    </el-main>
  </el-container>
</template>

<style lang="scss" scoped>
.el-tabs {
  :deep(.el-tabs__header) {
    margin-right: 1px;
  }
  :deep(.el-tabs__content) {
    flex-grow: 0;
  }
}
.watermark-position {
  @apply bg-gray-100 w-36 h-36 flex flex-wrap rounded border border-dashed;
  :deep(.el-radio__label) {
    padding-left: 0;
  }
  :deep(.el-radio) {
    margin-right: 0;
    @apply w-1/3 h-1/3 flex justify-center items-center;
  }
}
</style>
