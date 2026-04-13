import { watch, onBeforeMount, onBeforeUnmount, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useAppStateStore } from '@/stores/appStateStore';

const { body } = document;
const WIDTH = 992; // refer to Bootstrap's responsive design

export default function useResizeHandler() {
  const route = useRoute();
  const appState = useAppStateStore();

  const isMobile = () => {
    const rect = body.getBoundingClientRect();
    return rect.width - 1 < WIDTH;
  };

  const resizeHandler = () => {
    if (!document.hidden && isMobile()) appState.closeSidebar();
  };

  watch(
    () => route.path,
    () => {
      if (isMobile() && appState.sidebar) appState.closeSidebar();
    },
  );

  onBeforeMount(() => {
    window.addEventListener('resize', resizeHandler);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resizeHandler);
  });

  onMounted(() => {
    if (isMobile()) {
      appState.closeSidebar();
    }
  });

  return { isMobile, resizeHandler };
}
