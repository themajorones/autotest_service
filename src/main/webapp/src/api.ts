export async function getText(path: string): Promise<string> {
  const response = await fetch(path, { headers: { Accept: 'text/plain' } });
  await assertOk(response);
  return response.text();
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, { headers: { Accept: 'application/json' } });
  await assertOk(response);
  return response.json() as Promise<T>;
}

export async function sendJson<T>(path: string, method: 'POST' | 'PUT', body?: unknown): Promise<T> {
  const response = await fetch(path, {
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

export async function deleteResource(path: string): Promise<void> {
  const response = await fetch(path, { method: 'DELETE' });
  await assertOk(response);
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
