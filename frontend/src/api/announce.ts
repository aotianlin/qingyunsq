import { request } from './request';
import type {
  GetAiRecommendationsRequest,
  GetAiRecommendationsResponse,
  GetDailyHotArticlesRequest,
  GetDailyHotArticlesResponse,
  GetHotTopicsRequest,
  GetHotTopicsResponse,
  GetOfficialAnnouncementsRequest,
  GetOfficialAnnouncementsResponse,
  GetPostDetailSidebarRequest,
  GetPostDetailSidebarResponse,
  HotArticle,
  ReportHotArticleClickRequest,
  ReportHotArticleClickResponse,
} from '@/types/announce';

const HOT_ARTICLE_CACHE_PREFIX = 'campus:daily-hot:article:';

export async function getDailyHotArticles(
  params?: GetDailyHotArticlesRequest,
): Promise<GetDailyHotArticlesResponse> {
  const res = await request<GetDailyHotArticlesResponse>({
    method: 'GET',
    url: '/announce/daily-hot',
    params,
  });
  return res.data;
}

export async function reportHotArticleClick(
  data: ReportHotArticleClickRequest,
): Promise<ReportHotArticleClickResponse> {
  const res = await request<ReportHotArticleClickResponse>({
    method: 'POST',
    url: '/announce/daily-hot/clicks',
    data,
  });
  return res.data;
}

export async function getOfficialAnnouncements(
  params?: GetOfficialAnnouncementsRequest,
): Promise<GetOfficialAnnouncementsResponse> {
  const res = await request<GetOfficialAnnouncementsResponse>({
    method: 'GET',
    url: '/announce/official-announcements',
    params,
  });
  return res.data;
}

export async function getAiRecommendations(
  params?: GetAiRecommendationsRequest,
): Promise<GetAiRecommendationsResponse> {
  const res = await request<GetAiRecommendationsResponse>({
    method: 'GET',
    url: '/announce/ai-recommendations',
    params,
  });
  return res.data;
}

export async function getHotTopics(params?: GetHotTopicsRequest): Promise<GetHotTopicsResponse> {
  const res = await request<GetHotTopicsResponse>({
    method: 'GET',
    url: '/announce/hot-topics',
    params,
  });
  return res.data;
}

export async function getPostDetailSidebar(
  params: GetPostDetailSidebarRequest,
): Promise<GetPostDetailSidebarResponse> {
  const res = await request<GetPostDetailSidebarResponse>({
    method: 'GET',
    url: '/announce/post-detail-sidebar',
    params,
  });
  return res.data;
}

export function cacheHotArticleForFallback(article: HotArticle) {
  if (typeof window === 'undefined') return;
  const fallbackId = String(article.postId ?? article.id);
  window.sessionStorage.setItem(`${HOT_ARTICLE_CACHE_PREFIX}${fallbackId}`, JSON.stringify(article));
}

export function readCachedHotArticle(fallbackId: string): HotArticle | null {
  if (typeof window === 'undefined') return null;
  const raw = window.sessionStorage.getItem(`${HOT_ARTICLE_CACHE_PREFIX}${fallbackId}`);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as HotArticle;
  } catch {
    return null;
  }
}
