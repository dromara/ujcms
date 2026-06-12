<script lang="ts">
export default { name: 'ImportData' };
</script>

<script setup lang="ts">
import LabelTip from '@/components/LabelTip.vue';
import { Delete, QuestionFilled } from '@element-plus/icons-vue';
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { storeToRefs } from 'pinia';
import { useImportDataStore, useImportDataPasswordStore } from '@/stores/importDataStore';
import { importDataTestConnection, importDataChannel, importDataArticle, importDataDeleteCorrespond } from '@/api/system';

const { t } = useI18n();
const datasourceForm = ref<any>();
const datasourceLoading = ref<boolean>(false);
const channelForm = ref<any>();
const channelLoading = ref<boolean>(false);
const articleForm = ref<any>();
const articleLoading = ref<boolean>(false);
const store = useImportDataStore();
const { datasource, channel, article } = storeToRefs(store);
const passwordStore = useImportDataPasswordStore();

const fetchData = async () => {};
onMounted(() => {
  fetchData();
});

const handleDeleteCorrespond = async () => {
  await importDataDeleteCorrespond();
  fetchData();
  ElMessage.success(t('success'));
};

const handleConnectionTest = async () => {
  datasourceForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    datasourceLoading.value = true;
    try {
      await importDataTestConnection({ ...datasource.value, password: passwordStore.password });
      ElMessage.success(t('success'));
    } finally {
      datasourceLoading.value = false;
    }
  });
};
const handleImportChannel = async () => {
  datasourceForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    channelForm.value.validate(async (sqlValid: boolean) => {
      if (!sqlValid) return;
      channelLoading.value = true;
      try {
        await importDataChannel({ ...datasource.value, password: passwordStore.password, ...channel.value });
        ElMessage.success(t('success'));
      } finally {
        channelLoading.value = false;
      }
    });
  });
};
const handleImportArticle = async () => {
  datasourceForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    articleForm.value.validate(async (sqlValid: boolean) => {
      if (!sqlValid) return;
      articleLoading.value = true;
      try {
        await importDataArticle({ ...datasource.value, password: passwordStore.password, ...article.value });
        ElMessage.success(t('success'));
      } finally {
        articleLoading.value = false;
      }
    });
  });
};
</script>

<template>
  <div>
    <div>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDeleteCorrespond()">
        <template #reference>
          <el-button :icon="Delete">{{ $t('importData.op.deleteCorrespond') }}</el-button>
        </template>
      </el-popconfirm>
      <el-tooltip :content="$t('importData.op.deleteCorrespond.tooltip')" placement="top">
        <el-icon class="ml-1 text-base align-middle"><QuestionFilled /></el-icon>
      </el-tooltip>
    </div>
    <div class="p-3 mt-3 app-block">
      <el-form ref="datasourceForm" v-loading="datasourceLoading" :model="datasource" label-width="160px">
        <el-row>
          <el-col :span="24">
            <el-form-item prop="url" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="importData.datasource.url" help /></template>
              <el-input v-model="datasource.url"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="username" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="importData.datasource.username" /></template>
              <el-input v-model="datasource.username"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="password">
              <template #label><label-tip message="importData.datasource.password" /></template>
              <el-input v-model="passwordStore.password" show-password></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="driverClassName" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="importData.datasource.driverClassName" help /></template>
              <el-input v-model="datasource.driverClassName"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <el-button type="primary" native-type="submit" @click.prevent="() => handleConnectionTest()">
                {{ $t('importData.op.connectionTest') }}
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-divider content-position="left">{{ $t('importData.channel') }}</el-divider>
      <el-form ref="channelForm" v-loading="channelLoading" :model="channel" label-width="160px">
        <el-row>
          <el-col :span="24">
            <el-form-item prop="sql" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="importData.channel.sql" help /></template>
              <el-input v-model="channel.sql" :autosize="{ minRows: 5, maxRows: 20 }" type="textarea" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <el-button type="primary" native-type="submit" @click.prevent="() => handleImportChannel()">
                {{ $t('importData.op.importChannel') }}
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-divider content-position="left">{{ $t('importData.article') }}</el-divider>
      <el-form ref="articleForm" v-loading="articleLoading" :model="article" label-width="160px">
        <el-row>
          <el-col :span="24">
            <el-form-item prop="sql" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="importData.article.sql" help /></template>
              <el-input v-model="article.sql" :autosize="{ minRows: 5, maxRows: 20 }" type="textarea" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <el-button type="primary" native-type="submit" @click.prevent="() => handleImportArticle()">
                {{ $t('importData.op.importArticle') }}
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>
  </div>
</template>
