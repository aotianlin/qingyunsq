<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NSpin, NIcon } from 'naive-ui';
import { 
  PlanetOutline,
  BonfireOutline,
  SparklesOutline,
  LibraryOutline,
  CheckmarkCircleOutline,
  NotificationsOutline,
  StarOutline,
  SearchOutline,
  MenuOutline,
  ShareSocialOutline,
  ThumbsUpOutline,
  ChatboxOutline,
  ArrowBackOutline
} from '@vicons/ionicons5';
import { getSpaceById, getSpaceMembers } from '@/api/spaces';
import { getPosts } from '@/api/posts';
import type { SpaceVO, SpaceMemberVO } from '@/types/space';
import type { PostVO } from '@/types/post';

const route = useRoute();
const router = useRouter();

const space = ref<SpaceVO | null>(null);
const members = ref<SpaceMemberVO[]>([]);
const posts = ref<PostVO[]>([]);
const loading = ref(true);

const sidebarMenus = [
  { label: '广场', icon: PlanetOutline, path: '/square' },
  { 
    label: '学习圈', 
    icon: BonfireOutline, 
    path: '/spaces',
    active: true,
    children: ['我的圈子', '我加入的', '圈子广场', '圈子管理']
  },
  { label: '打卡', icon: CheckmarkCircleOutline, path: '/checkin' },
  { label: '积分中心', icon: StarOutline, path: '/points' },
  { label: 'AI 助手', icon: SparklesOutline, path: '/ai' },
];

const tabs = ['首页', '帖子', '精华', '文件', '成员', '打卡', '设置'];
const activeTab = ref('帖子');

async function loadSpace() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    space.value = await getSpaceById(id);
    members.value = await getSpaceMembers(id);
    posts.value = await getPosts({ scope: 'SPACE', limit: 10 });
  } catch {
    space.value = {
      id: 1,
      ownerId: 1,
      owner: null,
      name: '计算机科学与技术',
      description: 'CS学习交流圈',
      category: 'MAJOR',
      visibility: 'PUBLIC',
      memberCount: 2341,
      postCount: 8712,
      status: 1,
      isMember: false,
      memberRole: null,
      createdAt: new Date().toISOString(),
    };
  }
  loading.value = false;
}

onMounted(loadSpace);
</script>

<template>
  <div class="layout-container">
    <div class="main-wrapper">
      <!-- Top Header -->
      <header class="top-header">
        <div class="header-left" @click="router.push('/spaces')">
          <n-icon size="20">
            <ArrowBackOutline />
          </n-icon>
          <span>返回学习圈广场</span>
        </div>
        <div class="search-bar">
          <n-icon
            size="18"
            color="#8b949e"
          >
            <SearchOutline />
          </n-icon>
          <input
            type="text"
            placeholder="搜索圈内内容"
          />
        </div>
        <div class="header-actions">
          <n-icon size="24">
            <NotificationsOutline />
          </n-icon>
          <n-icon size="24">
            <MenuOutline />
          </n-icon>
          <div class="avatar" />
        </div>
      </header>

      <div class="content-scroll">
        <template v-if="loading">
          <div class="loading">
            <n-spin size="large" />
          </div>
        </template>
        <template v-else-if="space">
          <div class="space-layout">
            <!-- Center Column -->
            <div class="center-col">
              <!-- Space Banner -->
              <div class="space-banner glass-card">
                <div class="banner-bg" />
                <div class="banner-content">
                  <div class="space-icon">
                    <n-icon
                      size="36"
                      color="white"
                    >
                      <LibraryOutline />
                    </n-icon>
                  </div>
                  <div class="space-info">
                    <div class="title-row">
                      <h2>{{ space.name || '计算机科学与技术' }}</h2>
                      <span class="tag">公开圈子</span>
                    </div>
                    <p class="desc">
                      {{ space.description || 'CS学习交流圈' }}
                    </p>
                    <div class="stats">
                      成员 {{ space.memberCount || '2,341' }} <span class="dot">·</span> 
                      帖子 {{ space.postCount || '8,712' }} <span class="dot">·</span> 
                      今日活跃 342
                    </div>
                  </div>
                  <button class="neon-btn header-btn">
                    + 发布帖子
                  </button>
                </div>
              </div>

              <!-- Tabs -->
              <div class="space-tabs">
                <div
                  v-for="tab in tabs"
                  :key="tab" 
                  class="tab-item" 
                  :class="{ active: activeTab === tab }"
                  @click="activeTab = tab"
                >
                  {{ tab }}
                </div>
              </div>

              <!-- Filter -->
              <div v-if="activeTab === '帖子'" class="post-filters">
                <span class="active">最新</span>
                <span>热门</span>
                <span>精华</span>
              </div>

              <!-- Post List -->
              <div v-if="activeTab === '帖子'" class="post-list">
                <!-- Mock Pinned Post -->
                <div class="post-item glass-card">
                  <div class="post-author">
                    <div class="avatar admin-avatar" />
                    <div class="author-info">
                      <span class="name">代码骑士</span>
                      <span class="time">10 分钟前</span>
                    </div>
                    <n-icon class="more-icon">
                      <MenuOutline />
                    </n-icon>
                  </div>
                  <h3 class="post-title">
                    求推荐系统学习操作系统的路线 <span class="tag blue">求助</span>
                  </h3>
                  <p class="post-preview">
                    我想深入学习操作系统原理，希望能推荐一些经典书籍、视频教程以及实验项目。最好能按照由浅入深的顺序，非常感谢大家！
                  </p>
                  <div class="post-actions">
                    <span class="action"><n-icon><ThumbsUpOutline /></n-icon> 12</span>
                    <span class="action"><n-icon><ChatboxOutline /></n-icon> 36</span>
                    <span class="action right"><n-icon><ShareSocialOutline /></n-icon> 分享</span>
                  </div>
                </div>

                <!-- Mock Post 2 -->
                <div class="post-item glass-card">
                  <div class="post-author">
                    <div class="avatar default-avatar" />
                    <div class="author-info">
                      <span class="name">算法小能手</span>
                      <span class="time">1 小时前</span>
                    </div>
                  </div>
                  <h3 class="post-title">
                    分享：图解操作系统进程调度算法 <span class="tag purple">分享</span>
                  </h3>
                  <p class="post-preview">
                    结合我最近复习整理的笔记，用图解的方式重新讲一遍 FCFS, SJF, RR 等调度算法的区别...
                  </p>
                  <div class="post-actions">
                    <span class="action"><n-icon><ThumbsUpOutline /></n-icon> 89</span>
                    <span class="action"><n-icon><ChatboxOutline /></n-icon> 45</span>
                  </div>
                </div>
              </div>

              <!-- Members Tab -->
              <div v-if="activeTab === '成员'" class="members-view">
                <div class="member-header">
                  <h3>圈子成员</h3>
                  <div class="search-member">
                    <input type="text" placeholder="搜索成员昵称" />
                  </div>
                </div>
                <div class="member-grid">
                  <div v-for="m in members" :key="m.userId" class="member-card glass-card">
                    <div class="avatar">{{ m.user?.nickname?.charAt(0) || 'U' }}</div>
                    <div class="info">
                      <span class="name">{{ m.user?.nickname || '未知用户' }}</span>
                      <span class="role">{{ m.role === 'OWNER' ? '圈主' : (m.role === 'ADMIN' ? '管理员' : '成员') }}</span>
                    </div>
                  </div>
                  <!-- Mock members if empty -->
                  <div v-if="members.length === 0" class="member-card glass-card" v-for="i in 5" :key="i">
                    <div class="avatar" style="background: var(--cf-gradient-primary);">U</div>
                    <div class="info">
                      <span class="name">学习者 {{ i }}</span>
                      <span class="role">成员</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Files Tab -->
              <div v-if="activeTab === '文件'" class="files-view glass-card">
                <div class="empty-state">
                  <n-icon size="48" color="rgba(255,255,255,0.2)"><DocumentTextOutline /></n-icon>
                  <p>暂无文件，快来分享第一份资料吧~</p>
                  <button class="neon-btn">上传文件</button>
                </div>
              </div>

              <!-- Other Tabs Fallback -->
              <div v-if="!['帖子', '成员', '文件'].includes(activeTab)" class="files-view glass-card">
                <div class="empty-state">
                  <p>该功能模块建设中...</p>
                </div>
              </div>
            </div>

            <!-- Right Column -->
            <div class="right-col">
              <!-- Notice Card -->
              <div class="widget-card glass-card">
                <div class="widget-header">
                  <h3>圈子公告</h3>
                  <span class="more">更多 ></span>
                </div>
                <div class="notice-content">
                  <p>欢迎加入本学习圈！请遵守发帖规范，友善交流，共同进步！</p>
                  <div class="date">
                    2024-05-01
                  </div>
                </div>
              </div>

              <!-- Data Card -->
              <div class="widget-card glass-card">
                <div class="widget-header">
                  <h3>圈子数据</h3>
                </div>
                <div class="data-content">
                  <div class="data-row">
                    <span class="label">活跃趋势 (7日)</span>
                    <span class="value">342 <span class="up">+12.5%</span></span>
                  </div>
                  <!-- CSS Simulated Chart -->
                  <div class="mock-chart">
                    <svg
                      viewBox="0 0 100 30"
                      class="chart-svg"
                    >
                      <path
                        d="M0,20 L20,10 L40,25 L60,5 L80,15 L100,5"
                        fill="none"
                        stroke="#6366f1"
                        stroke-width="2"
                      />
                      <circle
                        cx="0"
                        cy="20"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="20"
                        cy="10"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="40"
                        cy="25"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="60"
                        cy="5"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="80"
                        cy="15"
                        r="2"
                        fill="#6366f1"
                      />
                      <circle
                        cx="100"
                        cy="5"
                        r="2"
                        fill="#6366f1"
                      />
                    </svg>
                  </div>
                  <div class="data-row mt">
                    <span class="label">活跃成员</span>
                    <div class="members-avatars">
                      <div
                        class="avatar"
                        style="background:#ef4444"
                      />
                      <div
                        class="avatar"
                        style="background:#3b82f6"
                      />
                      <div
                        class="avatar"
                        style="background:#10b981"
                      />
                      <span class="count">> 2,341</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.layout-container {
  display: flex;
  height: 100vh;
  background-color: transparent;
  color: var(--cf-text-primary);
  overflow: hidden;
}

.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
}

.top-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  border-bottom: 1px solid var(--cf-border);
  background: rgba(250, 249, 246, 0.85);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    font-weight: 500;
    color: var(--cf-text-primary);
    transition: color 0.2s;
    &:hover { color: var(--cf-primary); }
  }

  .search-bar {
    display: flex;
    align-items: center;
    background: var(--cf-bg-elevated);
    border: 1px solid var(--cf-border);
    border-radius: 20px;
    padding: 8px 16px;
    width: 300px;
    gap: 8px;

    input {
      background: transparent;
      border: none;
      color: var(--cf-text-primary);
      outline: none;
      width: 100%;
      &::placeholder { color: var(--cf-text-muted); }
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 20px;
    color: var(--cf-text-secondary);
    
    .avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: #c084fc;
    }
  }
}

.content-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 32px;
}

.space-layout {
  display: flex;
  gap: 32px;
  max-width: 1200px;
  margin: 0 auto;

  .center-col {
    flex: 1;
    min-width: 0;

    .space-banner {
      position: relative;
      padding: 32px;
      overflow: hidden;
      margin-bottom: 24px;

      .banner-bg {
        position: absolute;
        top: 0; left: 0; right: 0; bottom: 0;
        background: linear-gradient(135deg, rgba(99,102,241,0.2) 0%, rgba(30,37,48,0) 100%);
        z-index: 0;
      }

      .banner-content {
        position: relative;
        z-index: 1;
        display: flex;
        align-items: center;
        gap: 24px;

        .space-icon {
          width: 80px; height: 80px;
          border-radius: 16px;
          background: var(--cf-primary);
          display: flex;
          align-items: center;
          justify-content: center;
          box-shadow: var(--cf-shadow-glow);
        }

        .space-info {
          flex: 1;
          .title-row {
            display: flex;
            align-items: center;
            gap: 12px;
            h2 { margin: 0; font-size: 24px; color: var(--cf-text-primary); }
            .tag { font-size: 12px; padding: 2px 8px; border-radius: 4px; border: 1px solid var(--cf-warning); color: var(--cf-warning); }
          }
          .desc { color: var(--cf-text-secondary); margin: 8px 0; font-size: 14px; }
          .stats { color: var(--cf-text-muted); font-size: 13px; .dot { margin: 0 8px; } }
        }

        .header-btn { padding: 10px 24px; }
      }
    }

    .space-tabs {
      display: flex;
      gap: 32px;
      border-bottom: 1px solid var(--cf-border);
      margin-bottom: 24px;

      .tab-item {
        padding: 12px 0;
        color: var(--cf-text-secondary);
        cursor: pointer;
        position: relative;

        &.active {
          color: var(--cf-text-primary);
          &::after {
            content: '';
            position: absolute;
            bottom: -1px; left: 0; width: 100%; height: 2px;
            background: var(--cf-primary);
            box-shadow: 0 -2px 8px rgba(99,102,241,0.5);
          }
        }
      }
    }

    .post-filters {
      display: flex;
      gap: 16px;
      margin-bottom: 20px;
      span {
        padding: 4px 12px; border-radius: 16px; background: rgba(255,255,255,0.05);
        color: var(--cf-text-secondary); font-size: 14px; cursor: pointer;
        &.active { background: var(--cf-primary); color: white; }
      }
    }

    .post-list {
      display: flex;
      flex-direction: column;
      gap: 16px;

      .post-item {
        padding: 24px;
        background: var(--cf-bg-elevated);
        border: 1px solid var(--cf-border);
        border-radius: 16px;
        
        .post-author {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 16px;
          
          .avatar { width: 36px; height: 36px; border-radius: 50%; }
          .admin-avatar { background: #38bdf8; }
          .default-avatar { background: #10b981; }

          .author-info {
            display: flex;
            flex-direction: column;
            .name { font-size: 14px; color: var(--cf-text-primary); font-weight: 500; }
            .time { font-size: 12px; color: var(--cf-text-muted); }
          }
          .more-icon { margin-left: auto; color: var(--cf-text-secondary); cursor: pointer; }
        }

        .post-title {
          margin: 0 0 12px;
          font-size: 18px;
          color: var(--cf-text-primary);
          display: flex;
          align-items: center;
          gap: 12px;

          .tag {
            font-size: 12px; padding: 2px 8px; border-radius: 4px; font-weight: normal;
            &.blue { background: rgba(56,189,248,0.1); color: #38bdf8; border: 1px solid rgba(56,189,248,0.3); }
            &.purple { background: rgba(192,132,252,0.1); color: #c084fc; border: 1px solid rgba(192,132,252,0.3); }
          }
        }

        .post-preview {
          color: var(--cf-text-secondary);
          font-size: 15px;
          line-height: 1.6;
          margin-bottom: 20px;
        }

        .post-actions {
          display: flex;
          gap: 24px;
          .action {
            display: flex; align-items: center; gap: 6px; color: var(--cf-text-secondary); font-size: 14px; cursor: pointer;
            &:hover { color: var(--cf-primary); }
            &.right { margin-left: auto; }
          }
        }
      }
    }
  }

  .right-col {
    width: 320px;
    display: flex;
    flex-direction: column;
    gap: 24px;

    .widget-card {
      padding: 24px;
      background: var(--cf-bg-elevated);
      border: 1px solid var(--cf-border);
      border-radius: 16px;

      .widget-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
        h3 { margin: 0; font-size: 16px; color: var(--cf-text-primary); }
        .more { font-size: 13px; color: var(--cf-text-secondary); cursor: pointer; }
      }

      .notice-content {
        p { margin: 0 0 12px; color: var(--cf-text-secondary); font-size: 14px; line-height: 1.6; }
        .date { font-size: 12px; color: var(--cf-text-muted); }
      }

      .data-content {
        .data-row {
          display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
          .label { font-size: 14px; color: var(--cf-text-secondary); }
          .value { font-size: 18px; font-weight: bold; color: var(--cf-text-primary); }
          .up { color: var(--cf-success); font-size: 12px; margin-left: 8px; font-weight: normal; }
          
          &.mt { margin-top: 24px; margin-bottom: 0; }
        }

        .mock-chart {
          height: 60px;
          width: 100%;
          .chart-svg { width: 100%; height: 100%; overflow: visible; }
        }

        .members-avatars {
          display: flex; align-items: center;
          .avatar { width: 28px; height: 28px; border-radius: 50%; border: 2px solid var(--cf-bg-card); margin-left: -8px; &:first-child { margin-left: 0; } }
          .count { font-size: 13px; color: var(--cf-text-secondary); margin-left: 8px; }
        }
      }
    }
  }
}

.members-view {
  .member-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    h3 { margin: 0; color: var(--cf-text-primary); font-size: 18px; }
    .search-member {
      background: rgba(255, 255, 255, 0.05);
      border-radius: 20px;
      padding: 6px 16px;
      input {
        background: transparent;
        border: none;
        color: var(--cf-text-primary);
        outline: none;
        font-size: 14px;
        &::placeholder { color: var(--cf-text-muted); }
      }
    }
  }

  .member-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 16px;

    .member-card {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px;
      background: var(--cf-bg-elevated);
      border: 1px solid var(--cf-border);
      border-radius: 12px;

      .avatar {
        width: 48px;
        height: 48px;
        border-radius: 50%;
        background: #6366f1;
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        font-size: 18px;
        font-weight: bold;
      }

      .info {
        display: flex;
        flex-direction: column;
        .name { color: white; font-size: 15px; font-weight: 500; }
        .role { color: var(--cf-text-secondary); font-size: 12px; margin-top: 4px; }
      }
    }
  }
}

.files-view {
  padding: 60px;
  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    p { margin: 16px 0 24px; color: var(--cf-text-secondary); }
    button { padding: 8px 24px; }
  }
}
</style>
