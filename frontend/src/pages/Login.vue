<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NInput, useMessage } from 'naive-ui';
import { login } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';
import campusHeroImg from '@/assets/images/campus_hero_3d.png';

const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const email = ref('');
const password = ref('');
const loading = ref(false);

const canvasRef = ref<HTMLCanvasElement | null>(null);
let animationFrameId: number;

onMounted(() => {
  initParticles();
});

onUnmounted(() => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId);
});

function initParticles() {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  let width = canvas.offsetWidth;
  let height = canvas.offsetHeight;
  canvas.width = width * window.devicePixelRatio;
  canvas.height = height * window.devicePixelRatio;
  ctx.scale(window.devicePixelRatio, window.devicePixelRatio);

  const particles: any[] = [];
  const particleCount = 80;
  
  let mouse = { x: width / 2, y: height / 2, radius: 150 };

  canvas.addEventListener('mousemove', (e) => {
    const rect = canvas.getBoundingClientRect();
    mouse.x = e.clientX - rect.left;
    mouse.y = e.clientY - rect.top;
  });

  class Particle {
    x: number;
    y: number;
    size: number;
    baseX: number;
    baseY: number;
    density: number;
    color: string;

    constructor(x: number, y: number) {
      this.x = x;
      this.y = y;
      this.baseX = x;
      this.baseY = y;
      this.size = Math.random() * 2 + 1;
      this.density = (Math.random() * 20) + 1;
      
      const colors = ['#6366f1', '#8b5cf6', '#38bdf8', '#c084fc'];
      this.color = colors[Math.floor(Math.random() * colors.length)];
    }

    draw() {
      if(!ctx) return;
      ctx.fillStyle = this.color;
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
      ctx.closePath();
      ctx.fill();
    }

    update() {
      let dx = mouse.x - this.x;
      let dy = mouse.y - this.y;
      let distance = Math.sqrt(dx * dx + dy * dy);
      let forceDirectionX = dx / distance;
      let forceDirectionY = dy / distance;
      let maxDistance = mouse.radius;
      let force = (maxDistance - distance) / maxDistance;
      let directionX = forceDirectionX * force * this.density;
      let directionY = forceDirectionY * force * this.density;

      // 粒子被鼠标吸引（如果想排斥，改成 -directionX）
      if (distance < mouse.radius) {
        this.x += directionX * 1.5;
        this.y += directionY * 1.5;
      } else {
        if (this.x !== this.baseX) {
          let dx = this.x - this.baseX;
          this.x -= dx / 20;
        }
        if (this.y !== this.baseY) {
          let dy = this.y - this.baseY;
          this.y -= dy / 20;
        }
      }
    }
  }

  function init() {
    for (let i = 0; i < particleCount; i++) {
      let x = Math.random() * width;
      let y = Math.random() * height;
      particles.push(new Particle(x, y));
    }
  }

  function connect() {
    if(!ctx) return;
    for (let a = 0; a < particles.length; a++) {
      for (let b = a; b < particles.length; b++) {
        let dx = particles[a].x - particles[b].x;
        let dy = particles[a].y - particles[b].y;
        let distance = dx * dx + dy * dy;

        if (distance < (width/7) * (height/7)) {
          let opacity = 1 - (distance / 10000);
          ctx.strokeStyle = `rgba(139, 92, 246, ${opacity * 0.5})`;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(particles[a].x, particles[a].y);
          ctx.lineTo(particles[b].x, particles[b].y);
          ctx.stroke();
        }
      }
    }
  }

  function animate() {
    if(!ctx) return;
    ctx.clearRect(0, 0, width, height);
    for (let i = 0; i < particles.length; i++) {
      particles[i].update();
      particles[i].draw();
    }
    connect();
    animationFrameId = requestAnimationFrame(animate);
  }

  init();
  animate();

  window.addEventListener('resize', () => {
    width = canvas.offsetWidth;
    height = canvas.offsetHeight;
    canvas.width = width * window.devicePixelRatio;
    canvas.height = height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    particles.length = 0;
    init();
  });
}

async function handleLogin() {
  if (!email.value || !password.value) {
    message.warning('请填写邮箱和密码');
    return;
  }
  loading.value = true;
  try {
    const res = await login({ email: email.value, password: password.value });
    authStore.setToken(res.token);
    authStore.setUser(res.user);
    message.success('登录成功');
    router.push('/');
  } catch {
    message.error('登录失败，请检查邮箱和密码');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-layout">
    <div class="left-panel">
      <canvas ref="canvasRef" class="particle-canvas"></canvas>
      <div class="content-wrap">
        <h1 class="brand">CampusForum</h1>
        <h2 class="slogan">探索全新的学术元宇宙</h2>
        <p class="desc">连接数万高校学子，分享知识，共同成长。开启你的数字校园生活。</p>
        <img :src="campusHeroImg" class="hero-img" alt="Campus 3D" />
      </div>
      <div class="bg-decoration"></div>
    </div>
    <div class="right-panel">
      <div class="login-box glass-card">
        <h3 class="box-title">欢迎回来 👋</h3>
        <p class="box-subtitle">请输入您的账号密码登录</p>

        <div class="form">
          <div class="form-group">
            <label>邮箱</label>
            <n-input v-model:value="email" size="large" placeholder="name@college.edu" />
          </div>
          <div class="form-group">
            <label>密码</label>
            <n-input v-model:value="password" type="password" size="large" placeholder="••••••••" show-password-on="click" />
          </div>
          
          <div class="forgot-row">
            <n-button text color="#8b949e" @click="router.push('/forgot-password')">忘记密码？</n-button>
          </div>

          <button class="neon-btn submit-btn" :disabled="loading" @click="handleLogin">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </div>

        <div class="register-prompt">
          还没有账号？ 
          <span class="link" @click="router.push('/register')">立即注册</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-layout {
  display: flex;
  height: 100vh;
  background: var(--cf-bg-base);
}

.left-panel {
  flex: 1;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(13, 17, 23, 1) 0%, rgba(30, 37, 48, 1) 100%);
  
  .bg-decoration {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background-image: radial-gradient(circle at 20% 30%, rgba(99, 102, 241, 0.2), transparent 40%),
                      radial-gradient(circle at 80% 70%, rgba(139, 92, 246, 0.2), transparent 40%);
    z-index: 1;
    pointer-events: none;
  }

  .particle-canvas {
    position: absolute;
    top: 0; left: 0; width: 100%; height: 100%;
    z-index: 2;
  }

  .content-wrap {
    position: relative;
    z-index: 3;
    text-align: center;
    pointer-events: none;
    max-width: 600px;
    padding: 40px;

    .brand {
      font-size: 48px;
      font-weight: 800;
      margin: 0 0 16px;
      background: var(--cf-gradient-primary);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .slogan {
      font-size: 28px;
      color: white;
      margin: 0 0 16px;
    }

    .desc {
      font-size: 16px;
      color: var(--cf-text-secondary);
      line-height: 1.6;
      margin-bottom: 40px;
    }

    .hero-img {
      width: 100%;
      max-width: 500px;
      animation: float 6s ease-in-out infinite;
      filter: drop-shadow(0 20px 40px rgba(99, 102, 241, 0.2));
    }
  }
}

.right-panel {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(22, 27, 34, 0.95);
  border-left: 1px solid var(--cf-border);

  .login-box {
    width: 100%;
    max-width: 360px;
    padding: 40px 32px;
    background: transparent;
    border: none;
    box-shadow: none;

    .box-title {
      font-size: 24px;
      color: white;
      margin: 0 0 8px;
    }

    .box-subtitle {
      color: var(--cf-text-secondary);
      font-size: 14px;
      margin: 0 0 32px;
    }

    .form {
      display: flex;
      flex-direction: column;
      gap: 20px;

      .form-group {
        display: flex;
        flex-direction: column;
        gap: 8px;
        label {
          font-size: 14px;
          color: var(--cf-text-primary);
        }
      }

      .forgot-row {
        display: flex;
        justify-content: flex-end;
      }

      .submit-btn {
        width: 100%;
        padding: 12px;
        font-size: 16px;
        margin-top: 8px;
        
        &:disabled {
          opacity: 0.7;
          cursor: not-allowed;
        }
      }
    }

    .register-prompt {
      margin-top: 32px;
      text-align: center;
      font-size: 14px;
      color: var(--cf-text-secondary);
      
      .link {
        color: var(--cf-primary);
        cursor: pointer;
        font-weight: 500;
        &:hover { text-decoration: underline; }
      }
    }
  }
}

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-15px); }
  100% { transform: translateY(0px); }
}
</style>
