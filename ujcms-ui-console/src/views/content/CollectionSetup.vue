<script setup lang="ts">
import { watch, computed, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { collectionSetupListUrls, collectionSetupDetailUrls, collectionSetupFetchContent, collectionSetupMatch, collectionSetupFilter } from '@/api/interaction';

defineOptions({
  name: 'CollectionSetup',
});
const visible = defineModel<boolean>({ default: false });
const { bean, setupSource, type, code } = defineProps<{ bean: any; setupSource: 'list' | 'detail'; type: 'list' | 'system' | 'custom'; code: 'area' | 'item' | string }>();
const emit = defineEmits<{
  finished: [values: any];
}>();
const values = ref<any>({});
const source = ref<'list' | 'detail'>('detail');
const loading = ref<boolean>(false);
const urls = ref<string[]>([]);
const url = ref<string>();
const content = ref<string>('');
const texts = ref<string[]>();

const field = computed(() => {
  if (type === 'list') {
    return {};
  }
  return values.value?.fields.find((item: any) => item.type === (type === 'custom' ? 2 : 1) && item.code === code);
});

const fetchContent = async () => {
  if (url.value) {
    loading.value = true;
    try {
      content.value = await collectionSetupFetchContent(url.value, values.value.userAgent, values.value.charset);
      texts.value = undefined;
    } finally {
      loading.value = false;
    }
  }
};

const fetchUrls = async () => {
  loading.value = true;
  try {
    if (source.value === 'detail') {
      urls.value = await collectionSetupDetailUrls(
        values.value.listUrls,
        values.value.pageBegin,
        values.value.pageEnd,
        values.value.userAgent,
        values.value.charset,
        values.value.listAreaPattern,
        values.value.itemUrlPattern,
        values.value.itemUrlReg,
        values.value.itemUrlJs,
      );
    } else {
      urls.value = await collectionSetupListUrls(values.value.listUrls, values.value.pageBegin, values.value.pageEnd, values.value.listDesc);
    }

    content.value = '';
    texts.value = undefined;
    const first = urls.value[0];
    if (first) {
      url.value = first;
      content.value = await collectionSetupFetchContent(first, values.value.userAgent, values.value.charset);
    }
  } finally {
    loading.value = false;
  }
};

const changeSource = (value: number) => {
  const changed = value === 2 ? 'list' : 'detail';
  if (changed !== source.value) {
    source.value = changed;
    fetchUrls();
  }
};

watch(visible, () => {
  if (visible.value) {
    values.value = cloneDeep(bean);
    source.value = setupSource;
    fetchUrls();
  }
});

const doMatch = async (pattern: string, isMulti: boolean, isReg: boolean, isJs: boolean) => {
  const result = await collectionSetupMatch(content.value, texts.value, pattern, isMulti, isReg, isJs);
  if (Array.isArray(result)) {
    texts.value = result;
    content.value = result.join('\n');
  } else {
    texts.value = undefined;
    content.value = result;
  }
};

const doFilter = async (filter: string, isMulti: boolean) => {
  const result = await collectionSetupFilter(content.value, texts.value, filter, isMulti);
  if (Array.isArray(result)) {
    texts.value = result;
    content.value = result.join('\n');
  } else {
    texts.value = undefined;
    content.value = result;
  }
};

const finish = () => {
  visible.value = false;
  emit('finished', values.value);
};
</script>

<template>
  <el-dialog v-model="visible" :title="$t('collection.op.setup')" width="98%" top="16px">
    <div>
      <el-select v-model="url" @change="() => fetchContent()">
        <el-option v-for="item in urls" :key="item" :label="item" :value="item" />
      </el-select>
      <el-input v-model="url">
        <template #append>
          <el-button @click="() => fetchContent()">{{ $t('collection.op.fetch') }}</el-button>
        </template>
      </el-input>
    </div>
    <div class="mt-2">
      <el-row :gutter="12">
        <el-col :span="18">
          <el-input v-model="content" type="textarea" :autosize="{ minRows: 20, maxRows: 38 }" input-style="white-space: pre; overflow-wrap: normal; overflow-x: scroll;" />
        </el-col>
        <el-col :span="6">
          <el-form :model="values" label-position="top">
            <el-form-item v-if="type !== 'list'">
              <template #label><label-tip message="collection.source" /></template>
              <el-select v-model="field.source" class="w-40 ml-2" @change="changeSource">
                <el-option v-for="n in [1, 2]" :key="n" :label="$t(`collection.source.${n}`)" :value="n" :disabled="!values.itemAreaPattern && n === 2" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="source === 'list'" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.listAreaPattern" /></template>
              <el-input v-model="values.listAreaPattern" :disabled="type !== 'list'" type="textarea" :rows="5" maxlength="300"></el-input>
              <div class="mt-2">
                <el-button @click="() => doMatch(values.listAreaPattern, false, values.listAreaReg, false)">{{ $t('collection.op.match') }}</el-button>
                <span class="ml-3">{{ $t('collection.isReg') }}</span>
                <el-switch v-model="values.listAreaReg" :disabled="type !== 'list'" class="ml-1" />
              </div>
            </el-form-item>
            <el-form-item v-if="source === 'list'">
              <template #label><label-tip message="collection.itemAreaPattern" /></template>
              <el-input v-model="values.itemAreaPattern" :disabled="type !== 'list'" type="textarea" :rows="5" maxlength="300"></el-input>
              <div class="mt-2">
                <el-button @click="() => doMatch(values.itemAreaPattern, type === 'list', values.itemAreaReg, false)">{{ $t('collection.op.match') }}</el-button>
                <span class="ml-3">{{ $t('collection.isReg') }}</span>
                <el-switch v-model="values.itemAreaReg" :disabled="type !== 'list'" class="ml-1" />
              </div>
            </el-form-item>
            <el-divider v-if="type === 'list' && code === 'item'">{{ $t('collection.itemUrlPattern') }}</el-divider>
            <el-form-item v-if="type === 'list' && code === 'item'" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.pattern" help /></template>
              <el-input v-model="values.itemUrlPattern" type="textarea" :rows="5" maxlength="300"></el-input>
              <div class="mt-2">
                <el-button @click="() => doMatch(values.itemUrlPattern, true, values.itemUrlReg, values.itemUrlJs)">{{ $t('collection.op.match') }}</el-button>
                <span class="ml-3">{{ $t('collection.isReg') }}</span>
                <el-switch v-model="values.itemUrlReg" class="ml-1" />
                <span class="ml-3">{{ $t('collection.isJs') }}</span>
                <el-switch v-model="values.itemUrlJs" class="ml-1" />
              </div>
            </el-form-item>
            <el-form-item v-if="type === 'list' && code === 'item'">
              <template #label><label-tip message="collection.filter" help /></template>
              <el-input v-model="values.itemUrlFilter" type="textarea" :rows="5" maxlength="500"></el-input>
              <div class="mt-2">
                <el-button :disabled="!values.itemUrlFilter" @click="() => doFilter(values.itemUrlFilter, true)">{{ $t('collection.op.filter') }}</el-button>
              </div>
            </el-form-item>
            <el-divider v-if="type !== 'list'">{{ field.name }}</el-divider>
            <el-form-item v-if="type !== 'list'" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.pattern" help /></template>
              <el-input v-model="field.itemPattern" type="textarea" :rows="5" maxlength="300"></el-input>
              <div class="mt-2">
                <el-button @click="() => doMatch(field.itemPattern, false, values.itemReg, values.itemJs)">{{ $t('collection.op.match') }}</el-button>
                <span class="ml-3">{{ $t('collection.isReg') }}</span>
                <el-switch v-model="field.itemReg" class="ml-1" />
                <span class="ml-3">{{ $t('collection.isJs') }}</span>
                <el-switch v-model="field.itemJs" class="ml-1" />
              </div>
            </el-form-item>
            <el-form-item v-if="type !== 'list'">
              <template #label><label-tip message="collection.filter" help /></template>
              <el-input v-model="field.itemFilter" type="textarea" :rows="5" maxlength="500"></el-input>
              <div class="mt-2">
                <el-button :disabled="!field.itemFilter" @click="() => doFilter(field.itemFilter, false)">{{ $t('collection.op.filter') }}</el-button>
              </div>
            </el-form-item>
          </el-form>
          <el-divider />
          <div>
            <el-button type="primary" @click="() => finish()">{{ $t('save') }}</el-button>
          </div>
        </el-col>
      </el-row>
    </div>
  </el-dialog>
</template>
