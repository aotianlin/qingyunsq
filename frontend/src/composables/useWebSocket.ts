import { ref, onMounted, onUnmounted } from 'vue';
import { request } from '@/api/request';

export interface NotifyEvent {
  type: string;
  title?: string;
  content?: string;
  postId?: number;
  action?: string;
  [key: string]: unknown;
}

interface WsTicketResponse {
  ticket: string;
  expiresAt: number;
}

/**
 * 全局 WebSocket 连接管理器。
 * 维护一个全局连接实例，所有组件共享同一个 WebSocket。
 * 退出登录时调用 disconnectGlobalWebSocket() 主动断开。
 *
 * 安全说明：建立连接前先调用 /api/v1/auth/ws-ticket 获取 30 秒短期票据，
 * 用 ticket 替代 Sa-Token 主令牌走 query string，避免主令牌泄漏到 access log/Referer。
 */
let globalWs: WebSocket | null = null;
let globalReconnectTimer: ReturnType<typeof setTimeout> | null = null;
const globalListeners: Set<(event: NotifyEvent) => void> = new Set();
let globalConnected = false;
let connecting = false;

async function fetchWsTicket(): Promise<string | null> {
  try {
    const resp = await request<WsTicketResponse>({
      method: 'POST',
      url: '/auth/ws-ticket',
    });
    return resp.data?.ticket || null;
  } catch (err) {
    console.warn('[WebSocket] 获取 ticket 失败', err);
    return null;
  }
}

async function globalConnect() {
  // 防止并发触发多次握手
  if (connecting || globalWs) return;
  const token = localStorage.getItem('token');
  if (!token) return;

  connecting = true;
  try {
    const ticket = await fetchWsTicket();
    if (!ticket) {
      // 票据获取失败：不建立连接，5 秒后由 onclose 路径或下一次组件挂载触发重试
      connecting = false;
      scheduleReconnect();
      return;
    }

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${location.host}/ws/notify?ticket=${encodeURIComponent(ticket)}`;

    globalWs = new WebSocket(url);

    globalWs.onopen = () => {
      globalConnected = true;
    };

    globalWs.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data) as NotifyEvent;
        globalListeners.forEach((listener) => listener(data));
      } catch {
        // 忽略格式错误的消息
      }
    };

    globalWs.onclose = () => {
      globalConnected = false;
      globalWs = null;
      // 仅在用户仍登录时重连
      const stillLogged = localStorage.getItem('token');
      if (stillLogged) {
        scheduleReconnect();
      }
    };

    globalWs.onerror = () => {
      globalWs?.close();
    };
  } finally {
    connecting = false;
  }
}

function scheduleReconnect() {
  if (globalReconnectTimer) return;
  globalReconnectTimer = setTimeout(() => {
    globalReconnectTimer = null;
    globalConnect();
  }, 5000);
}

/**
 * 主动断开全局 WebSocket 连接。
 * 在用户退出登录时调用，防止退出后仍收到消息提醒。
 */
export function disconnectGlobalWebSocket() {
  if (globalReconnectTimer) {
    clearTimeout(globalReconnectTimer);
    globalReconnectTimer = null;
  }
  if (globalWs) {
    globalWs.onclose = null;
    globalWs.close();
    globalWs = null;
  }
  globalConnected = false;
  globalListeners.clear();
}

/**
 * 确保全局 WebSocket 已连接（首次调用时建立连接）。
 */
export function ensureGlobalWebSocket() {
  if (!globalWs && !connecting && localStorage.getItem('token')) {
    globalConnect();
  }
}

/**
 * WebSocket 通知连接 composable。
 *
 * 连接 URL 格式：/ws/notify?ticket={WsTicket}
 * 后端 TenantHandshakeInterceptor 优先识别 ticket，校验通过后从中提取 userId/tenantId
 * 写入 WebSocket attributes。ticket 由 SignedUrlService HMAC 签名，TTL=30 秒。
 */
export function useWebSocket(onNotify?: (event: NotifyEvent) => void) {
  const connected = ref(false);

  function updateConnected() {
    connected.value = globalConnected;
  }

  onMounted(() => {
    ensureGlobalWebSocket();
    if (onNotify) {
      globalListeners.add(onNotify);
    }
    updateConnected();
  });

  onUnmounted(() => {
    if (onNotify) {
      globalListeners.delete(onNotify);
    }
  });

  return { connected, disconnect: disconnectGlobalWebSocket };
}
