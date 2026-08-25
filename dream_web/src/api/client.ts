export class ApiClientError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) {
    super(message);
  }
}

const base = "/api";

export function resolveAssetUrl(value?: string | null) {
  if (!value) return "";
  if (/^(https?:|data:|blob:|\/\/)/i.test(value)) return value;
  if (value.startsWith("/api/")) return value;
  if (value.startsWith("/dream_web/")) return `${base}${value}`;
  const appBase = (import.meta.env.BASE_URL === "/" ? "/dream_web/" : import.meta.env.BASE_URL).replace(/\/$/, "");
  return value.startsWith("/") ? `${appBase}${value}` : `${appBase}/${value}`;
}

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
  session: () => request<AuthSession>("/dream_web/auth/session"),
  sendCode: (phone: string) => request<CodeResponse>("/dream_web/auth/codes", { method: "POST", body: JSON.stringify({ phone }) }),
  login: (payload: LoginPayload) => request<AuthSession>("/dream_web/auth/login", { method: "POST", body: JSON.stringify(payload) }),
  captcha: () => request<CaptchaResponse>("/dream_web/auth/captcha"),
  passwordLogin: (payload: PasswordLoginPayload) => request<AuthSession>("/dream_web/auth/password-login", { method: "POST", body: JSON.stringify(payload) }),
  logout: () => request<void>("/dream_web/auth/logout", { method: "POST" }),
  inspirations: (params: URLSearchParams, signal?: AbortSignal) => request<InspirationPage>(`/dream_web/inspirations?${params}`, { signal }),
  inspiration: (slug: string) => request<Inspiration>(`/dream_web/inspirations/${encodeURIComponent(slug)}`),
  generation: {
    options: () => request<GenerationOptions>("/dream_web/generation/options"),
    quota: () => request<GenerationQuota>("/dream_web/generation/quota"),
    sessions: () => request<{ items: GenerationSessionSummary[] }>("/dream_web/generation/sessions"),
    session: (id: string) => request<GenerationSession>(`/dream_web/generation/sessions/${encodeURIComponent(id)}`),
    createSession: (draft?: GenerationDraft) => request<GenerationSession>("/dream_web/generation/sessions", { method: "POST", body: JSON.stringify(draft ?? {}) }),
    renameSession: (id: string, title: string) => request<GenerationSession>(`/dream_web/generation/sessions/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify({ title }) }),
    draft: (id: string, draft: GenerationDraft) => request<GenerationSession>(`/dream_web/generation/sessions/${encodeURIComponent(id)}/draft`, { method: "PATCH", body: JSON.stringify(draft) }),
    deleteSession: (id: string) => request<void>(`/dream_web/generation/sessions/${encodeURIComponent(id)}`, { method: "DELETE" }),
    submit: (payload: GenerationTaskRequest) => request<GenerationSubmitResponse>("/dream_web/generation/tasks", { method: "POST", body: JSON.stringify(payload) }),
    task: (id: string) => request<GenerationTask>(`/dream_web/generation/tasks/${encodeURIComponent(id)}`),
    plan: (id: string) => request<GenerationPlan>(`/dream_web/generation/tasks/${encodeURIComponent(id)}/plan`),
    cancel: (id: string) => request<GenerationTask>(`/dream_web/generation/tasks/${encodeURIComponent(id)}/cancel`, { method: "POST" }),
    retry: (id: string) => request<GenerationSubmitResponse>(`/dream_web/generation/tasks/${encodeURIComponent(id)}/retry`, { method: "POST" }),
    uploadReference: (file: File) => { const body = new FormData(); body.append("file", file); return request<ReferenceUpload>("/dream_web/uploads/references", { method: "POST", body }); },
  },
  moderation: {
    cases: () => request<ModerationCase[]>("/dream_web/moderation/cases"),
    appeal: (caseId: string, reason: string) => request<ModerationCase>(`/dream_web/moderation/cases/${encodeURIComponent(caseId)}/appeals`, { method: "POST", body: JSON.stringify({ reason }) }),
  },
  account: {
    account: () => request<{ account: BillingAccount }>("/dream_web/account"),
    ledger: (params = "") => request<{ items: BillingLedgerItem[]; total: number }>(`/dream_web/account/ledger${params ? `?${params}` : ""}`),
    products: () => request<BillingProduct[]>("/dream_web/account/products"),
    createOrder: (payload: BillingOrderRequest) => request<BillingOrder>("/dream_web/account/orders", { method: "POST", body: JSON.stringify(payload) }),
    order: (orderNo: string) => request<BillingOrder>(`/dream_web/account/orders/${encodeURIComponent(orderNo)}`),
    orders: (params = "") => request<{ items: BillingOrder[]; total: number }>(`/dream_web/account/orders${params ? `?${params}` : ""}`),
    cancelOrder: (orderNo: string) => request<BillingOrder>(`/dream_web/account/orders/${encodeURIComponent(orderNo)}/cancel`, { method: "POST" }),
  },
};

export interface AuthUser { id: string; phoneMasked: string; createdAt: string }
export interface BillingAccount { userId: string; phoneMasked: string; status: string; displayName?: string; total: number; available: number; reserved: number; used: number; createdAt: string; lastLoginAt?: string | null }
export interface BillingLedgerItem { id: string; type: string; amount: number; balanceAfter: number; sourceType?: string | null; sourceId?: string | null; taskId?: string | null; ruleId?: string | null; ruleVersion?: number | null; reasonCode?: string | null; createdAt: string }
export interface BillingProduct { id: string; code: string; name: string; creditAmount: number; amountMinor: number; currency: string; validityDays?: number | null; status: string; sortOrder: number; createdAt: string; updatedAt: string }
export interface BillingOrderRequest { productId: string; quantity: number; provider?: string; idempotencyKey: string }
export interface BillingOrder { orderNo: string; productCode: string; productName: string; quantity: number; creditAmount: number; amountMinor: number; currency: string; status: string; provider: string; expiresAt: string; paidAt?: string | null; createdAt: string }
export interface AuthSession { authenticated: boolean; user?: AuthUser }
export interface CodeResponse { challengeId: string; expiresAt: string; retryAfterSeconds: number }
export interface LoginPayload { phone: string; challengeId: string; code: string; version: string; termsAccepted: boolean; privacyAccepted: boolean; aiTermsAccepted: boolean }
export interface CaptchaResponse { captchaId: string; imageData: string; expiresAt: string; retryAfterSeconds: number }
export interface PasswordLoginPayload { phone: string; password: string; captchaId: string; captchaCode: string; version: string; termsAccepted: boolean; privacyAccepted: boolean; aiTermsAccepted: boolean }
export interface Inspiration { id: string; slug: string; title: string; promptSummary?: string; prompt: string; category: string; imageUrl: string; thumbnailUrl: string; width: number; height: number; authorDisplayName: string; likeCount: number; modelName: string; ratio: string; resolutionLabel: string; isAiGenerated: boolean; sourceName?: string; sourceUrl?: string; publishedAt?: string }
export interface InspirationPage { items: Inspiration[]; total: number; page?: number; pageSize?: number; pageCount?: number }
export type GenerationMode = "AUTO";
export type GenerationRatio = "smart" | "21:9" | "16:9" | "3:2" | "4:3" | "1:1" | "3:4" | "2:3" | "9:16" | "custom";
export type GenerationResolution = "2K" | "4K";
export interface GenerationRatioOption { value: Exclude<GenerationRatio, "custom">; label: string }
export interface GenerationResolutionOption { value: GenerationResolution; label: string; maxEdge: number; maxPixels: number; unitCost: number; enabled: boolean; disabledReason?: string | null }
export interface GenerationOptions { modes: GenerationMode[]; ratios: GenerationRatioOption[]; resolutions: GenerationResolutionOption[]; dimensions: { minEdge: number; step: number }; referenceImages: { max: number; maxBytes: number; mimeTypes: string[] } }
export interface GenerationQuota { total: number; available: number; reserved: number; used: number; remainingPercent: number }
export interface GenerationDraft { mode: GenerationMode; prompt: string; imageIds: string[]; ratio: GenerationRatio; resolution: GenerationResolution; width: number | null; height: number | null }
export interface GenerationSessionSummary { id: string; title: string; thumbnailUrl?: string | null; createdAt: string; updatedAt: string }
export interface GenerationSession extends GenerationSessionSummary { draft?: GenerationDraft | null; tasks: GenerationTask[] }
export interface GenerationTaskRequest extends GenerationDraft { idempotencyKey: string; sessionId?: string }
export interface GenerationResult { id: string; index: number; contentUrl: string; thumbnailUrl: string; width: number; height: number; mimeType: string; byteSize: number; isAiGenerated: boolean; moderationStatus?: string | null }
export interface GenerationTask extends GenerationTaskRequest { id: string; status: string; model: string; imageCount: 1; unitCost: number; totalCost: number; planStatus?: string | null; currentStage?: string | null; currentIteration: number; evaluationScore?: number | null; errorCode?: string | null; errorMessage?: string | null; startedAt?: string | null; completedAt?: string | null; createdAt: string; updatedAt: string; results: GenerationResult[] }
export interface GenerationSubmitResponse { session: GenerationSession; task: GenerationTask; quota: GenerationQuota; replayed: boolean }
export interface GenerationPlan { taskId: string; status: string; requirement: unknown; structure: unknown; visual: unknown; promptPackage: unknown; iterations: unknown[] }
export interface ReferenceUpload { id: string; url: string; filename: string; mimeType: string; width: number; height: number; byteSize: number; checksumSha256: string }
export interface ModerationCase { id: string; taskId: string; stage: string; status: string; reasonCode: string; createdAt: string; resolvedAt?: string | null; appeal?: { id: string; reason: string; status: string; createdAt: string; resolvedAt?: string | null } | null }
