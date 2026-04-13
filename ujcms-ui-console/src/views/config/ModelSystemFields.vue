<script setup lang="ts">
import { ref, toRefs, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { getModelData, mergeModelFields } from '@/data';
import { currentUser } from '@/stores/useCurrentUser';
import { queryModel, updateModel } from '@/api/config';

defineOptions({
  name: 'ModelSystemFields',
});
const props = defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null } });
const emit = defineEmits({ 'update:modelValue': null });
const { beanId, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const bean = ref<any>({});
const buttonLoading = ref<boolean>(false);
const mains = ref<any[]>([]);
const asides = ref<any[]>([]);
watch(visible, async () => {
  if (visible.value && beanId.value) {
    bean.value = await queryModel(beanId.value);
    const modelData = getModelData()[bean.value.type];
    mains.value = mergeModelFields(
      modelData.mains.filter((item) => currentUser.epRank >= (item.epRank ?? 0)),
      bean.value.mains,
      bean.value.type,
    );
    if (modelData.asides?.length > 0) {
      asides.value = mergeModelFields(
        modelData.asides.filter((item) => currentUser.epRank >= (item.epRank ?? 0)),
        bean.value.asides,
        bean.value.type,
      );
    }
  }
});
const handleSubmit = async () => {
  buttonLoading.value = true;
  try {
    mains.value = mains.value.map((item) => ({ ...item, name: item.name === '' ? null : item.name }));
    asides.value = asides.value.map((item) => ({ ...item, name: item.name === '' ? null : item.name }));
    await updateModel({ ...bean.value, mains: JSON.stringify(mains.value), asides: JSON.stringify(asides.value) });
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
    emit('update:modelValue', false);
  }
};
const handleReset = () => {
  const modelData = getModelData()[bean.value.type];
  mains.value = mergeModelFields(modelData.mains, null, bean.value.type);
  if (modelData.asides?.length > 0) {
    asides.value = mergeModelFields(modelData.asides, null, bean.value.type);
  }
};
</script>

<template>
  <el-dialog :title="$t('model.fun.systemFields')" :model-value="modelValue" top="5vh" width="1024px" @update:model-value="(event) => $emit('update:modelValue', event)">
    <el-form>
      <el-table :data="mains">
        <el-table-column prop="code" :label="$t('model.field.code')" min-width="110"></el-table-column>
        <el-table-column prop="name" :label="$t('model.field.name')" min-width="140">
          <template #default="{ row }"><el-input v-model="row.name" :placeholder="$t(row.label)" class="w-11/12" /></template>
        </el-table-column>
        <el-table-column prop="show" :label="$t('model.field.show')">
          <template #default="{ row }"><el-switch v-model="row.show" :disabled="row.must"></el-switch></template>
        </el-table-column>
        <el-table-column prop="double" :label="$t('model.field.double')">
          <template #default="{ row }"><el-switch v-model="row.double"></el-switch></template>
        </el-table-column>
        <el-table-column prop="required" :label="$t('model.field.required')">
          <template #default="{ row }"><el-switch v-model="row.required" :disabled="row.must"></el-switch></template>
        </el-table-column>
        <el-table-column :label="$t('model.field.attribute')" min-width="180">
          <template #default="{ row }">
            <template v-if="row.type === 'image'">
              <el-input v-model.number="row.imageHeight">
                <template #prepend>{{ $t('model.field.imageHeight') }}</template>
              </el-input>
              <el-input v-model.number="row.imageWidth">
                <template #prepend>{{ $t('model.field.imageWidth') }}</template>
              </el-input>
              <el-select v-model="row.imageMode" :placeholder="$t('model.field.imageMode')" class="w-full">
                <el-option v-for="n in ['manual', 'cut', 'resize', 'none']" :key="n" :value="n" :label="$t(`model.field.imageMode.${n}`)"></el-option>
              </el-select>
            </template>
            <template v-else-if="row.type === 'imageList'">
              <el-input v-model.number="row.imageMaxHeight">
                <template #prepend>{{ $t('model.field.imageMaxHeight') }}</template>
              </el-input>
              <el-input v-model.number="row.imageMaxWidth">
                <template #prepend>{{ $t('model.field.imageMaxWidth') }}</template>
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
      <el-table v-if="asides.length > 0" :data="asides" class="mt-5">
        <el-table-column prop="code" :label="$t('model.field.code')" min-width="100"></el-table-column>
        <el-table-column prop="name" :label="$t('model.field.name')" min-width="120">
          <template #default="{ row }"><el-input v-model="row.name" :placeholder="$t(row.label)" class="w-11/12" /></template>
        </el-table-column>
        <el-table-column prop="show" :label="$t('model.field.show')">
          <template #default="{ row }"><el-switch v-model="row.show" :disabled="row.must"></el-switch></template>
        </el-table-column>
        <el-table-column prop="required" :label="$t('model.field.required')">
          <template #default="{ row }"><el-switch v-model="row.required" :disabled="row.must"></el-switch></template>
        </el-table-column>
      </el-table>
      <div class="mt-3">
        <el-button :loading="buttonLoading" type="primary" native-type="submit" @click.prevent="handleSubmit">{{ $t('save') }}</el-button>
        <el-button @click="handleReset">{{ $t('restoreInitialSettings') }}</el-button>
      </div>
    </el-form>
  </el-dialog>
</template>
