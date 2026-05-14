import { request } from './request';
import type { ReportVO } from '@/types/report';

export async function createReport(data: {
  targetType: string;
  targetId: number;
  reason: string;
  description?: string;
}) {
  await request({ method: 'POST', url: '/reports', data });
}

export async function getReports(params?: {
  cursor?: number;
  limit?: number;
  targetType?: string;
  status?: number;
}): Promise<ReportVO[]> {
  const res = await request<ReportVO[]>({ method: 'GET', url: '/admin/reports', params });
  return res.data;
}

export async function handleReport(id: number, data: { status: number; note?: string }) {
  await request({ method: 'PUT', url: `/admin/reports/${id}/handle`, data });
}

export async function batchHandleReports(ids: number[], status: number, note?: string) {
  await request({ method: 'PUT', url: '/admin/reports/batch-handle', data: { ids, status, note } });
}
