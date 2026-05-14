import { request } from './request';

interface UserVO {
  id: number;
  studentNo: string;
  email: string;
  nickname: string;
  avatarUrl: string;
  bio: string;
  college: string;
  major: string;
  grade: string;
  role: string;
  points: number;
  status: number;
  lastLoginAt: string;
  createdAt: string;
}

interface LoginResponse {
  token: string;
  user: UserVO;
}

export async function register(data: {
  email: string;
  password: string;
  studentNo?: string;
  nickname: string;
}): Promise<UserVO> {
  const res = await request<UserVO>({ method: 'POST', url: '/auth/register', data });
  return res.data;
}

export async function login(data: { email: string; password: string }): Promise<LoginResponse> {
  const res = await request<LoginResponse>({ method: 'POST', url: '/auth/login', data });
  return res.data;
}

export async function logout(): Promise<void> {
  await request({ method: 'POST', url: '/auth/logout' });
}

export async function getMe(): Promise<UserVO> {
  const res = await request<UserVO>({ method: 'GET', url: '/auth/me' });
  return res.data;
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await request({ method: 'PUT', url: '/auth/password', data: { oldPassword, newPassword } });
}

export async function forgotPassword(email: string): Promise<{ message: string; token: string }> {
  const res = await request<{ message: string; token: string }>({ method: 'POST', url: '/auth/forgot-password', data: { email } });
  return res.data;
}

export async function resetPassword(email: string, token: string, newPassword: string): Promise<void> {
  await request({ method: 'POST', url: '/auth/reset-password', data: { email, token, newPassword } });
}
