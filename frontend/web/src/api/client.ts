export class ApiClientError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) {
    super(message);
  }
}

const base = "/api";

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type") && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(`${base}${path}`, { ...init, headers, credentials: "include" });
  const text = await response.text();
  let body: unknown = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  if (!response.ok) {
    const error = body as { code?: string; message?: string } | null;
    throw new ApiClientError(response.status, error?.code ?? "REQUEST_FAILED", error?.message ?? "Request failed");
  }
  return body as T;
}

export const api = {
  session: () => request<AuthSession>("/auth/session"),
  sendCode: (phone: string) => request<CodeResponse>("/auth/codes", { method: "POST", body: JSON.stringify({ phone }) }),
  login: (payload: LoginPayload) => request<AuthSession>("/auth/login", { method: "POST", body: JSON.stringify(payload) }),
  logout: () => request<void>("/auth/logout", { method: "POST" }),
  inspirations: (params: URLSearchParams, signal?: AbortSignal) => request<InspirationPage>(`/inspirations?${params}`, { signal }),
  inspiration: (slug: string) => request<Inspiration>(`/inspirations/${encodeURIComponent(slug)}`),
  generation: {
    options: () => request<GenerationOptions>("/generation/options"),
    quota: () => request<GenerationQuota>("/generation/quota"),
    sessions: () => request<{ items: GenerationSessionSummary[] }>("/generation/sessions"),
    session: (id: string) => request<GenerationSession>(`/generation/sessions/${encodeURIComponent(id)}`),
    createSession: (draft?: GenerationDraft) => request<GenerationSession>("/generation/sessions", { method: "POST", body: JSON.stringify(draft ?? {}) }),
    renameSession: (id: string, title: string) => request<GenerationSession>(`/generation/sessions/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify({ title }) }),
    draft: (id: string, draft: GenerationDraft) => request<GenerationSession>(`/generation/sessions/${encodeURIComponent(id)}/draft`, { method: "PATCH", body: JSON.stringify(draft) }),
    deleteSession: (id: string) => request<void>(`/generation/sessions/${encodeURIComponent(id)}`, { method: "DELETE" }),
    submit: (payload: GenerationTaskRequest) => request<GenerationSubmitResponse>("/generation/tasks", { method: "POST", body: JSON.stringify(payload) }),
    task: (id: string) => request<GenerationTask>(`/generation/tasks/${encodeURIComponent(id)}`),
    cancel: (id: string) => request<GenerationTask>(`/generation/tasks/${encodeURIComponent(id)}/cancel`, { method: "POST" }),
    retry: (id: string) => request<GenerationSubmitResponse>(`/generation/tasks/${encodeURIComponent(id)}/retry`, { method: "POST" }),
    uploadReference: (file: File) => { const body = new FormData(); body.append("file", file); return request<ReferenceUpload>("/uploads/references", { method: "POST", body }); },
  },
};

export interface AuthUser { id: string; phoneMasked: string; createdAt: string }
export interface AuthSession { authenticated: boolean; user?: AuthUser }
export interface CodeResponse { challengeId: string; expiresAt: string; retryAfterSeconds: number; demoCode?: string | null }
export interface LoginPayload { phone: string; challengeId: string; code: string; version: string; termsAccepted: boolean; privacyAccepted: boolean; aiTermsAccepted: boolean }
export interface Inspiration { id: string; slug: string; title: string; promptSummary?: string; prompt: string; category: string; imageUrl: string; thumbnailUrl: string; width: number; height: number; authorDisplayName: string; likeCount: number; modelName: string; ratio: string; resolutionLabel: string; isAiGenerated: boolean; sourceName?: string; sourceUrl?: string; publishedAt?: string }
export interface InspirationPage { items: Inspiration[]; total: number; page?: number; pageSize?: number; pageCount?: number }
export interface GenerationOptions { models: string[]; ratios: string[]; resolutions: string[]; imageCount: { min: number; max: number }; referenceImages: { max: number; maxBytes: number; mimeTypes: string[] }; costPerImage: number; externalServicesMode: string }
export interface GenerationQuota { total: number; available: number; reserved: number; used: number; remainingPercent: number }
export interface GenerationDraft { prompt: string; model: string; ratio: string; resolution: string; imageCount: number; referenceImageUrls: string[] }
export interface GenerationSessionSummary { id: string; title: string; thumbnailUrl?: string | null; createdAt: string; updatedAt: string }
export interface GenerationSession extends GenerationSessionSummary { draft?: GenerationDraft | null; tasks: GenerationTask[] }
export interface GenerationTaskRequest extends GenerationDraft { idempotencyKey: string; sessionId?: string }
export interface GenerationResult { id: string; index: number; contentUrl: string; thumbnailUrl: string; width: number; height: number; mimeType: string; byteSize: number; isAiGenerated: boolean; moderationStatus?: string | null }
export interface GenerationTask extends GenerationTaskRequest { id: string; status: string; unitCost: number; totalCost: number; errorCode?: string | null; errorMessage?: string | null; startedAt?: string | null; completedAt?: string | null; createdAt: string; updatedAt: string; results: GenerationResult[] }
export interface GenerationSubmitResponse { session: GenerationSession; task: GenerationTask; quota: GenerationQuota; replayed: boolean }
export interface ReferenceUpload { id: string; url: string; filename: string; mimeType: string; width: number; height: number; byteSize: number; checksumSha256: string }
