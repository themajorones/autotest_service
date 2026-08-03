import { FormEvent, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { clearStoredAuthTokens, deleteResource, exchangeAuthCode, getBlob, getJson, getStoredAccessToken, sendFormData, sendJson, setStoredAuthTokens } from './api';

type Tab = 'ollama' | 'docker' | 'android' | 'artifacts' | 'tests' | 'logs';
type HealthTab = Exclude<Tab, 'logs' | 'artifacts' | 'tests'>;
type ArtifactFilter = 'all' | 'upload' | 'artifact';
type ArtifactSource = 'UPLOAD' | 'ARTIFACT';

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

type RedroidImageOption = {
  label: string;
  value: string;
};

type AndroidResolutionPreset = {
  id: string;
  label: string;
  width: number;
  height: number;
};

type AndroidFormState = {
  type: 'REDROID' | 'DIRECT';
  dockerId: string;
  name: string;
  image: string;
  accelerationMode: string;
  width: string;
  height: string;
  dpi: string;
  resolutionPreset: string;
  adbHost: string;
  adbPort: string;
  pairPort: string;
  pairCode: string;
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

type AndroidTestDetail = {
  id: number;
  status: string;
  content: string;
  result?: string | null;
  startedAt?: number | null;
  endedAt?: number | null;
  request?: Record<string, unknown> | null;
  summary?: Record<string, unknown> | null;
  stepCount: number;
};

type AndroidTestStepHistory = {
  id: number;
  taskLogId: number;
  stepNumber: number;
  startedAt?: number | null;
  endedAt?: number | null;
  foreground?: string | null;
  uiHash?: string | null;
  uiContext?: string | null;
  visionProvider?: string | null;
  visionText?: string | null;
  action?: string | null;
  state?: string | null;
  targetElementId?: number | null;
  targetX?: number | null;
  targetY?: number | null;
  swipeX1?: number | null;
  swipeY1?: number | null;
  swipeX2?: number | null;
  swipeY2?: number | null;
  swipeDurationMs?: number | null;
  inputText?: string | null;
  reasoning?: string | null;
  decisionJson?: string | null;
  actionResult?: string | null;
  error?: string | null;
  imageStorageKey?: string | null;
};

type TaskProgressEvent = {
  taskLogId?: number | null;
  taskType?: string | null;
  eventType: string;
  emittedAt?: number | null;
  taskLog?: TaskLog | null;
  step?: AndroidTestStepHistory | null;
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
  taskLogId?: number;
  status?: string;
  message?: string;
  androidId?: number;
};

type Artifact = {
  id: number;
  source: ArtifactSource;
  name: string;
  size?: number | null;
  githubArtifactId?: number | null;
  repoId?: number | null;
  repoFullName?: string | null;
  workflowRunId?: number | null;
  workflowId?: number | null;
  headSha?: string | null;
  storageKey?: string | null;
  originalFileName?: string | null;
  contentType?: string | null;
};

type ArtifactUploadFormState = {
  name: string;
  file: File | null;
};

type AndroidDeviceSelectionState = {
  query: string;
  open: boolean;
  androidId: number | null;
  searchActive: boolean;
};

type AndroidTestFormState = {
  androidId: string;
  artifactId: string;
  ollamaId: string;
  objective: string;
  maxSteps: string;
};

const REDROID_IMAGE_FALLBACK = 'redroid/redroid:16.0.0-latest';
const REDROID_RESOLUTION_PRESETS: AndroidResolutionPreset[] = [
  { id: '1080x2400', label: '1080 x 2400', width: 1080, height: 2400 },
  { id: '1080x2340', label: '1080 x 2340', width: 1080, height: 2340 },
  { id: '1920x1200', label: '1920 x 1200', width: 1920, height: 1200 },
  { id: '2560x1600', label: '2560 x 1600', width: 2560, height: 1600 },
  { id: '720x1280', label: '720 x 1280', width: 720, height: 1280 },
  { id: '1080x1920', label: '1080 x 1920', width: 1080, height: 1920 },
  { id: '1000x1200', label: '1000 x 1200', width: 1000, height: 1200 },
];

function defaultRedroidImage(options: RedroidImageOption[]) {
  return options[0]?.value || REDROID_IMAGE_FALLBACK;
}

export function App() {
  const [tab, setTab] = useState<Tab>(() => tabFromLocationPath());
  const [authInfo, setAuthInfo] = useState<AuthInfo | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [ollama, setOllama] = useState<Ollama[]>([]);
  const [docker, setDocker] = useState<Docker[]>([]);
  const [android, setAndroid] = useState<Android[]>([]);
  const [artifacts, setArtifacts] = useState<Artifact[]>([]);
  const [logs, setLogs] = useState<TaskLog[]>([]);
  const [models, setModels] = useState<OllamaModel[]>([]);
  const [redroidImages, setRedroidImages] = useState<RedroidImageOption[]>([]);
  const [artifactFilter, setArtifactFilter] = useState<ArtifactFilter>('all');
  const [artifactAndroidSelection, setArtifactAndroidSelection] = useState<AndroidDeviceSelectionState>({
    query: '',
    open: false,
    androidId: null,
    searchActive: false,
  });
  const [testAndroidSelection, setTestAndroidSelection] = useState<AndroidDeviceSelectionState>({
    query: '',
    open: false,
    androidId: null,
    searchActive: false,
  });
  const [artifactUploadForm, setArtifactUploadForm] = useState<ArtifactUploadFormState>({
    name: '',
    file: null,
  });
  const [androidTestForm, setAndroidTestForm] = useState<AndroidTestFormState>({
    androidId: '',
    artifactId: '',
    ollamaId: '',
    objective: '',
    maxSteps: '20',
  });
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
  const [selectedAndroidTestDetail, setSelectedAndroidTestDetail] = useState<AndroidTestDetail | null>(null);
  const [androidTestSteps, setAndroidTestSteps] = useState<AndroidTestStepHistory[]>([]);
  const [androidTestStepImageUrls, setAndroidTestStepImageUrls] = useState<Record<number, string>>({});
  const [status, setStatus] = useState('Idle');
  const ollamaRef = useRef<Ollama[]>([]);
  const dockerRef = useRef<Docker[]>([]);
  const androidRef = useRef<Android[]>([]);
  const logsRef = useRef<TaskLog[]>([]);
  const androidTestStepsRef = useRef<AndroidTestStepHistory[]>([]);
  const selectedAndroidTestDetailRef = useRef<AndroidTestDetail | null>(null);
  const artifactUploadFormRef = useRef<HTMLFormElement | null>(null);
  const androidTestStepImageUrlsRef = useRef<Record<number, string>>({});
  const websocketClientRef = useRef<Client | null>(null);
  const websocketTaskLogsSubscriptionRef = useRef<StompSubscription | null>(null);
  const websocketTestDetailSubscriptionRef = useRef<StompSubscription | null>(null);
  const websocketHasConnectedRef = useRef(false);
  const tabRef = useRef<Tab>(tab);

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
  const [androidForm, setAndroidForm] = useState<AndroidFormState>(() => createAndroidFormState([], 'REDROID'));

  useEffect(() => {
    void bootstrap();
  }, []);

  useEffect(() => {
    const nextPath = tabToPath(tab);
    const currentPath = `${window.location.pathname}${window.location.search}`;
    if (currentPath !== nextPath) {
      window.history.pushState({}, document.title, nextPath);
    }
  }, [tab]);

  useEffect(() => {
    const handlePopState = () => {
      setTab(tabFromLocationPath());
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    if (authLoading) {
      return;
    }
    loadTab(tab)
      .then(() => {
        if (tab !== 'logs' && tab !== 'artifacts' && tab !== 'tests') {
          return refreshTabHealth(tab as HealthTab);
        }
        if (tab === 'tests') {
          return refreshTabHealth('android');
        }
        return undefined;
      })
      .catch((error) => setStatus(message(error)));
  }, [tab, authLoading]);

  useEffect(() => {
    selectedAndroidTestDetailRef.current = selectedAndroidTestDetail;
  }, [selectedAndroidTestDetail]);

  useEffect(() => {
    tabRef.current = tab;
  }, [tab]);

  useEffect(() => {
    if (authLoading) {
      return;
    }
    const token = getStoredAccessToken();
    if (!token) {
      return;
    }

    const client = new Client({
      brokerURL: websocketBrokerUrl(),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
    });

    const syncSubscriptions = () => {
      websocketTaskLogsSubscriptionRef.current?.unsubscribe();
      websocketTaskLogsSubscriptionRef.current = client.subscribe('/topic/task-logs', handleTaskProgressMessage);

      websocketTestDetailSubscriptionRef.current?.unsubscribe();
      const selectedId = selectedAndroidTestDetailRef.current?.id;
      if (tabRef.current === 'tests' && selectedId != null) {
        websocketTestDetailSubscriptionRef.current = client.subscribe(`/topic/android-tests/${selectedId}`, handleTaskProgressMessage);
      } else {
        websocketTestDetailSubscriptionRef.current = null;
      }
    };

    client.onConnect = () => {
      syncSubscriptions();
      if (websocketHasConnectedRef.current) {
        void refreshVisibleDataAfterReconnect();
      } else {
        websocketHasConnectedRef.current = true;
      }
    };

    client.onWebSocketClose = () => {
      websocketTaskLogsSubscriptionRef.current = null;
      websocketTestDetailSubscriptionRef.current = null;
    };

    client.onStompError = (frame) => {
      setStatus(frame.body || 'WebSocket error');
    };

    websocketClientRef.current = client;
    websocketHasConnectedRef.current = false;
    client.activate();

    return () => {
      websocketTaskLogsSubscriptionRef.current?.unsubscribe();
      websocketTaskLogsSubscriptionRef.current = null;
      websocketTestDetailSubscriptionRef.current?.unsubscribe();
      websocketTestDetailSubscriptionRef.current = null;
      websocketClientRef.current?.deactivate();
      websocketClientRef.current = null;
    };
  }, [authLoading]);

  useEffect(() => {
    const client = websocketClientRef.current;
    if (!client || !client.connected) {
      return;
    }
    websocketTestDetailSubscriptionRef.current?.unsubscribe();
    const selectedId = selectedAndroidTestDetail?.id;
    if (tab === 'tests' && selectedId != null) {
      websocketTestDetailSubscriptionRef.current = client.subscribe(`/topic/android-tests/${selectedId}`, handleTaskProgressMessage);
      return;
    }
    websocketTestDetailSubscriptionRef.current = null;
  }, [selectedAndroidTestDetail?.id, tab]);

  useEffect(() => {
    if (authLoading || tab !== 'artifacts') {
      return;
    }
    loadArtifacts(artifactFilter).catch((error) => setStatus(message(error)));
  }, [artifactFilter, authLoading, tab]);

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
    logsRef.current = logs;
  }, [logs]);

  useEffect(() => {
    androidTestStepsRef.current = androidTestSteps;
  }, [androidTestSteps]);

  useEffect(() => {
    return () => {
      Object.values(androidTestStepImageUrlsRef.current).forEach((url) => window.URL.revokeObjectURL(url));
    };
  }, []);

  useEffect(() => {
    if ((tab !== 'artifacts' && tab !== 'tests') || android.length === 0) {
      return;
    }
    const syncSelection = (current: AndroidDeviceSelectionState) => {
      const selected = android.find((row) => row.id === current.androidId) || null;
      if (selected) {
        const nextQuery = current.searchActive ? current.query : artifactAndroidLabel(selected, healthStatuses.android);
        return {
          ...current,
          query: nextQuery,
        };
      }
      const next = pickDefaultArtifactAndroid(android, healthStatuses.android);
      if (!next) {
        return {
          ...current,
          androidId: null,
          query: '',
          searchActive: false,
        };
      }
      return {
        ...current,
        androidId: next.id,
        query: artifactAndroidLabel(next, healthStatuses.android),
        searchActive: false,
      };
    };
    if (tab === 'artifacts') {
      setArtifactAndroidSelection(syncSelection);
    } else {
      setTestAndroidSelection((current) => {
        const next = syncSelection(current);
        setAndroidTestForm((form) => ({ ...form, androidId: next.androidId == null ? '' : String(next.androidId) }));
        return next;
      });
    }
  }, [android, healthStatuses.android, tab]);

  useEffect(() => {
    if (authLoading) {
      return;
    }
    if (tab === 'artifacts') {
      const timer = window.setInterval(() => {
        refreshTabHealth('android')
          .catch((error) => setStatus(message(error)));
      }, 5_000);
      return () => window.clearInterval(timer);
    }
    return undefined;
  }, [tab, authLoading]);

  async function bootstrap() {
    try {
      const params = new URLSearchParams(window.location.search);
      const authCode = params.get('authCode');
      if (authCode) {
        const tokens = await exchangeAuthCode(authCode);
        setStoredAuthTokens(tokens);
        params.delete('authCode');
        const nextSearch = params.toString();
        const nextUrl = `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ''}`;
        window.history.replaceState({}, document.title, nextUrl);
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
    } else if (nextTab === 'artifacts') {
      await Promise.all([loadAndroid(), loadArtifacts()]);
      await refreshTabHealth('android');
    } else if (nextTab === 'tests') {
      await Promise.all([loadAndroid(), loadArtifacts('all'), loadOllama(), loadLogs()]);
      await refreshTabHealth('android');
    } else {
      await loadLogs();
    }
  }

  async function refreshTabHealth(nextTab: HealthTab) {
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
    const [rows, images] = await Promise.all([
      getJson<Android[]>('/api/connections/android'),
      getJson<RedroidImageOption[]>('/api/connections/redroid/images').catch(() => []),
    ]);
    setRedroidImages(images);
    androidRef.current = rows;
    setAndroid(rows);
    return rows;
  }

  async function loadLogs(init: RequestInit = {}) {
    const rows = await getJson<TaskLog[]>('/api/task-logs', init);
    logsRef.current = rows;
    setLogs(rows);
    return rows;
  }

  async function loadArtifacts(source: ArtifactFilter = artifactFilter) {
    const query = source === 'all' ? '' : `?source=${encodeURIComponent(source)}`;
    setArtifacts(await getJson<Artifact[]>(`/api/artifacts${query}`));
  }

  async function retryTaskLog(id: number) {
    const taskLog = await sendJson<TaskLog>(`/api/task-logs/${id}/retry`, 'POST');
    await loadLogs();
    setStatus(`Retry queued for task log #${taskLog.id}`);
  }

  async function submitArtifactUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!artifactUploadForm.file) {
      setStatus('Please choose an APK file');
      return;
    }
    const formData = new FormData();
    if (artifactUploadForm.name.trim()) {
      formData.set('name', artifactUploadForm.name.trim());
    }
    formData.set('file', artifactUploadForm.file);
    await sendFormData<Artifact>('/api/artifacts', 'POST', formData);
    setStatus('Artifact uploaded');
    setArtifactUploadForm({ name: '', file: null });
    artifactUploadFormRef.current?.reset();
    await loadArtifacts(artifactFilter);
  }

  async function downloadArtifact(row: Artifact) {
    const blob = await getBlob(`/api/artifacts/${row.id}/download`);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${artifactDownloadName(row)}.apk`;
    link.click();
    window.setTimeout(() => window.URL.revokeObjectURL(url), 5_000);
    setStatus(`Downloading ${row.name}`);
  }

  async function deleteArtifact(row: Artifact) {
    await deleteResource(`/api/artifacts/${row.id}`);
    setArtifacts((current) => current.filter((artifact) => artifact.id !== row.id));
    setStatus(`Deleted ${row.name}`);
  }

  async function installArtifact(row: Artifact) {
    const target = selectedArtifactAndroid(android, healthStatuses.android, artifactAndroidSelection.androidId);
    if (!target) {
      setStatus('Select an active Android device first');
      return;
    }
    if (!isArtifactAndroidActive(target, healthStatuses.android)) {
      setStatus('Select an active Android device first');
      return;
    }
    const taskLog = await sendJson<TaskLog>(`/api/artifacts/${row.id}/install`, 'POST', { androidId: target.id });
    setStatus(`Queued install task #${taskLog.id} for ${row.name}`);
  }

  async function submitAndroidTest(event: FormEvent) {
    event.preventDefault();
    const androidId = Number(testAndroidSelection.androidId || androidTestForm.androidId || pickDefaultArtifactAndroid(android, healthStatuses.android)?.id);
    const artifactId = Number(androidTestForm.artifactId || artifacts[0]?.id);
    const ollamaId = Number(androidTestForm.ollamaId || ollama[0]?.id);
    const maxSteps = parseOptionalInteger(androidTestForm.maxSteps) ?? 20;
    if (!androidId || Number.isNaN(androidId)) {
      setStatus('Select an Android device');
      return;
    }
    const selectedAndroid = android.find((row) => row.id === androidId);
    if (!selectedAndroid || !isArtifactAndroidActive(selectedAndroid, healthStatuses.android)) {
      setStatus('Select an active Android device');
      return;
    }
    if (!artifactId || Number.isNaN(artifactId)) {
      setStatus('Select an APK artifact');
      return;
    }
    if (!ollamaId || Number.isNaN(ollamaId)) {
      setStatus('Select an Ollama connection');
      return;
    }
    if (!androidTestForm.objective.trim()) {
      setStatus('Objective is required');
      return;
    }
    const taskLog = await sendJson<TaskLog>('/api/tests/android', 'POST', {
      androidId,
      artifactId,
      ollamaId,
      objective: androidTestForm.objective.trim(),
      maxSteps,
    });
    setStatus(`Queued Android test task #${taskLog.id}`);
    await loadLogs();
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

  function syncResolutionPreset(nextWidth: string, nextHeight: string) {
    return findResolutionPreset(nextWidth, nextHeight)?.id || 'custom';
  }

  function applyResolutionPreset(presetId: string) {
    if (presetId === 'custom') {
      setAndroidForm((current) => ({ ...current, resolutionPreset: 'custom' }));
      return;
    }
    const preset = REDROID_RESOLUTION_PRESETS.find((item) => item.id === presetId);
    if (!preset) {
      return;
    }
    setAndroidForm((current) => ({
      ...current,
      resolutionPreset: preset.id,
      width: String(preset.width),
      height: String(preset.height),
    }));
  }

  async function fetchOllamaModels() {
    setStatus('Fetching Ollama models');
    const nextModels = await fetchOllamaModelsForBaseUrl(ollamaForm.baseUrl);
    setModels(nextModels);
    setOllamaForm((current) => ({
      ...current,
      model: nextModels.some((model) => model.name === current.model) ? current.model : nextModels[0]?.name || '',
    }));
    setStatus(`Loaded ${nextModels.length} models`);
  }

  async function fetchOllamaModelsForBaseUrl(baseUrl: string) {
    return sendJson<OllamaModel[]>('/api/connections/ollama/models', 'POST', { baseUrl });
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
    if (!androidForm.image.trim()) {
      setStatus('Image is required for Redroid');
      return;
    }
    const width = parseOptionalInteger(androidForm.width);
    const height = parseOptionalInteger(androidForm.height);
    const dpi = parseOptionalInteger(androidForm.dpi);
    const name = androidForm.name.trim() || generateAndroidName();
    const payload = {
      name,
      dockerId,
      type: 'REDROID' as const,
      image: androidForm.image,
      accelerationMode: androidForm.accelerationMode || 'AUTO',
      width,
      height,
      dpi,
    };
    if (editingAndroidId == null) {
      const created = await sendJson<AndroidCreateResponse>('/api/connections/android', 'POST', payload);
      setStatus(`Android creation queued${created.taskLogId ? ` (#${created.taskLogId})` : ''}`);
      resetAndroidForm();
      await loadAndroid();
      await loadLogs();
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
      name: androidForm.name.trim() || generateAndroidName(),
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

  async function editOllama(row: Ollama) {
    setEditingOllamaId(row.id);
    setOllamaForm({
      name: row.name,
      baseUrl: row.baseUrl,
      model: row.model,
      enabled: row.enabled,
    });
    setModels([{ name: row.model, model: row.model, size: 0 }]);
    setStatus('Editing Ollama connection; refreshing models');
    const nextModels = await fetchOllamaModelsForBaseUrl(row.baseUrl);
    setModels(nextModels);
    setOllamaForm((current) => ({
      ...current,
      model: nextModels.some((model) => model.name === row.model) ? row.model : nextModels[0]?.name || row.model,
    }));
    setStatus(`Editing Ollama connection; loaded ${nextModels.length} models`);
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
    const nextImage = redroidImages.some((option) => option.value === row.image)
      ? row.image
      : defaultRedroidImage(redroidImages);
    const nextResolutionPreset = findResolutionPreset(
      row.width == null ? '' : String(row.width),
      row.height == null ? '' : String(row.height),
    )?.id || 'custom';
    setEditingAndroidId(row.id);
    setAndroidForm({
      type: row.type === 'DIRECT' ? 'DIRECT' : 'REDROID',
      dockerId: row.dockerId == null ? '' : String(row.dockerId),
      name: row.name,
      image: row.type === 'DIRECT' ? '' : nextImage,
      accelerationMode: row.accelerationMode || 'AUTO',
      width: row.width == null ? '' : String(row.width),
      height: row.height == null ? '' : String(row.height),
      dpi: row.dpi == null ? '' : String(row.dpi),
      resolutionPreset: nextResolutionPreset,
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

  async function loadAndroidTestDetail(id: number, init: RequestInit = {}) {
    const [detail, steps] = await Promise.all([
      getJson<AndroidTestDetail>(`/api/tests/android/${id}`, init),
      getJson<AndroidTestStepHistory[]>(`/api/tests/android/${id}/steps`, init),
    ]);
    setSelectedAndroidTestDetail(detail);
    setAndroidTestSteps(steps);
    await loadAndroidTestStepImages(id, steps, init);
    setStatus(`Loaded Android test #${id}`);
    return detail;
  }

  async function loadAndroidTestStepImages(taskLogId: number, steps: AndroidTestStepHistory[], init: RequestInit = {}) {
    Object.values(androidTestStepImageUrlsRef.current).forEach((url) => window.URL.revokeObjectURL(url));
    const urls: Record<number, string> = {};
    await Promise.all(steps.map(async (step) => {
      if (!step.imageStorageKey) {
        return;
      }
      const blob = await getBlob(`/api/tests/android/${taskLogId}/steps/${step.stepNumber}/image`, init);
      urls[step.stepNumber] = window.URL.createObjectURL(blob);
    }));
    androidTestStepImageUrlsRef.current = urls;
    setAndroidTestStepImageUrls(urls);
  }

  async function refreshVisibleDataAfterReconnect() {
    if (tab === 'logs' || tab === 'tests') {
      await loadLogs();
    }
    if (tab === 'tests' && selectedAndroidTestDetailRef.current) {
      await loadAndroidTestDetail(selectedAndroidTestDetailRef.current.id);
    }
  }

  function handleTaskProgressMessage(frame: IMessage) {
    try {
      const event = JSON.parse(frame.body) as TaskProgressEvent;
      applyTaskProgressEvent(event);
    } catch (error) {
      setStatus(message(error));
    }
  }

  function applyTaskProgressEvent(event: TaskProgressEvent) {
    if (!event) {
      return;
    }
    if (event.eventType === 'TASK_LOGS_CLEARED') {
      Object.values(androidTestStepImageUrlsRef.current).forEach((url) => window.URL.revokeObjectURL(url));
      androidTestStepImageUrlsRef.current = {};
      setLogs([]);
      setSelectedAndroidTestDetail(null);
      setAndroidTestSteps([]);
      setAndroidTestStepImageUrls({});
      return;
    }

    if (event.taskLog) {
      setLogs((current) => upsertTaskLog(current, event.taskLog as TaskLog));
      if (selectedAndroidTestDetailRef.current && selectedAndroidTestDetailRef.current.id === event.taskLog.id) {
        if (event.taskLog.status === 'QUEUED' && !event.taskLog.result) {
          Object.values(androidTestStepImageUrlsRef.current).forEach((url) => window.URL.revokeObjectURL(url));
          androidTestStepImageUrlsRef.current = {};
          androidTestStepsRef.current = [];
          setAndroidTestSteps([]);
          setAndroidTestStepImageUrls({});
        }
        setSelectedAndroidTestDetail((current) => updateAndroidTestDetailFromTaskLog(current, event.taskLog as TaskLog));
      }
    }

    if (event.step) {
      const step = event.step;
      if (selectedAndroidTestDetailRef.current && selectedAndroidTestDetailRef.current.id === step.taskLogId) {
        const nextSteps = upsertAndroidTestStep(androidTestStepsRef.current, step);
        androidTestStepsRef.current = nextSteps;
        setAndroidTestSteps(nextSteps);
        if (step.imageStorageKey) {
          void loadAndroidTestStepImage(step.taskLogId, step.stepNumber).catch((error) => setStatus(message(error)));
        }
        setSelectedAndroidTestDetail((current) => current && current.id === step.taskLogId
          ? { ...current, stepCount: nextSteps.length }
          : current);
      }
    }
  }

  async function loadAndroidTestStepImage(taskLogId: number, stepNumber: number) {
    const previous = androidTestStepImageUrlsRef.current[stepNumber];
    if (previous) {
      window.URL.revokeObjectURL(previous);
    }
    const blob = await getBlob(`/api/tests/android/${taskLogId}/steps/${stepNumber}/image`);
    const url = window.URL.createObjectURL(blob);
    androidTestStepImageUrlsRef.current = {
      ...androidTestStepImageUrlsRef.current,
      [stepNumber]: url,
    };
    setAndroidTestStepImageUrls(androidTestStepImageUrlsRef.current);
  }

  function upsertTaskLog(rows: TaskLog[], nextRow: TaskLog) {
    const index = rows.findIndex((row) => row.id === nextRow.id);
    if (index === -1) {
      return [nextRow, ...rows].sort((left, right) => right.id - left.id);
    }
    const next = [...rows];
    next[index] = nextRow;
    return next;
  }

  function updateAndroidTestDetailFromTaskLog(current: AndroidTestDetail | null, nextRow: TaskLog) {
    if (!current || current.id !== nextRow.id) {
      return current;
    }
    return {
      ...current,
      status: nextRow.status,
      content: nextRow.content,
      result: nextRow.result ?? null,
      startedAt: nextRow.startedAt ?? current.startedAt,
      endedAt: nextRow.endedAt ?? current.endedAt,
      request: parseJsonRecord(nextRow.content),
      summary: parseJsonRecord(nextRow.result),
    };
  }

  function upsertAndroidTestStep(rows: AndroidTestStepHistory[], nextStep: AndroidTestStepHistory) {
    const index = rows.findIndex((row) => row.stepNumber === nextStep.stepNumber);
    if (index === -1) {
      return [...rows, nextStep].sort((left, right) => left.stepNumber - right.stepNumber);
    }
    const next = [...rows];
    next[index] = nextStep;
    return next;
  }

  function parseJsonRecord(value?: string | null) {
    if (!value) {
      return null;
    }
    try {
      const parsed = JSON.parse(value) as Record<string, unknown>;
      return parsed;
    } catch {
      return null;
    }
  }

  async function copyText(value: string, label?: string) {
    await navigator.clipboard.writeText(value);
    setStatus(`Copied ${label || 'value'}`);
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
    setAndroidForm(createAndroidFormState(redroidImages, 'REDROID'));
  }

  const isDirectAndroidForm = androidForm.type === 'DIRECT';
  const selectedArtifactAndroidTarget = selectedArtifactAndroid(android, healthStatuses.android, artifactAndroidSelection.androidId);
  const selectedArtifactAndroidReady = selectedArtifactAndroidTarget != null && isArtifactAndroidActive(selectedArtifactAndroidTarget, healthStatuses.android);
  const selectedArtifactAndroidStatus = selectedArtifactAndroidTarget
    ? artifactAndroidStatusLabel(selectedArtifactAndroidTarget, healthStatuses.android)
    : 'Pick device';
  const selectedTestAndroidTarget = selectedArtifactAndroid(android, healthStatuses.android, testAndroidSelection.androidId);
  const selectedTestAndroidReady = selectedTestAndroidTarget != null && isArtifactAndroidActive(selectedTestAndroidTarget, healthStatuses.android);
  const selectedTestAndroidStatus = selectedTestAndroidTarget
    ? artifactAndroidStatusLabel(selectedTestAndroidTarget, healthStatuses.android)
    : 'Pick device';
  const artifactAndroidOptions = filterArtifactAndroidOptions(
    android,
    healthStatuses.android,
    artifactAndroidSelection.query,
    artifactAndroidSelection.searchActive,
  );
  const testAndroidOptions = filterArtifactAndroidOptions(
    android,
    healthStatuses.android,
    testAndroidSelection.query,
    testAndroidSelection.searchActive,
  );
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
        {(['ollama', 'docker', 'android', 'artifacts', 'tests', 'logs'] as Tab[]).map((item) => (
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
          <form className="form-shell" onSubmit={(event) => submitOllama(event).catch((error) => setStatus(message(error)))}>
            <div className="form-fields form-grid ollama-form-grid">
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
              <div className="form-actions">
                <button type="button" onClick={() => fetchOllamaModels().catch((error) => setStatus(message(error)))}>
                  Refresh models
                </button>
                <button type="submit">{editingOllamaId == null ? 'Save Ollama' : 'Update Ollama'}</button>
                {editingOllamaId != null && <button type="button" onClick={resetOllamaForm}>Cancel</button>}
              </div>
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
          <form className="form-shell" onSubmit={(event) => submitDocker(event).catch((error) => setStatus(message(error)))}>
            <div className="form-fields form-grid">
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
              <div className="form-actions">
                <button type="submit">{editingDockerId == null ? 'Save Docker' : 'Update Docker'}</button>
                {editingDockerId != null && <button type="button" onClick={resetDockerForm}>Cancel</button>}
              </div>
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
          <form className="form-shell" onSubmit={(event) => submitAndroid(event).catch((error) => setStatus(message(error)))}>
            <div className={`form-fields form-grid android-form-grid ${isDirectAndroidForm ? 'direct-android-grid' : 'redroid-android-grid'}`}>
              <label>
                Type
                <select
                  value={androidForm.type}
                  onChange={(event) => {
                    const type = event.target.value;
                    setAndroidForm((current) => {
                      const next = createAndroidFormState(redroidImages, type === 'DIRECT' ? 'DIRECT' : 'REDROID');
                      return {
                        ...next,
                        name: current.name.trim() || next.name,
                        pairPort: current.pairPort,
                        pairCode: '',
                        adbHost: current.adbHost,
                        adbPort: current.adbPort,
                      };
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
                    <select
                      value={androidForm.image}
                      onChange={(event) => setAndroidForm({ ...androidForm, image: event.target.value })}
                    >
                      {redroidImages.length === 0 ? (
                        <option value={REDROID_IMAGE_FALLBACK}>Android 16</option>
                      ) : (
                        redroidImages.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))
                      )}
                    </select>
                  </label>
                  <label>
                    Acceleration
                    <select
                      value={androidForm.accelerationMode || 'AUTO'}
                      onChange={(event) => setAndroidForm({ ...androidForm, accelerationMode: event.target.value })}
                    >
                      <option value="GUEST">GUEST</option>
                      <option value="HOST">HOST</option>
                      <option value="AUTO">AUTO</option>
                    </select>
                  </label>
                  <div className="form-actions">
                    <button type="submit">
                      {editingAndroidId == null ? 'Create Android' : 'Update Android'}
                    </button>
                    {editingAndroidId != null && <button type="button" onClick={resetAndroidForm}>Cancel</button>}
                  </div>
                  <fieldset className="field-group span-all">
                    <legend>Redroid options</legend>
                    <div className="form-grid compact-grid">
                      <label>
                        Resolution preset
                        <select
                          value={androidForm.resolutionPreset}
                          onChange={(event) => applyResolutionPreset(event.target.value)}
                        >
                          <option value="custom">Custom</option>
                          {REDROID_RESOLUTION_PRESETS.map((preset) => (
                            <option key={preset.id} value={preset.id}>
                              {preset.label}
                            </option>
                          ))}
                        </select>
                      </label>
                      <label>
                        Height
                        <input
                          inputMode="numeric"
                          value={androidForm.width}
                          onChange={(event) => setAndroidForm((current) => {
                            const width = event.target.value;
                            const resolutionPreset = syncResolutionPreset(width, current.height);
                            return { ...current, width, resolutionPreset };
                          })}
                        />
                      </label>
                      <label>
                        Width
                        <input
                          inputMode="numeric"
                          value={androidForm.height}
                          onChange={(event) => setAndroidForm((current) => {
                            const height = event.target.value;
                            const resolutionPreset = syncResolutionPreset(current.width, height);
                            return { ...current, height, resolutionPreset };
                          })}
                        />
                      </label>
                      <label>
                        DPI
                        <input
                          inputMode="numeric"
                          value={androidForm.dpi}
                          onChange={(event) => setAndroidForm((current) => {
                            const dpi = event.target.value;
                            return { ...current, dpi };
                          })}
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
                  <div className="form-actions">
                    <button type="submit">
                      {editingAndroidId == null ? 'Create Android' : 'Update Android'}
                    </button>
                    {editingAndroidId != null && <button type="button" onClick={resetAndroidForm}>Cancel</button>}
                  </div>
                </>
              )}
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
                  <button type="button" className="text-button" onClick={() => copyText(address, 'address').catch((error) => setStatus(message(error)))}>
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
                          void copyText(address, 'address');
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

      {tab === 'artifacts' && (
        <section className="panel artifacts-panel">
          <div className="artifact-toolbar">
            <label className="artifact-toolbar-field artifact-filter">
              <span>Source</span>
              <select value={artifactFilter} onChange={(event) => setArtifactFilter(event.target.value as ArtifactFilter)}>
                <option value="all">All</option>
                <option value="upload">Upload</option>
                <option value="artifact">Artifact</option>
              </select>
            </label>
            <div className="artifact-toolbar-field artifact-device-picker">
              <div className="artifact-toolbar-label-row">
                <span>Android device</span>
                <span className={`artifact-device-badge ${selectedArtifactAndroidReady ? 'is-active' : 'is-empty'}`}>
                  {selectedArtifactAndroidStatus}
                </span>
              </div>
              <div className="artifact-combobox">
                <input
                  value={artifactAndroidSelection.query}
                  placeholder="Search Android device"
                  onFocus={() => setArtifactAndroidSelection((current) => ({ ...current, open: true }))}
                  onBlur={() => window.setTimeout(() => {
                    setArtifactAndroidSelection((current) => {
                      const next = selectedArtifactAndroid(android, healthStatuses.android, current.androidId);
                      if (!next) {
                        return {
                          ...current,
                          androidId: null,
                          query: '',
                          open: false,
                          searchActive: false,
                        };
                      }
                      return {
                        ...current,
                        androidId: next.id,
                        query: artifactAndroidLabel(next, healthStatuses.android),
                        open: false,
                        searchActive: false,
                      };
                    });
                  }, 120)}
                  onChange={(event) => setArtifactAndroidSelection((current) => ({
                    ...current,
                    query: event.target.value,
                    open: true,
                    searchActive: true,
                  }))}
                />
              </div>
              <div className={`artifact-combobox-menu ${artifactAndroidSelection.open ? 'is-open' : ''}`}>
                {artifactAndroidOptions.length === 0 ? (
                  <p className="artifact-combobox-empty">No matching Android devices.</p>
                ) : (
                  artifactAndroidOptions.map((option) => {
                    const isSelected = option.id === artifactAndroidSelection.androidId;
                    const isActive = isArtifactAndroidActive(option, healthStatuses.android);
                    return (
                      <button
                        key={option.id}
                        type="button"
                        className={`artifact-combobox-option ${isSelected ? 'is-selected' : ''}`}
                        onMouseDown={(event) => {
                          event.preventDefault();
                          setArtifactAndroidSelection({
                            androidId: option.id,
                            query: artifactAndroidLabel(option, healthStatuses.android),
                            open: false,
                            searchActive: false,
                          });
                        }}
                      >
                        {renderAndroidDeviceOptionCard(option, healthStatuses.android, redroidImages, isActive)}
                      </button>
                    );
                  })
                )}
              </div>
            </div>
            <form
              ref={artifactUploadFormRef}
              className="artifact-upload-inline"
              onSubmit={(event) => submitArtifactUpload(event).catch((error) => setStatus(message(error)))}
            >
              <label className="artifact-toolbar-field">
                <span>Display name</span>
                <input
                  value={artifactUploadForm.name}
                  onChange={(event) => setArtifactUploadForm((current) => ({ ...current, name: event.target.value }))}
                  placeholder="Optional"
                />
              </label>
              <label className="artifact-toolbar-field artifact-file-input">
                <span>APK file</span>
                <input
                  accept=".apk,application/vnd.android.package-archive"
                  type="file"
                  onChange={(event) => {
                    const file = event.target.files?.[0] || null;
                    setArtifactUploadForm((current) => ({
                      ...current,
                      file,
                      name: current.name.trim()
                        ? current.name
                        : file == null
                          ? ''
                          : stripApkSuffix(file.name),
                    }));
                  }}
                />
              </label>
              <button className="artifact-save-button" type="submit">Save APK</button>
            </form>
          </div>
          <DataTable
            rows={artifacts}
            columns={['name', 'source', 'size', 'repoFullName', 'workflowRunId', 'githubArtifactId']}
            columnLabels={{
              repoFullName: 'Repo',
              workflowRunId: 'Run ID',
              githubArtifactId: 'GitHub ID',
            }}
            renderActions={(row) => {
              const typedRow = row as Artifact;
              return (
                <>
                  <button type="button" onClick={() => downloadArtifact(typedRow).catch((error) => setStatus(message(error)))}>
                    Download
                  </button>
                  <button
                    type="button"
                    disabled={!selectedArtifactAndroidReady}
                    onClick={() => installArtifact(typedRow).catch((error) => setStatus(message(error)))}
                  >
                    Install
                  </button>
                  <button
                    type="button"
                    onClick={() => deleteArtifact(typedRow).catch((error) => setStatus(message(error)))}
                  >
                    Delete
                  </button>
                </>
              );
            }}
            renderCell={(row, column) => {
              const typedRow = row as Artifact;
              if (column === 'source') {
                return <span className={`artifact-source-badge source-${typedRow.source.toLowerCase()}`}>{artifactSourceLabel(typedRow.source)}</span>;
              }
              if (column === 'size') {
                return formatArtifactSize(typedRow.size);
              }
              if (column === 'repoFullName') {
                return typedRow.repoFullName || '-';
              }
              if (column === 'workflowRunId') {
                return typedRow.workflowRunId == null ? '-' : typedRow.workflowRunId;
              }
              if (column === 'githubArtifactId') {
                return typedRow.githubArtifactId == null ? '-' : typedRow.githubArtifactId;
              }
              return undefined;
            }}
          />
        </section>
      )}

      {tab === 'tests' && (
        <section className="panel">
          <form className="form-shell" onSubmit={(event) => submitAndroidTest(event).catch((error) => setStatus(message(error)))}>
            <div className="form-fields form-grid test-form-grid">
              <div className="artifact-toolbar-field artifact-device-picker test-device-picker">
                <div className="artifact-toolbar-label-row">
                  <span>Android device</span>
                  <span className={`artifact-device-badge ${selectedTestAndroidReady ? 'is-active' : 'is-empty'}`}>
                    {selectedTestAndroidStatus}
                  </span>
                </div>
                <div className="artifact-combobox">
                  <input
                    value={testAndroidSelection.query}
                    placeholder="Search Android device"
                    onFocus={() => setTestAndroidSelection((current) => ({ ...current, open: true }))}
                    onBlur={() => window.setTimeout(() => {
                      setTestAndroidSelection((current) => {
                        const next = selectedArtifactAndroid(android, healthStatuses.android, current.androidId);
                        if (!next) {
                          setAndroidTestForm((form) => ({ ...form, androidId: '' }));
                          return {
                            ...current,
                            androidId: null,
                            query: '',
                            open: false,
                            searchActive: false,
                          };
                        }
                        setAndroidTestForm((form) => ({ ...form, androidId: String(next.id) }));
                        return {
                          ...current,
                          androidId: next.id,
                          query: artifactAndroidLabel(next, healthStatuses.android),
                          open: false,
                          searchActive: false,
                        };
                      });
                    }, 120)}
                    onChange={(event) => setTestAndroidSelection((current) => ({
                      ...current,
                      query: event.target.value,
                      open: true,
                      searchActive: true,
                    }))}
                  />
                </div>
                <div className={`artifact-combobox-menu ${testAndroidSelection.open ? 'is-open' : ''}`}>
                  {testAndroidOptions.length === 0 ? (
                    <p className="artifact-combobox-empty">No matching Android devices.</p>
                  ) : (
                    testAndroidOptions.map((option) => {
                      const isSelected = option.id === testAndroidSelection.androidId;
                      const isActive = isArtifactAndroidActive(option, healthStatuses.android);
                      return (
                        <button
                          key={option.id}
                          type="button"
                          className={`artifact-combobox-option ${isSelected ? 'is-selected' : ''}`}
                          onMouseDown={(event) => {
                            event.preventDefault();
                            setTestAndroidSelection({
                              androidId: option.id,
                              query: artifactAndroidLabel(option, healthStatuses.android),
                              open: false,
                              searchActive: false,
                            });
                            setAndroidTestForm((current) => ({ ...current, androidId: String(option.id) }));
                          }}
                        >
                          {renderAndroidDeviceOptionCard(option, healthStatuses.android, redroidImages, isActive)}
                        </button>
                      );
                    })
                  )}
                </div>
              </div>
              <label>
                APK artifact
                <select
                  value={androidTestForm.artifactId || artifacts[0]?.id || ''}
                  onChange={(event) => setAndroidTestForm((current) => ({ ...current, artifactId: event.target.value }))}
                >
                  <option value="">Select artifact</option>
                  {artifacts.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Ollama
                <select
                  value={androidTestForm.ollamaId || ollama[0]?.id || ''}
                  onChange={(event) => setAndroidTestForm((current) => ({ ...current, ollamaId: event.target.value }))}
                >
                  <option value="">Select Ollama</option>
                  {ollama.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} · {item.model}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Max steps
                <input
                  inputMode="numeric"
                  value={androidTestForm.maxSteps}
                  onChange={(event) => setAndroidTestForm((current) => ({ ...current, maxSteps: event.target.value }))}
                />
              </label>
              <label className="span-all">
                Objective
                <input
                  value={androidTestForm.objective}
                  onChange={(event) => setAndroidTestForm((current) => ({ ...current, objective: event.target.value }))}
                  placeholder="Open the app and verify the login screen appears"
                />
              </label>
              <div className="form-actions span-all">
                <button type="submit">Run Android test</button>
              </div>
            </div>
          </form>
          <DataTable
            rows={logs.filter((row) => row.type === 'ANDROID_AUTONOMOUS_TEST')}
            columns={['id', 'status', 'duration', 'content', 'result']}
            columnLabels={{
              id: 'ID',
              status: 'Status',
              duration: 'Duration',
              content: 'Request',
              result: 'Result',
            }}
            renderActions={(row) => {
              const taskLog = row as TaskLog;
              return (
                <>
                  <button type="button" onClick={() => loadAndroidTestDetail(taskLog.id).catch((error) => setStatus(message(error)))}>
                    Detail
                  </button>
                </>
              );
            }}
            renderCell={(row, column) => {
              if (column === 'status') {
                const value = (row as TaskLog).status;
                return <span className={`log-status ${taskStatusClass(value)}`}>{value}</span>;
              }
              if (column === 'duration') {
                return formatTaskLogDuration(row as TaskLog);
              }
              if (column === 'content' || column === 'result') {
                const value = (row as TaskLog)[column as 'content' | 'result'];
                return value ? (
                  <button type="button" className="text-button copy-cell ellipsis-cell" onClick={() => copyText(value, column).catch((error) => setStatus(message(error)))}>
                    {value}
                  </button>
                ) : (
                  '-'
                );
              }
              return undefined;
            }}
          />
          {selectedAndroidTestDetail && (
            <div className="test-detail-shell">
              <div className="detail-grid">
                <div>
                  <span>Test</span>
                  <strong>#{selectedAndroidTestDetail.id}</strong>
                </div>
                <div>
                  <span>Status</span>
                  <strong>{selectedAndroidTestDetail.status}</strong>
                </div>
                <div>
                  <span>Duration</span>
                  <strong>{formatTaskLogDuration(selectedAndroidTestDetail)}</strong>
                </div>
                <div>
                  <span>Steps</span>
                  <strong>{selectedAndroidTestDetail.stepCount}</strong>
                </div>
                <div>
                  <span>Package</span>
                  <strong>{summaryValue(selectedAndroidTestDetail.summary, 'packageName')}</strong>
                </div>
                <div>
                  <span>Serial</span>
                  <strong>{summaryValue(selectedAndroidTestDetail.summary, 'serial')}</strong>
                </div>
                <div>
                  <span>Reason</span>
                  <strong>{summaryValue(selectedAndroidTestDetail.summary, 'reason')}</strong>
                </div>
              </div>
              <div className="test-objective">
                <span>Objective</span>
                <strong>{summaryValue(selectedAndroidTestDetail.request, 'objective')}</strong>
              </div>
              <div className="test-step-list">
                {androidTestSteps.map((step) => (
                  <div key={step.id} className="test-step-card">
                    <div className="test-step-media">
                      {androidTestStepImageUrls[step.stepNumber] ? (
                        <img
                          className="test-step-image"
                          src={androidTestStepImageUrls[step.stepNumber]}
                          alt={`Step ${step.stepNumber}`}
                        />
                      ) : (
                        <div className="test-step-image-empty">No screenshot</div>
                      )}
                    </div>
                    <div className="test-step-fields">
                      <div className="detail-grid">
                        <div>
                          <span>Step</span>
                          <strong>{step.stepNumber}</strong>
                        </div>
                        <div>
                          <span>Timing</span>
                          <strong>{formatStepDuration(step)}</strong>
                        </div>
                        <div>
                          <span>Action</span>
                          <strong>{step.action || '-'}</strong>
                        </div>
                        <div>
                          <span>Target</span>
                          <strong>{formatTarget(step)}</strong>
                        </div>
                        <div>
                          <span>Swipe</span>
                          <strong>{formatSwipe(step)}</strong>
                        </div>
                        <div>
                          <span>Foreground</span>
                          <strong>{step.foreground || '-'}</strong>
                        </div>
                      </div>
                      <TestStepBlock title="Reasoning" value={step.reasoning} />
                      <TestStepBlock title="UI context" value={step.uiContext} />
                      <TestStepBlock title="Vision" value={step.visionText} />
                      <TestStepBlock title="Decision JSON" value={step.decisionJson} />
                      <TestStepBlock title="Action result" value={step.actionResult} />
                      <TestStepBlock title="Error" value={step.error} />
                    </div>
                  </div>
                ))}
              </div>
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
          </div>
          <DataTable
            rows={logs}
            columns={['id', 'type', 'status', 'duration', 'content', 'result']}
            columnLabels={{
              id: 'ID',
              type: 'Type',
              status: 'Status',
              duration: 'Duration',
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
            renderCell={(row, column) => {
              if (column === 'status') {
                const value = (row as TaskLog).status;
                return <span className={`log-status ${taskStatusClass(value)}`}>{value}</span>;
              }
              if (column === 'duration') {
                return formatTaskLogDuration(row as TaskLog);
              }
              if (column === 'content') {
                const value = (row as TaskLog).content;
                return value ? (
                  <button type="button" className="text-button copy-cell ellipsis-cell" onClick={() => copyText(value, 'message').catch((error) => setStatus(message(error)))}>
                    {value}
                  </button>
                ) : (
                  '-'
                );
              }
              if (column === 'result') {
                const value = (row as TaskLog).result;
                return value ? (
                  <button type="button" className="text-button copy-cell ellipsis-cell" onClick={() => copyText(value, 'result').catch((error) => setStatus(message(error)))}>
                    {value}
                  </button>
                ) : (
                  '-'
                );
              }
              return undefined;
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

function TestStepBlock({ title, value }: { title: string; value?: string | null }) {
  if (!value) {
    return null;
  }
  return (
    <div className="test-step-block">
      <span>{title}</span>
      <pre>{value}</pre>
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

function summaryValue(source: Record<string, unknown> | null | undefined, key: string) {
  const value = source?.[key];
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  return String(value);
}

function hasActiveTaskLogs(rows: TaskLog[]) {
  return rows.some((row) => !isTerminalTaskStatus(row.status));
}

function isTerminalTaskStatus(status?: string | null) {
  const normalized = (status || '').toUpperCase();
  return normalized === 'SUCCESS' || normalized === 'FAILED' || normalized === 'CANCELLED';
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError';
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
  const isPortrait = typeof android.width === 'number' && typeof android.height === 'number' && android.height > android.width;
  return isPortrait ? `${height}x${width}@${dpi}` : `${width}x${height}@${dpi}`;
}

function createAndroidFormState(redroidImages: RedroidImageOption[], type: 'REDROID' | 'DIRECT' = 'REDROID'): AndroidFormState {
  const isRedroid = type === 'REDROID';
  return {
    type,
    dockerId: '',
    name: generateAndroidName(),
    image: isRedroid ? defaultRedroidImage(redroidImages) : '',
    accelerationMode: 'AUTO',
    width: '',
    height: '',
    dpi: '',
    resolutionPreset: 'custom',
    adbHost: '',
    adbPort: '',
    pairPort: '',
    pairCode: '',
  };
}

function findResolutionPreset(width: string, height: string) {
  const parsedWidth = parseOptionalInteger(width);
  const parsedHeight = parseOptionalInteger(height);
  if (parsedWidth == null || parsedHeight == null) {
    return null;
  }
  return REDROID_RESOLUTION_PRESETS.find((preset) => (
    preset.width === parsedWidth
    && preset.height === parsedHeight
  )) || null;
}

function generateAndroidName() {
  const adjectives = [
    'amber',
    'brisk',
    'calm',
    'dapper',
    'eager',
    'frosty',
    'gentle',
    'hollow',
    'ivory',
    'jolly',
    'kind',
    'lively',
    'mellow',
    'noble',
    'ocean',
    'plucky',
    'quiet',
    'radiant',
    'shady',
    'tidy',
    'upbeat',
    'vivid',
    'wavy',
    'zesty',
  ];
  const nouns = [
    'branch',
    'canyon',
    'drift',
    'ember',
    'forest',
    'garden',
    'harbor',
    'island',
    'junction',
    'kernel',
    'lantern',
    'meadow',
    'nest',
    'orchard',
    'pebble',
    'quartz',
    'river',
    'summit',
    'trail',
    'valley',
    'window',
    'yard',
    'zenith',
  ];
  return `${randomWord(adjectives)}-${randomWord(nouns)}`;
}

function randomWord(words: string[]) {
  return words[randomIndex(words.length)];
}

function randomIndex(max: number) {
  if (max <= 0) {
    return 0;
  }
  if (window.crypto?.getRandomValues) {
    const buffer = new Uint32Array(1);
    window.crypto.getRandomValues(buffer);
    return buffer[0] % max;
  }
  return Math.floor(Math.random() * max);
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

function formatTaskLogDuration(taskLog: Pick<TaskLog, 'startedAt' | 'endedAt'>) {
  if (taskLog.startedAt == null) {
    return '-';
  }
  const endedAt = taskLog.endedAt ?? Date.now();
  const durationSeconds = Math.max(0, (endedAt - taskLog.startedAt) / 1000);
  return `${durationSeconds.toFixed(1)}s`;
}

function formatStepDuration(step: Pick<AndroidTestStepHistory, 'startedAt' | 'endedAt'>) {
  if (step.startedAt == null) {
    return '-';
  }
  const endedAt = step.endedAt ?? Date.now();
  return `${Math.max(0, (endedAt - step.startedAt) / 1000).toFixed(1)}s`;
}

function formatTarget(step: AndroidTestStepHistory) {
  const element = step.targetElementId == null ? null : `#${step.targetElementId}`;
  if (step.targetX == null || step.targetY == null) {
    return element ?? '-';
  }
  const coordinates = `${step.targetX},${step.targetY}`;
  return element == null ? coordinates : `${element} -> ${coordinates}`;
}

function formatSwipe(step: AndroidTestStepHistory) {
  if (step.swipeX1 == null || step.swipeY1 == null || step.swipeX2 == null || step.swipeY2 == null) {
    return '-';
  }
  const duration = step.swipeDurationMs == null ? '' : ` ${step.swipeDurationMs}ms`;
  return `${step.swipeX1},${step.swipeY1} -> ${step.swipeX2},${step.swipeY2}${duration}`;
}

function truncateText(value: string, maxLength: number) {
  return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value;
}

function tabFromLocationPath(): Tab {
  const parts = window.location.pathname.split('/').filter(Boolean);
  const value = parts[parts.length - 1];
  if (value === 'ollama' || value === 'docker' || value === 'android' || value === 'artifacts' || value === 'tests' || value === 'logs') {
    return value;
  }
  return 'ollama';
}

function tabToPath(tab: Tab) {
  return `/${tab}${window.location.search}`;
}

function websocketBrokerUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
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

function taskStatusClass(status: string) {
  const normalized = status.toLowerCase();
  if (normalized.includes('pend')) {
    return 'is-pending';
  }
  if (normalized.includes('run')) {
    return 'is-running';
  }
  if (normalized.includes('fail') || normalized.includes('error')) {
    return 'is-failed';
  }
  if (normalized.includes('success') || normalized.includes('done') || normalized.includes('complete')) {
    return 'is-success';
  }
  if (normalized.includes('stop')) {
    return 'is-stopped';
  }
  return 'is-neutral';
}

function label(tab: Tab) {
  if (tab === 'android') {
    return 'Android';
  }
  if (tab === 'artifacts') {
    return 'Artifacts';
  }
  if (tab === 'tests') {
    return 'Tests';
  }
  return tab[0].toUpperCase() + tab.slice(1);
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

function formatArtifactSize(size?: number | null) {
  if (size == null) {
    return '-';
  }
  return `${Number(size).toFixed(2)} MB`;
}

function artifactSourceLabel(source: ArtifactSource) {
  return source === 'UPLOAD' ? 'Upload' : 'Artifact';
}

function artifactDownloadName(artifact: Artifact) {
  const base = artifact.originalFileName || artifact.name || `artifact-${artifact.id}`;
  return stripApkSuffix(base);
}

function artifactAndroidLabel(android: Android, statuses: Record<number, string>) {
  const address = formatAndroidAddress(android);
  const status = artifactAndroidStatusLabel(android, statuses);
  const name = android.name || `android-${android.id}`;
  return address ? `${name} · ${address} · ${status}` : `${name} · ${status}`;
}

function renderAndroidDeviceOptionCard(
  android: Android,
  statuses: Record<number, string>,
  redroidImages: RedroidImageOption[],
  isActive: boolean,
) {
  const status = artifactAndroidStatusLabel(android, statuses);
  return (
    <span className="android-device-card">
      <span className="android-device-card-top">
        <span className="android-device-card-name">{android.name || `android-${android.id}`}</span>
        <span className={`artifact-combobox-option-status ${isActive ? 'is-active' : 'is-inactive'}`}>{status}</span>
      </span>
      <span className="android-device-card-grid">
        <span>
          <span>Android version</span>
          <strong>{androidVersionLabel(android, redroidImages)}</strong>
        </span>
        <span>
          <span>Address:port</span>
          <strong>{formatAndroidAddress(android) || '-'}</strong>
        </span>
        <span>
          <span>Name</span>
          <strong>{android.name || `android-${android.id}`}</strong>
        </span>
        <span>
          <span>Type</span>
          <strong>{androidTypeLabel(android)}</strong>
        </span>
      </span>
    </span>
  );
}

function androidVersionLabel(android: Android, redroidImages: RedroidImageOption[]) {
  if (android.type === 'DIRECT') {
    return '-';
  }
  const option = redroidImages.find((item) => item.value === android.image);
  if (option) {
    return option.label;
  }
  const version = android.image.match(/redroid:(\d+(?:\.\d+)?)/)?.[1];
  return version ? `Android ${version.replace(/\.0$/, '')}` : android.image || '-';
}

function androidTypeLabel(android: Pick<Android, 'type'>) {
  return android.type === 'DIRECT' ? 'Physical' : android.type.toLowerCase();
}

function artifactAndroidStatusLabel(android: Android, statuses: Record<number, string>) {
  const status = statuses[android.id] || android.status || 'UNKNOWN';
  return status;
}

function isArtifactAndroidActive(android: Android, statuses: Record<number, string>) {
  const status = artifactAndroidStatusLabel(android, statuses);
  return Boolean(formatAndroidAddress(android)) && (status === 'CONNECTED' || status === 'RUNNING');
}

function selectedArtifactAndroid(rows: Android[], statuses: Record<number, string>, androidId: number | null) {
  if (androidId != null) {
    const selected = rows.find((row) => row.id === androidId) || null;
    if (selected) {
      return selected;
    }
  }
  return pickDefaultArtifactAndroid(rows, statuses);
}

function pickDefaultArtifactAndroid(rows: Android[], statuses: Record<number, string>) {
  const active = rows.find((row) => isArtifactAndroidActive(row, statuses));
  return active || rows[0] || null;
}

function filterArtifactAndroidOptions(rows: Android[], statuses: Record<number, string>, query: string, searchActive: boolean) {
  const normalized = query.trim().toLowerCase();
  if (!searchActive) {
    return [...rows].sort((left, right) => {
      const leftActive = isArtifactAndroidActive(left, statuses) ? 1 : 0;
      const rightActive = isArtifactAndroidActive(right, statuses) ? 1 : 0;
      if (leftActive !== rightActive) {
        return rightActive - leftActive;
      }
      return left.name.localeCompare(right.name);
    });
  }
  return [...rows]
    .sort((left, right) => {
      const leftActive = isArtifactAndroidActive(left, statuses) ? 1 : 0;
      const rightActive = isArtifactAndroidActive(right, statuses) ? 1 : 0;
      if (leftActive !== rightActive) {
        return rightActive - leftActive;
      }
      return left.name.localeCompare(right.name);
    })
    .filter((row) => {
      if (!normalized) {
        return true;
      }
      const haystack = [
        row.name,
        row.dockerName,
        row.type,
        row.adbHost,
        row.adbPort == null ? '' : String(row.adbPort),
        artifactAndroidStatusLabel(row, statuses),
      ].filter(Boolean).join(' ').toLowerCase();
      return haystack.includes(normalized);
    });
}

function stripApkSuffix(value: string) {
  if (value.toLowerCase().endsWith('.apk')) {
    return value.slice(0, -4);
  }
  return value;
}
