<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { storeToRefs } from 'pinia';
import { Fold, Expand, HomeFilled, User, SwitchButton } from '@element-plus/icons-vue';
import { setCookieLocale } from '@/utils/common';
import { languages } from '@/i18n';
import { queryCurrentSiteList } from '@/api/login';
import { currentUser, perm, hasPermission, isInclude, logout } from '@/stores/useCurrentUser';
import { useAppStateStore } from '@/stores/appStateStore';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import BreadCrumb from '@/components/BreadCrumb/index.vue';
import PasswordForm from '@/views/personal/PasswordForm.vue';
import MachineCode from '@/views/personal/MachineCode.vue';
import MachineLicense from '@/views/personal/MachineLicense.vue';
import SystemInfo from '@/views/personal/SystemInfo.vue';
import SystemMonitor from '@/views/personal/SystemMonitor.vue';
import GeneratedKey from '@/views/personal/GeneratedKey.vue';

const { locale } = useI18n({ useScope: 'global' });

const appState = useAppStateStore();
const siteList = ref<any[]>([]);
const currentSiteStore = useCurrentSiteStore();
const { getCurrentSiteId, setCurrentSiteId } = currentSiteStore;
const { currentSite } = storeToRefs(currentSiteStore);
const fetchSiteList = async () => {
  siteList.value = await queryCurrentSiteList();
  currentSite.value = siteList.value.find((item) => item.id === getCurrentSiteId()) ?? siteList.value[0];
};
const changeSiteId = (id: string) => {
  setCurrentSiteId(id);
  window.location.reload();
};
onMounted(() => {
  fetchSiteList();
});

const changeLocale = (lang: string) => {
  locale.value = lang;
  setCookieLocale(lang);
};
const handleLogout = () => {
  logout();
  // router.push(`/login?redirect=${route.fullPath}`);
  window.location.reload();
};

const passwordFormVisible = ref<boolean>(false);
const machineCodeVisible = ref<boolean>(false);
const machineLicenseVisible = ref<boolean>(false);
const systemInfoVisible = ref<boolean>(false);
const systemMonitorVisible = ref<boolean>(false);
const generatedKeyVisible = ref<boolean>(false);
</script>

<template>
  <div class="flex items-center justify-between h-12 overflow-hidden bg-white shadow">
    <div class="flex items-center h-full">
      <div class="flex items-center h-full px-4 align-middle cursor-pointer hover:bg-gray-100" @click="() => appState.toggleSidebar()">
        <el-icon :size="18"><component :is="appState.sidebar ? Fold : Expand"></component></el-icon>
      </div>
      <bread-crumb class="inline-block" />
    </div>
    <div class="h-full">
      <div v-if="currentSite" class="inline-block h-full">
        <el-link class="h-full px-3 hover:bg-gray-100" :href="currentSite.url" :underline="false" :title="$t('siteHome')" target="_blank">
          <el-icon :size="16" class="align-text-top"><HomeFilled /></el-icon>
        </el-link>
      </div>
      <el-dropdown v-if="currentSite" class="h-full">
        <div class="flex items-center h-full px-3 hover:bg-gray-100">
          {{ currentSite.name }}
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="item in siteList" :key="item.id" :disabled="currentSite?.id === item.id" @click="() => changeSiteId(item.id)">
              <span :style="{ 'margin-left': item.depth * 16 + 'px' }">{{ item.name }}</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown class="h-full">
        <div class="flex items-center h-full px-3 hover:bg-gray-100">
          {{ languages[(locale as string) || 'zh-cn'] }}
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="(name, lang) in languages" :key="lang" :disabled="locale === lang" @click="() => changeLocale(lang)">{{ name }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown class="h-full">
        <div class="flex items-center h-full px-3 hover:bg-gray-100">
          <el-icon><User /></el-icon>
          <span class="ml-1">{{ currentUser.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <!--
            <router-link to="/">
              <el-dropdown-item>Home</el-dropdown-item>
            </router-link>
             -->
            <el-dropdown-item :disabled="perm('password:update')" @click="() => (passwordFormVisible = true)">{{ $t('changePassword') }}</el-dropdown-item>
            <el-dropdown-item divided :disabled="perm('homepage:systemInfo')" @click="() => (systemInfoVisible = true)">
              {{ $t('menu.personal.homepage.systemInfo') }}
            </el-dropdown-item>
            <el-dropdown-item v-if="hasPermission('homepage:systemMonitor') && isInclude('homepage:systemMonitor')" @click="() => (systemMonitorVisible = true)">
              {{ $t('menu.personal.homepage.systemMonitor') }}
            </el-dropdown-item>
            <el-dropdown-item divided :disabled="perm('homepage:generatedKey')" @click="() => (generatedKeyVisible = true)">
              {{ $t('menu.personal.homepage.generatedKey') }}
            </el-dropdown-item>
            <el-dropdown-item v-if="hasPermission('machine:code') && isInclude('machine:code')" divided @click="() => (machineCodeVisible = true)">
              {{ $t('menu.personal.machine.code') }}
            </el-dropdown-item>
            <el-dropdown-item v-if="hasPermission('machine:license') && isInclude('machine:license')" @click="() => (machineLicenseVisible = true)">
              {{ $t('menu.personal.machine.license') }}
            </el-dropdown-item>
            <el-dropdown-item divided :icon="SwitchButton" @click="() => handleLogout()">{{ $t('logout') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <password-form v-model="passwordFormVisible" />
    <system-info v-if="hasPermission('homepage:systemInfo')" v-model="systemInfoVisible"></system-info>
    <system-monitor v-if="hasPermission('homepage:systemMonitor') && isInclude('homepage:systemMonitor')" v-model="systemMonitorVisible"></system-monitor>
    <generated-key v-if="hasPermission('homepage:generatedKey')" v-model="generatedKeyVisible"></generated-key>
    <machine-code v-if="hasPermission('machine:code') && isInclude('machine:code')" v-model="machineCodeVisible"></machine-code>
    <machine-license v-if="hasPermission('machine:license') && isInclude('machine:license')" v-model="machineLicenseVisible"></machine-license>
  </div>
</template>
