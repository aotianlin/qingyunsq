<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { NIcon } from 'naive-ui';
import { MegaphoneOutline, SparklesOutline } from '@vicons/ionicons5';
import type { OfficialAnnouncementItem } from '@/types/announce';

const props = withDefaults(
  defineProps<{
    items: readonly OfficialAnnouncementItem[];
    autoplay?: boolean;
    intervalMs?: number;
  }>(),
  {
    autoplay: true,
    intervalMs: 4000,
  },
);

const emit = defineEmits<{
  (event: 'open', item: OfficialAnnouncementItem): void;
}>();

const activeIndex = ref(0);
const paused = ref(false);
let timer: number | undefined;

const activeItem = computed(() => props.items[activeIndex.value]);
const activeBadge = computed(() => activeItem.value?.badge || activeItem.value?.meta || '官方公告');
const activeDescription = computed(() => activeItem.value?.description || activeItem.value?.summary || '');
const activeButtonText = computed(() => activeItem.value?.buttonText || '查看详情');
const activeTitle = computed(() => (activeItem.value?.title || '').replace(/[。.]$/, ''));
const hasMultiple = computed(() => props.items.length > 1);

function getIntervalMs() {
  if (props.intervalMs > 0) return props.intervalMs;
  if (typeof window === 'undefined') return 0;
  const token = window
    .getComputedStyle(document.documentElement)
    .getPropertyValue('--cf-announcement-interval')
    .trim();
  const value = Number.parseFloat(token);
  if (!Number.isFinite(value)) return 0;
  return token.endsWith('s') && !token.endsWith('ms') ? value * 1000 : value;
}

function stop() {
  if (timer !== undefined) {
    window.clearTimeout(timer);
    timer = undefined;
  }
}

function schedule() {
  stop();
  if (!props.autoplay || paused.value || !hasMultiple.value) return;
  const interval = getIntervalMs();
  if (interval <= 0) return;
  timer = window.setTimeout(() => {
    activeIndex.value = (activeIndex.value + 1) % props.items.length;
    schedule();
  }, interval);
}

function setPaused(value: boolean) {
  paused.value = value;
  schedule();
}

function go(index: number) {
  activeIndex.value = index;
  schedule();
}

function openActive() {
  if (!activeItem.value) return;
  emit('open', activeItem.value);
}

watch(
  () => props.items.length,
  (length) => {
    if (activeIndex.value >= length) activeIndex.value = 0;
    schedule();
  },
);

onMounted(schedule);
onBeforeUnmount(stop);
</script>

<template>
  <section
    v-if="activeItem"
    class="announcement-hero apple-card"
    aria-label="官方公告"
    @mouseenter="setPaused(true)"
    @mouseleave="setPaused(false)"
    @focusin="setPaused(true)"
    @focusout="setPaused(false)"
  >
    <div class="announcement-ambient" aria-hidden="true" />

    <Transition name="announcement-slide" mode="out-in">
      <div :key="activeItem.id" class="announcement-layout">
        <div class="announcement-content">
          <span class="announcement-badge">
            <n-icon>
              <MegaphoneOutline />
            </n-icon>
            {{ activeBadge }}
          </span>

          <div class="announcement-copy">
            <h1>{{ activeTitle }}</h1>
            <p>{{ activeDescription }}</p>
          </div>

          <button class="announcement-cta" type="button" @click="openActive">
            {{ activeButtonText }}
          </button>
        </div>

        <div class="announcement-visual" aria-hidden="true">
          <div class="announcement-visual-placeholder">
            <div class="visual-orbit visual-orbit-back" />
            <div class="visual-orbit visual-orbit-front" />
            <div class="visual-panel visual-panel-top" />
            <div class="visual-panel visual-panel-card">
              <span />
              <span />
              <span />
            </div>
            <div class="visual-chip chip-a" />
            <div class="visual-chip chip-b" />
            <div class="visual-chip chip-c" />
            <div class="visual-star star-a">
              <n-icon><SparklesOutline /></n-icon>
            </div>
            <div class="visual-star star-b">
              <n-icon><SparklesOutline /></n-icon>
            </div>
            <div class="glass-robot">
              <span class="robot-ear left" />
              <span class="robot-ear right" />
              <span class="robot-head">
                <i />
                <b />
                <b />
              </span>
              <span class="robot-neck" />
              <span class="robot-body">
                <i />
              </span>
              <span class="robot-arm left" />
              <span class="robot-arm right" />
              <span class="robot-foot left" />
              <span class="robot-foot right" />
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <div v-if="hasMultiple" class="announcement-dots" aria-label="公告切换">
      <button
        v-for="(item, index) in items"
        :key="item.id"
        type="button"
        :class="{ active: index === activeIndex }"
        :aria-label="`切换到${item.title}`"
        @click="go(index)"
      />
    </div>
  </section>
</template>

<style scoped>
.announcement-hero {
  position: relative;
  overflow: hidden;
  min-height: 240px;
  border: 0 !important;
  border-radius: 20px;
  padding: 28px 32px;
  background: #fafafa !important;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.03) !important;
  isolation: isolate;
}

.announcement-hero:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 34px rgba(0, 0, 0, 0.04);
}

.announcement-ambient {
  position: absolute;
  top: 50%;
  right: -5%;
  inline-size: min(56%, 560px);
  aspect-ratio: 1;
  border-radius: var(--cf-radius-pill);
  background:
    radial-gradient(circle at 35% 38%, rgba(0, 201, 177, 0.88), transparent 34%),
    radial-gradient(circle at 68% 62%, rgba(0, 115, 128, 0.74), transparent 42%),
    radial-gradient(circle at 52% 52%, rgba(185, 255, 246, 0.9), transparent 64%);
  filter: blur(80px);
  opacity: 0.15;
  transform: translateY(-50%);
  pointer-events: none;
  z-index: 0;
}

.announcement-layout {
  position: relative;
  z-index: 1;
  inline-size: 100%;
  min-block-size: 184px;
  margin-inline: auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 0.72fr);
  align-items: center;
  gap: clamp(24px, 4vw, 56px);
}

.announcement-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
}

.announcement-badge {
  border: 0;
  border-radius: var(--cf-radius-pill);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--cf-announcement-inline-gap);
  padding: var(--cf-announcement-badge-padding-block) var(--cf-announcement-badge-padding-inline);
  background: rgba(0, 201, 177, 0.1);
  color: #007d80;
  font-size: 12px;
  font-weight: 600;
  box-shadow: none;
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.announcement-copy {
  display: grid;
  gap: var(--cf-radius-md);
}

.announcement-copy h1 {
  max-inline-size: 18ch;
  margin: 0;
  color: #1d1d1f;
  font-family: var(--cf-font-heading);
  font-size: clamp(24px, 2.2vw, 30px);
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: -0.02em;
}

.announcement-copy p {
  max-inline-size: 80%;
  margin: 0;
  color: #86868b;
  font-size: 14px;
  line-height: 1.6;
}

.announcement-cta {
  border: 0;
  border-radius: var(--cf-radius-pill);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-block-size: 40px;
  padding-inline: 20px;
  color: #fff;
  background: #00c9b1;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 16px 32px rgba(0, 201, 177, 0.24);
  transition: all 0.2s cubic-bezier(0.25, 1, 0.5, 1);
}

.announcement-cta:hover {
  transform: scale(0.98);
  filter: none;
  box-shadow: 0 16px 32px rgba(0, 201, 177, 0.24);
}

.announcement-visual {
  min-width: 0;
  display: grid;
  place-items: center;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.announcement-visual-placeholder {
  inline-size: min(100%, 270px);
  aspect-ratio: 1;
}

.announcement-visual-placeholder {
  position: relative;
  display: grid;
  place-items: center;
  background: transparent;
  animation: announcementFloat 6s ease-in-out infinite;
  mix-blend-mode: multiply;
}

.visual-orbit,
.visual-panel,
.visual-chip,
.visual-star,
.glass-robot,
.glass-robot span,
.glass-robot i,
.glass-robot b {
  position: absolute;
}

.visual-orbit {
  border-radius: 50%;
  background:
    radial-gradient(circle at 42% 34%, rgba(255, 255, 255, 0.44), transparent 30%),
    radial-gradient(circle at 50% 50%, rgba(26, 211, 198, 0.08), transparent 64%);
  box-shadow: none;
}

.visual-orbit-back {
  width: 76%;
  aspect-ratio: 1;
  transform: translate(-4%, 2%);
  opacity: 0.72;
}

.visual-orbit-front {
  width: 56%;
  aspect-ratio: 1;
  transform: translate(5%, 6%);
  opacity: 0.5;
}

.visual-panel {
  border-radius: 18px;
  background:
    linear-gradient(145deg, rgba(166, 250, 243, 0.76), rgba(255, 255, 255, 0.58));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.86),
    0 18px 34px rgba(30, 173, 164, 0.12);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.visual-panel-top {
  width: 30%;
  height: 38%;
  right: 21%;
  top: 13%;
  transform: rotate(28deg);
}

.visual-panel-card {
  width: 38%;
  height: 30%;
  left: 20%;
  bottom: 24%;
  transform: rotate(14deg);
  display: grid;
  gap: 8px;
  padding: 16px;
}

.visual-panel-card span {
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
}

.visual-chip {
  width: 36px;
  height: 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(105, 235, 224, 0.88), rgba(215, 255, 251, 0.9));
  box-shadow: 0 12px 22px rgba(34, 211, 197, 0.18);
}

.chip-a {
  top: 18%;
  left: 23%;
  transform: rotate(-35deg);
}

.chip-b {
  top: 22%;
  right: 8%;
  transform: rotate(30deg);
}

.chip-c {
  right: 10%;
  bottom: 37%;
  transform: rotate(-32deg);
}

.visual-star {
  color: rgba(32, 207, 194, 0.62);
}

.star-a {
  left: 2%;
  top: 20%;
  font-size: 28px;
}

.star-b {
  right: 5%;
  top: 32%;
  font-size: 22px;
}

.glass-robot {
  width: 42%;
  aspect-ratio: 1;
  transform: translate(20%, 4%);
}

.robot-head {
  inset: 4% 6% auto;
  height: 41%;
  border-radius: 34px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(205, 244, 248, 0.94));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 18px 30px rgba(25, 120, 132, 0.16);
}

.robot-head i {
  left: 20%;
  right: 20%;
  top: 32%;
  height: 38%;
  border-radius: 999px;
  background: #102636;
}

.robot-head b {
  top: 47%;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #12d7c9;
}

.robot-head b:nth-of-type(1) {
  left: 38%;
}

.robot-head b:nth-of-type(2) {
  right: 38%;
}

.robot-ear {
  top: 20%;
  width: 12%;
  height: 22%;
  border-radius: 999px;
  background: rgba(196, 240, 246, 0.9);
}

.robot-ear.left {
  left: 0;
}

.robot-ear.right {
  right: 0;
}

.robot-neck {
  left: 43%;
  top: 45%;
  width: 14%;
  height: 10%;
  border-radius: 999px;
  background: rgba(206, 241, 246, 0.9);
}

.robot-body {
  left: 27%;
  right: 27%;
  top: 54%;
  height: 35%;
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(205, 244, 248, 0.92));
  box-shadow: 0 18px 30px rgba(25, 120, 132, 0.13);
}

.robot-body i {
  left: 42%;
  top: 34%;
  width: 16%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: #26d5c7;
}

.robot-arm {
  top: 58%;
  width: 14%;
  height: 30%;
  border-radius: 999px;
  background: rgba(207, 243, 247, 0.92);
}

.robot-arm.left {
  left: 12%;
  transform: rotate(22deg);
}

.robot-arm.right {
  right: 12%;
  transform: rotate(-22deg);
}

.robot-foot {
  bottom: 1%;
  width: 14%;
  height: 16%;
  border-radius: 999px;
  background: rgba(186, 234, 241, 0.92);
}

.robot-foot.left {
  left: 32%;
}

.robot-foot.right {
  right: 32%;
}

.announcement-dots {
  position: absolute;
  z-index: 2;
  bottom: 18px;
  left: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--cf-announcement-inline-gap);
  pointer-events: none;
  transform: translateX(-50%);
}

.announcement-dots button {
  inline-size: 7px;
  block-size: 7px;
  border: 0;
  border-radius: var(--cf-radius-pill);
  background: #d5e3e5;
  cursor: pointer;
  pointer-events: auto;
}

.announcement-dots button.active {
  inline-size: 28px;
  background: #00c9b1;
}

.announcement-slide-enter-active,
.announcement-slide-leave-active {
  transition-property: opacity, transform, filter;
  transition-duration: 420ms;
  transition-timing-function: cubic-bezier(0.45, 0, 0.25, 1);
}

.announcement-slide-enter-from {
  opacity: 0;
  filter: blur(var(--cf-announcement-slide-blur));
  transform: var(--cf-announcement-slide-enter-transform);
}

.announcement-slide-leave-to {
  opacity: 0;
  filter: blur(var(--cf-announcement-slide-blur));
  transform: var(--cf-announcement-slide-leave-transform);
}

@keyframes announcementFloat {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-12px);
  }
}

@keyframes announcementDrift {
  from {
    transform: translate3d(-4%, 3%, 0) scale(0.98);
  }
  to {
    transform: translate3d(5%, -4%, 0) scale(1.02);
  }
}

@media (max-width: 767px) {
  .announcement-layout {
    grid-template-columns: 1fr;
  }

  .announcement-content {
    align-items: stretch;
  }

  .announcement-copy h1,
  .announcement-copy p {
    max-inline-size: none;
  }

  .announcement-visual {
    order: -1;
  }

  .announcement-cta {
    inline-size: 100%;
  }
}
</style>
