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
 * WebSocket 通知连接 composable。
 *
 * 连接 URL 格式：/ws/notify?token={SaToken}
 * 后端 TenantHandshakeInterceptor 从 query string 中提取 token，
 * 验证有效性并从 Sa-Token Session 读取 tenantId 写入 WebSocket attributes。
 *
 * NOTE: package.json 中残留 socket.io-client 依赖但未使用，
 * 可在后续清理中移除（参见 OS-2 spec）。
 */
export function useWebSocket(onNotify?: (event: NotifyEvent) => void) {
  const connected = ref(false);
  let ws: WebSocket | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  function connect() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    // token 通过 query string 传递，供后端 TenantHandshakeInterceptor 校验
    const url = `${protocol}//${location.host}/ws/notify?token=${token}`;

    ws = new WebSocket(url);

    ws.onopen = () => {
      connected.value = true;
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data) as NotifyEvent;
        onNotify?.(data);
      } catch {
        // ignore malformed messages
      }
    };

    ws.onclose = () => {
      connected.value = false;
      ws = null;
      reconnectTimer = setTimeout(connect, 5000);
    };

    ws.onerror = () => {
      ws?.close();
    };
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    if (ws) {
      ws.onclose = null;
      ws.close();
      ws = null;
    }
    connected.value = false;
  }

  onMounted(connect);
  onUnmounted(disconnect);

  return { connected, disconnect };
}
