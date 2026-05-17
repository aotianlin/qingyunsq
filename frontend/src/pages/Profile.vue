<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { 
  MenuOutline,
  SettingsOutline,
  ArrowBackOutline,
  ThumbsUpOutline,
  ChatboxOutline,
  ShareSocialOutline,
  DocumentTextOutline
} from '@vicons/ionicons5';
import auroraBg from '@/assets/images/aurora_bg.png';

const router = useRouter();
const activeTab = ref('动态');
const tabs = ['动态', '帖子', '回复', '收藏', '打卡', '成就'];

// Mock Data for UI
const user = {
  nickname: '代码骑士',
  level: 'LV.16',
  title: '计算机科学与技术专业 · 大三',
  bio: '热爱编程、算法、开源，正在成为更好的自己！',
  following: 128,
  followers: '1,234',
  likes: '8,912',
  points: '12,345'
};
</script>

<template>
  <div class="profile-layout">
    <!-- Top Header -->
    <header class="top-header">
      <div
        class="header-left"
        @click="router.back()"
      >
        <n-icon size="20">
          <ArrowBackOutline />
        </n-icon>
        <span>个人主页</span>
      </div>
      <div class="header-actions">
        <n-icon size="20">
          <DocumentTextOutline />
        </n-icon>
        <n-icon size="20">
          <SettingsOutline />
        </n-icon>
      </div>
    </header>

    <div class="scroll-content">
      <div class="profile-container">
        <!-- Cover Area -->
        <div class="cover-section">
          <img
            :src="auroraBg"
            class="cover-img"
            alt="cover"
          />
          <div class="profile-main-info">
            <div class="avatar-wrapper">
              <div class="avatar">
                <!-- Fallback to a gradient or placeholder if no image -->
                <div class="avatar-placeholder">
                  代
                </div>
              </div>
            </div>
            <div class="user-details">
              <div class="name-row">
                <h2>{{ user.nickname }}</h2>
                <span class="level-tag">{{ user.level }}</span>
              </div>
              <p class="title">
                {{ user.title }}
              </p>
              <p class="bio">
                {{ user.bio }}
              </p>
              
              <div class="stats-row">
                <div class="stat">
                  <span class="label">关注</span>
                  <span class="val">{{ user.following }}</span>
                </div>
                <div class="stat">
                  <span class="label">粉丝</span>
                  <span class="val">{{ user.followers }}</span>
                </div>
                <div class="stat">
                  <span class="label">获赞</span>
                  <span class="val">{{ user.likes }}</span>
                </div>
                <div class="stat">
                  <span class="label">积分</span>
                  <span class="val">{{ user.points }}</span>
                </div>
                
                <button class="outline-btn edit-btn">
                  编辑资料
                </button>
                <button class="icon-btn">
                  <n-icon><SettingsOutline /></n-icon>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Content Area -->
        <div class="content-section">
          <!-- Main Left -->
          <div class="main-left">
            <div class="tabs">
              <div
                v-for="tab in tabs"
                :key="tab" 
                class="tab"
                :class="{ active: activeTab === tab }"
                @click="activeTab = tab"
              >
                {{ tab }}
              </div>
            </div>

            <div class="feed-list">
              <!-- Post item -->
              <div class="feed-item glass-card">
                <div class="feed-header">
                  <div class="avatar">
                    代
                  </div>
                  <div class="info">
                    <span class="name">代码骑士</span>
                    <span class="time">2 小时前</span>
                  </div>
                  <n-icon class="more">
                    <MenuOutline />
                  </n-icon>
                </div>
                <div class="feed-content">
                  <p>今天又是努力学习的一天！ 💪</p>
                  <div class="attachment-card">
                    <div class="icon">
                      📝
                    </div>
                    <div class="att-info">
                      <span class="att-name">数据结构与算法笔记.md</span>
                      <span class="att-size">1.2 MB</span>
                    </div>
                  </div>
                </div>
                <div class="feed-actions">
                  <div class="action">
                    <n-icon><ThumbsUpOutline /></n-icon> 24
                  </div>
                  <div class="action">
                    <n-icon><ChatboxOutline /></n-icon> 36
                  </div>
                  <div class="action right">
                    <n-icon><ShareSocialOutline /></n-icon> 分享
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Sidebar Right -->
          <div class="sidebar-right">
            <!-- Achievements -->
            <div class="glass-card widget">
              <div class="widget-header">
                <h3>个人成就</h3>
                <span class="more">查看全部</span>
              </div>
              <div class="badges">
                <div class="badge neon-glow">
                  <span class="emoji">🏆</span>
                </div>
                <div class="badge neon-glow">
                  <span class="emoji">🥇</span>
                </div>
                <div class="badge neon-glow">
                  <span class="emoji">🎖️</span>
                </div>
                <div class="badge neon-glow">
                  <span class="emoji">🏅</span>
                </div>
              </div>
            </div>

            <!-- Calendar -->
            <div class="glass-card widget">
              <div class="widget-header">
                <h3>近期打卡</h3>
                <span class="streak">连续打卡 21 天</span>
              </div>
              <div class="calendar-grid">
                <!-- CSS grid for squares -->
                <div class="day-label">
                  一
                </div>
                <div class="square active" />
                <div class="square active" />
                <div class="square active" />
                <div class="square active" />
                <div class="square active" />
                <div class="day-label">
                  二
                </div>
                <div class="square active" />
                <div class="square active" />
                <div class="square active" />
                <div class="square active" />
                <div class="square" />
                <div class="day-label">
                  三
                </div>
                <div class="square active" />
                <div class="square active" />
                <div class="square" />
                <div class="square active" />
                <div class="square active" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.profile-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: transparent;
  color: var(--cf-text-primary);
  overflow: hidden;
}

.top-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--cf-border);
  background: rgba(13, 17, 23, 0.8);
  backdrop-filter: blur(12px);
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    font-size: 16px;
    font-weight: 500;
  }
  .header-actions {
    display: flex;
    gap: 20px;
    color: var(--cf-text-secondary);
    cursor: pointer;
  }
}

.scroll-content {
  flex: 1;
  overflow-y: auto;
}

.profile-container {
  max-width: 1000px;
  margin: 0 auto;
  padding-bottom: 60px;
}

.cover-section {
  position: relative;
  
  .cover-img {
    width: 100%;
    height: 300px;
    object-fit: cover;
    mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 60%, rgba(0,0,0,0) 100%);
    -webkit-mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 60%, rgba(0,0,0,0) 100%);
  }

  .profile-main-info {
    position: relative;
    margin-top: -60px;
    padding: 0 40px;
    display: flex;
    gap: 32px;
    align-items: flex-end;

    .avatar-wrapper {
      .avatar {
        width: 140px;
        height: 140px;
        border-radius: 50%;
        border: 4px solid var(--cf-bg-base);
        background: var(--cf-bg-card);
        overflow: hidden;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 8px 24px rgba(0,0,0,0.4);
        
        .avatar-placeholder {
          font-size: 64px;
          color: white;
          background: linear-gradient(135deg, #38bdf8, #8b5cf6);
          width: 100%; height: 100%;
          display: flex; align-items: center; justify-content: center;
        }
      }
    }

    .user-details {
      flex: 1;
      padding-bottom: 12px;

      .name-row {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 8px;

        h2 { margin: 0; font-size: 28px; color: var(--cf-text-primary); }
        .level-tag { 
          font-size: 12px; padding: 2px 8px; border-radius: 12px;
          background: linear-gradient(90deg, #f59e0b, #ef4444);
          color: white; font-weight: bold;
        }
      }

      .title { color: var(--cf-text-secondary); font-size: 15px; margin: 0 0 8px; }
      .bio { color: var(--cf-text-primary); font-size: 14px; margin: 0 0 20px; }

      .stats-row {
        display: flex;
        align-items: center;
        gap: 24px;

        .stat {
          display: flex; flex-direction: column; gap: 4px;
          .label { color: var(--cf-text-secondary); font-size: 13px; }
          .val { color: var(--cf-text-primary); font-size: 18px; font-weight: bold; }
        }

        .edit-btn {
          margin-left: auto;
          background: var(--cf-bg-elevated);
          border: 1px solid var(--cf-border);
          color: var(--cf-text-primary); padding: 6px 16px; border-radius: 20px;
          cursor: pointer; transition: all 0.2s;
          &:hover { background: var(--cf-bg-soft); }
        }

        .icon-btn {
          background: var(--cf-bg-elevated);
          border: 1px solid var(--cf-border);
          color: var(--cf-text-primary); width: 36px; height: 36px; border-radius: 50%;
          display: flex; align-items: center; justify-content: center;
          cursor: pointer;
        }
      }
    }
  }
}

.content-section {
  display: flex;
  gap: 32px;
  padding: 40px;

  .main-left {
    flex: 1;
    min-width: 0;

    .tabs {
      display: flex;
      gap: 32px;
      border-bottom: 1px solid var(--cf-border);
      margin-bottom: 24px;

      .tab {
        padding: 12px 0;
        color: var(--cf-text-secondary);
        font-size: 15px;
        cursor: pointer;
        position: relative;

        &.active {
          color: var(--cf-text-primary);
          font-weight: 500;
          &::after {
            content: ''; position: absolute; bottom: -1px; left: 0; width: 100%; height: 2px;
            background: var(--cf-primary);
            box-shadow: 0 -2px 8px rgba(99,102,241,0.5);
          }
        }
      }
    }

    .feed-list {
      .feed-item {
        padding: 24px;
        margin-bottom: 16px;

        .feed-header {
          display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
          .avatar { width: 40px; height: 40px; border-radius: 50%; background: linear-gradient(135deg, #38bdf8, #8b5cf6); display: flex; align-items: center; justify-content: center; color: white;}
          .info { display: flex; flex-direction: column; .name{ color: var(--cf-text-primary); font-size: 15px; } .time{ color: var(--cf-text-muted); font-size: 12px; } }
          .more { margin-left: auto; color: var(--cf-text-secondary); cursor: pointer; }
        }

        .feed-content {
          p { margin: 0 0 16px; line-height: 1.6; font-size: 15px; }
          .attachment-card {
            display: flex; align-items: center; gap: 16px; padding: 16px;
            background: var(--cf-bg-soft); border: 1px solid var(--cf-border); border-radius: 8px;
            .icon { font-size: 24px; }
            .att-info { display: flex; flex-direction: column; .att-name{ color: var(--cf-primary); font-size: 14px; } .att-size{ color: var(--cf-text-muted); font-size: 12px; } }
          }
        }

        .feed-actions {
          display: flex; gap: 32px; margin-top: 20px;
          .action { display: flex; align-items: center; gap: 6px; color: var(--cf-text-secondary); font-size: 14px; cursor: pointer; &:hover{ color: var(--cf-primary); } }
          .right { margin-left: auto; }
        }
      }
    }
  }

  .sidebar-right {
    width: 300px;
    display: flex;
    flex-direction: column;
    gap: 24px;

    .widget {
      padding: 24px;
      .widget-header {
        display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
        h3 { margin: 0; font-size: 16px; color: var(--cf-text-primary); }
        .more { font-size: 13px; color: var(--cf-primary); cursor: pointer; }
        .streak { font-size: 12px; color: var(--cf-warning); padding: 2px 8px; border-radius: 12px; background: rgba(210,153,34,0.1); }
      }

      .badges {
        display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;
        .badge {
          aspect-ratio: 1; border-radius: 12px; background: var(--cf-bg-elevated); border: 1px solid var(--cf-border);
          display: flex; align-items: center; justify-content: center; font-size: 24px;
          &.neon-glow {
            box-shadow: inset 0 0 10px rgba(139,92,246,0.1);
            border-color: rgba(139,92,246,0.3);
          }
        }
      }

      .calendar-grid {
        display: grid; grid-template-columns: 20px repeat(5, 1fr); gap: 6px; align-items: center;
        .day-label { font-size: 12px; color: var(--cf-text-secondary); text-align: right; padding-right: 4px; }
        .square {
          aspect-ratio: 1; border-radius: 4px; background: rgba(0,0,0,0.05);
          &.active { background: var(--cf-primary); box-shadow: 0 0 8px rgba(99,102,241,0.2); }
        }
      }
    }
  }
}
</style>
