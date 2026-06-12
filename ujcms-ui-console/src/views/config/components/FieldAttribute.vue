<script lang="ts" setup>
import { ref, toRefs, watchEffect } from 'vue';
import { queryDictTypeList } from '@/api/config';
import { queryDictList } from '@/api/content';
import LabelTip from '@/components/LabelTip.vue';

const props = defineProps({ selected: { type: Object, required: true } });
const { selected: field } = toRefs(props);
const dictTypeList = ref<any[]>([]);
const dictList = ref<any[]>([]);
watchEffect(async () => {
  if (field.value.type === 'date' && !field.value.dateType) {
    field.value.dateType = 'date';
  }
  if (field.value.type === 'switch' && !field.value.inactiveValue) {
    field.value.inactiveValue = '0';
  }
  if (field.value.type === 'switch' && !field.value.activeValue) {
    field.value.activeValue = '1';
  }
  if (['radio', 'checkbox'].includes(field.value.type) && !field.value.checkStyle) {
    field.value.checkStyle = 'default';
  }
  if (['checkbox', 'multipleSelect'].includes(field.value.type) && !field.value.defaultValue) {
    field.value.defaultValue = [];
  }
  if (['checkbox', 'multipleSelect'].includes(field.value.type)) {
    field.value.multiple = true;
  }
  if (['radio', 'checkbox', 'select', 'multipleSelect'].includes(field.value.type)) {
    dictTypeList.value = await queryDictTypeList();
  }
});
watchEffect(async () => {
  if (field.value.dictTypeId != null) {
    dictList.value = await queryDictList({ typeId: field.value.dictTypeId });
  }
});
const dictTypeChange = (typeId: string) => {
  field.value.defaultValue = field.value.multiple ? [] : undefined;
  field.value.defaultValueKey = field.value.multiple ? [] : undefined;
  field.value.dataType = dictTypeList.value.find((item: any) => item.id === typeId)?.dataType === 1 ? 'number' : 'string';
};
</script>

<!--
      { label: '单行文本', type: 'text' },
      { label: '多行文本', type: 'textarea' },
      { label: '计数器', type: 'number' },
      { label: '日期选择器', type: 'date' },
      { label: '颜色选择器', type: 'color' },
      { label: '滑块', type: 'slider' },
      { label: '单选框组', type: 'radio' },
      { label: '多选框组', type: 'checkbox' },
      { label: '下拉单选', type: 'select' },
      { label: '下拉多选', type: 'multipleSelect' },
      { label: '级联选择', type: 'cascader' },
      { label: '开关', type: 'switch' },
      { label: '富文本编辑器', type: 'tinyEditor' },
      { label: '图片上传', type: 'imageUpload' },
      { label: '视频上传', type: 'videoUpload' },
      { label: '音频上传', type: 'audioUpload' },
      { label: '文件上传', type: 'fileUpload' },
-->
<template>
  <el-form-item
    prop="code"
    :label="$t('model.field.code')"
    :rules="[
      { required: true, message: () => $t('v.required') },
      {
        validator: (rule: any, value: any, callback: any) => {
          if (!/^[a-zA-Z0-9]+$/.test(value)) {
            callback($t('model.field.error.invalidCode'));
            return;
          }
          callback();
        },
      },
    ]"
  >
    <el-input v-model="field.code" maxlength="50"></el-input>
  </el-form-item>
  <el-form-item prop="name" :label="$t('model.field.name')" :rules="{ required: true, message: () => $t('v.required') }">
    <el-input v-model="field.name" maxlength="50"></el-input>
  </el-form-item>
  <template v-if="['tinyEditor', 'textarea'].includes(field.type)">
    <el-form-item prop="clob" :label="$t('model.field.clob')">
      <template #label><label-tip message="model.field.clob" help /></template>
      <el-switch v-model="field.clob"></el-switch>
    </el-form-item>
  </template>
  <el-form-item prop="double" :label="$t('model.field.double')">
    <el-switch v-model="field.double"></el-switch>
  </el-form-item>
  <template v-if="['text', 'textarea', 'number', 'date', 'color', 'slider', 'switch', 'radio', 'checkbox', 'select', 'multipleSelect'].includes(field.type)">
    <el-form-item prop="required" :label="$t('model.field.showInList')">
      <el-switch v-model="field.showInList"></el-switch>
    </el-form-item>
  </template>
  <el-form-item prop="required" :label="$t('model.field.required')">
    <el-switch v-model="field.required"></el-switch>
  </el-form-item>
  <template v-if="['text', 'textarea', 'number', 'select', 'multipleSelect', 'videoUpload', 'audioUpload', 'fileUpload', 'tinyEditor'].includes(field.type)">
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
    <!--
    <el-form-item prop="activeValue" :label="$t('model.field.activeValue')" :rules="{ required: true, message: () => $t('v.required') }">
      <el-input v-model="field.activeValue" maxlength="20"></el-input>
    </el-form-item>
    <el-form-item prop="inactiveValue" :label="$t('model.field.inactiveValue')" :rules="{ required: true, message: () => $t('v.required') }">
      <el-input v-model="field.inactiveValue" maxlength="20"></el-input>
    </el-form-item>
     -->
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
        <el-option v-for="item in dictTypeList" :key="item.id" :value="item.id" :label="`${item.name}(${item.alias})`"></el-option>
      </el-select>
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
  <template v-if="['tinyEditor'].includes(field.type)">
    <el-form-item prop="minHeight" :label="$t('model.field.minHeight')">
      <el-input-number v-model="field.minHeight" :min="1" :max="65535"></el-input-number>
    </el-form-item>
    <el-form-item prop="maxHeight" :label="$t('model.field.maxHeight')">
      <el-input-number v-model="field.maxHeight" :min="1" :max="65535"></el-input-number>
    </el-form-item>
  </template>
</template>
