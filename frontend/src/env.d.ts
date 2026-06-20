/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<object, object, unknown>;
  export default component;
}

interface Window {
  wx?: {
    miniProgram?: {
      navigateTo(options: { url: string; success?: () => void; fail?: () => void }): void;
      getEnv(callback: (env: { miniprogram: boolean }) => void): void;
    };
  };
}
