/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<object, object, unknown>;
  export default component;
}

interface Window {
  wx?: {
    miniProgram?: {
      getEnv(callback: (env: { miniprogram: boolean }) => void): void;
      navigateTo(options: { url: string; fail?: () => void }): void;
    };
  };
}
