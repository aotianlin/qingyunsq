<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { NSpin, NIcon, NModal, NInput, NSelect, useMessage } from 'naive-ui';
import {
  PlanetOutline,
  PeopleOutline,
  LibraryOutline,
  ColorPaletteOutline,
  BasketballOutline,
  AddOutline,
  ChevronForwardOutline,
  BonfireOutline
} from '@vicons/ionicons5';
import { createSpace, getSpaces } from '@/api/spaces';
import type { SpaceVO } from '@/types/space';

const router = useRouter();
const message = useMessage();
const spaces = ref<SpaceVO[]>([]);
const loading = ref(false);
const category = ref<string | undefined>(undefined);
const createVisible = ref(false);
const createSubmitting = ref(false);
const createName = ref('');
const createDescription = ref('');
const createCategory = ref('');
const createVisibility = ref('PUBLIC');

const categories = [
  { key: undefined, label: '全部学习圈', icon: PlanetOutline },
  { key: 'MAJOR', label: '专业系', icon: LibraryOutline },
  { key: 'CLASS', label: '班级圈', icon: PeopleOutline },
  { key: 'CLUB', label: '社团联盟', icon: ColorPaletteOutline },
  { key: 'INTEREST', label: '兴趣圈', icon: BasketballOutline },
] as const;
const categoryOptions = categories
  .filter((item) => item.key)
  .map((item) => ({
    label: item.label,
    value: item.key as string,
  }));

const visibilityOptions = [
  { value: 'PUBLIC', label: '公开（任何人可加入）' },
  { value: 'REVIEW', label: '审核（需管理员审核）' },
];

async function loadSpaces() {
  loading.value = true;
  try {
    spaces.value = await getSpaces({ category: category.value, limit: 20 });
  } catch {
    spaces.value = [];
  }
  loading.value = false;
}

function switchCategory(cat: string | undefined) {
  category.value = cat;
  loadSpaces();
}

function goDetail(id: number) {
  router.push(`/spaces/${id}`);
}

function goCreate() {
  createVisible.value = true;
}

function resetCreateForm() {
  createName.value = '';
  createDescription.value = '';
  createCategory.value = '';
  createVisibility.value = 'PUBLIC';
}

function closeCreateModal() {
  if (createSubmitting.value) return;
  createVisible.value = false;
}

async function submitCreateSpace() {
  const name = createName.value.trim();
  if (!name || !createCategory.value) {
    message.warning('请填写空间名称和分类');
    return;
  }

  createSubmitting.value = true;
  try {
    const space = await createSpace({
      name,
      description: createDescription.value.trim() || undefined,
      category: createCategory.value,
      visibility: createVisibility.value,
    });
    message.success('学习圈创建成功');
    createVisible.value = false;
    resetCreateForm();
    await loadSpaces();
    router.push(`/spaces/${space.id}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建失败');
  } finally {
    createSubmitting.value = false;
  }
}

watch(createVisible, (visible) => {
  if (!visible && !createSubmitting.value) {
    resetCreateForm();
  }
});

function getCategoryColor(cat: string) {
  const colors: Record<string, string> = {
    'MAJOR': '#38bdf8',
    'CLASS': '#10b981',
    'CLUB': '#c084fc',
    'INTEREST': '#f472b6'
  };
  return colors[cat] || '#818cf8';
}

function getCategoryLabel(cat: string) {
  return categories.find((c) => c.key === cat)?.label || '其他';
}

onMounted(loadSpaces);
</script>

<template>
  <div class="spaces-page">
    <div class="header-banner">
      <div class="banner-content">
        <h1 class="page-title gradient-text">
          <n-icon
            size="32"
            class="title-icon"
          >
            <BonfireOutline />
          </n-icon>
          学习圈
        </h1>
        <p class="page-subtitle">
          加入志同道合的圈子，共同进步
        </p>
      </div>
      <div class="header-actions">
        <button
          class="neon-btn create-btn"
          @click="goCreate"
        >
          <n-icon><AddOutline /></n-icon> 创建学习圈
        </button>
      </div>
    </div>

    <div class="main-container">
      <div class="sort-bar glass-card">
        <div 
          v-for="c in categories" 
          :key="c.key || 'all'"
          class="sort-item"
          :class="{ active: category === c.key }"
          @click="switchCategory(c.key)"
        >
          <n-icon size="18">
            <component :is="c.icon" />
          </n-icon>
          <span>{{ c.label }}</span>
        </div>
      </div>

      <div
        v-if="loading"
        class="loading-state"
      >
        <n-spin size="large" />
      </div>

      <div
        v-else-if="spaces.length === 0"
        class="empty-state glass-card"
      >
        <n-icon
          size="64"
          color="rgba(255,255,255,0.1)"
        >
          <BonfireOutline />
        </n-icon>
        <h3>暂无学习圈</h3>
        <p>该分类下还没有任何学习圈哦</p>
        <button
          class="neon-btn mt-4"
          @click="goCreate"
        >
          建立第一个学习圈
        </button>
      </div>

      <div
        v-else
        class="spaces-grid"
      >
        <div 
          v-for="space in spaces" 
          :key="space.id" 
          class="space-card glass-card" 
          @click="goDetail(space.id)"
        >
          <div class="card-header">
            <div
              class="space-icon"
              :style="{ backgroundColor: getCategoryColor(space.category) + '20', color: getCategoryColor(space.category) }"
            >
              <n-icon size="28">
                <BonfireOutline />
              </n-icon>
            </div>
            <div class="header-right">
              <div
                class="category-tag"
                :style="{ color: getCategoryColor(space.category), borderColor: getCategoryColor(space.category) + '40', backgroundColor: getCategoryColor(space.category) + '10' }"
              >
                {{ getCategoryLabel(space.category) }}
              </div>
              <div
                v-if="space.visibility === 'REVIEW'"
                class="review-tag"
              >
                需审核
              </div>
            </div>
          </div>
          
          <div class="card-body">
            <h3 class="space-name">
              {{ space.name }}
            </h3>
            <p class="space-desc">
              {{ space.description || '这个空间的主人很懒，什么也没留下~' }}
            </p>
          </div>
          
          <div class="card-footer">
            <div class="stats">
              <div class="stat-item">
                <span class="val">{{ space.memberCount }}</span>
                <span class="lbl">成员</span>
              </div>
              <div class="stat-divider" />
              <div class="stat-item">
                <span class="val">{{ space.postCount }}</span>
                <span class="lbl">帖子</span>
              </div>
            </div>
            
            <div class="owner-info">
              <div
                class="avatar"
                :style="{ background: 'var(--cf-gradient-primary)' }"
              >
                {{ space.owner?.nickname?.charAt(0) || '匿' }}
              </div>
              <span>{{ space.owner?.nickname || '未知用户' }}</span>
            </div>
          </div>

          <div class="hover-action">
            <span>进入学习圈</span>
            <n-icon><ChevronForwardOutline /></n-icon>
          </div>
        </div>
      </div>
    </div>

    <NModal
      v-model:show="createVisible"
      preset="card"
      title="创建学习圈"
      class="space-modal create-space-modal"
      transform-origin="center"
      :closable="!createSubmitting"
      :mask-closable="!createSubmitting"
      :style="{ width: 'min(92vw, 560px)' }"
      @after-leave="resetCreateForm"
    >
      <div class="create-space-form">
        <label class="create-field">
          <span>空间名称</span>
          <NInput
            v-model:value="createName"
            placeholder="例如：Java 学习小组"
            maxlength="64"
          />
        </label>

        <label class="create-field">
          <span>简介</span>
          <NInput
            v-model:value="createDescription"
            type="textarea"
            placeholder="简单介绍一下空间..."
            maxlength="255"
            :autosize="{ minRows: 3, maxRows: 5 }"
          />
        </label>

        <label class="create-field">
          <span>分类</span>
          <NSelect
            v-model:value="createCategory"
            :options="categoryOptions"
            placeholder="选择分类"
          />
        </label>

        <label class="create-field">
          <span>加入方式</span>
          <NSelect
            v-model:value="createVisibility"
            :options="visibilityOptions"
          />
        </label>

        <div class="create-actions">
          <button
            class="neon-btn outline-btn"
            type="button"
            :disabled="createSubmitting"
            @click="closeCreateModal"
          >
            取消
          </button>
          <button
            class="neon-btn"
            type="button"
            :disabled="createSubmitting"
            @click="submitCreateSpace"
          >
            {{ createSubmitting ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.spaces-page {
  height: 100%;
  overflow-y: auto;
  background: transparent;
  perspective: 1200px;
}

:global(.space-modal.create-space-modal.n-card) {
  width: min(92vw, 560px);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cf-bg-base) 16%, transparent), transparent 54%),
    color-mix(in srgb, var(--cf-bg-base) 72%, transparent);
  border: 1px solid var(--cf-border-glass);
  border-radius: 16px;
  box-shadow: 0 24px 72px color-mix(in srgb, var(--cf-text-primary) 18%, transparent), 0 10px 28px
    color-mix(in srgb, var(--cf-text-primary) 8%, transparent);
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(136%);
}

:global(.space-modal.create-space-modal .n-card-header) {
  padding: 14px 14px 6px;
}

:global(.space-modal.create-space-modal .n-card-header__main) {
  font-size: 15px;
  font-weight: 800;
}

:global(.space-modal.create-space-modal .n-card__content) {
  padding: 8px 14px 14px;
}

.header-banner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 32px 24px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;

  .banner-content {
    .page-title {
      font-size: 36px;
      font-weight: 800;
      margin: 0 0 8px;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .page-subtitle {
      color: var(--cf-text-secondary);
      font-size: 16px;
      margin: 0;
    }
  }

  .header-actions {
    .create-btn {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 40px;
      padding: 0 24px;
      border-radius: 20px;
      font-weight: bold;
    }
  }
}

.create-space-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.create-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.create-field > span {
  color: var(--cf-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.main-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 32px 40px;
}

.sort-bar {
  display: flex;
  padding: 8px;
  gap: 8px;
  background: var(--cf-bg-glass-soft);
  border-radius: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;

  .sort-item {
    flex: 1;
    min-width: 120px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 12px 16px;
    border-radius: 12px;
    color: var(--cf-text-secondary);
    cursor: pointer;
    font-weight: 500;
    transition: all 0.3s;

    &:hover {
      background: var(--cf-bg-soft);
      color: var(--cf-text-primary);
    }

    &.active {
      background: var(--cf-primary-soft);
      color: var(--cf-primary);
    }
  }
}

.loading-state {
  padding: 60px;
  display: flex;
  justify-content: center;
}

.empty-state {
  padding: 80px 0;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  
  h3 { margin: 20px 0 8px; color: var(--cf-text-primary); font-size: 20px; }
  p { margin: 0; color: var(--cf-text-secondary); font-size: 15px; }
  .mt-4 { margin-top: 24px; }
}

.spaces-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 24px;
}

.space-card {
  padding: 24px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  min-height: 260px;
  box-shadow: var(--cf-shadow-float);
  transform-style: preserve-3d;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    background: linear-gradient(160deg, color-mix(in srgb, var(--cf-primary) 10%, transparent), transparent 38%);
    opacity: 0.86;
  }
  
  &:hover {
    transform: translate3d(0, -7px, 0) rotateX(0.8deg);
    box-shadow: var(--cf-shadow-card-hover);
    border-color: color-mix(in srgb, var(--cf-primary) 28%, var(--cf-border));
    
    .hover-action {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 20px;

    .space-icon {
      width: 56px;
      height: 56px;
      border-radius: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: inset 0 1px 0 var(--cf-surface-highlight), var(--cf-shadow-soft);
      backdrop-filter: blur(14px);
      -webkit-backdrop-filter: blur(14px);
    }

    .header-right {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 8px;

      .category-tag {
        padding: 4px 12px;
        border-radius: 20px;
        font-size: 12px;
        font-weight: bold;
        border: 1px solid transparent;
        backdrop-filter: blur(12px);
        -webkit-backdrop-filter: blur(12px);
      }
      
      .review-tag {
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 12px;
        color: var(--cf-warning);
        background: color-mix(in srgb, var(--cf-warning) 12%, transparent);
        border: 1px solid color-mix(in srgb, var(--cf-warning) 28%, transparent);
      }
    }
  }

  .card-body {
    flex: 1;
    margin-bottom: 24px;

    .space-name {
      font-size: 20px;
      font-weight: 700;
      color: var(--cf-text-primary);
      margin: 0 0 12px;
      line-height: 1.3;
    }

    .space-desc {
      color: var(--cf-text-secondary);
      font-size: 14px;
      line-height: 1.6;
      margin: 0;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-top: 16px;
    border-top: 1px solid var(--cf-border);

    .stats {
      display: flex;
      align-items: center;
      gap: 12px;
      
      .stat-item {
        display: flex;
        flex-direction: column;
        
        .val { font-size: 16px; font-weight: bold; color: var(--cf-text-primary); }
        .lbl { font-size: 12px; color: var(--cf-text-muted); }
      }
      
      .stat-divider {
        width: 1px;
        height: 24px;
        background: var(--cf-border);
      }
    }

    .owner-info {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: var(--cf-text-secondary);
      
      .avatar {
        width: 24px;
        height: 24px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        font-weight: bold;
        font-size: 12px;
      }
    }
  }

  .hover-action {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 12px;
    background: linear-gradient(to top, color-mix(in srgb, var(--cf-primary) 86%, transparent), transparent);
    color: var(--cf-text-inverse);
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 500;
    opacity: 0;
    transform: translateY(100%);
    transition: all 0.3s ease;
  }
}

@media (prefers-reduced-motion: no-preference) {
  .space-card:nth-child(2n) {
    animation-delay: 0.05s;
  }

  .space-card:nth-child(3n) {
    animation-delay: 0.1s;
  }
}
</style>
