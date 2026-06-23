import { request } from './request';
import type { PointsLogVO } from '@/types/points';

export async function getBalance(userId?: number): Promise<number> {
  const res = await request<number>({ method: 'GET', url: '/points/balance', params: userId ? { userId } : {} });
  return res.data;
}

export async function getPointsLogs(userId?: number, cursor?: number, limit?: number): Promise<PointsLogVO[]> {
  const res = await request<PointsLogVO[]>({ method: 'GET', url: '/points/logs', params: { userId, cursor, limit } });
  return res.data;
}
