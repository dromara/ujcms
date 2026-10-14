<script setup lang="ts">
import { computed, watch, ref, toRefs, onMounted, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { toTree, disableSubtree } from '@/utils/tree';
import { querySite, createSite, updateSite, deleteSite, querySiteList, querySiteThemeList } from '@/api/system';
import { queryModelList, queryDictListByAlias } from '@/api/config';
import { queryOrgList } from '@/api/user';
import { hasPermission } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import { ImageUpload } from '@/components/Upload';

defineOptions({
  name: 'SiteForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  parentId: { type: String, default: null },
  showGlobalData: { type: Boolean, required: true },
  // 「新增语言版本」入口传入的源站点行：新增表单预填 语言组/域名/名称/复制来源（multilingual P4）
  variantFrom: { type: Object, default: null },
});
const { showGlobalData, modelValue: visible } = toRefs(props);
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const currentSiteStore = useCurrentSiteStore();
// 编辑的是否当前站点：模型下拉里本站私有的模型只对当前站点可选（AppHeader 写入 currentSite；深链/刷新时回落到持久化的 id）
const editingCurrentSite = computed(() => values.value.id != null && String(values.value.id) === String(currentSiteStore.currentSite?.id ?? currentSiteStore.currentSiteId));
const focus = ref<any>();
const values = ref<any>({});
const siteTree = ref<any[]>([]);
const siteFlatList = ref<any[]>([]);
const parentSiteTree = computed(() => disableSubtree(siteTree.value, values.value.id));
const copyFromSiteTree = ref<any[]>([]);
const copyFromFlatList = ref<any[]>([]);
const orgTree = ref<any[]>([]);
const themeList = ref<string[]>([]);
const modelList = ref<any[]>([]);

// 语言组主站点候选：不属于任何组的站点（排除自身）。变体不可再挂变体（服务端同样拒绝链）
const langMasterCandidates = computed(() => siteFlatList.value.filter((item) => item.langParentId == null && item.id !== values.value.id));
// 语言组内语言唯一：同组（含主站点）已有相同语言 → 提示。服务端会再次校验
const langDuplicateValidator = (rule: any, value: any, callback: any) => {
  const masterId = values.value.langParentId;
  const lang = values.value.lang;
  if (masterId == null || !lang) {
    callback();
    return;
  }
  const duplicated = siteFlatList.value.some((item) => (item.langParentId === masterId || item.id === masterId) && item.lang === lang && item.id !== values.value.id);
  callback(duplicated ? new Error(t('site.error.langDuplicate')) : undefined);
};
// 新增语言版本：选定语言后为空白子目录给出建议值（可修改）。多域名部署可改为填写子域名
watch(
  () => values.value.lang,
  (lang, oldLang) => {
    if (props.variantFrom == null || values.value.id != null) {
      return;
    }
    const subDir = values.value.subDir;
    const next = lang?.toLowerCase();
    // 值没变就不写：写一个 undefined 也会给表单添出一个基线里没有的键，刚打开的对话框就成了“未保存”
    if ((subDir == null || subDir === '' || subDir === oldLang?.toLowerCase()) && subDir !== next) {
      values.value.subDir = next;
    }
  },
);

const fetchSiteList = async () => {
  const list = await querySiteList({ current: !showGlobalData.value });
  siteFlatList.value = [...list];
  siteTree.value = toTree(list);
};
const fetchCopyFromSiteList = async () => {
  const list = await querySiteList({ currentOrg: !showGlobalData.value });
  copyFromFlatList.value = [...list];
  copyFromSiteTree.value = toTree(list);
};
const beanChange = async (bean: any) => {
  themeList.value = bean.id ? await querySiteThemeList(bean.id) : [];
};
const finished = async () => {
  await fetchSiteList();
  emit('finished');
};
const fetchOrgList = async () => {
  orgTree.value = toTree(await queryOrgList({ current: !showGlobalData.value }));
};
const fetchModelList = async () => {
  modelList.value = await queryModelList({ type: 'site' });
};
watch(visible, () => {
  if (visible.value) {
    fetchCopyFromSiteList();
    fetchSiteList();
    fetchOrgList();
  }
});
// 站点语言下拉取自字典 sys_site_lang（docs/spec/dictionary P4）；缺 dict:list 或请求失败时降级为自由输入
const langOptions = ref<Array<{ value: string; label: string }>>([]);
const fetchLangOptions = async () => {
  if (!hasPermission('dict:list')) return;
  try {
    const list = await queryDictListByAlias('sys_site_lang', undefined, true);
    langOptions.value = list.map((item: any) => ({ value: item.value, label: `${item.name} (${item.value})` }));
  } catch {
    langOptions.value = [];
  }
};
onMounted(() => {
  fetchOrgList();
  fetchModelList();
  fetchLangOptions();
});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.system.site')"
    :query-bean="querySite"
    :create-bean="createSite"
    :update-bean="updateSite"
    :delete-bean="deleteSite"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="
      (bean: any) => ({
        status: 0,
        pageSize: 20,
        protocol: 'http',
        lang: 'zh-CN',
        copyData: ['template', 'model'],
        theme: themeList[0],
        mobileTheme: themeList[0],
        copyFromId: copyFromSiteTree[0]?.id,
        parentId: bean?.id !== parentId ? (bean?.parentId ?? parentId) : parentId,
        orgId: bean?.orgId ?? orgTree[0]?.id,
        // 新增语言版本（multilingual P4）：以源站点为复制来源（连模板/模型/栏目一起复制，
        // 栏目别名保留是语言切换/hreflang 按别名映射的基础），预填域名/名称并挂入其语言组
        ...(variantFrom != null
          ? {
              copyFromId: variantFrom.id,
              copyData: ['template', 'model', 'channel'],
              parentId: variantFrom.parentId ?? parentId,
              orgId: variantFrom.orgId ?? orgTree[0]?.id,
              protocol: variantFrom.protocol ?? 'http',
              domain: variantFrom.domain,
              name: variantFrom.name,
              langParentId: variantFrom.langParentId ?? variantFrom.id,
              lang: undefined,
            }
          : {}),
      })
    "
    :to-values="(bean: any) => ({ ...bean })"
    perms="site"
    :model-value="modelValue"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="finished"
    @bean-change="beanChange"
  >
    <template #header-status="{ isEdit }">
      <template v-if="isEdit">
        <span v-if="values.status != null">
          <el-tag v-if="values.status === 0" type="success" size="small" disable-transitions>{{ $t(`site.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 1" size="small" disable-transitions>{{ $t(`site.status.${values.status}`) }}</el-tag>
        </span>
      </template>
    </template>
    <template #default="{ isEdit }">
      <el-row>
        <el-col v-if="!isEdit" :span="12">
          <el-form-item prop="copyFromId" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="site.copyFrom" help /></template>
            <el-tree-select
              v-model="values.copyFromId"
              :data="copyFromSiteTree"
              node-key="id"
              :default-expanded-keys="copyFromSiteTree.map((item) => item.id)"
              :props="{ label: 'name' }"
              :render-after-expand="false"
              check-strictly
              class="w-full"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="!isEdit" :span="12">
          <el-form-item prop="copyData">
            <template #label><label-tip message="site.copyData" help /></template>
            <el-checkbox-group v-model="values.copyData">
              <el-checkbox
                v-for="(item, index) in ['template', 'model', 'channel']"
                :key="item"
                :value="item"
                @change="(value) => (values.copyData = ['template', 'model', 'channel'].slice(0, value ? index + 1 : index))"
              >
                {{ $t('site.copyData.' + item) }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </el-col>
        <el-col v-if="!isEdit || showGlobalData || values.id !== siteTree[0]?.id" :span="24">
          <el-form-item prop="parentId" :label="$t('site.parent')">
            <el-tree-select
              v-model="values.parentId"
              :data="parentSiteTree"
              node-key="id"
              :default-expanded-keys="parentSiteTree.map((item) => item.id)"
              :props="{ label: 'name', disabled: 'disabled' }"
              :render-after-expand="false"
              :disabled="isEdit"
              check-strictly
              :clearable="showGlobalData"
              class="w-full"
            />
          </el-form-item>
        </el-col>
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
              { pattern: /^(?!(uploads|templates|themes|WEB-INF|cp|api|frontend|m)$).*/i, message: () => $t('site.error.excludesPattern') },
            ]"
          >
            <template #label><label-tip message="site.subDir" help /></template>
            <el-input v-model="values.subDir" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            prop="lang"
            :rules="[
              { required: true, message: () => $t('v.required') },
              { pattern: /^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$/, message: () => $t('site.error.langPattern') },
              { validator: langDuplicateValidator },
            ]"
          >
            <template #label><label-tip message="site.lang" help /></template>
            <el-select v-model="values.lang" filterable allow-create clearable class="w-full">
              <el-option v-for="item in langOptions" :key="item.value" :label="item.label" :value="item.value"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="langParentId" :rules="{ validator: langDuplicateValidator }">
            <template #label><label-tip message="site.langGroup" help /></template>
            <el-select v-model="values.langParentId" filterable clearable class="w-full">
              <!-- 站点名为主、语言为弱化后缀（候选与选中态同一形态） -->
              <template #label="{ label, value }">
                <span>{{ label }}</span>
                <span class="ml-2 text-xs text-gray-400">{{ langMasterCandidates.find((site) => site.id === value)?.lang }}</span>
              </template>
              <el-option v-for="item in langMasterCandidates" :key="item.id" :label="item.name" :value="item.id">
                <span>{{ item.name }}</span>
                <span class="ml-2 text-xs text-gray-400">{{ item.lang }}</span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="orgId" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="site.org" help /></template>
            <el-tree-select
              v-model="values.orgId"
              :data="orgTree"
              node-key="id"
              :default-expanded-keys="orgTree.map((item) => item.id)"
              :props="{ label: 'name' }"
              :render-after-expand="false"
              check-strictly
              class="w-full"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="isEdit" :span="12">
          <el-form-item prop="modelId" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="site.model" help /></template>
            <!-- 列表是当前站点可见的模型（本站私有 + 全局共享）：编辑其它站点时只能改绑全局共享的模型，本站私有的对那个站点不可见（服务端同规） -->
            <el-select v-model="values.modelId" class="w-full">
              <el-option v-for="item in modelList" :key="item.id" :label="item.name" :value="item.id" :disabled="item.scope !== 2 && !editingCurrentSite"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="isEdit" :span="12">
          <el-form-item prop="theme" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="site.theme" help /></template>
            <el-select v-model="values.theme" class="w-full">
              <el-option v-for="item in themeList" :key="item" :label="item" :value="item"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="isEdit" :span="12">
          <el-form-item prop="mobileTheme" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="site.mobileTheme" help /></template>
            <el-select v-model="values.mobileTheme" class="w-full">
              <el-option v-for="item in themeList" :key="item" :label="item" :value="item"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="logo">
            <template #label><label-tip message="site.logo" help /></template>
            <image-upload v-model="values.logo"></image-upload>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="description">
            <template #label><label-tip message="site.description" /></template>
            <el-input v-model="values.description" maxlength="1000"></el-input>
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
        <el-col :span="24">
          <el-form-item prop="pageSize">
            <template #label><label-tip message="site.pageSize" help /></template>
            <el-input-number v-model="values.pageSize"></el-input-number>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>
