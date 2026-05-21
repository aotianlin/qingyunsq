import { request } from './request';
import type { NotificationVO } from '@/types/notification';

export async function getNotifications(cursor?: number, limit = 20): Promise<NotificationVO[]> {
  const res = await request<NotificationVO[]>({
    method: 'GET',
    url: '/notifications',
    params: { cursor, limit },
  });
  return res.data;
}

export async function getUnreadCount(): Promise<number> {
  const res = await request<{ count: number }>({
    method: 'GET',
    url: '/notifications/unread-count',
  });
  return res.data.count;
}

export async function markRead(id: number): Promise<void> {
  await request({ method: 'PUT', url: `/notifications/${id}/read` });
}

export async function markAllRead(): Promise<void> {
  await request({ method: 'PUT', url: '/notifications/read-all' });
}

export async function batchMarkRead(ids: number[]): Promise<{ count: number }> {
  const res = await request<{ count: number }>({
    method: 'PUT',
    url: '/notifications/batch-read',
    data: { ids },
  });
  return res.data;
}
