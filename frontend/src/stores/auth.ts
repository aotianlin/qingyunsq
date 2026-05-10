import { defineStore } from 'pinia';

interface UserInfo {
  id: number;
  nickname: string;
  avatarUrl: string;
  email: string;
  role: string;
  points: number;
}

interface AuthState {
  token: string | null;
  user: UserInfo | null;
  isLoggedIn: boolean;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('token'),
    user: null,
    isLoggedIn: !!localStorage.getItem('token'),
  }),

  actions: {
    setToken(token: string) {
      this.token = token;
      this.isLoggedIn = true;
      localStorage.setItem('token', token);
    },

    setUser(user: UserInfo) {
      this.user = user;
      localStorage.setItem('role', user.role);
    },

    logout() {
      this.token = null;
      this.user = null;
      this.isLoggedIn = false;
      localStorage.removeItem('token');
      localStorage.removeItem('role');
    },
  },
});
