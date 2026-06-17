const ACCESS_TOKEN_KEY = 'ats.accessToken';
const ACCESS_TOKEN_EXPIRES_AT_KEY = 'ats.accessTokenExpiresAt';
const REFRESH_TOKEN_KEY = 'ats.refreshToken';
const REFRESH_TOKEN_EXPIRES_AT_KEY = 'ats.refreshTokenExpiresAt';

export type AuthTokens = {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: number;
  refreshToken: string;
  refreshTokenExpiresAt: number;
};

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: BodyInit | null;
  authenticated?: boolean;
  retryOnUnauthorized?: boolean;
};

export function getStoredAccessToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getStoredRefreshToken() {
  return window.localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setStoredAuthTokens(tokens: AuthTokens) {
  window.localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
  window.localStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_KEY, String(tokens.accessTokenExpiresAt));
  window.localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  window.localStorage.setItem(REFRESH_TOKEN_EXPIRES_AT_KEY, String(tokens.refreshTokenExpiresAt));
}

export function clearStoredAuthTokens() {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_EXPIRES_AT_KEY);
}

export async function getText(path: string, init: RequestInit = {}): Promise<string> {
  const response = await request(path, { ...init, headers: { Accept: 'text/plain', ...(init.headers || {}) } });
  await assertOk(response);
  return response.text();
}

export async function getJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await request(path, { ...init, headers: { Accept: 'application/json', ...(init.headers || {}) } });
  await assertOk(response);
  return response.json() as Promise<T>;
}

export async function sendJson<T>(path: string, method: 'POST' | 'PUT', body?: unknown): Promise<T> {
  const response = await request(path, {
    method,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  await assertOk(response);
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export async function sendFormData<T>(path: string, method: 'POST' | 'PUT', body: FormData): Promise<T> {
  const response = await request(path, {
    method,
    body,
  });
  await assertOk(response);
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export async function getBlob(path: string, init: RequestInit = {}): Promise<Blob> {
  const response = await request(path, { ...init, headers: { Accept: '*/*', ...(init.headers || {}) } });
  await assertOk(response);
  return response.blob();
}

export async function deleteResource(path: string): Promise<void> {
  const response = await request(path, { method: 'DELETE' });
  await assertOk(response);
}

export async function exchangeAuthCode(code: string): Promise<AuthTokens> {
  const response = await request('/auth/exchange', {
    method: 'POST',
    authenticated: false,
    retryOnUnauthorized: false,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ code }),
  });
  await assertOk(response);
  return response.json() as Promise<AuthTokens>;
}

export async function refreshAuthTokens(): Promise<AuthTokens> {
  const refreshToken = getStoredRefreshToken();
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  const response = await request('/auth/refresh', {
    method: 'POST',
    authenticated: false,
    retryOnUnauthorized: false,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ refreshToken }),
  });
  await assertOk(response);
  const tokens = await response.json() as AuthTokens;
  setStoredAuthTokens(tokens);
  return tokens;
}

async function request(path: string, options: RequestOptions = {}): Promise<Response> {
  const {
    authenticated = true,
    retryOnUnauthorized = true,
    headers,
    body,
    ...init
  } = options;

  const requestHeaders = new Headers(headers || {});
  if (!requestHeaders.has('Accept')) {
    requestHeaders.set('Accept', 'application/json');
  }

  const accessToken = authenticated ? getStoredAccessToken() : null;
  if (accessToken) {
    requestHeaders.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(path, {
    ...init,
    headers: requestHeaders,
    body,
  });

  if (response.status === 401 && authenticated && retryOnUnauthorized) {
    const refreshToken = getStoredRefreshToken();
    if (refreshToken) {
      try {
        await refreshAuthTokens();
        return fetch(path, {
          ...init,
          headers: buildRetryHeaders(headers),
          body,
        });
      } catch {
        clearStoredAuthTokens();
      }
    }
  }

  return response;
}

function buildRetryHeaders(headers: HeadersInit | undefined) {
  const nextHeaders = new Headers(headers || {});
  if (!nextHeaders.has('Accept')) {
    nextHeaders.set('Accept', 'application/json');
  }
  const accessToken = getStoredAccessToken();
  if (accessToken) {
    nextHeaders.set('Authorization', `Bearer ${accessToken}`);
  }
  return nextHeaders;
}

async function assertOk(response: Response): Promise<void> {
  if (response.ok) {
    return;
  }
  const text = await response.text();
  if (text) {
    try {
      const parsed = JSON.parse(text) as { message?: string; code?: string };
      throw new Error(parsed.message || parsed.code || text);
    } catch (error) {
      if (error instanceof SyntaxError) {
        throw new Error(text);
      }
      throw error;
    }
  }
  throw new Error(`${response.status} ${response.statusText}`);
}
