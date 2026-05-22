import { request } from './request';
import type { ResourcePreviewVO, ResourceVO } from '@/types/resource';

export const resourceAccept =
  '.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.zip,.rar,.7z,.jpg,.jpeg,.png,.gif,.webp,.md,.markdown';

export async function uploadResource(
  file: File,
  data: Record<string, unknown>,
): Promise<ResourceVO> {
  const formData = new FormData();
  formData.append('file', file);
  for (const [key, value] of Object.entries(data)) {
    if (value !== undefined && value !== null) {
      if (Array.isArray(value)) {
        value.forEach((v) => formData.append(key, String(v)));
      } else {
        formData.append(key, String(value));
      }
    }
  }
  const res = await request<ResourceVO>({
    method: 'POST',
    url: '/resources',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function getResources(params: {
  spaceId?: number;
  college?: string;
  major?: string;
  course?: string;
  cursor?: number;
  limit?: number;
}): Promise<ResourceVO[]> {
  const res = await request<ResourceVO[]>({ method: 'GET', url: '/resources', params });
  return res.data;
}

export async function getResourceById(id: number): Promise<ResourceVO> {
  const res = await request<ResourceVO>({ method: 'GET', url: `/resources/${id}` });
  return res.data;
}

interface SignedUrlResponse {
  token: string;
  expiresAt: number;
}

/**
 * 申请短期下载/预览签名 URL。
 *
 * <p>把会话 token 拼到 URL 会让 token 出现在 access log/Referer/浏览器历史里，因此改用：
 * <ol>
 *   <li>前端先调用此接口获取一次性 sig；</li>
 *   <li>用 ?sig= 拼到下载/预览 URL；</li>
 *   <li>服务端 HMAC 校验 + 短期过期。</li>
 * </ol>
 */
async function fetchSignedToken(id: number, action: 'download' | 'preview'): Promise<string> {
  const res = await request<SignedUrlResponse>({
    method: 'GET',
    url: `/resources/${id}/signed-url`,
    params: { action },
  });
  return res.data.token;
}

export async function getDownloadUrl(id: number): Promise<string> {
  const sig = await fetchSignedToken(id, 'download');
  const base = import.meta.env.VITE_API_BASE || '/api/v1';
  return `${base}/resources/${id}/download?sig=${encodeURIComponent(sig)}`;
}

export async function getPreviewUrl(id: number): Promise<string> {
  const sig = await fetchSignedToken(id, 'preview');
  const base = import.meta.env.VITE_API_BASE || '/api/v1';
  return `${base}/resources/${id}/preview?sig=${encodeURIComponent(sig)}`;
}

export async function getResourcePreviewText(id: number): Promise<ResourcePreviewVO> {
  const res = await request<ResourcePreviewVO>({ method: 'GET', url: `/resources/${id}/preview-text` });
  return res.data;
}

export async function deleteResource(id: number): Promise<void> {
  await request({ method: 'DELETE', url: `/resources/${id}` });
}
