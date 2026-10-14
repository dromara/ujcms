<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { queryWechatReply, createWechatReply, updateWechatReply, deleteWechatReply } from '@/api/wechat';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'WechatReplyForm',
});
defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  accountId: { type: String, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});

// 图文回复的表单字段与 content JSON 互转
const toValues = (bean: any): any => {
  const vals = { ...bean };
  if (vals.replyType === 2 && vals.content) {
    try {
      const [news] = JSON.parse(vals.content);
      vals.newsTitle = news?.title;
      vals.newsDescription = news?.description;
      vals.newsPicUrl = news?.picUrl;
      vals.newsUrl = news?.url;
    } catch {
      // 内容不是合法JSON时忽略，保存后覆盖
    }
  }
  return vals;
};
const toBean = (vals: any): any => {
  const { newsTitle, newsDescription, newsPicUrl, newsUrl, ...bean } = vals;
  if (bean.replyType === 2) {
    bean.content = JSON.stringify([{ title: newsTitle, description: newsDescription, picUrl: newsPicUrl, url: newsUrl }]);
  }
  return bean;
};
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.wechat.wechatReply')"
    :query-bean="queryWechatReply"
    :create-bean="(data) => createWechatReply(toBean(data))"
    :update-bean="(data) => updateWechatReply(toBean(data))"
    :delete-bean="deleteWechatReply"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ accountId, type: 1, matchType: 0, replyType: 0, enabled: true })"
    :to-values="toValues"
    :model-value="modelValue"
    perms="wechatReply"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="type">
        <template #label><label-tip message="wechatReply.type" help /></template>
        <el-radio-group v-model="values.type">
          <el-radio-button v-for="n in [1, 0, 2]" :key="n" :value="n">{{ $t(`wechatReply.type.${n}`) }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="values.type === 1">
        <el-form-item prop="keyword" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="wechatReply.keyword" /></template>
          <el-input ref="focus" v-model="values.keyword" maxlength="30"></el-input>
        </el-form-item>
        <el-form-item prop="matchType">
          <template #label><label-tip message="wechatReply.matchType" help /></template>
          <el-radio-group v-model="values.matchType">
            <el-radio-button v-for="n in [0, 1]" :key="n" :value="n">{{ $t(`wechatReply.matchType.${n}`) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </template>
      <el-form-item prop="replyType">
        <template #label><label-tip message="wechatReply.replyType" /></template>
        <el-radio-group v-model="values.replyType">
          <el-radio-button v-for="n in [0, 1, 2]" :key="n" :value="n">{{ $t(`wechatReply.replyType.${n}`) }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="values.replyType === 0" prop="content" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="wechatReply.content" /></template>
        <el-input v-model="values.content" type="textarea" :rows="4" maxlength="600"></el-input>
      </el-form-item>
      <el-form-item v-if="values.replyType === 1" prop="mediaId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="wechatReply.mediaId" help /></template>
        <el-input v-model="values.mediaId" maxlength="64"></el-input>
      </el-form-item>
      <template v-if="values.replyType === 2">
        <el-form-item prop="newsTitle" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="wechatReply.newsTitle" /></template>
          <el-input v-model="values.newsTitle" maxlength="64"></el-input>
        </el-form-item>
        <el-form-item prop="newsDescription">
          <template #label><label-tip message="wechatReply.newsDescription" /></template>
          <el-input v-model="values.newsDescription" type="textarea" :rows="2" maxlength="120"></el-input>
        </el-form-item>
        <el-form-item prop="newsPicUrl">
          <template #label><label-tip message="wechatReply.newsPicUrl" /></template>
          <el-input v-model="values.newsPicUrl" maxlength="300"></el-input>
        </el-form-item>
        <el-form-item prop="newsUrl" :rules="{ required: true, message: () => $t('v.required') }">
          <template #label><label-tip message="wechatReply.newsUrl" /></template>
          <el-input v-model="values.newsUrl" maxlength="300"></el-input>
        </el-form-item>
      </template>
      <el-form-item prop="enabled">
        <template #label><label-tip message="wechatReply.enabled" /></template>
        <el-switch v-model="values.enabled"></el-switch>
      </el-form-item>
    </template>
  </dialog-form>
</template>
