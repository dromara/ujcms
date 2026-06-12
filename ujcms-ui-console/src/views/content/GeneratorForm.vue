<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { ElForm } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { querySiteHtmlSettings, updateSiteHtmlSettings, queryConfigGrey, updateConfigGrey } from '@/api/config';
import { fulltextReindexAll, fulltextReindexSite, htmlAll, htmlAllHome, htmlHome, htmlChannel, htmlArticle } from '@/api/content';
import { perm, hasPermission, isInclude, currentUser } from '@/stores/useCurrentUser';
import LabelTip from '@/components/LabelTip.vue';
import TaskList from '@/views/system/TaskList.vue';

type FormInstance = InstanceType<typeof ElForm>;
defineOptions({
  name: 'GeneratorForm',
});
const { t } = useI18n();
const buttonLoading = ref<boolean>(false);
const taskListRef = ref<any>();

const htmlForm = ref<FormInstance>();
const htmlValues = ref<any>({});
const htmlLoading = ref<boolean>(false);

const greyForm = ref<FormInstance>();
const greyValues = ref<any>({});
const greyLoading = ref<boolean>(false);

const fetchHtmlSetting = async () => {
  htmlLoading.value = true;
  try {
    htmlValues.value = await querySiteHtmlSettings();
  } finally {
    htmlLoading.value = false;
  }
};

const fetchGreySetting = async () => {
  if (!hasPermission('config:grey:show')) {
    return;
  }
  greyLoading.value = true;
  try {
    greyValues.value = await queryConfigGrey();
  } finally {
    greyLoading.value = false;
  }
};

onMounted(async () => {
  fetchHtmlSetting();
  fetchGreySetting();
});

const handleFulltextReindexAll = async () => {
  buttonLoading.value = true;
  try {
    await fulltextReindexAll();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleFulltextReindexSite = async () => {
  buttonLoading.value = true;
  try {
    await fulltextReindexSite();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlAll = async () => {
  buttonLoading.value = true;
  try {
    await htmlAll();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlHome = async () => {
  buttonLoading.value = true;
  try {
    await htmlHome();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlChannel = async () => {
  buttonLoading.value = true;
  try {
    await htmlChannel();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlArticle = async () => {
  buttonLoading.value = true;
  try {
    await htmlArticle();
    await taskListRef.value.fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleHtmlUpdate = () => {
  if (!htmlForm.value) return;
  htmlForm.value.validate(async (isValid?: boolean) => {
    if (!isValid) return;
    htmlLoading.value = true;
    try {
      await updateSiteHtmlSettings(htmlValues.value);
      await htmlAll();
      await taskListRef.value.fetchData();
      ElMessage.success(t('success'));
    } finally {
      htmlLoading.value = false;
    }
  });
};
const handleGreyUpdate = () => {
  if (!greyForm.value) return;
  greyForm.value.validate(async (isValid?: boolean) => {
    if (!isValid) return;
    greyLoading.value = true;
    try {
      await updateConfigGrey(greyValues.value);
      await htmlAllHome();
      ElMessage.success(t('success'));
    } finally {
      greyLoading.value = false;
    }
  });
};
</script>

<template>
  <div>
    <div class="p-3 app-block">
      <el-form label-width="200px">
        <el-form-item>
          <template #label><label-tip message="generator.fulltext" help /></template>
          <el-button :disabled="perm('generator:fulltext:reindexAll')" :loading="buttonLoading" type="primary" plain @click.prevent="handleFulltextReindexAll">
            {{ $t('generator.op.fulltext.reindexAll') }}
          </el-button>
          <el-button
            v-if="isInclude('generator:fulltext:reindexSite')"
            :disabled="perm('generator:fulltext:reindexSite')"
            :loading="buttonLoading"
            type="primary"
            plain
            @click.prevent="handleFulltextReindexSite"
          >
            {{ $t('generator.op.fulltext.reindexSite') }}
          </el-button>
        </el-form-item>
        <el-form-item>
          <template #label><label-tip message="generator.html" help /></template>
          <el-button :disabled="perm('generator:html')" :loading="buttonLoading" type="primary" plain @click.prevent="handleHtmlAll">
            {{ $t('generator.op.html.all') }}
          </el-button>
          <span class="mx-2">|</span>
          <el-button :disabled="perm('generator:html')" :loading="buttonLoading" type="primary" plain @click.prevent="handleHtmlHome">
            {{ $t('generator.op.html.home') }}
          </el-button>
          <el-button :disabled="perm('generator:html')" :loading="buttonLoading" type="primary" plain @click.prevent="handleHtmlChannel">
            {{ $t('generator.op.html.channel') }}
          </el-button>
          <el-button :disabled="perm('generator:html')" :loading="buttonLoading" type="primary" plain @click.prevent="handleHtmlArticle">
            {{ $t('generator.op.html.article') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="p-3 mt-3 app-block">
      <div class="pb-2 border-b text-gray-primary">{{ $t('site.settings.html') }}</div>
      <el-form ref="htmlForm" v-loading="htmlLoading" class="mt-3" :model="htmlValues" :disabled="perm('siteSettings:html:update')" label-width="200px">
        <el-row>
          <el-col :span="12">
            <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="site.html.enabled" help /></template>
              <el-switch v-model="htmlValues.enabled" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="auto" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="site.html.auto" help /></template>
              <el-switch v-model="htmlValues.auto" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="channel" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="site.html.channel" help /></template>
              <el-input v-model="htmlValues.channel" maxlength="100"><template #append>.html</template></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="article" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="site.html.article" help /></template>
              <el-input v-model="htmlValues.article" maxlength="100"><template #append>.html</template></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="listPages" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="site.html.listPages" help /></template>
              <el-select v-model="htmlValues.listPages" placeholder="Select">
                <el-option
                  v-for="item in [
                    { label: '1', value: 1 },
                    { label: '3', value: 3 },
                    { label: '10', value: 10 },
                    { label: '100', value: 100 },
                    { label: '1000', value: 1000 },
                    { label: '全部', value: 2147483647 },
                  ]"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div>
          <el-button :disabled="perm(`siteSettings:html:update`)" type="primary" native-type="submit" @click.prevent="handleHtmlUpdate">
            {{ $t('submit') }}
          </el-button>
        </div>
      </el-form>
    </div>
    <div v-if="hasPermission('config:grey:show') && currentUser.epRank >= 2" class="p-3 mt-3 app-block">
      <div class="pb-2 border-b text-gray-primary">{{ $t('config.settings.grey') }}</div>
      <el-form ref="greyForm" v-loading="greyLoading" class="mt-3" :model="greyValues" :disabled="perm('config:grey:update')" label-width="200px">
        <el-row>
          <el-col :span="24">
            <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="config.grey.enabled" /></template>
              <el-switch v-model="greyValues.enabled" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="greyDates">
              <template #label><label-tip message="config.grey.greyDates" help /></template>
              <el-input v-model="greyValues.greyDates" maxlength="1000"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <div>
          <el-button :disabled="perm(`config:grey:update`)" type="primary" native-type="submit" @click.prevent="handleGreyUpdate">
            {{ $t('submit') }}
          </el-button>
        </div>
      </el-form>
    </div>
    <div class="p-3 mt-3 app-block">
      <div class="text-gray-primary">{{ $t('generator.taskList') }}</div>
    </div>
    <task-list ref="taskListRef" class="mt-3" />
  </div>
</template>
