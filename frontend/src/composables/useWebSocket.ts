import { ref, onMounted, onUnmounted } from 'vue';

export interface NotifyEvent {
  type: string;
  title?: string;
  content?: string;
  postId?: number;
  action?: string;
  [key: string]: unknown;
}

/**
 * 全局 WebSocket 连接管理器。
 * 维护一个全局连接实例，所有组件共享同一个 WebSocket。
 * 退出登录时调用 disconnectGlobalWebSocket() 主动断开。
 */
let globalWs: WebSocket | null = null;
let globalReconnectTimer: ReturnType<typeof setTimeout> | null = null;
let globalListeners: Set<(event: NotifyEvent) => void> = new Set();
let globalConnected = false;

function globalConnect() {
  const token = localStorage.getItem('token');
  if (!token) return;

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const url = `${protocol}//${location.host}/ws/notify?token=${token}`;

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
    const token = localStorage.getItem('token');
    if (token) {
      globalReconnectTimer = setTimeout(globalConnect, 5000);
    }
  };

  globalWs.onerror = () => {
    globalWs?.close();
  };
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
  if (!globalWs && localStorage.getItem('token')) {
    globalConnect();
  }
}

/**
 * WebSocket 通知连接 composable。
 *
 * 连接 URL 格式：/ws/notify?token={SaToken}
 * 后端 TenantHandshakeInterceptor 从 query string 中提取 token，
 * 验证有效性并从 Sa-Token Session 读取 tenantId 写入 WebSocket attributes。
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
