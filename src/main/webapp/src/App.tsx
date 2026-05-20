import { FormEvent, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { deleteResource, getJson, sendJson } from './api';

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
  type: string;
  dockerId: number;
  dockerName: string;
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
  login?: string;
  name?: string;
  avatarUrl?: string;
  attributes?: Record<string, unknown>;
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
    dockerId: '',
    name: 'redroid-1',
    image: 'redroid/redroid:15.0.0_64only-latest',
    accelerationMode: 'GUEST',
    width: '720',
    height: '1280',
    dpi: '320',
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
      refreshTabHealth(tab)
        .catch((error) => setStatus(message(error)));
    }, 10_000);
    return () => window.clearInterval(timer);
  }, [tab, authLoading]);

  async function bootstrap() {
    try {
      const info = await getJson<AuthInfo>('/auth/info');
      setAuthInfo(info);
    } catch (error) {
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
  }

  async function loadLogs() {
    setLogs(await getJson<TaskLog[]>('/api/task-logs'));
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
    const selectedDockerId = androidForm.dockerId || docker[0]?.id;
    const dockerId = Number(selectedDockerId);
    if (!selectedDockerId || Number.isNaN(dockerId)) {
      setStatus('Please select a Docker connection before saving the Android');
      return;
    }
    const width = parseOptionalInteger(androidForm.width);
    const height = parseOptionalInteger(androidForm.height);
    const dpi = parseOptionalInteger(androidForm.dpi);
    const payload = {
      ...androidForm,
      dockerId,
      width,
      height,
      dpi,
    };
    if (editingAndroidId == null) {
      await sendJson('/api/connections/android', 'POST', payload);
      setStatus('Android creation queued');
    } else {
      await sendJson(`/api/connections/android/${editingAndroidId}`, 'PUT', payload);
      setStatus('Android saved');
    }
    resetAndroidForm();
    await loadAndroid();
    await loadLogs();
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
      dockerId: String(row.dockerId),
      name: row.name,
      image: row.image,
      accelerationMode: row.accelerationMode,
      width: row.width == null ? '' : String(row.width),
      height: row.height == null ? '' : String(row.height),
      dpi: row.dpi == null ? '' : String(row.dpi),
    });
    setStatus('Editing Android');
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
      dockerId: '',
      name: 'redroid-1',
      image: 'redroid/redroid:15.0.0_64only-latest',
      accelerationMode: 'GUEST',
      width: '720',
      height: '1280',
      dpi: '320',
    });
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Autotest service</p>
          <h1>Connection manager</h1>
        </div>
        <div className="button-row">
          {authLoading ? (
            <span className="auth-chip">Checking session</span>
          ) : authInfo?.login ? (
            <span className="auth-chip">Signed in as {authInfo.login}</span>
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
              <select value="REDROID" disabled>
                <option value="REDROID">Redroid</option>
              </select>
            </label>
            <label>
              Docker
              <select value={androidForm.dockerId || docker[0]?.id || ''} onChange={(event) => setAndroidForm({ ...androidForm, dockerId: event.target.value })}>
                {docker.map((item) => (
                  <option key={item.id} value={item.id}>{item.name}</option>
                ))}
              </select>
            </label>
            <label>
              Name
              <input value={androidForm.name} onChange={(event) => setAndroidForm({ ...androidForm, name: event.target.value })} />
            </label>
            <label>
              Image
              <input value={androidForm.image} onChange={(event) => setAndroidForm({ ...androidForm, image: event.target.value })} />
            </label>
            <label>
              Acceleration
              <select value={androidForm.accelerationMode} onChange={(event) => setAndroidForm({ ...androidForm, accelerationMode: event.target.value })}>
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
            <div className="button-row form-actions">
              <button type="submit">{editingAndroidId == null ? 'Create Android' : 'Update Android'}</button>
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
            onStop={(row) => stopAndroid(row.id).catch((error) => setStatus(message(error)))}
            onDelete={(row) => deleteAndroid(row.id).catch((error) => setStatus(message(error)))}
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
                  <strong>{selectedAndroidDetail.android.dockerName}</strong>
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
                  <strong>{selectedAndroidDetail.android.accelerationMode}</strong>
                </div>
                <div>
                  <span>Resolution</span>
                  <strong>{formatAndroidResolution(selectedAndroidDetail.android)}</strong>
                </div>
                <div>
                  <span>Container</span>
                  <strong>{selectedAndroidDetail.android.containerName || selectedAndroidDetail.android.containerId || '-'}</strong>
                </div>
              </div>
              {selectedAndroidDetail.dockerInspectJson && <pre className="detail">{selectedAndroidDetail.dockerInspectJson}</pre>}
            </div>
          )}
        </section>
      )}

      {tab === 'logs' && (
        <section className="panel">
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
}: {
  rows: T[];
  columns: string[];
  columnLabels?: Record<string, string>;
  renderCell?: (row: T, column: string) => ReactNode;
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
            {(onDelete || onDetail || onEdit || onStop) && <th>actions</th>}
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
              {(onDelete || onDetail || onEdit || onStop) && (
                <td>
                  <div className="table-actions">
                    {onDetail && <button type="button" onClick={() => onDetail(row)}>Detail</button>}
                    {onEdit && <button type="button" onClick={() => onEdit(row)}>Edit</button>}
                    {onStop && <button type="button" onClick={() => onStop(row)}>Stop</button>}
                    {onDelete && <button type="button" onClick={() => onDelete(row)}>Delete</button>}
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

function message(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}
