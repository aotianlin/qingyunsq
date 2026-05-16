<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { 
  SearchOutline, 
  SchoolOutline, 
  PeopleOutline, 
  ChatbubblesOutline, 
  PlanetOutline,
  CheckmarkCircleOutline
} from '@vicons/ionicons5';
import campusHeroImg from '@/assets/images/campus_hero_3d.png';

const router = useRouter();

const navLinks = [
  { name: '首页', path: '/', active: true },
  { name: '学习空间', path: '/spaces', active: false },
  { name: '帖子', path: '/square', active: false },
  { name: '打卡', path: '/checkin', active: false },
  { name: '排行榜', path: '/points', active: false },
  { name: 'AI 助手', path: '/ai', active: false },
];

const stats = [
  { label: '高校已入驻', value: '128+', icon: SchoolOutline, color: '#38bdf8' },
  { label: '注册用户', value: '56,231+', icon: PeopleOutline, color: '#2dd4bf' },
  { label: '帖子总数', value: '324,112+', icon: ChatbubblesOutline, color: '#c084fc' },
  { label: '学习空间', value: '12,421+', icon: PlanetOutline, color: '#f472b6' },
  { label: '今日打卡', value: '8,931+', icon: CheckmarkCircleOutline, color: '#fb923c' },
];

const hotSpaces = [
  { name: '计算机科学与技术', desc: 'CS学习交流空间', members: '2,341', posts: '8,712', type: '公开', color: '#3b82f6' },
  { name: '高等数学学习交流', desc: '一起攻克数学难题', members: '1,892', posts: '6,421', type: '公开', color: '#10b981' },
  { name: '考研复习互助', desc: '2025考研人加油！', members: '3,421', posts: '12,341', type: '公开', color: '#6366f1' },
  { name: '算法竞赛交流', desc: 'ACM/ICPC 交流空间', members: '952', posts: '3,421', type: '公开', color: '#f59e0b' },
];
</script>

<template>
  <div class="home-container">
    <!-- Navbar -->
    <nav class="navbar">
      <div class="nav-left">
        <div class="logo">
          <n-icon size="24" color="#6366f1"><PlanetOutline /></n-icon>
          <span>CampusForum</span>
        </div>
        <div class="nav-links">
          <a v-for="link in navLinks" :key="link.name" 
             :class="{ active: link.active }"
             @click="router.push(link.path)">
            {{ link.name }}
          </a>
        </div>
      </div>
      <div class="nav-right">
        <n-input round placeholder="搜索帖子、用户或空间" class="search-input">
          <template #prefix>
            <n-icon><SearchOutline /></n-icon>
          </template>
        </n-input>
        <n-button text class="login-btn" @click="router.push('/login')">登录</n-button>
        <button class="neon-btn register-btn" @click="router.push('/register')">注册</button>
      </div>
    </nav>

    <!-- Hero Section -->
    <main class="hero-section">
      <div class="hero-content">
        <div class="tag">多租户开源高校学习社群平台</div>
        <h1 class="hero-title">CampusForum</h1>
        <h2 class="hero-subtitle">连接 <span class="dot">·</span> 学习 <span class="dot">·</span> 分享 <span class="dot">·</span> 成长</h2>
        <p class="hero-desc">
          专为高校打造的学习社群平台，支撑多学习空间、打卡系统、积分成就、AI 助手等丰富功能，助力学习与成长。
        </p>
        <div class="hero-actions">
          <button class="neon-btn large" @click="router.push('/register')">立即加入</button>
          <button class="outline-btn large">了解更多</button>
        </div>
      </div>
      <div class="hero-image">
        <img :src="campusHeroImg" alt="Campus 3D Illustration" class="floating-img" />
      </div>
    </main>

    <!-- Stats Section -->
    <div class="stats-container">
      <div v-for="stat in stats" :key="stat.label" class="glass-card stat-card">
        <div class="stat-icon" :style="{ backgroundColor: stat.color + '20', color: stat.color }">
          <n-icon size="24"><component :is="stat.icon" /></n-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </div>

    <!-- Hot Spaces -->
    <div class="hot-spaces-section">
      <div class="section-header">
        <div class="title-wrap">
          <n-icon size="24" color="#38bdf8"><PlanetOutline /></n-icon>
          <h3>热门学习空间</h3>
        </div>
        <a class="view-all" @click="router.push('/spaces')">查看全部 ></a>
      </div>
      <div class="spaces-grid">
        <div v-for="space in hotSpaces" :key="space.name" class="glass-card space-card">
          <div class="space-header">
            <div class="space-icon" :style="{ backgroundColor: space.color }">
              <n-icon size="24" color="#fff"><SchoolOutline /></n-icon>
            </div>
            <div class="space-title">
              <h4>{{ space.name }}</h4>
              <p>{{ space.desc }}</p>
            </div>
          </div>
          <div class="space-stats">
            <span>成员 {{ space.members }}</span>
            <span class="dot">·</span>
            <span>帖子 {{ space.posts }}</span>
          </div>
          <div class="space-footer">
            <div class="avatars">
              <div class="avatar" style="background: #ef4444"></div>
              <div class="avatar" style="background: #3b82f6"></div>
              <div class="avatar" style="background: #10b981"></div>
            </div>
            <div class="tag">{{ space.type }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.home-container {
  min-height: 100vh;
  background: var(--cf-bg-base);
  background-image: radial-gradient(circle at 15% 50%, rgba(99, 102, 241, 0.15), transparent 25%),
                    radial-gradient(circle at 85% 30%, rgba(139, 92, 246, 0.15), transparent 25%);
  padding-bottom: 80px;
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 40px;
  background: rgba(13, 17, 23, 0.8);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid var(--cf-border);

  .nav-left {
    display: flex;
    align-items: center;
    gap: 40px;

    .logo {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 20px;
      font-weight: bold;
      color: var(--cf-text-primary);
    }

    .nav-links {
      display: flex;
      gap: 24px;
      
      a {
        color: var(--cf-text-secondary);
        text-decoration: none;
        font-size: 15px;
        cursor: pointer;
        position: relative;
        padding: 4px 0;
        transition: color 0.3s;

        &:hover, &.active {
          color: var(--cf-text-primary);
        }

        &.active::after {
          content: '';
          position: absolute;
          bottom: -18px;
          left: 0;
          width: 100%;
          height: 3px;
          background: var(--cf-primary);
          border-radius: 3px 3px 0 0;
          box-shadow: 0 -2px 10px rgba(99, 102, 241, 0.5);
        }
      }
    }
  }

  .nav-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .search-input {
      width: 240px;
      background: rgba(255, 255, 255, 0.05);
    }

    .login-btn {
      color: var(--cf-text-primary);
    }
    
    .register-btn {
      padding: 6px 20px;
    }
  }
}

.hero-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1400px;
  margin: 60px auto 0;
  padding: 0 40px;

  .hero-content {
    flex: 1;
    max-width: 600px;

    .tag {
      display: inline-block;
      padding: 6px 16px;
      border-radius: 20px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--cf-border);
      color: var(--cf-text-secondary);
      font-size: 14px;
      margin-bottom: 24px;
    }

    .hero-title {
      font-size: 72px;
      font-weight: 800;
      margin: 0;
      background: linear-gradient(135deg, #38bdf8 0%, #818cf8 50%, #c084fc 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      line-height: 1.1;
      letter-spacing: -1px;
    }

    .hero-subtitle {
      font-size: 32px;
      font-weight: 600;
      color: var(--cf-text-primary);
      margin: 16px 0 24px;
      
      .dot {
        color: var(--cf-primary);
        margin: 0 4px;
      }
    }

    .hero-desc {
      font-size: 16px;
      color: var(--cf-text-secondary);
      line-height: 1.6;
      margin-bottom: 40px;
    }

    .hero-actions {
      display: flex;
      gap: 16px;

      .large {
        padding: 12px 32px;
        font-size: 16px;
      }

      .outline-btn {
        background: transparent;
        color: var(--cf-text-primary);
        border: 1px solid var(--cf-border-light);
        border-radius: var(--cf-radius-md);
        cursor: pointer;
        transition: all 0.3s;
        
        &:hover {
          background: rgba(255, 255, 255, 0.05);
          border-color: var(--cf-text-secondary);
        }
      }
    }
  }

  .hero-image {
    flex: 1;
    display: flex;
    justify-content: flex-end;
    
    .floating-img {
      max-width: 750px;
      width: 100%;
      animation: float 6s ease-in-out infinite;
      filter: drop-shadow(0 20px 40px rgba(99, 102, 241, 0.2));
    }
  }
}

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-20px); }
  100% { transform: translateY(0px); }
}

.stats-container {
  display: flex;
  gap: 20px;
  max-width: 1400px;
  margin: 60px auto;
  padding: 0 40px;
  flex-wrap: wrap;

  .stat-card {
    flex: 1;
    min-width: 200px;
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px 24px;

    .stat-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .stat-info {
      .stat-value {
        font-size: 24px;
        font-weight: 700;
        color: var(--cf-text-primary);
      }
      .stat-label {
        font-size: 13px;
        color: var(--cf-text-secondary);
        margin-top: 4px;
      }
    }
  }
}

.hot-spaces-section {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 40px;

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;

    .title-wrap {
      display: flex;
      align-items: center;
      gap: 12px;
      
      h3 {
        margin: 0;
        font-size: 20px;
        color: var(--cf-text-primary);
      }
    }

    .view-all {
      color: var(--cf-text-secondary);
      font-size: 14px;
      cursor: pointer;
      &:hover { color: var(--cf-primary); }
    }
  }

  .spaces-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 24px;

    .space-card {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;

      .space-header {
        display: flex;
        gap: 16px;

        .space-icon {
          width: 48px;
          height: 48px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          flex-shrink: 0;
        }

        .space-title {
          h4 {
            margin: 0 0 4px;
            font-size: 16px;
            color: var(--cf-text-primary);
          }
          p {
            margin: 0;
            font-size: 13px;
            color: var(--cf-text-secondary);
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
          }
        }
      }

      .space-stats {
        font-size: 13px;
        color: var(--cf-text-secondary);
        .dot { margin: 0 8px; }
      }

      .space-footer {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: auto;

        .avatars {
          display: flex;
          .avatar {
            width: 24px;
            height: 24px;
            border-radius: 50%;
            border: 2px solid var(--cf-bg-card);
            margin-left: -8px;
            &:first-child { margin-left: 0; }
          }
        }

        .tag {
          font-size: 12px;
          padding: 2px 8px;
          border-radius: 12px;
          background: rgba(255, 255, 255, 0.1);
          color: var(--cf-text-secondary);
        }
      }
    }
  }
}
</style>
