import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AxiosRequestConfig } from 'axios';

const axiosMocks = vi.hoisted(() => {
  const requestUse = vi.fn();
  const responseUse = vi.fn();
  const request = vi.fn();
  const create = vi.fn(() => ({
    interceptors: {
      request: { use: requestUse },
      response: { use: responseUse },
    },
    request,
  }));
  return { create, requestUse, responseUse, request };
});

vi.mock('axios', () => ({
  default: {
    create: axiosMocks.create,
  },
}));

import './request';

const applyRequestInterceptor = (config: AxiosRequestConfig) => {
  const interceptor = axiosMocks.requestUse.mock.calls[0][0] as (cfg: AxiosRequestConfig) => AxiosRequestConfig;
  return interceptor(config);
};

describe('api/request.ts - auth header injection', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => 'STALE_TOKEN'),
      removeItem: vi.fn(),
    });
  });

  it('doesNotSendStaleTokenToEmailCodeEndpoint', () => {
    const config = applyRequestInterceptor({
      url: '/auth/email-code',
      headers: {
        Authorization: 'STALE_TOKEN',
        authorization: 'LOWERCASE_STALE_TOKEN',
        'X-Tenant-Id': '2',
        'x-tenant-id': '2',
      },
    });

    expect(config.headers?.Authorization).toBeUndefined();
    expect(config.headers?.authorization).toBeUndefined();
    expect(config.headers?.['X-Tenant-Id']).toBeUndefined();
    expect(config.headers?.['x-tenant-id']).toBeUndefined();
  });

  it('doesNotSendStaleTokenWhenAxiosHeadersDeleteMethodIsUsed', () => {
    const headers = {
      Authorization: 'STALE_TOKEN',
      authorization: 'LOWERCASE_STALE_TOKEN',
      'X-Tenant-Id': '2',
      delete: vi.fn(function (this: Record<string, unknown>, name: string) {
        delete this[name];
      }),
    } as unknown as AxiosRequestConfig['headers'] & { delete: ReturnType<typeof vi.fn> };

    const config = applyRequestInterceptor({
      url: '/auth/email-code',
      headers,
    });

    expect(headers.delete).toHaveBeenCalledWith('Authorization');
    expect(headers.delete).toHaveBeenCalledWith('authorization');
    expect(headers.delete).toHaveBeenCalledWith('X-Tenant-Id');
    expect(config.headers?.Authorization).toBeUndefined();
    expect(config.headers?.authorization).toBeUndefined();
    expect(config.headers?.['X-Tenant-Id']).toBeUndefined();
  });

  it('doesNotSendStaleTokenToAnonymousAuthEndpointWithApiPrefix', () => {
    const config = applyRequestInterceptor({
      url: '/api/v1/auth/register?invite=abc',
      headers: { Authorization: 'STALE_TOKEN' },
    });

    expect(config.headers?.Authorization).toBeUndefined();
  });

  it('sendsTokenToAuthenticatedEndpoint', () => {
    const config = applyRequestInterceptor({
      url: '/users/me',
      headers: {},
    });

    expect(config.headers?.Authorization).toBe('STALE_TOKEN');
  });
});
