import { computed, readonly, ref } from 'vue';

export type ThemeMode = 'light' | 'dark';

const THEME_STORAGE_KEY = 'campus-theme';
const theme = ref<ThemeMode>('light');
let initialized = false;

function getStoredTheme(): ThemeMode | null {
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  return stored === 'light' || stored === 'dark' ? stored : null;
}

function getPreferredTheme(): ThemeMode {
  const stored = getStoredTheme();
  if (stored) return stored;
  return window.matchMedia?.('(prefers-color-scheme: dark)')?.matches ? 'dark' : 'light';
}

function updateThemeMeta(mode: ThemeMode) {
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (!meta) return;
  meta.content = mode === 'dark' ? '#050505' : '#ffffff';
}

function applyTheme(mode: ThemeMode) {
  // 统一把主题状态同步到响应式数据、DOM 标记、本地缓存和浏览器地址栏主题色，确保 Naive UI 与自定义 CSS 变量保持一致。
  theme.value = mode;
  document.documentElement.dataset.theme = mode;
  document.documentElement.style.colorScheme = mode;

  if (mode === 'dark') {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }

  localStorage.setItem(THEME_STORAGE_KEY, mode);
  updateThemeMeta(mode);
}

export function initTheme() {
  if (initialized) return;
  initialized = true;
  applyTheme(getPreferredTheme());
}

export function useTheme() {
  initTheme();

  const isDarkTheme = computed(() => theme.value === 'dark');

  function setTheme(mode: ThemeMode) {
    applyTheme(mode);
  }

  function toggleTheme() {
    applyTheme(isDarkTheme.value ? 'light' : 'dark');
  }

  return {
    theme: readonly(theme),
    isDarkTheme,
    setTheme,
    toggleTheme,
  };
}
