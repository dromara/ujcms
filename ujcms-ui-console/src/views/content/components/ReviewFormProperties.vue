<script setup lang="ts">
import { ref, watch } from 'vue';
import LabelTip from '@/components/LabelTip.vue';

const visible = defineModel<boolean>();
const props = defineProps<{ formProperties: any[] }>();
const emit = defineEmits<{ finished: [payload: any] }>();

const comment = ref<string>();
const confirm = () => {
  visible.value = false;
  const properties = props.formProperties.reduce((acc, { id, value }) => ({ ...acc, [id]: value }), {});
  emit('finished', { properties, comment: comment.value });
};
watch(visible, () => {
  if (visible.value) {
    comment.value = undefined;
  }
});
</script>

<template>
  <el-dialog v-model="visible" :title="$t('flowable.pass')" width="800" append-to-body>
    <el-form label-width="120px">
      <el-form-item v-for="property in formProperties" :key="property.id">
        <template #label><label-tip :label="property.name" /></template>
        <el-input v-if="property.type.name === 'string'" v-model="property.value" />
        <el-input v-if="property.type.name === 'long'" v-model="property.value" />
        <el-switch v-if="property.type.name === 'boolean'" v-model="property.value" />
        <el-select v-if="property.type.name === 'enum'" v-model="property.value">
          <el-option v-for="option in property.type.values" :key="option.id" :label="option.name" :value="option.value"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label><label-tip message="flowable.opinion" /></template>
        <el-input v-model="comment" />
      </el-form-item>
      <div class="flex justify-end mt-2">
        <el-button type="primary" native-type="submit" @click.prevent="confirm">{{ $t('ok') }}</el-button>
      </div>
    </el-form>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
