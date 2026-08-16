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
};

export interface AuthUser { id: string; phoneMasked: string; createdAt: string }
export interface AuthSession { authenticated: boolean; user?: AuthUser }
export interface CodeResponse { challengeId: string; expiresAt: string; retryAfterSeconds: number; demoCode?: string | null }
export interface LoginPayload { phone: string; challengeId: string; code: string; version: string; termsAccepted: boolean; privacyAccepted: boolean; aiTermsAccepted: boolean }
export interface Inspiration { id: string; slug: string; title: string; promptSummary?: string; prompt: string; category: string; imageUrl: string; thumbnailUrl: string; width: number; height: number; authorDisplayName: string; likeCount: number; modelName: string; ratio: string; resolutionLabel: string; isAiGenerated: boolean; sourceName?: string; sourceUrl?: string; publishedAt?: string }
export interface InspirationPage { items: Inspiration[]; total: number; page?: number; pageSize?: number; pageCount?: number }
