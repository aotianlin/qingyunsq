<script setup lang="ts">
import { useRouter } from 'vue-router';
import { NIcon } from 'naive-ui';
import { ArrowBackOutline, CompassOutline, HomeOutline } from '@vicons/ionicons5';

const router = useRouter();
</script>

<template>
  <div class="not-found">
    <section class="not-found-card">
      <div class="status-badge">404</div>
      <h1>页面未找到</h1>
      <p>这个入口可能已经移动或暂时不可访问，回到广场继续浏览会更稳妥。</p>
      <div class="actions">
        <button
          class="primary-action"
          @click="router.push('/square')"
        >
          <NIcon size="18"><HomeOutline /></NIcon>
          返回广场
        </button>
        <button
          class="ghost-action"
          @click="router.back()"
        >
          <NIcon size="18"><ArrowBackOutline /></NIcon>
          返回上一页
        </button>
      </div>
      <div class="hint-row">
        <NIcon size="18"><CompassOutline /></NIcon>
        <span>也可以通过顶部导航重新进入学习圈、资源或打卡模块。</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.not-found {
  min-height: calc(100vh - 120px);
  display: grid;
  place-items: center;
  padding: 40px 16px;
}

.not-found-card {
  width: min(640px, 100%);
  padding: 38px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow:
    0 24px 70px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(24px) saturate(150%);
  text-align: center;
}

.status-badge {
  width: 86px;
  height: 86px;
  margin: 0 auto 18px;
  border-radius: 24px;
  display: grid;
  place-items: center;
  background: var(--cf-primary-soft);
  color: var(--cf-primary);
  font-family: var(--cf-font-heading);
  font-size: 26px;
  font-weight: 900;
}

h1 {
  margin: 0;
  font-family: var(--cf-font-heading);
  font-size: 34px;
}

p {
  margin: 12px auto 0;
  max-width: 430px;
  color: var(--cf-text-secondary);
  line-height: 1.8;
}

.actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}

button {
  height: 44px;
  padding: 0 18px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  font-weight: 800;
  transition: all 0.22s var(--cf-motion-ease);
}

button:hover {
  transform: translateY(-1px);
}

.primary-action {
  background: var(--cf-primary);
  color: var(--cf-text-inverse);
  box-shadow: 0 14px 30px color-mix(in srgb, var(--cf-primary) 26%, transparent);
}

.ghost-action {
  background: var(--cf-bg-elevated);
  color: var(--cf-text-secondary);
  border-color: var(--cf-border);
}

.ghost-action:hover {
  color: var(--cf-primary);
  border-color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.hint-row {
  margin-top: 24px;
  padding: 12px 14px;
  border-radius: 16px;
  background: var(--cf-bg-soft);
  color: var(--cf-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 1.5;
}

@media (max-width: 560px) {
  .not-found-card {
    padding: 28px 20px;
  }

  h1 {
    font-size: 28px;
  }

  .actions button {
    width: 100%;
    justify-content: center;
  }
}
</style>
