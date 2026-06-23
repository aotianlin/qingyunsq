export interface AiRequest {
  content?: string;
  title?: string;
  messages?: ChatMessage[];
  context?: string;
}

export interface ChatMessage {
  role: string;
  content: string;
}

export interface AiCitation {
  type: string;
  id: number;
  title: string;
  snippet: string;
  url: string;
}

export interface AiResponse {
  summary: string;
  riskLevel: number;
  riskReason: string;
  tags: string[];
  reply: string;
  citations?: AiCitation[];
}

export interface PostAiCard {
  tldr: string | null;
  audience: string | null;
  valueType: string | null;
  readMinutes: number | null;
  commentConsensus: string | null;
  commentDisputes: string | null;
  hotCommentId: number | null;
  hotCommentExcerpt: string | null;
  highlights: string[] | null;
}
