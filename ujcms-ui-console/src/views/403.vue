<script lang="ts">
import { defineComponent, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { logout } from '@/stores/useCurrentUser';

export default defineComponent({
  name: 'ErrorPage403',
  setup() {
    const { t } = useI18n();
    const router = useRouter();
    const message = ref(t('error.forbidden'));
    const handleLogout = () => {
      logout();
      router.push('/login');
    };
    return { message, handleLogout };
  },
});
</script>

<template>
  <div class="h-full p-4 bg-gray-200">
    <div class="p-4 rounded shadow bg-white">
      <h1 class="font-bold text-3xl">403</h1>
      <p class="mt-4">{{ message }}</p>
      <p class="mt-4">
        <el-button type="primary" plain @click="() => handleLogout()">{{ $t('logout') }}</el-button>
      </p>
    </div>
  </div>
</template>
