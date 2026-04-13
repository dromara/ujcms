<script setup lang="ts">
import { toRefs, watch, ref } from 'vue';
import dayjs from 'dayjs';
import { queryMachineLicense } from '@/api/personal';

defineOptions({
  name: 'MachineLicense',
});
const props = defineProps({ modelValue: { type: Boolean, required: true } });
defineEmits({ 'update:modelValue': null });
const { modelValue: visible } = toRefs(props);
const loading = ref<boolean>(false);
const values = ref<any>({});
watch(visible, async () => {
  if (visible.value) {
    loading.value = true;
    try {
      values.value = await queryMachineLicense();
    } finally {
      loading.value = false;
    }
  }
});
</script>

<template>
  <el-dialog :title="$t('menu.personal.machine.license')" :model-value="modelValue" @update:model-value="(event) => $emit('update:modelValue', event)">
    <el-form v-loading="loading" :model="values" label-width="150px" label-position="right">
      <el-form-item :label="$t('license.activated')">
        <el-tag v-if="values.activated" type="success">{{ $t('license.activated.true') }}</el-tag>
        <el-tag v-else type="danger">{{ $t('license.activated.false') }}</el-tag>
      </el-form-item>
      <el-form-item v-if="!values.activated" :label="$t('license.reason')">
        <el-tag type="warning">{{ values.status != null ? $t('license.status.' + values.status) : undefined }}</el-tag>
      </el-form-item>
      <el-form-item :label="$t('license.domains')">
        <el-input :value="values.domains?.join(', ')" :readonly="true"></el-input>
      </el-form-item>
      <el-form-item :label="$t('license.names')">
        <el-input :value="values.names?.join(', ')" :readonly="true"></el-input>
      </el-form-item>
      <el-form-item :label="$t('license.expires')">
        <el-input :value="values.expires ? dayjs(values.expires).format('YYYY-MM-DD') : $t('license.expires.never')" :readonly="true"></el-input>
      </el-form-item>
      <el-form-item :label="$t('license.limit')">
        <el-input :value="values.limit === 0 ? $t('license.limit.0') : values.limit" :readonly="true"></el-input>
      </el-form-item>
      <el-form-item :label="$t('license.version')">
        <el-input :value="'V' + values.version" :readonly="true"></el-input>
      </el-form-item>
      <el-form-item :label="$t('license.rank')">
        <el-input :value="'R' + values.rank" :readonly="true"></el-input>
      </el-form-item>
      <!--
      <el-form-item :label="$t('license.subjectName')">
        <el-input :value="values.subjectName" :readonly="true"></el-input>
      </el-form-item>
      <el-form-item :label="$t('license.subjectCode')">
        <el-input :value="values.subjectCode" :readonly="true"></el-input>
      </el-form-item>
       -->
    </el-form>
  </el-dialog>
</template>
