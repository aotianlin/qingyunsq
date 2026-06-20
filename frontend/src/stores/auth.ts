import { defineStore } from 'pinia';
import { disconnectGlobalWebSocket } from '@/composables/useWebSocket';

interface UserInfo {
  id: number;
  nickname: string;
  avatarUrl: string;
  email: string;
  role: string;
}

interface AuthState {
  token: string | null;
  user: UserInfo | null;
  isLoggedIn: boolean;
  tenantId: number | null;
  tenantCode: string | null;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => {
    const token = localStorage.getItem('token');
    const isGuest = token === 'GUEST_TOKEN';
    return {
      token,
      user: isGuest
        ? {
            id: -1,
            nickname: '游客用户',
            avatarUrl: 'https://api.dicebear.com/7.x/initials/svg?seed=Guest',
            email: 'guest@campus.edu',
            role: 'GUEST',
          }
        : null,
      isLoggedIn: !!token,
      tenantId: localStorage.getItem('tenantId') ? Number(localStorage.getItem('tenantId')) : null,
      tenantCode: localStorage.getItem('tenantCode'),
    };
  },

  actions: {
    setToken(token: string) {
      this.token = token;
      this.isLoggedIn = true;
      localStorage.setItem('token', token);
      if (token === 'GUEST_TOKEN') {
        this.user = {
          id: -1,
          nickname: '游客用户',
          avatarUrl: 'https://api.dicebear.com/7.x/initials/svg?seed=Guest',
          email: 'guest@campus.edu',
          role: 'GUEST',
        };
        localStorage.setItem('role', 'GUEST');
      }
    },

    setUser(user: UserInfo) {
      this.user = user;
      localStorage.setItem('role', user.role);
    },

    setTenant(tenantId: number, tenantCode: string) {
      this.tenantId = tenantId;
      this.tenantCode = tenantCode;
      localStorage.setItem('tenantId', String(tenantId));
      localStorage.setItem('tenantCode', tenantCode);
    },

    logout() {
      this.token = null;
      this.user = null;
      this.isLoggedIn = false;
      this.tenantId = null;
      this.tenantCode = null;
      localStorage.removeItem('token');
      localStorage.removeItem('role');
      localStorage.removeItem('tenantId');
      localStorage.removeItem('tenantCode');
      // 退出登录时主动断开 WebSocket，防止退出后仍弹消息提醒
      disconnectGlobalWebSocket();
    },
  },
});
