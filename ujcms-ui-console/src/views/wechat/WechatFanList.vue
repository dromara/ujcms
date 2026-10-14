<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { blacklistWechatFan, queryWechatAccountList, queryWechatFanList, queryWechatTagList, taggingWechatFan, updateWechatFanRemark } from '@/api/wechat';
import { enumParam, stringParam, useRouteState } from '@/composables/useRouteState';

defineOptions({
  name: 'WechatFanList',
});
const { t } = useI18n();
const accountList = ref<any[]>([]);
// 当前公众号存于 query.accountId（docs/spec/console-view-state），默认首个公众号：视图在刷新、页签往返时都会重建，
// 不记住就会悄悄回到首个公众号，运营者可能在错误的公众号上继续操作
const { accountId, type } = useRouteState({
  accountId: stringParam(() => accountList.value[0]?.id),
  type: enumParam('all', ['all', 'blacklist']),
});
const currentPage = ref<number>(1);
const total = ref<number>(0);
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const tagList = ref<any[]>([]);
const buttonLoading = ref<boolean>(false);

const remarkVisible = ref<boolean>(false);
const remarkValues = ref<any>({});

const taggingVisible = ref<boolean>(false);
const tagging = ref<boolean>(true);
const taggingTagId = ref<string>();

/** 关注来源：微信返回 ADD_SCENE_* 代码，未翻译的代码原样显示（i18n 为扁平键，te() 不可用，需与 t() 结果比较） */
const sceneName = (scene?: string): string => {
  if (!scene) return '';
  const key = `wechatFan.scene.${scene}`;
  const name = t(key);
  return name === key ? scene : name;
};
const tagNames = computed(() => {
  const map: Record<string, string> = {};
  tagList.value.forEach((item) => {
    map[String(item.id)] = item.name;
  });
  return map;
});

const fetchData = async () => {
  if (accountId.value == null) {
    data.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await queryWechatFanList({ accountId: accountId.value, type: type.value, page: currentPage.value });
    data.value = result.items ?? [];
    total.value = Number(result.total ?? 0);
  } finally {
    loading.value = false;
  }
};
const fetchTagList = async () => {
  if (accountId.value == null) {
    tagList.value = [];
    return;
  }
  tagList.value = await queryWechatTagList(accountId.value);
};
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
  if (accountList.value.length > 0) {
    // 恢复出的公众号须仍在列表中（切换站点后整页重载而地址不变、公众号已删除），否则回落到首个
    if (!accountList.value.some((item) => item.id === accountId.value)) {
      accountId.value = accountList.value[0].id;
    }
    fetchData();
    fetchTagList();
  }
};
onMounted(fetchAccountList);

const handleAccountChange = () => {
  currentPage.value = 1;
  fetchData();
  fetchTagList();
};
const handleTypeChange = () => {
  currentPage.value = 1;
  fetchData();
};

const handleRemark = (row: any) => {
  remarkValues.value = { openid: row.openid, remark: row.remark };
  remarkVisible.value = true;
};
const handleRemarkSubmit = async () => {
  if (accountId.value == null) return;
  buttonLoading.value = true;
  try {
    await updateWechatFanRemark({ accountId: accountId.value, ...remarkValues.value });
    remarkVisible.value = false;
    fetchData();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};

const handleTagging = (isTagging: boolean) => {
  tagging.value = isTagging;
  taggingTagId.value = undefined;
  taggingVisible.value = true;
};
const handleTaggingSubmit = async () => {
  if (accountId.value == null || taggingTagId.value == null) return;
  buttonLoading.value = true;
  try {
    await taggingWechatFan({
      accountId: accountId.value,
      tagId: taggingTagId.value,
      openids: selection.value.map((row) => row.openid),
      tagging: tagging.value,
    });
    taggingVisible.value = false;
    fetchData();
    fetchTagList();
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
  }
};

const handleBlacklist = async (isBlacklist: boolean) => {
  if (accountId.value == null) return;
  await blacklistWechatFan({ accountId: accountId.value, openids: selection.value.map((row) => row.openid), blacklist: isBlacklist });
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" :placeholder="$t('wechat.selectAccount')" class="w-48" @change="() => handleAccountChange()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <template v-if="type === 'all'">
        <el-button :disabled="selection.length <= 0 || perm('wechatFan:tagging')" @click="() => handleTagging(true)">
          {{ $t('wechatFan.op.tagging') }}
        </el-button>
        <el-button :disabled="selection.length <= 0 || perm('wechatFan:tagging')" @click="() => handleTagging(false)">
          {{ $t('wechatFan.op.untagging') }}
        </el-button>
        <el-popconfirm :title="$t('wechatFan.blacklistConfirm')" @confirm="() => handleBlacklist(true)">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('wechatFan:blacklist')">{{ $t('wechatFan.op.blacklist') }}</el-button>
          </template>
        </el-popconfirm>
      </template>
      <el-popconfirm v-else :title="$t('wechatFan.unblacklistConfirm')" @confirm="() => handleBlacklist(false)">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('wechatFan:blacklist')">{{ $t('wechatFan.op.unblacklist') }}</el-button>
        </template>
      </el-popconfirm>
    </div>
    <el-alert v-if="accountList.length <= 0" :title="$t('wechat.noAccount')" type="info" :closable="false" class="mt-3" />
    <div class="mt-3 app-block">
      <el-tabs v-model="type" class="px-3" @tab-change="() => handleTypeChange()">
        <el-tab-pane v-for="item in ['all', 'blacklist']" :key="item" :name="item" :label="$t(`wechatFan.type.${item}`)"></el-tab-pane>
      </el-tabs>
      <el-table v-loading="loading" :row-key="(row: any) => row.openid" :data="data" @selection-change="(rows: any[]) => (selection = rows)">
        <el-table-column type="selection" width="38"></el-table-column>
        <!-- 微信自 2021-12-27 起不再通过接口提供昵称和头像，备注是运营者唯一可辨认的标识，故置于首列 -->
        <el-table-column property="remark" :label="$t('wechatFan.remark')" width="160" show-overflow-tooltip></el-table-column>
        <el-table-column property="openid" label="OpenID" :min-width="240" show-overflow-tooltip></el-table-column>
        <el-table-column :label="$t('wechatFan.tags')" :min-width="160">
          <template #default="{ row }">
            <el-tag v-for="tagId in row.tagIds" :key="tagId" size="small" class="mr-1">{{ tagNames[String(tagId)] ?? tagId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('wechatFan.subscribeScene')" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ sceneName(row.subscribeScene) }}</template>
        </el-table-column>
        <el-table-column property="subscribeTime" :label="$t('wechatFan.subscribeTime')" width="170">
          <template #default="{ row }">{{ row.subscribeTime ? dayjs(row.subscribeTime).format('YYYY-MM-DD HH:mm:ss') : '' }}</template>
        </el-table-column>
        <el-table-column :label="$t('table.action')" width="100">
          <template #default="{ row }">
            <el-button type="primary" :disabled="perm('wechatFan:remark')" size="small" link @click="() => handleRemark(row)">
              {{ $t('wechatFan.op.remark') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        :total="total"
        :page-size="20"
        layout="total, prev, pager, next"
        size="small"
        background
        class="justify-end px-3 py-2"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
    <el-dialog v-model="remarkVisible" :title="$t('wechatFan.op.remark')" :width="512">
      <el-form label-position="top">
        <el-form-item label="OpenID">
          <el-input :model-value="remarkValues.openid" disabled></el-input>
        </el-form-item>
        <el-form-item :label="$t('wechatFan.remark')">
          <el-input v-model="remarkValues.remark" maxlength="30"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="remarkVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="buttonLoading" @click="() => handleRemarkSubmit()">{{ $t('save') }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="taggingVisible" :title="$t(tagging ? 'wechatFan.op.tagging' : 'wechatFan.op.untagging')" :width="512">
      <el-form label-position="top">
        <el-form-item :label="$t('wechatFan.tags')">
          <el-select v-model="taggingTagId" :placeholder="$t('wechatFan.selectTag')" class="w-full">
            <el-option v-for="item in tagList" :key="item.id" :label="item.name" :value="item.id"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taggingVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="buttonLoading" :disabled="taggingTagId == null" @click="() => handleTaggingSubmit()">
          {{ $t('save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
