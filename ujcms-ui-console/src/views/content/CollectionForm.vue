<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import draggable from 'vuedraggable';
import { cloneDeep } from 'lodash-es';
import { queryCollection, createCollection, updateCollection, deleteCollection } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import { toTree } from '@/utils/tree';
import { perm } from '@/stores/useCurrentUser';
import { queryChannelList } from '@/api/content';
import CollectionSetup from './CollectionSetup.vue';

defineOptions({
  name: 'CollectionForm',
});
const visible = defineModel<boolean>({ default: false });
defineProps<{ beanId?: string; beanIds: string[] }>();
defineEmits({ finished: null });
const { t } = useI18n();
const dialog = ref<any>();
const focus = ref<any>();
const values = ref<any>({});

const steps = [t('collection.steps.0'), t('collection.steps.1'), t('collection.steps.2')];
const step = ref<number>(0);
const channelList = ref<any[]>([]);

const fetchChannelList = async () => {
  channelList.value = toTree(await queryChannelList({ isArticlePermission: true, isReal: true }));
};

watch(visible, () => {
  if (visible.value) {
    step.value = 0;
    fetchChannelList();
  }
});

const changeStep = (index: number) => {
  if (index < step.value) {
    step.value = index;
    return;
  }
  dialog.value.form.validate(async (valid: boolean) => {
    if (!valid) return;
    step.value = index;
  });
};
let fieldIndex = -1;
const SYSTEM_FIELDS = [
  { id: fieldIndex--, name: t('article.title'), code: 'title', source: 1, type: 1, dataType: 1 },
  { id: fieldIndex--, name: t('article.publishDate'), code: 'publishDate', source: 1, type: 1, dataType: 2 },
  { id: fieldIndex--, name: t('article.image'), code: 'image', source: 1, type: 1, dataType: 11 },
  { id: fieldIndex--, name: t('article.file'), code: 'file', source: 1, type: 1, dataType: 14 },
  { id: fieldIndex--, name: t('article.text'), code: 'text', source: 1, type: 1, dataType: 1 },
  { id: fieldIndex--, name: t('article.subtitle'), code: 'subtitle', source: 1, type: 1, dataType: 1 },
  { id: fieldIndex--, name: t('article.fullTitle'), code: 'fullTitle', source: 1, type: 1, dataType: 1 },
  { id: fieldIndex--, name: t('article.seoKeywords'), code: 'seoKeywords', source: 1, type: 1, dataType: 1 },
  { id: fieldIndex--, name: t('article.seoDescription'), code: 'seoDescription', source: 1, type: 1, dataType: 1 },
  { id: fieldIndex--, name: t('article.author'), code: 'author', source: 1, type: 1, dataType: 1 },
  { id: fieldIndex--, name: t('article.editor'), code: 'editor', source: 1, type: 1, dataType: 1 },
  { id: fieldIndex--, name: t('article.source'), code: 'source', source: 1, type: 1, dataType: 1 },
];

const systemFields = computed(() => {
  return SYSTEM_FIELDS.filter((item: any) => !values.value.fields.some((field: any) => field.type == 1 && field.name === item.name));
});

const addSystemFields = (code: string) => {
  values.value.fields.push(cloneDeep(systemFields.value.find((item) => item.code === code)));
};

const removeFields = (index: number) => {
  values.value.fields.splice(index, 1);
};

const setupFinished = (bean: any) => {
  values.value = bean;
};

const setupVisible = ref<boolean>(false);
const setupSource = ref<'list' | 'detail'>('list');
const setupType = ref<'list' | 'system' | 'custom'>('list');
const setupCode = ref<'area' | 'item' | string>('item');

const setup = (source: 'list' | 'detail', type: 'list' | 'system' | 'custom', name: 'area' | 'item' | string) => {
  setupSource.value = source;
  setupType.value = type;
  setupCode.value = name;
  setupVisible.value = true;
};
</script>

<template>
  <dialog-form
    ref="dialog"
    v-model="visible"
    v-model:values="values"
    :name="$t('menu.content.collection')"
    :query-bean="queryCollection"
    :create-bean="createCollection"
    :update-bean="updateCollection"
    :delete-bean="deleteCollection"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="
      () => {
        step = 0;
        return {
          charset: 'UTF-8',
          userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
          autoSubmit: true,
          allowDuplicate: false,
          downloadImage: true,
          intervalMin: 0,
          intervalMax: 0,
          pageBegin: 2,
          pageEnd: 10,
          listDesc: false,
          listAreaReg: false,
          itemAreaReg: false,
          itemUrlReg: false,
          itemUrlJs: false,
          fields: cloneDeep(SYSTEM_FIELDS.filter((item) => ['title', 'text'].includes(item.code))),
        };
      }
    "
    :to-values="(bean) => ({ ...bean })"
    large
    perms="collection"
    @finished="() => $emit('finished')"
  >
    <template #default="{ bean }">
      <el-steps :active="step" align-center>
        <el-step
          v-for="(item, index) in steps"
          :key="item"
          :title="item"
          :class="index < step ? 'cursor-pointer' : undefined"
          @click="
            () => {
              if (index < step) {
                changeStep(index);
              }
            }
          "
        />
      </el-steps>
      <el-row class="mt-3">
        <template v-if="step === 0">
          <el-col :span="12">
            <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.name" /></template>
              <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              prop="channelId"
              :rules="[
                { required: true, message: () => $t('v.required') },
                {
                  validator: (rule, value, callback) => {
                    if (channelList.find((it) => it.id === value)?.children.length > 0) {
                      callback($t('collection.error.leafChannelOnly'));
                      return;
                    }
                    callback();
                  },
                },
              ]"
            >
              <template #label><label-tip message="collection.channel" /></template>
              <el-tree-select
                v-model="values.channelId"
                :data="channelList"
                node-key="id"
                :props="{ label: 'name' }"
                :render-after-expand="false"
                :default-expanded-keys="bean.channel?.paths.map((it) => it.id)"
                class="w-full"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="description">
              <template #label><label-tip message="collection.description" /></template>
              <el-input v-model="values.description" maxlength="300"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="charset" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.charset" help /></template>
              <el-input v-model="values.charset" maxlength="50"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="autoSubmit">
              <template #label><label-tip message="collection.autoSubmit" help /></template>
              <el-switch v-model="values.autoSubmit"></el-switch>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="downloadImage">
              <template #label><label-tip message="collection.downloadImage" help /></template>
              <el-switch v-model="values.downloadImage"></el-switch>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="allowDuplicate">
              <template #label><label-tip message="collection.allowDuplicate" help /></template>
              <el-switch v-model="values.allowDuplicate"></el-switch>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="intervalMin" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.intervalMin" help /></template>
              <el-input-number v-model="values.intervalMin" :min="0" :max="1000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="intervalMax" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.intervalMax" help /></template>
              <el-input-number v-model="values.intervalMax" :min="0" :max="1000" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="userAgent" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.userAgent" help /></template>
              <el-input v-model="values.userAgent" maxlength="255"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="listUrls" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.listUrls" help /></template>
              <el-input v-model="values.listUrls" type="textarea" :rows="5" maxlength="2000"></el-input>
              <div class="mt-2">
                <span>{{ $t('collection.page') }}</span>
                <span class="ml-2">{{ $t('collection.page.from') }}</span>
                <el-input-number v-model="values.pageBegin" :min="0" :max="1000" class="mx-2" />
                <span>{{ $t('collection.page.to') }}</span>
                <el-input-number v-model="values.pageEnd" :min="0" :max="1000" class="mx-2" />
                <span>{{ $t('collection.listDesc') }}</span>
                <el-switch v-model="values.listDesc" class="ml-1" />
              </div>
            </el-form-item>
          </el-col>
        </template>
        <template v-if="step === 1">
          <el-col :span="24">
            <el-form-item prop="listAreaPattern" :rules="{ required: true, message: () => $t('v.required') }">
              <template #label><label-tip message="collection.listAreaPattern" help /></template>
              <el-input v-model="values.listAreaPattern" type="textarea" :rows="5" maxlength="300" class="mt-2"></el-input>
              <div class="mt-2">
                <el-button @click="() => setup('list', 'list', 'item')">{{ $t('collection.op.setup') }}</el-button>
                <span class="ml-3">{{ $t('collection.isReg') }}</span>
                <el-switch v-model="values.listAreaReg" class="ml-1" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="itemAreaPattern">
              <template #label><label-tip message="collection.itemAreaPattern" help /></template>
              <el-input v-model="values.itemAreaPattern" type="textarea" :rows="5" maxlength="300"></el-input>
              <div class="mt-2">
                <el-button @click="() => setup('list', 'list', 'area')">{{ $t('collection.op.setup') }}</el-button>
                <span class="ml-3">{{ $t('collection.isReg') }}</span>
                <el-switch v-model="values.itemAreaReg" class="ml-1" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <template #label><label-tip message="collection.itemUrlPattern" help /></template>
              <el-row :gutter="8" class="mt-1 w-full">
                <el-col :span="12">
                  <el-form-item prop="itemUrlPattern" label-position="top" :rules="{ required: true, message: () => $t('v.required') }">
                    <template #label><label-tip message="collection.pattern" help /></template>
                    <el-input v-model="values.itemUrlPattern" type="textarea" :rows="5" maxlength="300"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item prop="itemUrlFilter" label-position="top">
                    <template #label><label-tip message="collection.filter" help /></template>
                    <el-input v-model="values.itemUrlFilter" type="textarea" :rows="5" maxlength="300"></el-input>
                  </el-form-item>
                </el-col>
              </el-row>
              <div>
                <el-button @click="() => setup('list', 'list', 'item')">{{ $t('collection.op.setup') }}</el-button>
                <span class="ml-3">{{ $t('collection.isReg') }}</span>
                <el-switch v-model="values.itemUrlReg" class="ml-1" />
                <span class="ml-3">{{ $t('collection.isJs') }}</span>
                <el-switch v-model="values.itemUrlJs" class="ml-1" />
              </div>
            </el-form-item>
          </el-col>
        </template>
        <template v-if="step === 2">
          <draggable v-model="values.fields" :animation="250" tag="el-row" handle=".draggable-handle" item-key="id">
            <template #item="{ element: field, index }">
              <el-col :span="24">
                <el-form-item :label="field.name">
                  <div>
                    <el-button :disabled="field.code === 'title'" @click="() => removeFields(index)">{{ $t('delete') }}</el-button>
                    <span class="ml-3">{{ $t('collection.source') }}</span>
                    <el-select v-model="field.source" class="w-40 ml-2">
                      <el-option v-for="n in [1, 2, 3]" :key="n" :label="$t(`collection.source.${n}`)" :value="n" :disabled="!values.itemAreaPattern && n === 2" />
                    </el-select>
                    <el-icon class="ml-2 text-2xl align-middle cursor-move draggable-handle"><Grid /></el-icon>
                  </div>
                  <el-row :gutter="8" class="mt-1 w-full">
                    <el-col v-if="field.source !== 3" :span="12">
                      <el-form-item :prop="`fields.${index}.itemPattern`" label-position="top" :rules="{ required: true, message: () => $t('v.required') }">
                        <template #label><label-tip message="collection.pattern" help /></template>
                        <el-input v-model="field.itemPattern" type="textarea" :rows="5" maxlength="300"></el-input>
                      </el-form-item>
                    </el-col>
                    <el-col v-if="field.source !== 3" :span="12">
                      <el-form-item :prop="`fields.${index}.itemFilter`" label-position="top">
                        <template #label><label-tip message="collection.filter" help /></template>
                        <el-input v-model="field.itemFilter" type="textarea" :rows="5" maxlength="500"></el-input>
                      </el-form-item>
                    </el-col>
                    <el-col v-if="field.source === 3" :span="24">
                      <el-form-item :prop="`fields.${index}.staticValue`" label-position="top" :rules="{ required: true, message: () => $t('v.required') }">
                        <template #label><label-tip message="collection.staticValue" help /></template>
                        <el-input v-model="field.staticValue" type="textarea" :rows="5" maxlength="300"></el-input>
                      </el-form-item>
                    </el-col>
                    <el-col v-if="field.dataType === 2" :span="24">
                      <el-form-item :prop="`fields.${index}.dateFormat`" label-position="top" :rules="{ required: true, message: () => $t('v.required') }">
                        <template #label><label-tip message="collection.dateFormat" help /></template>
                        <el-input v-model="field.dateFormat" maxlength="50" />
                      </el-form-item>
                    </el-col>
                    <el-col v-if="field.dataType === 14" :span="24">
                      <el-form-item :prop="`fields.${index}.dataUrl`" label-position="top">
                        <template #label><label-tip message="collection.dataUrl" help /></template>
                        <el-input v-model="field.dataUrl" maxlength="50" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <div>
                    <el-button :disabled="field.source === 3" @click="() => setup(field.source === 2 ? 'list' : 'detail', field.type === 2 ? 'custom' : 'system', field.code)">
                      {{ $t('collection.op.setup') }}
                    </el-button>
                    <span class="ml-3">{{ $t('collection.isReg') }}</span>
                    <el-switch v-model="field.itemReg" class="ml-1" />
                    <span class="ml-3">{{ $t('collection.isJs') }}</span>
                    <el-switch v-model="field.itemJs" class="ml-1" />
                  </div>
                </el-form-item>
              </el-col>
            </template>
          </draggable>
          <el-col :span="24" class="border-t">
            <el-form-item>
              <div class="mt-4">
                <el-select :placeholder="$t('collection.op.addSystemField')" class="w-48" @change="(code) => addSystemFields(code)">
                  <el-option v-for="item in systemFields" :key="item.id" :label="item.name" :value="item.code" />
                </el-select>
              </div>
            </el-form-item>
          </el-col>
        </template>
      </el-row>
    </template>
    <template #footer-action="{ isEdit, handleSubmit }">
      <el-button :disabled="step <= 0" @click="() => changeStep(step - 1)">{{ $t('collection.op.prevStep') }}</el-button>
      <el-button :disabled="step >= steps.length - 1" @click="() => changeStep(step + 1)">{{ $t('collection.op.nextStep') }}</el-button>
      <el-button
        :disabled="perm(isEdit ? 'collection:update' : 'collection:create') || step < steps.length - 1"
        type="primary"
        native-type="submit"
        @click.prevent="() => handleSubmit()"
      >
        {{ $t('save') }}
      </el-button>
    </template>
  </dialog-form>
  <collection-setup v-model="setupVisible" :setup-source="setupSource" :type="setupType" :code="setupCode" :bean="values" @finished="setupFinished" />
</template>

<style lang="scss" scoped>
:deep(.el-form-item .el-form-item) {
  margin-bottom: 16px;
}
</style>
