<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { NSpin, NIcon } from 'naive-ui';
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
import { getSpaces } from '@/api/spaces';
import type { SpaceVO } from '@/types/space';

const router = useRouter();
const spaces = ref<SpaceVO[]>([]);
const loading = ref(false);
const category = ref<string | undefined>(undefined);

const categories = [
  { key: undefined, label: '全部学习圈', icon: PlanetOutline },
  { key: 'MAJOR', label: '专业系', icon: LibraryOutline },
  { key: 'CLASS', label: '班级圈', icon: PeopleOutline },
  { key: 'CLUB', label: '社团联盟', icon: ColorPaletteOutline },
  { key: 'INTEREST', label: '兴趣圈', icon: BasketballOutline },
] as const;

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
  router.push('/spaces/new');
}

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
  return categories.find(c => c.key === cat)?.label || '其他';
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
  </div>
</template>

<style scoped lang="scss">
.spaces-page {
  height: 100%;
  overflow-y: auto;
  background: transparent;
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

.main-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 32px 40px;
}

.sort-bar {
  display: flex;
  padding: 8px;
  gap: 8px;
  background: rgba(22, 27, 34, 0.5);
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
      background: rgba(255, 255, 255, 0.05);
      color: var(--cf-text-primary);
    }

    &.active {
      background: rgba(99, 102, 241, 0.15);
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
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.2);
    border-color: rgba(99, 102, 241, 0.3);
    
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
      }
      
      .review-tag {
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 12px;
        color: var(--cf-warning);
        background: rgba(210, 153, 34, 0.1);
        border: 1px solid rgba(210, 153, 34, 0.3);
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
    border-top: 1px solid rgba(255, 255, 255, 0.05);

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
        background: rgba(255, 255, 255, 0.1);
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
    background: linear-gradient(to top, rgba(99, 102, 241, 0.9), rgba(99, 102, 241, 0));
    color: white;
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
</style>
