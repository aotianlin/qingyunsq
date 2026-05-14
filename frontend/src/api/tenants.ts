import { request } from './request';

export interface TenantVO {
  id: number;
  code: string;
  name: string;
  logoUrl: string;
  domain: string;
  status: number;
  aiConfig: string;
  announcement: string;
  createdAt: string;
  updatedAt: string;
}

export async function getTenants(params?: {
  keyword?: string;
  cursor?: number;
  limit?: number;
}): Promise<TenantVO[]> {
  const res = await request<TenantVO[]>({ method: 'GET', url: '/admin/tenants', params });
  return res.data;
}

export async function createTenant(data: {
  code: string;
  name: string;
  domain?: string;
}): Promise<TenantVO> {
  const res = await request<TenantVO>({ method: 'POST', url: '/admin/tenants', data });
  return res.data;
}

export async function getTenantInfo(): Promise<{
  id: number; code: string; name: string;
  logoUrl: string; domain: string; announcement: string;
}> {
  const res = await request<any>({ method: 'GET', url: '/tenant/info' });
  return res.data;
}

export async function updateTenant(id: number, data: {
  name?: string;
  domain?: string;
  logoUrl?: string;
  announcement?: string;
}): Promise<TenantVO> {
  const res = await request<TenantVO>({ method: 'PUT', url: `/admin/tenants/${id}`, data });
  return res.data;
}

export async function toggleTenantStatus(id: number): Promise<void> {
  await request({ method: 'PUT', url: `/admin/tenants/${id}/status` });
}
