<script setup lang="ts">
import { nextTick, ref, watch, type PropType } from 'vue';
import type { SpeakerOption } from './interviewGuests';

/**
 * 发言人选择：主持人与各位嘉宾以按钮形式一键切换（直播时主持人与嘉宾频繁交替），
 * “其他”用于列表之外的发言人（如记者），展开输入框直接填写。
 */

defineOptions({
  name: 'InterviewSpeakerPicker',
});
const props = defineProps({
  modelValue: { type: String, default: '' },
  options: { type: Array as PropType<SpeakerOption[]>, default: () => [] },
  size: { type: String as PropType<'default' | 'small'>, default: 'default' },
});
const emit = defineEmits({ 'update:modelValue': null });

const OTHER = '__other__';
const otherActive = ref<boolean>(false);
const input = ref<any>();
const isKnown = (value: string) => props.options.some((item) => item.value === value);

// 外部值不在候选项中（历史发言的自定义发言人、没有主持人和嘉宾的访谈）时自动进入“其他”模式
watch(
  [() => props.modelValue, () => props.options],
  () => {
    if (props.modelValue && isKnown(props.modelValue)) {
      otherActive.value = false;
    } else if (props.modelValue || props.options.length === 0) {
      otherActive.value = true;
    }
  },
  { immediate: true },
);

const handlePick = (value: string | number | boolean | undefined) => {
  if (value === OTHER) {
    otherActive.value = true;
    emit('update:modelValue', '');
    nextTick(() => input.value?.focus());
    return;
  }
  otherActive.value = false;
  emit('update:modelValue', String(value ?? ''));
};
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <el-radio-group :model-value="otherActive ? OTHER : modelValue" :size="size" @update:model-value="handlePick">
      <el-radio-button v-for="item in options" :key="item.value" :value="item.value">
        {{ item.value }}<span class="ml-1 text-xs leading-none opacity-70">{{ item.role }}</span>
      </el-radio-button>
      <el-radio-button :value="OTHER">{{ $t('interviewMessage.speaker.other') }}</el-radio-button>
    </el-radio-group>
    <el-input
      v-if="otherActive"
      ref="input"
      :model-value="modelValue"
      :size="size"
      maxlength="50"
      :placeholder="$t('interviewMessage.speaker.placeholder')"
      class="!w-36"
      @update:model-value="(value: string) => $emit('update:modelValue', value)"
    ></el-input>
  </div>
</template>
