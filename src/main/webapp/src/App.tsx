import { FormEvent, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { clearStoredAuthTokens, deleteResource, exchangeAuthCode, getJson, sendJson, setStoredAuthTokens } from './api';

type Tab = 'ollama' | 'docker' | 'android' | 'logs';
type HealthTab = Exclude<Tab, 'logs'>;

type Ollama = {
  id: number;
  name: string;
  baseUrl: string;
  enabled: boolean;
  status?: string;
  model: string;
};

type Docker = {
  id: number;
  name: string;
  baseUrl: string;
  enabled: boolean;
  status?: string;
  apiVersion?: string;
  os?: string;
  arch?: string;
  graphicsRuntimesJson?: string;
  gpuDevicesJson?: string;
};

type Android = {
  id: number;
  type: 'REDROID' | 'DIRECT' | string;
  dockerId?: number | null;
  dockerName?: string | null;
  name: string;
  image: string;
  containerId?: string;
  containerName?: string;
  adbHost?: string;
  adbPort?: number;
  accelerationMode: string;
  width?: number;
  height?: number;
  dpi?: number;
  status?: string;
};

type AndroidDetail = {
  android: Android;
  dockerInspectJson?: string | null;
};

type AuthInfo = {
  userId?: number;
  githubId?: number;
  login?: string;
  displayName?: string;
  organizations?: string[];
};

type TaskLog = {
  id: number;
  type: string;
  status: string;
  content: string;
  result?: string;
  startedAt?: number;
  endedAt?: number;
};

type OllamaModel = {
  name: string;
  model: string;
  size: number;
};

type HealthResponse = {
  type: HealthTab;
  id: number;
  status: string;
};

type HealthCheckRequest = {
  ids: number[];
};

type AndroidCreateResponse = {
  androidId: number;
  taskLogId?: number;
  status?: string;
};

export function App() {
  const [tab, setTab] = useState<Tab>('ollama');
  const [authInfo, setAuthInfo] = useState<AuthInfo | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [ollama, setOllama] = useState<Ollama[]>([]);
  const [docker, setDocker] = useState<Docker[]>([]);
  const [android, setAndroid] = useState<Android[]>([]);
  const [logs, setLogs] = useState<TaskLog[]>([]);
  const [models, setModels] = useState<OllamaModel[]>([]);
  const [lastHealthChecks, setLastHealthChecks] = useState<Record<HealthTab, Record<number, number>>>({
    ollama: {},
    docker: {},
    android: {},
  });
  const [healthStatuses, setHealthStatuses] = useState<Record<HealthTab, Record<number, string>>>({
    ollama: {},
    docker: {},
    android: {},
  });
  const [selectedAndroidDetail, setSelectedAndroidDetail] = useState<AndroidDetail | null>(null);
  const [status, setStatus] = useState('Idle');
  const ollamaRef = useRef<Ollama[]>([]);
  const dockerRef = useRef<Docker[]>([]);
  const androidRef = useRef<Android[]>([]);

  const [editingOllamaId, setEditingOllamaId] = useState<number | null>(null);
  const [editingDockerId, setEditingDockerId] = useState<number | null>(null);
  const [editingAndroidId, setEditingAndroidId] = useState<number | null>(null);

  const [ollamaForm, setOllamaForm] = useState({
    name: 'bigscreen',
    baseUrl: 'http://bigscreen:11434',
    model: '',
    enabled: true,
  });
  const [dockerForm, setDockerForm] = useState({
    name: 'bigscreen',
    baseUrl: 'http://bigscreen:2375',
    enabled: true,
  });
  const [androidForm, setAndroidForm] = useState({
    type: 'REDROID',
    dockerId: '',
    name: '',
    image: '',
    accelerationMode: '',
    width: '',
    height: '',
    dpi: '',
    adbHost: '',
    adbPort: '',
    pairPort: '',
    pairCode: '',
  });

  useEffect(() => {
    void bootstrap();
  }, []);

  useEffect(() => {
    if (authLoading) {
      return;
    }
    loadTab(tab)
      .then(() => refreshTabHealth(tab))
      .catch((error) => setStatus(message(error)));
  }, [tab, authLoading]);

  useEffect(() => {
    ollamaRef.current = ollama;
  }, [ollama]);

  useEffect(() => {
    dockerRef.current = docker;
  }, [docker]);

  useEffect(() => {
    androidRef.current = android;
  }, [android]);

  useEffect(() => {
    if (authLoading) {
      return;
    }
    const timer = window.setInterval(() => {
      if (tab === 'logs') {
        loadLogs().catch((error) => setStatus(message(error)));
        return;
      }
      refreshTabHealth(tab)
        .catch((error) => setStatus(message(error)));
    }, 10_000);
    return () => window.clearInterval(timer);
  }, [tab, authLoading]);

  async function bootstrap() {
    try {
      const params = new URLSearchParams(window.location.search);
      const authCode = params.get('authCode');
      if (authCode) {
        const tokens = await exchangeAuthCode(authCode);
        setStoredAuthTokens(tokens);
        window.history.replaceState({}, document.title, `${window.location.pathname}${window.location.hash}`);
      }
      const info = await getJson<AuthInfo>('/auth/info');
      setAuthInfo(info);
    } catch (error) {
      clearStoredAuthTokens();
      window.location.href = '/oauth2/authorization/github';
    } finally {
      setAuthLoading(false);
    }
  }

  async function loadTab(nextTab: Tab) {
    if (nextTab === 'ollama') {
      await loadOllama();
    } else if (nextTab === 'docker') {
      await loadDocker();
    } else if (nextTab === 'android') {
      await Promise.all([loadDocker(), loadAndroid()]);
    } else {
      await loadLogs();
    }
  }

  async function refreshTabHealth(nextTab: Tab) {
    if (nextTab === 'logs') {
      return;
    }
    const rows = rowsForHealthTab(nextTab);
    if (rows.length === 0) {
      return;
    }
    const payload: HealthCheckRequest = { ids: rows.map((row) => row.id) };
    const checks = await sendJson<HealthResponse[]>(`/api/connections/${nextTab}/health`, 'POST', payload);
    markHealthChecked(nextTab, checks);
  }

  async function loadOllama() {
    const rows = await getJson<Ollama[]>('/api/connections/ollama');
    ollamaRef.current = rows;
    setOllama(rows);
  }

  async function loadDocker() {
    const rows = await getJson<Docker[]>('/api/connections/docker');
    dockerRef.current = rows;
    setDocker(rows);
  }

  async function loadAndroid() {
    const rows = await getJson<Android[]>('/api/connections/android');
    androidRef.current = rows;
    setAndroid(rows);
    return rows;
  }

  async function loadLogs() {
    setLogs(await getJson<TaskLog[]>('/api/task-logs'));
  }

  async function retryTaskLog(id: number) {
    const taskLog = await sendJson<TaskLog>(`/api/task-logs/${id}/retry`, 'POST');
    await loadLogs();
    setStatus(`Retry queued for task log #${taskLog.id}`);
  }

  async function clearTaskLogs() {
    if (!window.confirm('Clear all task logs from the database?')) {
      return;
    }
    await deleteResource('/api/task-logs');
    await loadLogs();
    setStatus('Task logs cleared');
  }

  function markHealthChecked(nextTab: HealthTab, checks: HealthResponse[]) {
    const checkedAt = Date.now();
    const ids = checks.map((check) => check.id);
    setLastHealthChecks((current) => ({
      ...current,
      [nextTab]: ids.reduce<Record<number, number>>((checks, id) => {
        checks[id] = checkedAt;
        return checks;
      }, { ...current[nextTab] }),
    }));
    setHealthStatuses((current) => ({
      ...current,
      [nextTab]: checks.reduce<Record<number, string>>((statuses, check) => {
        statuses[check.id] = check.status;
        return statuses;
      }, { ...current[nextTab] }),
    }));
  }

  function rowsForHealthTab(nextTab: HealthTab) {
    return nextTab === 'ollama' ? ollamaRef.current : nextTab === 'docker' ? dockerRef.current : androidRef.current;
  }

  function withLastHealthCheckedAt<T extends { id: number }>(rows: T[], nextTab: HealthTab) {
    const checks = lastHealthChecks[nextTab];
    const statuses = healthStatuses[nextTab];
    return rows.map((row) => ({
      ...row,
      status: statuses[row.id],
      lastHealthCheckedAt: checks[row.id],
    }));
  }

  async function fetchOllamaModels() {
    setStatus('Fetching Ollama models');
    const nextModels = await sendJson<OllamaModel[]>('/api/connections/ollama/models', 'POST', {
      baseUrl: ollamaForm.baseUrl,
    });
    setModels(nextModels);
    setOllamaForm((current) => ({
      ...current,
      model: nextModels.some((model) => model.name === current.model) ? current.model : nextModels[0]?.name || '',
    }));
    setStatus(`Loaded ${nextModels.length} models`);
  }

  async function submitOllama(event: FormEvent) {
    event.preventDefault();
    const path = editingOllamaId == null ? '/api/connections/ollama' : `/api/connections/ollama/${editingOllamaId}`;
    const method = editingOllamaId == null ? 'POST' : 'PUT';
    await sendJson<Ollama>(path, method, ollamaForm);
    resetOllamaForm();
    await loadOllama();
    setStatus('Ollama connection saved');
  }

  async function submitDocker(event: FormEvent) {
    event.preventDefault();
    const path = editingDockerId == null ? '/api/connections/docker' : `/api/connections/docker/${editingDockerId}`;
    const method = editingDockerId == null ? 'POST' : 'PUT';
    await sendJson<Docker>(path, method, dockerForm);
    resetDockerForm();
    await loadDocker();
    setStatus('Docker connection saved');
  }

  async function submitAndroid(event: FormEvent) {
    event.preventDefault();
    if (androidForm.type === 'DIRECT') {
      await submitDirectAndroid();
      return;
    }

    const selectedDockerId = androidForm.dockerId || docker[0]?.id;
    const dockerId = Number(selectedDockerId);
    if (!selectedDockerId || Number.isNaN(dockerId)) {
      setStatus('Please select a Docker connection before saving the Android');
      return;
    }
    if (!androidForm.image.trim() || !androidForm.accelerationMode.trim()) {
      setStatus('Image and acceleration are required for Redroid');
      return;
    }
    const width = parseOptionalInteger(androidForm.width);
    const height = parseOptionalInteger(androidForm.height);
    const dpi = parseOptionalInteger(androidForm.dpi);
    const payload = {
      ...androidForm,
      dockerId,
      type: 'REDROID',
      width,
      height,
      dpi,
    };
    if (editingAndroidId == null) {
      const created = await sendJson<AndroidCreateResponse>('/api/connections/android', 'POST', payload);
      setStatus('Android creation queued');
      resetAndroidForm();
      await loadAndroid();
      await loadLogs();
      void pollAndroidAddress(created.androidId);
    } else {
      await sendJson(`/api/connections/android/${editingAndroidId}`, 'PUT', payload);
      resetAndroidForm();
      await loadAndroid();
      await loadLogs();
      setStatus('Android saved');
    }
  }

  async function submitDirectAndroid() {
    const pairCode = androidForm.pairCode.trim();
    const pairPort = parseOptionalInteger(androidForm.pairPort);
    if ((pairCode && pairPort == null) || (!pairCode && pairPort != null)) {
      setStatus('Pair port and pair code must be provided together');
      return;
    }

    const adbPort = parseOptionalInteger(androidForm.adbPort);
    if (!androidForm.adbHost.trim() || adbPort == null) {
      setStatus('ADB host and connect port are required');
      return;
    }
    const payload = {
      type: 'DIRECT',
      name: androidForm.name || androidForm.adbHost.trim(),
      adbHost: androidForm.adbHost.trim(),
      adbPort,
      pairPort,
      pairCode: pairCode || undefined,
    };
    const path = editingAndroidId == null ? '/api/connections/android' : `/api/connections/android/${editingAndroidId}`;
    const method = editingAndroidId == null ? 'POST' : 'PUT';
    const saved = await sendJson<AndroidCreateResponse | Android>(path, method, payload);
    resetAndroidForm();
    await loadAndroid();
    setStatus(`Direct Android connected${'androidId' in saved ? ` (#${saved.androidId})` : ''}`);
  }

  async function pollAndroidAddress(androidId: number) {
    for (let attempt = 0; attempt < 20; attempt += 1) {
      await delay(3_000);
      const rows = await loadAndroid();
      const row = rows.find((item) => item.id === androidId);
      if (!row) {
        return;
      }
      if (row.adbHost && row.adbPort != null) {
        setStatus(`Android ready at ${row.adbHost}:${row.adbPort}`);
        return;
      }
    }
  }

  function editOllama(row: Ollama) {
    setEditingOllamaId(row.id);
    setOllamaForm({
      name: row.name,
      baseUrl: row.baseUrl,
      model: row.model,
      enabled: row.enabled,
    });
    setModels([{ name: row.model, model: row.model, size: 0 }]);
    setStatus('Editing Ollama connection');
  }

  function editDocker(row: Docker) {
    setEditingDockerId(row.id);
    setDockerForm({
      name: row.name,
      baseUrl: row.baseUrl,
      enabled: row.enabled,
    });
    setStatus('Editing Docker connection');
  }

  function editAndroid(row: Android) {
    setEditingAndroidId(row.id);
    setAndroidForm({
      type: row.type === 'DIRECT' ? 'DIRECT' : 'REDROID',
      dockerId: row.dockerId == null ? '' : String(row.dockerId),
      name: row.name,
      image: row.image,
      accelerationMode: row.accelerationMode || '',
      width: row.width == null ? '' : String(row.width),
      height: row.height == null ? '' : String(row.height),
      dpi: row.dpi == null ? '' : String(row.dpi),
      adbHost: row.adbHost || '',
      adbPort: row.adbPort == null ? '' : String(row.adbPort),
      pairPort: '',
      pairCode: '',
    });
    setStatus('Editing Android');
  }

  async function startAndroid(id: number) {
    const started = await sendJson<Android>(`/api/connections/android/${id}/start`, 'POST');
    await loadAndroid();
    setHealthStatuses((current) => ({
      ...current,
      android: {
        ...current.android,
        [id]: started.status || 'RUNNING',
      },
    }));
    setStatus('Android started');
  }

  async function stopAndroid(id: number) {
    const stopped = await sendJson<Android>(`/api/connections/android/${id}/stop`, 'POST');
    await loadAndroid();
    setHealthStatuses((current) => ({
      ...current,
      android: {
        ...current.android,
        [id]: stopped.status || 'STOPPED',
      },
    }));
    setStatus('Android stopped');
  }

  async function deleteAndroid(id: number) {
    await deleteResource(`/api/connections/android/${id}`);
    await loadAndroid();
    setStatus('Android deleted');
  }

  async function loadAndroidDetail(id: number) {
    const detail = await getJson<AndroidDetail>(`/api/connections/android/${id}`);
    setSelectedAndroidDetail(detail);
  }

  async function copyText(value: string) {
    await navigator.clipboard.writeText(value);
    setStatus(`Copied ${value}`);
  }

  function resetOllamaForm() {
    setEditingOllamaId(null);
    setModels([]);
    setOllamaForm({ name: 'bigscreen', baseUrl: 'http://bigscreen:11434', model: '', enabled: true });
  }

  function resetDockerForm() {
    setEditingDockerId(null);
    setDockerForm({ name: 'bigscreen', baseUrl: 'http://bigscreen:2375', enabled: true });
  }

  function resetAndroidForm() {
    setEditingAndroidId(null);
    setAndroidForm({
      type: 'REDROID',
      dockerId: '',
      name: '',
      image: '',
      accelerationMode: '',
      width: '',
      height: '',
      dpi: '',
      adbHost: '',
      adbPort: '',
      pairPort: '',
      pairCode: '',
    });
  }

  const isDirectAndroidForm = androidForm.type === 'DIRECT';
  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Auto test service</p>
          <h1>Connection manager</h1>
        </div>
        <div className="button-row">
          {authLoading ? (
            <span className="auth-chip">Checking login</span>
          ) : authInfo?.login ? (
            <span className="auth-chip">Signed in as {authInfo.displayName || authInfo.login}</span>
          ) : (
            <a className="button-link" href="/oauth2/authorization/github">Sign in</a>
          )}
        </div>
      </header>

      <nav className="tabs" aria-label="Connection manager sections">
        {(['ollama', 'docker', 'android', 'logs'] as Tab[]).map((item) => (
          <button
            key={item}
            className={tab === item ? 'active' : ''}
            type="button"
            onClick={() => setTab(item)}
          >
            {label(item)}
          </button>
        ))}
      </nav>

      {tab === 'ollama' && (
        <section className="panel">
          <form className="form-grid" onSubmit={(event) => submitOllama(event).catch((error) => setStatus(message(error)))}>
            <label>
              Name
              <input value={ollamaForm.name} onChange={(event) => setOllamaForm({ ...ollamaForm, name: event.target.value })} />
            </label>
            <label>
              Base URL
              <input value={ollamaForm.baseUrl} onChange={(event) => setOllamaForm({ ...ollamaForm, baseUrl: event.target.value })} />
            </label>
            <label>
              Model
              <select value={ollamaForm.model} onChange={(event) => setOllamaForm({ ...ollamaForm, model: event.target.value })}>
                <option value="">Select model</option>
                {models.map((model) => (
                  <option key={model.name} value={model.name}>{model.name}</option>
                ))}
              </select>
            </label>
            <label className="checkbox-label">
              <input
                checked={ollamaForm.enabled}
                type="checkbox"
                onChange={(event) => setOllamaForm({ ...ollamaForm, enabled: event.target.checked })}
              />
              Enabled
            </label>
            <div className="button-row">
              <button type="button" onClick={() => fetchOllamaModels().catch((error) => setStatus(message(error)))}>
                Refresh models
              </button>
              <button type="submit">{editingOllamaId == null ? 'Save Ollama' : 'Update Ollama'}</button>
              {editingOllamaId != null && <button type="button" onClick={resetOllamaForm}>Cancel</button>}
            </div>
          </form>
          <DataTable
            rows={withLastHealthCheckedAt(ollama, 'ollama')}
            columns={['name', 'baseUrl', 'model', 'status', 'enabled']}
            columnLabels={{
              baseUrl: 'Base URL',
              status: 'Status',
              enabled: 'Enabled',
            }}
            onEdit={editOllama}
            onDelete={(row) => deleteResource(`/api/connections/ollama/${row.id}`).then(loadOllama)}
          />
        </section>
      )}

      {tab === 'docker' && (
        <section className="panel">
          <form className="form-grid" onSubmit={(event) => submitDocker(event).catch((error) => setStatus(message(error)))}>
            <label>
              Name
              <input value={dockerForm.name} onChange={(event) => setDockerForm({ ...dockerForm, name: event.target.value })} />
            </label>
            <label>
              Base URL
              <input value={dockerForm.baseUrl} onChange={(event) => setDockerForm({ ...dockerForm, baseUrl: event.target.value })} />
            </label>
            <label className="checkbox-label">
              <input
                checked={dockerForm.enabled}
                type="checkbox"
                onChange={(event) => setDockerForm({ ...dockerForm, enabled: event.target.checked })}
              />
              Enabled
            </label>
            <div className="button-row">
              <button type="submit">{editingDockerId == null ? 'Save Docker' : 'Update Docker'}</button>
              {editingDockerId != null && <button type="button" onClick={resetDockerForm}>Cancel</button>}
            </div>
          </form>
          <DataTable
            rows={withLastHealthCheckedAt(docker, 'docker')}
            columns={['name', 'baseUrl', 'status', 'apiVersion', 'os', 'arch', 'graphicsRuntimesJson']}
            columnLabels={{
              baseUrl: 'Base URL',
              status: 'Status',
              apiVersion: 'API Version',
              graphicsRuntimesJson: 'Graphics Runtime',
            }}
            onEdit={editDocker}
            onDelete={(row) => deleteResource(`/api/connections/docker/${row.id}`).then(loadDocker)}
            renderCell={(row, column) => {
              if (column === 'graphicsRuntimesJson') {
                return formatJsonList((row as Docker).graphicsRuntimesJson);
              }
              return undefined;
            }}
          />
        </section>
      )}

      {tab === 'android' && (
        <section className="panel">
          <form className="form-grid" onSubmit={(event) => submitAndroid(event).catch((error) => setStatus(message(error)))}>
            <label>
              Type
              <select
                value={androidForm.type}
                onChange={(event) => {
                  const type = event.target.value;
                  setAndroidForm({
                    ...androidForm,
                    type,
                    name: '',
                    image: '',
                    accelerationMode: '',
                    width: '',
                    height: '',
                    dpi: '',
                    pairCode: '',
                  });
                }}
              >
                <option value="REDROID">Redroid</option>
                <option value="DIRECT">Direct ADB</option>
              </select>
            </label>
            <label>
              Name
              <input value={androidForm.name} onChange={(event) => setAndroidForm({ ...androidForm, name: event.target.value })} />
            </label>
            {!isDirectAndroidForm && (
              <>
                <label>
                  Docker
                  <select value={androidForm.dockerId || docker[0]?.id || ''} onChange={(event) => setAndroidForm({ ...androidForm, dockerId: event.target.value })}>
                    {docker.map((item) => (
                      <option key={item.id} value={item.id}>{item.name}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Image
                  <input value={androidForm.image} onChange={(event) => setAndroidForm({ ...androidForm, image: event.target.value })} />
                </label>
                <label>
                  Acceleration
                  <select value={androidForm.accelerationMode} onChange={(event) => setAndroidForm({ ...androidForm, accelerationMode: event.target.value })}>
                    <option value="">Select acceleration</option>
                    <option value="GUEST">GUEST</option>
                    <option value="HOST">HOST</option>
                    <option value="AUTO">AUTO</option>
                  </select>
                </label>
                <fieldset className="field-group span-all">
                  <legend>Redroid options</legend>
                  <div className="form-grid compact-grid">
                    <label>
                      Width
                      <input
                        inputMode="numeric"
                        value={androidForm.width}
                        onChange={(event) => setAndroidForm({ ...androidForm, width: event.target.value })}
                      />
                    </label>
                    <label>
                      Height
                      <input
                        inputMode="numeric"
                        value={androidForm.height}
                        onChange={(event) => setAndroidForm({ ...androidForm, height: event.target.value })}
                      />
                    </label>
                    <label>
                      DPI
                      <input
                        inputMode="numeric"
                        value={androidForm.dpi}
                        onChange={(event) => setAndroidForm({ ...androidForm, dpi: event.target.value })}
                      />
                    </label>
                  </div>
                </fieldset>
              </>
            )}
            {isDirectAndroidForm && (
              <>
                <label>
                  Host
                  <input
                    value={androidForm.adbHost}
                    onChange={(event) => setAndroidForm({ ...androidForm, adbHost: event.target.value })}
                  />
                </label>
                <label>
                  Connect Port
                  <input
                    inputMode="numeric"
                    value={androidForm.adbPort}
                    onChange={(event) => setAndroidForm({ ...androidForm, adbPort: event.target.value })}
                  />
                </label>
                <label>
                  Pair Port (Optional)
                  <input
                    inputMode="numeric"
                    value={androidForm.pairPort}
                    onChange={(event) => setAndroidForm({ ...androidForm, pairPort: event.target.value })}
                  />
                </label>
                <label>
                  Pair Code (Optional)
                  <input
                    value={androidForm.pairCode}
                    onChange={(event) => {
                      setAndroidForm({ ...androidForm, pairCode: event.target.value });
                    }}
                  />
                </label>
              </>
            )}
            <div className="button-row form-actions">
              <button type="submit">
                {editingAndroidId == null ? 'Create Android' : 'Update Android'}
              </button>
              {editingAndroidId != null && <button type="button" onClick={resetAndroidForm}>Cancel</button>}
            </div>
          </form>
          <DataTable
            rows={withLastHealthCheckedAt(android, 'android')}
            columns={['name', 'type', 'dockerName', 'image', 'status', 'adbHost', 'accelerationMode', 'width']}
            columnLabels={{
              dockerName: 'Docker',
              adbHost: 'Address',
              accelerationMode: 'Acceleration',
              width: 'Resolution',
            }}
            onDetail={(row) => loadAndroidDetail(row.id).catch((error) => setStatus(message(error)))}
            onEdit={editAndroid}
            onDelete={(row) => deleteAndroid(row.id).catch((error) => setStatus(message(error)))}
            renderActions={(row) => {
              const typedRow = row as Android;
              const status = healthStatuses.android[typedRow.id];
              const lifecycleAction = redroidLifecycleAction(typedRow, status);
              return (
                <>
                  <button type="button" onClick={() => loadAndroidDetail(typedRow.id).catch((error) => setStatus(message(error)))}>
                    Detail
                  </button>
                  <button type="button" onClick={() => editAndroid(typedRow)}>Edit</button>
                  {lifecycleAction === 'START' && (
                    <button type="button" onClick={() => startAndroid(typedRow.id).catch((error) => setStatus(message(error)))}>
                      Start
                    </button>
                  )}
                  {lifecycleAction === 'STOP' && (
                    <button type="button" onClick={() => stopAndroid(typedRow.id).catch((error) => setStatus(message(error)))}>
                      Stop
                    </button>
                  )}
                  <button type="button" onClick={() => deleteAndroid(typedRow.id).catch((error) => setStatus(message(error)))}>
                    Delete
                  </button>
                </>
              );
            }}
            renderCell={(row, column) => {
              if (column === 'adbHost') {
                const address = formatAndroidAddress(row as Android);
                return address ? (
                  <button type="button" className="text-button" onClick={() => copyText(address).catch((error) => setStatus(message(error)))}>
                    {address}
                  </button>
                ) : (
                  '-'
                );
              }
              if (column === 'width') {
                return formatAndroidResolution(row as Android);
              }
              return undefined;
            }}
          />
          {selectedAndroidDetail && (
            <div className="detail-shell">
              <div className="detail-grid">
                <div>
                  <span>Name</span>
                  <strong>{selectedAndroidDetail.android.name}</strong>
                </div>
                <div>
                  <span>Docker</span>
                  <strong>{selectedAndroidDetail.android.type === 'DIRECT' ? '-' : selectedAndroidDetail.android.dockerName}</strong>
                </div>
                <div>
                  <span>Image</span>
                  <strong>{selectedAndroidDetail.android.image}</strong>
                </div>
                <div>
                  <span>Status</span>
                  <strong>{healthStatuses.android[selectedAndroidDetail.android.id] || '-'}</strong>
                </div>
                <div>
                  <span>Type</span>
                  <strong>{selectedAndroidDetail.android.type}</strong>
                </div>
                <div>
                  <span>Address</span>
                  <strong>
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => {
                        const address = formatAndroidAddress(selectedAndroidDetail.android);
                        if (address) {
                          void copyText(address);
                        }
                      }}
                    >
                      {formatAndroidAddress(selectedAndroidDetail.android) || '-'}
                    </button>
                  </strong>
                </div>
                <div>
                  <span>Acceleration</span>
                  <strong>{selectedAndroidDetail.android.type === 'DIRECT' ? '-' : selectedAndroidDetail.android.accelerationMode}</strong>
                </div>
                <div>
                  <span>Resolution</span>
                  <strong>{formatAndroidResolution(selectedAndroidDetail.android)}</strong>
                </div>
                <div>
                  <span>Container</span>
                  <strong>{selectedAndroidDetail.android.type === 'DIRECT' ? '-' : selectedAndroidDetail.android.containerName || selectedAndroidDetail.android.containerId || '-'}</strong>
                </div>
              </div>
              {selectedAndroidDetail.dockerInspectJson && <pre className="detail">{selectedAndroidDetail.dockerInspectJson}</pre>}
            </div>
          )}
        </section>
      )}

      {tab === 'logs' && (
        <section className="panel">
          <div className="button-row">
            <button type="button" onClick={() => loadLogs().catch((error) => setStatus(message(error)))}>
              Refresh logs
            </button>
            <button type="button" onClick={() => clearTaskLogs().catch((error) => setStatus(message(error)))}>
              Clear all logs
            </button>
          </div>
          <DataTable
            rows={logs}
            columns={['id', 'type', 'status', 'startedAt', 'endedAt', 'content', 'result']}
            columnLabels={{
              id: 'ID',
              type: 'Type',
              status: 'Status',
              startedAt: 'Started',
              endedAt: 'Ended',
              content: 'Message',
              result: 'Result',
            }}
            renderActions={(row) => {
              const taskLog = row as TaskLog;
              const retryable = taskLog.status !== 'PENDING' && taskLog.status !== 'RUNNING';
              return (
                <>
                  <button
                    type="button"
                    disabled={!retryable}
                    onClick={() => retryTaskLog(taskLog.id).catch((error) => setStatus(message(error)))}
                  >
                    Retry
                  </button>
                </>
              );
            }}
          />
        </section>
      )}

      <p className="request-status" aria-live="polite">{status}</p>
    </main>
  );
}

function DataTable<T extends { id: number }>({
  rows,
  columns,
  columnLabels,
  renderCell,
  onDelete,
  onDetail,
  onEdit,
  onStop,
  renderActions,
}: {
  rows: T[];
  columns: string[];
  columnLabels?: Record<string, string>;
  renderCell?: (row: T, column: string) => ReactNode;
  renderActions?: (row: T) => ReactNode;
  onDelete?: (row: T) => void;
  onDetail?: (row: T) => void;
  onEdit?: (row: T) => void;
  onStop?: (row: T) => void;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((column) => <th key={column}>{columnLabels?.[column] ?? column}</th>)}
            {(renderActions || onDelete || onDetail || onEdit || onStop) && <th>actions</th>}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length + 1}>No records loaded.</td>
            </tr>
          )}
          {rows.map((row) => (
            <tr key={row.id}>
              {columns.map((column) => {
                const rendered = renderCell?.(row, column);
                return <td key={column}>{rendered === undefined ? cellValue(row, column) : rendered}</td>;
              })}
              {(renderActions || onDelete || onDetail || onEdit || onStop) && (
                <td>
                  <div className="table-actions">
                    {renderActions ? renderActions(row) : (
                      <>
                        {onDetail && <button type="button" onClick={() => onDetail(row)}>Detail</button>}
                        {onEdit && <button type="button" onClick={() => onEdit(row)}>Edit</button>}
                        {onStop && <button type="button" onClick={() => onStop(row)}>Stop</button>}
                        {onDelete && <button type="button" onClick={() => onDelete(row)}>Delete</button>}
                      </>
                    )}
                  </div>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function cellValue(row: Record<string, unknown>, column: string) {
  const value = row[column];
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  if (typeof value === 'boolean') {
    return value ? 'yes' : 'no';
  }
  if (typeof value === 'number' && column.endsWith('At')) {
    return new Date(value).toLocaleString();
  }
  const text = String(value);
  return text.length > 140 ? `${text.slice(0, 140)}...` : text;
}

function formatAndroidAddress(android: Pick<Android, 'adbHost' | 'adbPort'>) {
  if (!android.adbHost || android.adbPort == null) {
    return '';
  }
  return `${android.adbHost}:${android.adbPort}`;
}

function formatAndroidResolution(android: Pick<Android, 'width' | 'height' | 'dpi'>) {
  if (android.width == null && android.height == null && android.dpi == null) {
    return '-';
  }
  const width = android.width ?? '?';
  const height = android.height ?? '?';
  const dpi = android.dpi ?? '?';
  return `${width}x${height}@${dpi}`;
}

function formatJsonList(value?: string) {
  if (!value) {
    return '-';
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed.join(', ');
    }
  } catch {
    return value;
  }
  return '-';
}

function redroidLifecycleAction(android: Android, status?: string) {
  if (android.type !== 'REDROID') {
    return null;
  }
  if (status === 'STOPPED') {
    return 'START';
  }
  if (status === 'RUNNING' || status === 'CANT_REACH' || status === 'UNHEALTHY') {
    return 'STOP';
  }
  return null;
}

function label(tab: Tab) {
  return tab === 'android' ? 'Android' : tab[0].toUpperCase() + tab.slice(1);
}

function parseOptionalInteger(value: string) {
  if (!value.trim()) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isNaN(parsed) ? undefined : parsed;
}

function delay(timeoutMs: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, timeoutMs);
  });
}

function message(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}
