export type AdminRole = "viewer" | "operator" | "admin";
export type AdminPermission = "tasks:read" | "inspirations:read" | "inspirations:write";

export interface AdminUser {
  id: string;
  displayName: string;
  phoneMasked: string;
  role: AdminRole;
  permissions: AdminPermission[];
}

export type AdminSession =
  | { authenticated: false; user: null }
  | { authenticated: true; user: AdminUser };

export interface TaskSummary {
  id: string;
  sessionId: string;
  sessionTitle: string;
  userPhoneMasked: string;
  status: string;
  prompt: string;
  model: string;
  ratio: string;
  resolution: string;
  imageCount: number;
  resultCount: number;
  totalCost: number;
  attempts: number;
  inputModerationStatus: string;
  outputModerationStatus: string;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface GenerationResult {
  id: string;
  index: number;
  imageUrl: string;
  thumbnailUrl: string;
  width: number;
  height: number;
  mimeType: string;
  byteSize: number;
  isAiGenerated: boolean;
  moderationStatus: string;
}

export interface TaskDetail extends TaskSummary {
  imageIds: string[];
  errorCode: string | null;
  errorMessage: string | null;
  deadLetter: null | { errorCode: string; errorMessage: string; attempts: number; createdAt: string; resolvedAt: string | null };
  results: GenerationResult[];
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  pageCount: number;
}

export interface ReconciliationResponse {
  items: Array<{
    id: string;
    status: string;
    startedAt: string;
    completedAt: string | null;
    scannedUsers: number;
    scannedTasks: number;
    mismatchCount: number;
    repairedCount: number;
    errorMessage: string | null;
    findings: Array<{ id: string; status: string }>;
  }>;
}

export interface ModerationCase {
  id: string;
  taskId: string;
  userId: string;
  stage: string;
  status: string;
  reasonCode: string;
  model: string;
  modelVersion: string;
  createdAt: string;
  resolvedAt: string | null;
}

export interface ModerationDetail {
  reviewCase: ModerationCase;
  appeal: { id: string; reason: string; status: string; createdAt: string; resolvedAt: string | null } | null;
  audit: Array<{ id: string; actorId: string; actorType: string; action: string; before: unknown; after: unknown; createdAt: string }>;
}

export interface Inspiration {
  id: string;
  slug: string;
  title: string;
  prompt: string;
  category: string;
  imageUrl: string;
  thumbnailUrl: string;
  width: number;
  height: number;
  modelName: string;
  ratio: string;
  resolutionLabel: string;
  authorDisplayName: string;
  sourceType: string;
  sourceName: string;
  sourceUrl: string | null;
  licenseBasis: string;
  isAiGenerated: boolean;
  likeCount: number;
  sortOrder: number;
  status: string;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type InspirationInput = Omit<Inspiration, "id" | "status" | "publishedAt" | "createdAt" | "updatedAt"> & { updatedAt?: string };

export class AdminApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string) {
    super(message);
    this.name = "AdminApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    credentials: "include",
    ...init,
    headers: { ...(init?.body ? { "Content-Type": "application/json" } : {}), ...init?.headers },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string; code?: string } | null;
    throw new AdminApiError(payload?.message || `请求失败（${response.status}）`, response.status, payload?.code);
  }
  return response.status === 204 ? undefined as T : await response.json() as T;
}

function query(values: Record<string, string | number | undefined>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params.toString();
}

export const adminApi = {
  session: () => request<AdminSession>("/manage_web/auth/session"),
  sendCode: (phone: string) => request<{ challengeId: string; expiresAt: string; retryAfterSeconds: number }>("/manage_web/auth/codes", { method: "POST", body: JSON.stringify({ phone }) }),
  login: (phone: string, challengeId: string, code: string) => request<AdminSession>("/manage_web/auth/login", { method: "POST", body: JSON.stringify({ phone, challengeId, code }) }),
  logout: () => request<void>("/manage_web/auth/logout", { method: "POST" }),
  tasks: (filters: Record<string, string | number | undefined>) => request<Page<TaskSummary>>(`/manage_web/tasks?${query(filters)}`),
  task: (id: string) => request<TaskDetail>(`/manage_web/tasks/${encodeURIComponent(id)}`),
  reconciliation: () => request<ReconciliationResponse>("/manage_web/tasks/reconciliation/runs"),
  inspirations: (filters: Record<string, string | number | undefined>) => request<Page<Inspiration>>(`/manage_web/inspirations?${query(filters)}`),
  inspiration: (id: string) => request<Inspiration>(`/manage_web/inspirations/${encodeURIComponent(id)}`),
  createInspiration: (input: InspirationInput) => request<Inspiration>("/manage_web/inspirations", { method: "POST", body: JSON.stringify(input) }),
  updateInspiration: (id: string, input: InspirationInput) => request<Inspiration>(`/manage_web/inspirations/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify(input) }),
  publishInspiration: (item: Inspiration) => request<Inspiration>(`/manage_web/inspirations/${encodeURIComponent(item.id)}/publish`, { method: "POST", body: JSON.stringify({ updatedAt: item.updatedAt }) }),
  unpublishInspiration: (item: Inspiration) => request<Inspiration>(`/manage_web/inspirations/${encodeURIComponent(item.id)}/unpublish`, { method: "POST", body: JSON.stringify({ updatedAt: item.updatedAt }) }),
  moderation: {
    cases: (filters: Record<string, string | number | undefined>) => request<Page<ModerationCase>>(`/manage_web/moderation/cases?${query(filters)}`),
    detail: (id: string) => request<ModerationDetail>(`/manage_web/moderation/cases/${encodeURIComponent(id)}`),
    resolve: (id: string, outcome: "APPROVED" | "REJECTED", note: string) => request<ModerationDetail>(`/manage_web/moderation/cases/${encodeURIComponent(id)}/resolve`, { method: "POST", body: JSON.stringify({ outcome, note }) }),
  },
};

export function resolveAssetUrl(value: string) {
  if (/^(https?:|data:|blob:)/i.test(value)) return value;
  if (value.startsWith("/api/")) return value;
  if (value.startsWith("/manage_web/")) return `/api${value}`;
  const appBase = (import.meta.env.BASE_URL === "/" ? "/manage_web/" : import.meta.env.BASE_URL).replace(/\/$/, "");
  return value.startsWith("/") ? `${appBase}${value}` : `${appBase}/${value}`;
}
