import { request } from './request'
import type { MessageVO } from '@/types/message'

export async function sendMessage(receiverId: number, content: string, imageUrl?: string): Promise<MessageVO> {
  const res = await request<MessageVO>({
    method: 'POST',
    url: '/messages',
    data: { receiverId: String(receiverId), content, imageUrl },
  })
  return res.data
}

export async function listConversations(): Promise<MessageVO[]> {
  const res = await request<MessageVO[]>({ method: 'GET', url: '/messages/conversations' })
  return res.data
}

export async function getConversation(peerId: number, cursor?: number, limit = 50): Promise<MessageVO[]> {
  const res = await request<MessageVO[]>({
    method: 'GET',
    url: `/messages/conversations/${peerId}`,
    params: { cursor, limit },
  })
  return res.data
}

export async function markRead(peerId: number): Promise<void> {
  await request({ method: 'PUT', url: `/messages/conversations/${peerId}/read` })
}

export async function getUnreadCount(): Promise<number> {
  const res = await request<number>({ method: 'GET', url: '/messages/unread-count' })
  return res.data
}

export async function markAllMessagesRead(): Promise<{ count: number }> {
  const res = await request<{ count: number }>({
    method: 'PUT',
    url: '/messages/read-all',
  });
  return res.data;
}
