<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete, Refresh, Promotion } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { deleteWechatMenu, publishWechatMenu, queryWechatAccountList, queryWechatMenu } from '@/api/wechat';
import { stringParam, useRouteState } from '@/composables/useRouteState';

defineOptions({
  name: 'WechatMenuForm',
});
const { t } = useI18n();
const accountList = ref<any[]>([]);
// 当前公众号存于 query.accountId（docs/spec/console-view-state），默认首个公众号：视图在刷新、页签往返时都会重建，
// 不记住就会悄悄回到首个公众号，运营者可能在错误的公众号上继续操作
const { accountId } = useRouteState({ accountId: stringParam(() => accountList.value[0]?.id) });
const buttons = ref<any[]>([]);
const conditionalCount = ref<number>(0);
const selected = ref<{ i: number; j: number | null } | null>(null);
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
// 编辑区是本地草稿，与最近一次加载（或发布）的菜单比较得出是否有未发布的修改
const loadedAccountId = ref<string>();
const loadedSnapshot = ref<string>('[]');
const snapshot = (): string => JSON.stringify(buttons.value);
const dirty = computed(() => snapshot() !== loadedSnapshot.value);

// 仅保留微信菜单创建接口支持的字段
const cleanButton = (button: any): any => {
  const result: any = { name: button.name };
  if ((button.sub_button?.length ?? 0) > 0) {
    result.sub_button = button.sub_button.map(cleanButton);
    return result;
  }
  result.type = button.type ?? 'view';
  if (result.type === 'view') {
    result.url = button.url;
  } else if (result.type === 'click') {
    result.key = button.key;
  } else if (result.type === 'miniprogram') {
    result.appid = button.appid;
    result.pagepath = button.pagepath;
    result.url = button.url;
  }
  return result;
};

const fetchMenu = async () => {
  if (accountId.value == null) return;
  loading.value = true;
  selected.value = null;
  try {
    const result = await queryWechatMenu(accountId.value);
    buttons.value = result?.menu?.button ?? [];
    conditionalCount.value = result?.conditionalmenu?.length ?? 0;
    loadedAccountId.value = accountId.value;
    loadedSnapshot.value = snapshot();
  } finally {
    loading.value = false;
  }
};
const handleReload = async () => {
  if (dirty.value) {
    await ElMessageBox.confirm(t('wechatMenu.reloadConfirm'), { type: 'warning' });
  }
  await fetchMenu();
};
const handleAccountChange = async () => {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm(t('wechatMenu.switchConfirm'), { type: 'warning' });
    } catch {
      accountId.value = loadedAccountId.value;
      return;
    }
  }
  await fetchMenu();
};
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
  if (accountList.value.length > 0) {
    // 恢复出的公众号须仍在列表中（切换站点后整页重载而地址不变、公众号已删除），否则回落到首个
    if (!accountList.value.some((item) => item.id === accountId.value)) {
      accountId.value = accountList.value[0].id;
    }
    fetchMenu();
  }
};
onMounted(fetchAccountList);

const currentButton = computed(() => {
  if (selected.value == null) return null;
  const { i, j } = selected.value;
  return j == null ? buttons.value[i] : buttons.value[i]?.sub_button?.[j];
});

const handleSelect = (i: number, j: number | null) => {
  selected.value = { i, j };
  const button = j == null ? buttons.value[i] : buttons.value[i]?.sub_button?.[j];
  if (button != null && (button.sub_button?.length ?? 0) === 0 && button.type == null) {
    button.type = 'view';
  }
};
const handleAddTop = () => {
  buttons.value.push({ name: t('wechatMenu.newButton'), type: 'view', url: '' });
  handleSelect(buttons.value.length - 1, null);
};
const handleAddSub = (i: number) => {
  const parent = buttons.value[i];
  if (parent.sub_button == null) {
    parent.sub_button = [];
  }
  parent.sub_button.push({ name: t('wechatMenu.newButton'), type: 'view', url: '' });
  handleSelect(i, parent.sub_button.length - 1);
};
const handleRemove = () => {
  if (selected.value == null) return;
  const { i, j } = selected.value;
  if (j == null) {
    buttons.value.splice(i, 1);
  } else {
    buttons.value[i].sub_button.splice(j, 1);
  }
  selected.value = null;
};

const handlePublish = async () => {
  if (accountId.value == null) return;
  buttonLoading.value = true;
  try {
    await publishWechatMenu({ accountId: accountId.value, menu: { button: buttons.value.map(cleanButton) } });
    loadedSnapshot.value = snapshot();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};
const handleDelete = async () => {
  if (accountId.value == null) return;
  await deleteWechatMenu({ accountId: accountId.value });
  buttons.value = [];
  loadedSnapshot.value = snapshot();
  selected.value = null;
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" :placeholder="$t('wechat.selectAccount')" class="w-48" @change="() => handleAccountChange()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-button :disabled="accountId == null" :icon="Refresh" @click="() => handleReload()">{{ $t('wechatMenu.op.load') }}</el-button>
      <el-popconfirm :title="$t('wechatMenu.publishConfirm')" @confirm="() => handlePublish()">
        <template #reference>
          <el-button type="primary" :disabled="accountId == null || buttons.length <= 0 || perm('wechatMenu:update')" :loading="buttonLoading" :icon="Promotion">
            {{ $t('wechatMenu.op.publish') }}
          </el-button>
        </template>
      </el-popconfirm>
      <el-popconfirm :title="$t('wechatMenu.deleteConfirm')" @confirm="() => handleDelete()">
        <template #reference>
          <el-button type="danger" plain :disabled="accountId == null || perm('wechatMenu:delete')" :icon="Delete">{{ $t('wechatMenu.op.delete') }}</el-button>
        </template>
      </el-popconfirm>
    </div>
    <el-alert v-if="accountList.length <= 0" :title="$t('wechat.noAccount')" type="info" :closable="false" class="mt-3" />
    <el-alert v-if="conditionalCount > 0" :title="$t('wechatMenu.conditionalTip', { count: conditionalCount })" type="warning" :closable="false" class="mt-3" />
    <div v-loading="loading" class="flex mt-3 space-x-3">
      <!-- 菜单模拟器 -->
      <div class="app-block w-80 shrink-0 p-3">
        <div class="mb-2 text-xs" :class="dirty ? 'text-warning' : 'text-gray-secondary'">{{ dirty ? $t('wechatMenu.dirtyTip') : $t('wechatMenu.draftTip') }}</div>
        <div class="border rounded">
          <div v-for="(button, i) in buttons" :key="i" class="border-b last:border-b-0">
            <div
              class="px-3 py-2 cursor-pointer flex items-center justify-between"
              :class="selected?.i === i && selected?.j == null ? 'bg-primary-light text-primary' : ''"
              @click="() => handleSelect(i, null)"
            >
              <span class="truncate">{{ button.name }}</span>
              <el-tag v-if="(button.sub_button?.length ?? 0) > 0" size="small" type="info">{{ button.sub_button.length }}</el-tag>
            </div>
            <div v-for="(sub, j) in button.sub_button ?? []" :key="j">
              <div
                class="pl-8 pr-3 py-1.5 cursor-pointer text-sm"
                :class="selected?.i === i && selected?.j === j ? 'bg-primary-light text-primary' : 'text-gray-secondary'"
                @click="() => handleSelect(i, Number(j))"
              >
                {{ sub.name }}
              </div>
            </div>
            <div v-if="(button.sub_button?.length ?? 0) < 5" class="pl-8 pr-3 py-1.5">
              <el-button size="small" link :icon="Plus" @click="() => handleAddSub(i)">{{ $t('wechatMenu.addSub') }}</el-button>
            </div>
          </div>
          <div v-if="buttons.length < 3" class="px-3 py-2">
            <el-button size="small" link type="primary" :icon="Plus" @click="() => handleAddTop()">{{ $t('wechatMenu.addTop') }}</el-button>
          </div>
        </div>
      </div>
      <!-- 按钮编辑表单 -->
      <div class="app-block grow p-4">
        <template v-if="currentButton != null">
          <el-form label-width="150px">
            <el-form-item :label="$t('wechatMenu.name')">
              <el-input v-model="currentButton.name" maxlength="16" class="max-w-md"></el-input>
            </el-form-item>
            <template v-if="(currentButton.sub_button?.length ?? 0) === 0">
              <el-form-item :label="$t('wechatMenu.type')">
                <el-radio-group v-model="currentButton.type">
                  <el-radio-button v-for="item in ['view', 'click', 'miniprogram']" :key="item" :value="item">
                    {{ $t(`wechatMenu.type.${item}`) }}
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="currentButton.type === 'view' || currentButton.type === 'miniprogram'" :label="$t('wechatMenu.url')">
                <el-input v-model="currentButton.url" maxlength="1024" class="max-w-md" placeholder="https://"></el-input>
              </el-form-item>
              <el-form-item v-if="currentButton.type === 'click'" :label="$t('wechatMenu.key')">
                <el-input v-model="currentButton.key" maxlength="128" class="max-w-md"></el-input>
              </el-form-item>
              <template v-if="currentButton.type === 'miniprogram'">
                <el-form-item :label="$t('wechatMenu.appId')">
                  <el-input v-model="currentButton.appid" maxlength="32" class="max-w-md"></el-input>
                </el-form-item>
                <el-form-item :label="$t('wechatMenu.pagePath')">
                  <el-input v-model="currentButton.pagepath" maxlength="128" class="max-w-md"></el-input>
                </el-form-item>
              </template>
            </template>
            <el-form-item>
              <el-button :icon="Delete" @click="() => handleRemove()">{{ $t('wechatMenu.removeButton') }}</el-button>
            </el-form-item>
          </el-form>
        </template>
        <el-empty v-else :description="$t('wechatMenu.selectTip')" />
      </div>
    </div>
  </div>
</template>
