<script setup lang="ts">
import { computed, ref, toRefs, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { getModelData, mergeModelFields } from '@/data';
import { currentUser } from '@/stores/useCurrentUser';
import { queryModel, updateModelSystemFields } from '@/api/config';
import { loadOrClose } from '@/utils/common';
import { isReadonlyModel } from './components/modelRules';

// 系统字段：按模型类型的字段目录（data.ts getModelData）合并模型里存下的开关，主栏 / 侧栏两张表。
// 只保存字段集（model-management M2）；未保存的改动有守卫；保存失败对话框不关。
defineOptions({
  name: 'ModelSystemFields',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { beanId, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const bean = ref<any>({});
const buttonLoading = ref<boolean>(false);
const mains = ref<any[]>([]);
const asides = ref<any[]>([]);
// 未保存守卫的基线：合并后的结果（库中字符串与合并结果不等价，拿它比会把每次打开都算作已改）
const baseline = ref<string>('');
const snapshot = () => JSON.stringify({ mains: mains.value, asides: asides.value });
const dirty = computed(() => snapshot() !== baseline.value);
// 全局模型无全局数据权限时只读（服务端同规）：可查看开关，不可保存
const readonly = computed(() => isReadonlyModel(bean.value));
const title = computed(() => (bean.value?.name ? `${t('model.fun.systemFields')} - ${bean.value.name}` : t('model.fun.systemFields')));
const shownCount = (fields: any[]) => fields.filter((item) => item.show).length;

// 按当前版本可见的字段目录合并（低于某版本的字段不出现；恢复初始设置同此口径）
const merge = (defaults: any[] | undefined, stored: string | null) => {
  if (defaults == null || defaults.length <= 0) {
    return [];
  }
  return mergeModelFields(
    defaults.filter((item) => currentUser.commercialTier >= (item.commercialTier ?? 0)),
    stored,
    bean.value.type,
  );
};
// 主栏与侧栏都要重新赋值：上一个模型的侧栏不能留给没有侧栏的类型（否则会被存进去）
const load = (fromStored: boolean) => {
  const modelData = getModelData()[bean.value.type];
  mains.value = merge(modelData?.mains, fromStored ? bean.value.mains : null);
  asides.value = merge(modelData?.asides, fromStored ? bean.value.asides : null);
};
watch(visible, async () => {
  if (visible.value && beanId.value) {
    bean.value = await loadOrClose(
      () => queryModel(beanId.value),
      () => emit('update:modelValue', false),
    );
    load(true);
    baseline.value = snapshot();
  }
});
const close = () => emit('update:modelValue', false);
// 关闭前守卫：对话框的 ×、页脚的取消都经它；有未保存改动时确认
const handleBeforeClose = async (done: () => void) => {
  if (!dirty.value) {
    done();
    return;
  }
  try {
    await ElMessageBox.confirm(t('model.confirmDiscard'), { type: 'warning' });
    done();
  } catch {
    // 留在对话框里
  }
};
const serialize = (fields: any[]) => JSON.stringify(fields.map((item) => ({ ...item, name: item.name === '' ? null : item.name })));
const handleSubmit = async () => {
  buttonLoading.value = true;
  try {
    await updateModelSystemFields(bean.value.id, { mains: serialize(mains.value), asides: asides.value.length > 0 ? serialize(asides.value) : null });
    baseline.value = snapshot();
    ElMessage.success(t('success'));
    emit('finished');
    close();
  } finally {
    buttonLoading.value = false;
  }
};
const handleReset = () => load(false);
const handleCancel = () => handleBeforeClose(close);
</script>

<template>
  <el-dialog
    :title="title"
    :model-value="modelValue"
    :close-on-click-modal="!dirty"
    :close-on-press-escape="!dirty"
    :before-close="handleBeforeClose"
    top="5vh"
    width="1024px"
    class="system-fields-dialog"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <el-alert v-if="readonly" :title="$t('model.readonly')" type="info" :closable="false" show-icon class="mb-3" />
    <el-form :disabled="readonly">
      <div class="flex items-center mb-2">
        <span class="font-medium">{{ $t('model.mains') }}</span>
        <span class="ml-2 text-xs text-gray-secondary">{{ $t('model.fields.shown', { shown: shownCount(mains), total: mains.length }) }}</span>
      </div>
      <el-table :data="mains">
        <el-table-column prop="code" :label="$t('model.field.code')" min-width="150">
          <template #default="{ row }">
            <div class="leading-tight">
              <div>{{ $t(row.label) }}</div>
              <div class="text-xs text-gray-placeholder">{{ row.code }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" :label="$t('model.field.name')" min-width="140">
          <template #default="{ row }"><el-input v-model="row.name" :placeholder="$t(row.label)" class="w-11/12" /></template>
        </el-table-column>
        <el-table-column prop="show" :label="$t('model.field.show')" width="90">
          <template #default="{ row }"><el-switch v-model="row.show" :disabled="row.must"></el-switch></template>
        </el-table-column>
        <el-table-column prop="double" :label="$t('model.field.double')" width="90">
          <template #default="{ row }"><el-switch v-model="row.double"></el-switch></template>
        </el-table-column>
        <el-table-column prop="required" :label="$t('model.field.required')" width="90">
          <template #default="{ row }"><el-switch v-model="row.required" :disabled="row.must || row.requiredLocked"></el-switch></template>
        </el-table-column>
        <el-table-column :label="$t('model.field.attribute')" min-width="200">
          <template #default="{ row }">
            <template v-if="row.type === 'image'">
              <el-input v-model.number="row.imageWidth">
                <template #prepend>{{ $t('model.field.imageWidth') }}</template>
              </el-input>
              <el-input v-model.number="row.imageHeight">
                <template #prepend>{{ $t('model.field.imageHeight') }}</template>
              </el-input>
              <el-select v-model="row.imageMode" :placeholder="$t('model.field.imageMode')" class="w-full">
                <el-option v-for="n in ['manual', 'cut', 'resize', 'none']" :key="n" :value="n" :label="$t(`model.field.imageMode.${n}`)"></el-option>
              </el-select>
            </template>
            <template v-else-if="row.type === 'imageList'">
              <el-input v-model.number="row.imageMaxWidth">
                <template #prepend>{{ $t('model.field.imageMaxWidth') }}</template>
              </el-input>
              <el-input v-model.number="row.imageMaxHeight">
                <template #prepend>{{ $t('model.field.imageMaxHeight') }}</template>
              </el-input>
              <el-select v-model="row.imageListType" :placeholder="$t('model.field.imageListType')" class="w-full">
                <el-option v-for="n in ['pictureCard', 'picture']" :key="n" :value="n" :label="$t(`model.field.imageListType.${n}`)"></el-option>
              </el-select>
            </template>
            <template v-else-if="row.type === 'editor'">
              <el-select v-model="row.editorType" :placeholder="$t('model.field.editorType')" class="w-full">
                <el-option v-for="n in [1, 2]" :key="n" :value="n" :label="$t(`model.field.editorType.${n}`)"></el-option>
              </el-select>
              <el-checkbox v-model="row.editorSwitch"> {{ $t('model.field.editorSwitch') }}</el-checkbox>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <template v-if="asides.length > 0">
        <div class="flex items-center mt-5 mb-2">
          <span class="font-medium">{{ $t('model.asides') }}</span>
          <span class="ml-2 text-xs text-gray-secondary">{{ $t('model.fields.shown', { shown: shownCount(asides), total: asides.length }) }}</span>
        </div>
        <el-table :data="asides">
          <el-table-column prop="code" :label="$t('model.field.code')" min-width="150">
            <template #default="{ row }">
              <div class="leading-tight">
                <div>{{ $t(row.label) }}</div>
                <div class="text-xs text-gray-placeholder">{{ row.code }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" :label="$t('model.field.name')" min-width="140">
            <template #default="{ row }"><el-input v-model="row.name" :placeholder="$t(row.label)" class="w-11/12" /></template>
          </el-table-column>
          <el-table-column prop="show" :label="$t('model.field.show')" width="90">
            <template #default="{ row }"><el-switch v-model="row.show" :disabled="row.must"></el-switch></template>
          </el-table-column>
          <el-table-column prop="required" :label="$t('model.field.required')" width="90">
            <template #default="{ row }"><el-switch v-model="row.required" :disabled="row.must || row.requiredLocked"></el-switch></template>
          </el-table-column>
        </el-table>
      </template>
    </el-form>
    <template #footer>
      <div class="flex items-center">
        <el-tag v-if="dirty" type="danger" disable-transitions>{{ $t('form.unsaved') }}</el-tag>
        <div class="ml-auto">
          <el-button v-if="!readonly" @click="handleReset">{{ $t('restoreInitialSettings') }}</el-button>
          <el-button @click="handleCancel">{{ $t(readonly ? 'close' : 'cancel') }}</el-button>
          <el-button v-if="!readonly" :loading="buttonLoading" type="primary" @click="handleSubmit">{{ $t('save') }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
// 对话框正文可滚动、页脚常驻：三十多行的开关表不必滚到底才看见“保存”
.system-fields-dialog {
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  .el-dialog__body {
    overflow: auto;
    flex: 1 1 auto;
    min-height: 0;
  }
}
</style>
