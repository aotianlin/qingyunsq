import { h, type Component } from 'vue';
import { NIcon } from 'naive-ui';
import type { MenuOption } from 'naive-ui';

export function renderIcon(icon: Component): MenuOption['icon'] {
  return () => h(NIcon, null, { default: () => h(icon) });
}
