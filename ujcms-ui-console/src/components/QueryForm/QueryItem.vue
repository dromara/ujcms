<script setup lang="ts">
import { inject, PropType, ref, toRefs } from 'vue';

defineOptions({
  name: 'QueryItem',
});
const props = defineProps({
  label: { type: String, required: true },
  name: { type: String, required: true },
  // 'string' | 'date' | 'datetime' | 'number'
  type: { type: String, default: null },
  options: { type: Object as PropType<Array<{ label: string; value: string | number }>>, default: null },
  multiple: { type: Boolean, default: true },
});
const params = inject<any>('params');
const { name } = toRefs(props);
const [firstName, secondName] = name.value.split(',');
const first = ref<string>(firstName);
const second = ref<string>(secondName);
</script>

<template>
  <slot>
    <div v-if="type === 'number'" class="inline-block">
      <el-input-number v-model="params[first]" :placeholder="$t('begin.number')" class="w-48"></el-input-number>
      <el-input-number v-model="params[second]" :placeholder="$t('end.number')" class="w-48"></el-input-number>
    </div>
    <el-date-picker
      v-else-if="type === 'date'"
      v-model="params[name]"
      type="daterange"
      :start-placeholder="$t('begin.date')"
      :end-placeholder="$t('end.date')"
      :editable="false"
      class="w-96"
    ></el-date-picker>
    <el-date-picker
      v-else-if="type === 'datetime'"
      v-model="params[name]"
      type="datetimerange"
      :start-placeholder="$t('begin.date')"
      :end-placeholder="$t('end.date')"
      :editable="false"
      class="w-96"
    >
    </el-date-picker>
    <!--
    <div v-else-if="type === 'date'" class="inline-block">
      <el-date-picker v-model="params[first]" type="date" :placeholder="$t('begin.date')" class="w-48"></el-date-picker>
      <el-date-picker v-model="params[second]" type="date" :placeholder="$t('end.date')" class="w-48"></el-date-picker>
    </div>
    <div v-else-if="type === 'datetime'" class="inline-block">
      <el-date-picker v-model="params[first]" type="datetime" class="w-48"></el-date-picker>
      <el-date-picker v-model="params[second]" type="datetime" class="w-48"></el-date-picker>
    </div>
    -->
    <el-select v-else-if="options" v-model="params[name]" :multiple="multiple" class="w-96">
      <el-option v-for="item in options" :key="item.value" :label="item.label" :value="item.value"></el-option>
    </el-select>
    <el-input v-else v-model="params[name]" class="w-96"></el-input>
  </slot>
</template>
