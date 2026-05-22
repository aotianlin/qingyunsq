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
