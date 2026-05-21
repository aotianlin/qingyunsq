import { request } from './request';
import type { AiResponse } from '@/types/ai';

export async function aiSummarize(content: string): Promise<AiResponse> {
  const res = await request<AiResponse>({ method: 'POST', url: '/ai/summarize', data: { content } });
  return res.data;
}

export async function aiModerate(content: string): Promise<AiResponse> {
  const res = await request<AiResponse>({ method: 'POST', url: '/ai/moderate', data: { content } });
  return res.data;
}

export async function aiRecommendTags(title: string, content: string): Promise<AiResponse> {
  const res = await request<AiResponse>({ method: 'POST', url: '/ai/tags', data: { title, content } });
  return res.data;
}

export async function aiChat(messages: { role: string; content: string }[], context?: string): Promise<AiResponse> {
  const res = await request<AiResponse>({ method: 'POST', url: '/ai/chat', data: { messages, context } });
  return res.data;
}
