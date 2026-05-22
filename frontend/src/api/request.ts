import axios from 'axios';
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';

interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

const instance: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// 请求拦截：注入 Token
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = token;
  }
  // X-Tenant-Id 不再由前端注入，租户由服务端从 Sa-Token Session 权威解析
  return config;
});

// 响应拦截：统一错误处理
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { code, message } = response.data;
    if (code !== 0) {
      console.error(`[API Error] ${code}: ${message}`);
      return Promise.reject(new Error(message));
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    } else if (error.response?.status === 429) {
      const retryAfter = error.response.headers['retry-after'];
      const msg = retryAfter
        ? `请求过于频繁，请 ${retryAfter} 秒后重试`
        : '请求过于频繁，请稍后重试';
      console.warn(`[Rate Limited] ${msg}`);
      error.message = msg;
    } else if (error.response?.status === 415) {
      error.message = '该文件类型不支持此操作';
    } else if (error.response?.status === 413) {
      error.message = '文件过大';
    } else if (error.response?.data?.message) {
      // 提取后端返回的具体错误信息（如 400 参数校验失败）
      error.message = error.response.data.message;
    }
    return Promise.reject(error);
  },
);

export async function request<T>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  const response = await instance.request<ApiResponse<T>>(config);
  return response.data;
}

export default instance;
