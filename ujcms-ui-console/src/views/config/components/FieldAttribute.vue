<script lang="ts" setup>
import { computed, ref, toRefs, watchEffect, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { queryDictList } from '@/api/config';
import LabelTip from '@/components/LabelTip.vue';
import { SHOW_IN_LIST_MODEL_TYPES, validateField } from './fieldRules';

// 选中字段的属性面板。字段的默认属性在装载/创建时由 fieldRules.normalizeField 补齐，这里只编辑，选中不再改动字段
// （否则未保存守卫会把一次点击算作改动）。
const props = defineProps({
  selected: { type: Object, required: true },
  // 全部字段：代码重名、键名冲突要看全表
  fields: { type: Array as PropType<any[]>, default: () => [] },
  modelType: { type: String, default: '' },
  // 全局模型只能绑定全局共享的字典类型（model-management M8）
  modelGlobal: { type: Boolean, default: false },
  dictTypes: { type: Array as PropType<any[]>, default: () => [] },
  // 字段装载时的代码（已保存的字段）；改动它会让已录入内容中该字段的数据不再显示
  originalCode: { type: String, default: undefined },
});
const { selected: field, fields, dictTypes } = toRefs(props);
const { t } = useI18n();
const dictList = ref<any[]>([]);
watchEffect(async () => {
  if (field.value.dictTypeId != null) {
    dictList.value = await queryDictList({ typeId: field.value.dictTypeId });
  }
});
// 全局模型只列全局字典类型；已绑定的本站类型仍列出（禁用）以显示其名称，由操作员改绑
const dictTypeOptions = computed(() => {
  if (!props.modelGlobal) {
    return dictTypes.value;
  }
  return dictTypes.value.filter((item) => item.scope === 2 || String(item.id) === String(field.value.dictTypeId));
});
const dictTypeNotGlobal = computed(() => props.modelGlobal && dictTypes.value.some((item) => String(item.id) === String(field.value.dictTypeId) && item.scope !== 2));
const codeChanged = computed(() => props.originalCode != null && props.originalCode !== field.value.code);
const showInListAllowed = computed(() => SHOW_IN_LIST_MODEL_TYPES.includes(props.modelType));
const dictTypeChange = (typeId: string) => {
  field.value.defaultValue = field.value.multiple ? [] : undefined;
  field.value.defaultValueKey = field.value.multiple ? [] : undefined;
  field.value.dataType = dictTypes.value.find((item: any) => item.id === typeId)?.dataType === 1 ? 'number' : 'string';
};
// 代码规则与保存前的全表校验同源（fieldRules.validateField）
const codeValidator = (rule: any, value: any, callback: any) => {
  const message = validateField(field.value, fields.value, t).code;
  if (message) {
    callback(message);
    return;
  }
  callback();
};
</script>

<template>
  <el-form-item :label="$t('model.field.type')">
    <span>{{ $t(`model.fieldType.${field.type}`) }}</span>
  </el-form-item>
  <el-form-item prop="code" :label="$t('model.field.code')" :rules="[{ required: true, message: () => $t('v.required') }, { validator: codeValidator }]">
    <el-input v-model="field.code" maxlength="50"></el-input>
    <div v-if="codeChanged" class="w-full text-xs text-warning leading-5">{{ $t('model.field.codeChangedTip') }}</div>
  </el-form-item>
  <el-form-item prop="name" :label="$t('model.field.name')" :rules="{ required: true, message: () => $t('v.required') }">
    <el-input v-model="field.name" maxlength="50"></el-input>
  </el-form-item>
  <template v-if="['richEditor', 'textarea'].includes(field.type)">
    <el-form-item prop="clob" :label="$t('model.field.clob')">
      <template #label><label-tip message="model.field.clob" help /></template>
      <el-switch v-model="field.clob"></el-switch>
    </el-form-item>
  </template>
  <el-form-item prop="double" :label="$t('model.field.double')">
    <el-switch v-model="field.double"></el-switch>
  </el-form-item>
  <template v-if="showInListAllowed && ['text', 'textarea', 'number', 'date', 'color', 'slider', 'switch', 'radio', 'checkbox', 'select', 'multipleSelect'].includes(field.type)">
    <el-form-item prop="showInList" :label="$t('model.field.showInList')">
      <el-switch v-model="field.showInList"></el-switch>
    </el-form-item>
  </template>
  <el-form-item prop="required" :label="$t('model.field.required')">
    <el-switch v-model="field.required"></el-switch>
  </el-form-item>
  <template v-if="['text', 'textarea', 'number', 'select', 'multipleSelect', 'videoUpload', 'audioUpload', 'fileUpload', 'richEditor'].includes(field.type)">
    <el-form-item prop="placeholder" :label="$t('model.field.placeholder')">
      <el-input v-model="field.placeholder" maxlength="100"></el-input>
    </el-form-item>
  </template>
  <template v-if="['text', 'textarea'].includes(field.type)">
    <el-form-item prop="defaultValue" :label="$t('model.field.defaultValue')">
      <el-input v-model="field.defaultValue" maxlength="100"></el-input>
    </el-form-item>
    <el-form-item prop="minlength" :label="$t('model.field.minlength')">
      <el-input-number v-model="field.minlength" :min="1"></el-input-number>
    </el-form-item>
    <el-form-item prop="maxlength" :label="$t('model.field.maxlength')">
      <el-input-number v-model="field.maxlength" :min="1"></el-input-number>
    </el-form-item>
  </template>
  <template v-if="['textarea'].includes(field.type)">
    <el-form-item prop="rows" :label="$t('model.field.rows')">
      <el-input-number v-model="field.rows"></el-input-number>
    </el-form-item>
  </template>
  <template v-if="['number', 'slider'].includes(field.type)">
    <el-form-item prop="defaultValue" :label="$t('model.field.defaultValue')">
      <el-input-number v-model="field.defaultValue" :min="field.min" :max="field.max"></el-input-number>
    </el-form-item>
    <el-form-item prop="min" :label="$t('model.field.min')">
      <el-input-number v-model="field.min"></el-input-number>
    </el-form-item>
    <el-form-item prop="max" :label="$t('model.field.max')">
      <el-input-number v-model="field.max"></el-input-number>
    </el-form-item>
    <el-form-item prop="step" :label="$t('model.field.step')">
      <el-input-number v-model="field.step"></el-input-number>
    </el-form-item>
  </template>
  <template v-if="['number'].includes(field.type)">
    <el-form-item prop="precision" :label="$t('model.field.precision')">
      <el-input-number v-model="field.precision" :precision="0" :min="0" :max="4"></el-input-number>
    </el-form-item>
  </template>
  <template v-if="['slider'].includes(field.type)">
    <el-form-item prop="showInput" :label="$t('model.field.showInput')">
      <el-switch v-model="field.showInput"></el-switch>
    </el-form-item>
  </template>
  <template v-if="['date'].includes(field.type)">
    <el-form-item prop="dateType" :label="$t('model.field.dateType')">
      <el-select v-model="field.dateType">
        <el-option v-for="n in ['date', 'datetime']" :key="n" :label="$t(`model.field.dateType.${n}`)" :value="n"></el-option>
      </el-select>
    </el-form-item>
  </template>
  <template v-if="['color'].includes(field.type)">
    <el-form-item prop="defaultValue" :label="$t('model.field.defaultValue')">
      <el-color-picker v-model="field.defaultValue"></el-color-picker>
    </el-form-item>
  </template>
  <template v-if="['switch'].includes(field.type)">
    <el-form-item prop="defaultValue" :label="$t('model.field.defaultValue')">
      <el-switch v-model="field.defaultValue"></el-switch>
    </el-form-item>
  </template>
  <template v-if="['radio', 'checkbox'].includes(field.type)">
    <el-form-item prop="checkStyle" :label="$t('model.field.checkStyle')">
      <el-radio-group v-model="field.checkStyle">
        <el-radio-button v-for="n in ['default', 'button']" :key="n" :value="n">{{ $t(`model.field.checkStyle.${n}`) }}</el-radio-button>
      </el-radio-group>
    </el-form-item>
  </template>
  <template v-if="['select', 'multipleSelect'].includes(field.type)">
    <el-form-item prop="clearable" :label="$t('model.field.clearable')">
      <el-switch v-model="field.clearable"></el-switch>
    </el-form-item>
  </template>
  <template v-if="['radio', 'checkbox', 'select', 'multipleSelect'].includes(field.type)">
    <el-form-item prop="dictTypeId" :label="$t('model.field.dictType')" :rules="{ required: true, message: () => $t('v.required') }">
      <el-select v-model="field.dictTypeId" class="w-full" @change="(typeId) => dictTypeChange(typeId)">
        <el-option v-for="item in dictTypeOptions" :key="item.id" :value="item.id" :label="`${item.name}(${item.alias})`" :disabled="modelGlobal && item.scope !== 2"></el-option>
      </el-select>
      <div v-if="dictTypeNotGlobal" class="w-full text-xs text-warning leading-5">{{ $t('model.field.dictType.globalOnly') }}</div>
    </el-form-item>
    <el-form-item prop="dataType" :label="$t('model.field.dataType')">
      <el-select v-model="field.dataType" class="w-full" disabled>
        <el-option v-for="item in ['string', 'number']" :key="item" :value="item" :label="$t(`model.field.dataType.${item}`)"></el-option>
      </el-select>
    </el-form-item>
  </template>
  <template v-if="['radio', 'select'].includes(field.type)">
    <el-form-item prop="defaultValue" :label="$t('model.field.defaultValue')">
      <el-select v-model="field.defaultValueKey" clearable class="w-full" @change="(val) => (field.defaultValue = dictList.find((item) => item.value === val)?.name)">
        <el-option v-for="item in dictList" :key="item.id" :value="item.value" :label="item.name"></el-option>
      </el-select>
    </el-form-item>
  </template>
  <template v-if="['checkbox', 'multipleSelect'].includes(field.type)">
    <el-form-item prop="defaultValue" :label="$t('model.field.defaultValue')">
      <el-select
        v-model="field.defaultValueKey"
        clearable
        class="w-full"
        multiple
        @change="(val) => (field.defaultValue = dictList.filter((item) => val.indexOf(item.value) !== -1).map((item) => item.name))"
      >
        <el-option v-for="item in dictList" :key="item.id" :value="item.value" :label="item.name"></el-option>
      </el-select>
    </el-form-item>
  </template>
  <template v-if="['imageUpload'].includes(field.type)">
    <el-form-item prop="imageWidth" :label="$t('model.field.imageWidth')">
      <el-input-number v-model="field.imageWidth" :min="1" :max="65535"></el-input-number>
    </el-form-item>
    <el-form-item prop="imageHeight" :label="$t('model.field.imageHeight')">
      <el-input-number v-model="field.imageHeight" :min="1" :max="65535"></el-input-number>
    </el-form-item>
    <el-form-item prop="imageMode" :label="$t('model.field.imageMode')">
      <el-select v-model="field.imageMode" clearable class="w-full">
        <el-option v-for="item in ['cut', 'resize', 'manual', 'none']" :key="item" :value="item" :label="$t(`model.field.imageMode.${item}`)"></el-option>
      </el-select>
    </el-form-item>
  </template>
  <template v-if="['imageUpload', 'videoUpload', 'audioUpload', 'fileUpload'].includes(field.type)">
    <el-form-item prop="fileAccept">
      <template #label><label-tip message="model.field.fileAccept" help :fix-width="false" /></template>
      <el-input v-model="field.fileAccept" maxlength="255"></el-input>
    </el-form-item>
    <el-form-item prop="fileMaxSize" :rules="{ type: 'number', min: 0, max: 65535, message: () => $t('v.range', { min: 0, max: 65535 }) }">
      <template #label><label-tip message="model.field.fileMaxSize" help :fix-width="false" /></template>
      <el-input v-model.number="field.fileMaxSize" maxlength="5"></el-input>
    </el-form-item>
  </template>
  <template v-if="['richEditor'].includes(field.type)">
    <el-form-item prop="minHeight" :label="$t('model.field.minHeight')">
      <el-input-number v-model="field.minHeight" :min="1" :max="65535"></el-input-number>
    </el-form-item>
    <el-form-item prop="maxHeight" :label="$t('model.field.maxHeight')">
      <el-input-number v-model="field.maxHeight" :min="1" :max="65535"></el-input-number>
    </el-form-item>
    <el-form-item prop="policy">
      <template #label><label-tip message="model.field.policy" help /></template>
      <el-select v-model="field.policy" clearable class="w-full">
        <el-option v-for="n in ['rich', 'basic', 'strict']" :key="n" :value="n" :label="$t(`model.field.policy.${n}`)"></el-option>
      </el-select>
    </el-form-item>
  </template>
</template>
