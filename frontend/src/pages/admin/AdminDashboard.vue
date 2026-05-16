<script setup lang="ts">
import { 
  DesktopOutline,
  PeopleOutline,
  LibraryOutline,
  DocumentTextOutline,
  CheckmarkCircleOutline,
  TrophyOutline,
  SettingsOutline,
  HardwareChipOutline,
  ListOutline,
  NotificationsOutline
} from '@vicons/ionicons5';

const menus = [
  { label: '工作台', icon: DesktopOutline, active: true },
  { label: '用户管理', icon: PeopleOutline, active: false },
  { label: '空间管理', icon: LibraryOutline, active: false },
  { label: '内容管理', icon: DocumentTextOutline, active: false },
  { label: '打卡管理', icon: CheckmarkCircleOutline, active: false },
  { label: '积分管理', icon: TrophyOutline, active: false },
  { label: 'AI 配置', icon: HardwareChipOutline, active: false },
  { label: '系统设置', icon: SettingsOutline, active: false },
  { label: '日志管理', icon: ListOutline, active: false },
];

const stats = [
  { label: '总用户', value: '56,231', trend: '+12.5%', isUp: true },
  { label: '学习空间', value: '1,234', trend: '+8.2%', isUp: true },
  { label: '帖子总数', value: '324,112', trend: '+15.3%', isUp: true },
  { label: '今日活跃', value: '8,931', trend: '+18.6%', isUp: true },
];

const sysInfo = [
  { label: 'CPU 使用率', percent: 23, color: '#38bdf8' },
  { label: '内存使用率', percent: 45, color: '#c084fc' },
  { label: '存储使用率', percent: 32, color: '#10b981' },
];

const logs = [
  { user: '管理员', action: '创建了学习空间 "数据结构与算法"', time: '05-22 14:30' },
  { user: '系统', action: 'AI 敏感词库更新完成', time: '05-22 12:15' },
];
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="logo">
        <n-icon size="20" color="#6366f1"><LibraryOutline/></n-icon>
        CampusForum <span class="badge">后台管理</span>
      </div>
      <nav class="menu">
        <div v-for="m in menus" :key="m.label" 
             class="menu-item" :class="{ active: m.active }">
          <n-icon size="18"><component :is="m.icon" /></n-icon>
          {{ m.label }}
        </div>
      </nav>
    </aside>

    <main class="admin-content">
      <header class="top-bar">
        <div class="title">工作台</div>
        <div class="actions">
          <n-icon size="20"><NotificationsOutline/></n-icon>
          <div class="admin-profile">
            <div class="avatar"></div>
            <span>管理员</span>
          </div>
        </div>
      </header>

      <div class="dashboard-grid">
        <!-- Stats Row -->
        <div class="stats-row">
          <div v-for="stat in stats" :key="stat.label" class="glass-card stat-box">
            <div class="label">{{ stat.label }}</div>
            <div class="value-row">
              <span class="value">{{ stat.value }}</span>
              <span class="trend" :class="{ up: stat.isUp }">{{ stat.trend }}</span>
            </div>
            <!-- decorative sparkline -->
            <div class="sparkline">
              <svg viewBox="0 0 100 20" class="chart-svg">
                <path d="M0,15 L20,5 L40,10 L60,2 L80,8 L100,0" fill="none" :stroke="stat.isUp ? '#10b981' : '#ef4444'" stroke-width="2" />
              </svg>
            </div>
          </div>
        </div>

        <!-- Charts Row -->
        <div class="charts-row">
          <div class="glass-card chart-card flex-2">
            <div class="card-header">
              <h3>数据趋势</h3>
              <div class="legend">
                <span><span class="dot" style="background:#38bdf8"></span>用户数</span>
                <span><span class="dot" style="background:#c084fc"></span>帖子数</span>
                <span><span class="dot" style="background:#10b981"></span>活跃数</span>
              </div>
            </div>
            <div class="chart-area mock-line-chart">
              <!-- Mock Line Chart -->
              <svg viewBox="0 0 100 40" class="full-svg">
                <path d="M0,35 L20,25 L40,30 L60,10 L80,15 L100,5" fill="none" stroke="#38bdf8" stroke-width="1" />
                <path d="M0,38 L20,35 L40,20 L60,25 L80,10 L100,15" fill="none" stroke="#c084fc" stroke-width="1" />
                <path d="M0,39 L20,30 L40,35 L60,20 L80,5 L100,8" fill="none" stroke="#10b981" stroke-width="1" />
              </svg>
              <div class="x-axis">
                <span>05-16</span><span>05-17</span><span>05-18</span><span>05-19</span><span>05-20</span><span>05-21</span>
              </div>
            </div>
          </div>
          
          <div class="glass-card chart-card flex-1">
            <div class="card-header">
              <h3>用户来源</h3>
            </div>
            <div class="chart-area mock-pie-chart">
              <div class="donut">
                <div class="inner-circle">
                  <span class="total">56,231</span>
                  <span class="sub">总用户</span>
                </div>
              </div>
              <div class="pie-legend">
                <div class="l-item"><span class="dot" style="background:#3b82f6"></span> Web <span class="pct">56.2%</span></div>
                <div class="l-item"><span class="dot" style="background:#10b981"></span> Android <span class="pct">24.1%</span></div>
                <div class="l-item"><span class="dot" style="background:#8b5cf6"></span> iOS <span class="pct">12.4%</span></div>
                <div class="l-item"><span class="dot" style="background:#6b7280"></span> 其他 <span class="pct">7.3%</span></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Bottom Row -->
        <div class="bottom-row">
          <div class="glass-card info-card">
            <div class="card-header">
              <h3>系统信息</h3>
            </div>
            <div class="sys-list">
              <div v-for="sys in sysInfo" :key="sys.label" class="sys-item">
                <div class="sys-label">
                  <span><n-icon><HardwareChipOutline/></n-icon> {{ sys.label }}</span>
                  <span>{{ sys.percent }}%</span>
                </div>
                <div class="progress-bar">
                  <div class="fill" :style="{ width: sys.percent + '%', backgroundColor: sys.color }"></div>
                </div>
              </div>
            </div>
          </div>

          <div class="glass-card info-card">
            <div class="card-header">
              <h3>最近操作日志</h3>
              <span class="more">更多 ></span>
            </div>
            <div class="log-list">
              <div v-for="(log, idx) in logs" :key="idx" class="log-item">
                <div class="log-icon">
                  <div class="avatar-small"></div>
                </div>
                <div class="log-content">
                  <p><span class="user">{{ log.user }}</span> {{ log.action }}</p>
                </div>
                <div class="log-time">{{ log.time }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100vh;
  background: var(--cf-bg-base);
  color: var(--cf-text-primary);
}

.admin-sidebar {
  width: 220px;
  background: rgba(22, 27, 34, 0.95);
  border-right: 1px solid var(--cf-border);
  display: flex;
  flex-direction: column;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 20px;
    font-size: 16px;
    font-weight: bold;
    border-bottom: 1px solid var(--cf-border);

    .badge {
      font-size: 10px;
      font-weight: normal;
      background: rgba(255,255,255,0.1);
      padding: 2px 6px;
      border-radius: 4px;
      color: var(--cf-text-secondary);
    }
  }

  .menu {
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 4px;

    .menu-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 6px;
      color: var(--cf-text-secondary);
      font-size: 14px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover { background: rgba(255,255,255,0.05); color: white; }
      &.active {
        background: rgba(99,102,241,0.15);
        color: var(--cf-primary);
        font-weight: 500;
      }
    }
  }
}

.admin-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .top-bar {
    height: 60px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 32px;
    border-bottom: 1px solid var(--cf-border);
    
    .title { font-size: 18px; font-weight: 600; }
    .actions {
      display: flex; align-items: center; gap: 24px; color: var(--cf-text-secondary);
      .admin-profile {
        display: flex; align-items: center; gap: 8px; font-size: 14px; color: white;
        .avatar { width: 32px; height: 32px; border-radius: 50%; background: var(--cf-primary); }
      }
    }
  }

  .dashboard-grid {
    flex: 1;
    overflow-y: auto;
    padding: 24px 32px;
    display: flex;
    flex-direction: column;
    gap: 24px;

    .stats-row {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 24px;

      .stat-box {
        padding: 24px;
        position: relative;
        overflow: hidden;

        .label { color: var(--cf-text-secondary); font-size: 14px; margin-bottom: 12px; }
        .value-row {
          display: flex; align-items: baseline; gap: 12px;
          .value { font-size: 28px; font-weight: bold; color: white; }
          .trend { font-size: 13px; &.up { color: var(--cf-success); } }
        }

        .sparkline {
          margin-top: 16px;
          height: 30px;
          width: 100%;
          .chart-svg { width: 100%; height: 100%; overflow: visible; }
        }
      }
    }

    .charts-row {
      display: flex;
      gap: 24px;

      .chart-card {
        padding: 24px;
        display: flex; flex-direction: column;

        &.flex-2 { flex: 2; }
        &.flex-1 { flex: 1; }

        .card-header {
          display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;
          h3 { margin: 0; font-size: 16px; font-weight: 500; }
          .legend {
            display: flex; gap: 16px; font-size: 12px; color: var(--cf-text-secondary);
            span { display: flex; align-items: center; gap: 6px; .dot { width: 8px; height: 8px; border-radius: 50%; } }
          }
        }

        .chart-area {
          flex: 1;
          display: flex;
          flex-direction: column;
          min-height: 200px;
          
          &.mock-line-chart {
            justify-content: flex-end;
            .full-svg { width: 100%; height: 180px; overflow: visible; }
            .x-axis {
              display: flex; justify-content: space-between; margin-top: 12px;
              span { font-size: 11px; color: var(--cf-text-muted); }
            }
          }

          &.mock-pie-chart {
            flex-direction: row; align-items: center; gap: 32px;
            .donut {
              width: 140px; height: 140px; border-radius: 50%;
              background: conic-gradient(#3b82f6 0% 56.2%, #10b981 56.2% 80.3%, #8b5cf6 80.3% 92.7%, #6b7280 92.7% 100%);
              display: flex; align-items: center; justify-content: center;
              .inner-circle {
                width: 100px; height: 100px; border-radius: 50%; background: var(--cf-bg-card);
                display: flex; flex-direction: column; align-items: center; justify-content: center;
                .total { font-size: 18px; font-weight: bold; color: white; }
                .sub { font-size: 12px; color: var(--cf-text-secondary); }
              }
            }
            .pie-legend {
              flex: 1; display: flex; flex-direction: column; gap: 12px;
              .l-item {
                display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--cf-text-secondary);
                .dot { width: 10px; height: 10px; border-radius: 50%; }
                .pct { margin-left: auto; color: white; font-weight: 500; }
              }
            }
          }
        }
      }
    }

    .bottom-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;

      .info-card {
        padding: 24px;
        .card-header {
          display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;
          h3 { margin: 0; font-size: 16px; font-weight: 500; }
          .more { font-size: 13px; color: var(--cf-text-secondary); cursor: pointer; }
        }

        .sys-list {
          display: flex; flex-direction: column; gap: 20px;
          .sys-item {
            .sys-label {
              display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px; color: var(--cf-text-secondary);
              span { display: flex; align-items: center; gap: 6px; }
            }
            .progress-bar {
              height: 6px; background: rgba(255,255,255,0.05); border-radius: 3px; overflow: hidden;
              .fill { height: 100%; border-radius: 3px; }
            }
          }
        }

        .log-list {
          display: flex; flex-direction: column; gap: 16px;
          .log-item {
            display: flex; gap: 16px; align-items: flex-start;
            .avatar-small { width: 32px; height: 32px; border-radius: 50%; background: #38bdf8; flex-shrink: 0; }
            .log-content { flex: 1; p { margin: 0; font-size: 14px; color: var(--cf-text-secondary); .user { color: white; font-weight: 500; } } }
            .log-time { font-size: 12px; color: var(--cf-text-muted); flex-shrink: 0; }
          }
        }
      }
    }
  }
}
</style>
