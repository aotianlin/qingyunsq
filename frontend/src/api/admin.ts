import { request } from './request';
import type { DashboardVO, AuditLogVO } from '@/types/admin';
import type { UserBrief } from '@/types/post';
import type { PostVO } from '@/types/post';
import type { SpaceVO } from '@/types/space';

// Dashboard
export async function getDashboard(): Promise<DashboardVO> {
  const res = await request<DashboardVO>({ method: 'GET', url: '/admin/dashboard' });
  return res.data;
}

// Users
export async function getAdminUsers(params: {
  keyword?: string;
  role?: string;
  status?: number;
  cursor?: number;
  limit?: number;
}): Promise<AdminUserItem[]> {
  const res = await request<AdminUserItem[]>({ method: 'GET', url: '/admin/users', params });
  return res.data;
}

export async function banUser(id: number): Promise<void> {
  await request({ method: 'PUT', url: `/admin/users/${id}/ban` });
}

export async function unbanUser(id: number): Promise<void> {
  await request({ method: 'PUT', url: `/admin/users/${id}/unban` });
}

export async function changeUserRole(id: number, role: string): Promise<void> {
  await request({ method: 'PUT', url: `/admin/users/${id}/role`, data: { role } });
}

// Posts
export async function getAdminPosts(params: {
  keyword?: string;
  status?: number;
  scope?: string;
  cursor?: number;
  limit?: number;
}): Promise<PostVO[]> {
  const res = await request<PostVO[]>({ method: 'GET', url: '/admin/posts', params });
  return res.data;
}

export async function togglePin(id: number): Promise<void> {
  await request({ method: 'PUT', url: `/admin/posts/${id}/pin` });
}

export async function toggleEssence(id: number): Promise<void> {
  await request({ method: 'PUT', url: `/admin/posts/${id}/essence` });
}

export async function setPostStatus(id: number, status: number): Promise<void> {
  await request({ method: 'PUT', url: `/admin/posts/${id}/status`, data: { status } });
}

export async function adminDeleteSpace(id: number): Promise<void> {
  await request({ method: 'DELETE', url: `/admin/spaces/${id}` });
}

// Spaces
export async function getAdminSpaces(params: {
  keyword?: string;
  category?: string;
  status?: number;
  cursor?: number;
  limit?: number;
}): Promise<SpaceVO[]> {
  const res = await request<SpaceVO[]>({ method: 'GET', url: '/admin/spaces', params });
  return res.data;
}

export async function setSpaceStatus(id: number, status: number): Promise<void> {
  await request({ method: 'PUT', url: `/admin/spaces/${id}/status`, data: { status } });
}

// Audit Logs
export async function getAuditLogs(params: {
  operatorId?: number;
  action?: string;
  cursor?: number;
  limit?: number;
}): Promise<AuditLogVO[]> {
  const res = await request<AuditLogVO[]>({ method: 'GET', url: '/admin/audit-logs', params });
  return res.data;
}

export interface AdminUserItem extends UserBrief {
  studentNo: string;
  email: string;
  role: string;
  status: number;
  lastLoginAt: string;
  createdAt: string;
}
