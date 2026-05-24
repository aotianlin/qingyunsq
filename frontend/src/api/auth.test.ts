import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * auth.ts 单元测试：覆盖 T3.3 关键行为
 * 1) changePassword 成功后必须主动 logout + push('/login')
 * 2) resetPassword 成功后必须主动 logout + push('/login')
 * 3) request 抛错时不应触发 logout / push（保持原会话）
 *
 * 实现说明：
 * - 用 vi.mock 拦截 './request'、'@/stores/auth'、'@/router' 三个依赖。
 * - notifyAfterCredentialChange 内部用动态 import 引入 store 与 router，
 *   vi.mock 默认是 hoisted 且对动态 import 同样生效，所以无需额外配置。
 */

// 模拟 request：默认成功，单测里按需通过 mockRejectedValueOnce 切到失败分支
const requestMock = vi.fn();
vi.mock('./request', () => ({
  request: (...args: unknown[]) => requestMock(...args),
}));

// 模拟 useAuthStore：返回带 logout spy 的对象
const logoutSpy = vi.fn();
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    logout: logoutSpy,
  }),
}));

// 模拟 router：default 导出一个带 push spy 的对象
const pushSpy = vi.fn();
vi.mock('@/router', () => ({
  default: {
    push: pushSpy,
  },
}));

// 注意：要在 vi.mock 之后再 import 被测模块，确保模拟生效
import { changePassword, resetPassword } from './auth';

describe('api/auth.ts - 密码变更后登出与跳转', () => {
  beforeEach(() => {
    requestMock.mockReset();
    logoutSpy.mockReset();
    pushSpy.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('changePassword_success_logsOutAndRedirects: 修改密码成功后调用 logout 与 router.push("/login")', async () => {
    // request 返回成功（resolve undefined 即可，调用方不消费返回值）
    requestMock.mockResolvedValueOnce({ data: undefined, code: 0, message: 'ok' });

    await changePassword('OldPwd123', 'NewPwd456');

    // 1) request 被以正确参数调用
    expect(requestMock).toHaveBeenCalledTimes(1);
    expect(requestMock).toHaveBeenCalledWith({
      method: 'PUT',
      url: '/auth/password',
      data: { oldPassword: 'OldPwd123', newPassword: 'NewPwd456' },
    });

    // 2) 登出与跳转各被触发 1 次
    expect(logoutSpy).toHaveBeenCalledTimes(1);
    expect(pushSpy).toHaveBeenCalledTimes(1);
    expect(pushSpy).toHaveBeenCalledWith('/login');
  });

  it('resetPassword_success_logsOutAndRedirects: 重置密码成功后调用 logout 与 router.push("/login")', async () => {
    requestMock.mockResolvedValueOnce({ data: undefined, code: 0, message: 'ok' });

    await resetPassword('user@example.com', '123456', 'NewPwd789');

    expect(requestMock).toHaveBeenCalledTimes(1);
    expect(requestMock).toHaveBeenCalledWith({
      method: 'POST',
      url: '/auth/reset-password',
      data: { email: 'user@example.com', emailCode: '123456', newPassword: 'NewPwd789' },
    });

    expect(logoutSpy).toHaveBeenCalledTimes(1);
    expect(pushSpy).toHaveBeenCalledTimes(1);
    expect(pushSpy).toHaveBeenCalledWith('/login');
  });

  it('changePassword_failure_doesNotLogout: 后端报错时不应登出也不跳转', async () => {
    const apiError = new Error('旧密码不正确');
    requestMock.mockRejectedValueOnce(apiError);

    await expect(changePassword('WrongOld', 'NewPwd456')).rejects.toThrow('旧密码不正确');

    // 失败分支不能影响当前会话
    expect(logoutSpy).not.toHaveBeenCalled();
    expect(pushSpy).not.toHaveBeenCalled();
  });
});
