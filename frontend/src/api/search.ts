import { request } from './request';
import type { SearchResult } from '@/types/search';

export interface SearchParams {
  keyword: string;
  type?: string;
  sort?: string;
  cursor?: number;
  limit?: number;
}

export async function search(params: SearchParams): Promise<SearchResult[]> {
  const res = await request<SearchResult[]>({ method: 'GET', url: '/search', params });
  return res.data;
}
