import type { Page, Route } from "@playwright/test";

const image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
const now = "2026-08-17T00:00:00.000Z";
const apiUrl = /^https?:\/\/[^/]+\/api(?:\/|$)/;

const inspiration = { id: "inspiration-1", slug: "portrait-01", title: "自然光肖像", prompt: "A quiet editorial portrait in natural afternoon light.", promptSummary: "Curated visual reference.", category: "portrait", imageUrl: "/inspiration/portrait-01.webp", thumbnailUrl: "/inspiration/portrait-01.webp", width: 1350, height: 1800, authorDisplayName: "Dream Space", likeCount: 12, modelName: "Image 4.7", ratio: "3:4", resolutionLabel: "2K", isAiGenerated: true, sourceName: "Dream Space Gallery", sourceUrl: null, publishedAt: now };
const task = { id: "task-1", sessionId: "session-1", sessionTitle: "新的创作", userPhoneMasked: "188****0000", status: "succeeded", prompt: "自然光下的安静编辑肖像", model: "image-4.7", ratio: "1:1", resolution: "2K", imageCount: 1, referenceImageUrls: [], resultCount: 0, results: [], unitCost: 1, totalCost: 1, attempts: 1, idempotencyKey: "e2e-task-1", inputModerationStatus: "approved", outputModerationStatus: "approved", createdAt: now, updatedAt: now, startedAt: now, completedAt: now };
const adminInspiration = { ...inspiration, id: "inspiration-1", slug: "neon-city", title: "雨夜霓虹街景", category: "photography", thumbnailUrl: image, imageUrl: image, sourceType: "internal", sourceName: "造梦空间", licenseBasis: "内部生成素材", sortOrder: 10, status: "published", updatedAt: now, createdAt: now };

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

export async function routeWebApi(page: Page) {
  await page.route(apiUrl, async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, "");
    if (path === "/auth/session") return json(route, { authenticated: true, user: { id: "user-1", phoneMasked: "188****0000", createdAt: now } });
    if (path === "/inspirations") return json(route, { items: [inspiration, { ...inspiration, id: "inspiration-2", slug: "portrait-02", title: "柔和人像" }], total: 2, page: 1, pageSize: 2, pageCount: 1 });
    if (path.startsWith("/inspirations/")) return json(route, inspiration);
    if (path === "/generation/options") return json(route, { models: ["image-4.7"], ratios: ["1:1", "16:9"], resolutions: ["2K", "4K"], imageCount: { min: 1, max: 4 }, referenceImages: { max: 4, maxBytes: 10485760, mimeTypes: ["image/jpeg", "image/png", "image/webp"] }, costPerImage: 1, externalServicesMode: "mock" });
    if (path === "/generation/quota") return json(route, { total: 100, available: 80, reserved: 0, used: 20, remainingPercent: 80 });
    if (path === "/generation/sessions") return json(route, { items: [{ id: "session-1", title: "新的创作", thumbnailUrl: null, createdAt: now, updatedAt: now }] });
    if (path === "/generation/sessions/session-1") return json(route, { id: "session-1", title: "新的创作", draft: { prompt: "", model: "image-4.7", ratio: "1:1", resolution: "2K", imageCount: 1, referenceImageUrls: [] }, createdAt: now, updatedAt: now, tasks: [task] });
    if (path.startsWith("/generation/tasks/") && path.endsWith("/events")) return route.fulfill({ status: 200, contentType: "text/event-stream", body: ": keep-alive\n\n" });
    if (path.startsWith("/generation/tasks/")) return json(route, { ...task, results: [] });
    return json(route, {});
  });
}

export async function routeAdminApi(page: Page) {
  await page.route(apiUrl, async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, "");
    if (path === "/admin/auth/session") return json(route, { authenticated: true, user: { id: "admin-1", displayName: "运营管理员", phoneMasked: "188****0000", role: "admin", permissions: ["tasks:read", "inspirations:read", "inspirations:write"] } });
    if (path === "/admin/tasks") return json(route, { items: [task], total: 1, page: 1, pageSize: 20, pageCount: 1 });
    if (path === "/admin/tasks/reconciliation/runs") return json(route, { items: [{ id: "run-1", status: "completed", startedAt: now, completedAt: now, scannedUsers: 1, scannedTasks: 1, mismatchCount: 0, repairedCount: 0, errorMessage: null, findings: [] }] });
    if (path === "/admin/tasks/task-1") return json(route, { ...task, referenceImageUrls: [], errorCode: null, errorMessage: null, deadLetter: null, results: [] });
    if (path === "/admin/inspirations") return json(route, { items: [adminInspiration], total: 1, page: 1, pageSize: 20, pageCount: 1 });
    if (path === "/admin/inspirations/inspiration-1") return json(route, adminInspiration);
    return json(route, {});
  });
}

export async function auditDom(page: Page) {
  return page.evaluate(() => {
    const ids = [...document.querySelectorAll<HTMLElement>("[id]")].map((element) => element.id);
    const duplicateIds = ids.filter((id, index) => ids.indexOf(id) !== index);
    const missingTargets: string[] = [];
    document.querySelectorAll<HTMLLabelElement>("label").forEach((label) => {
      const target = label.htmlFor ? document.getElementById(label.htmlFor) : label.querySelector("input,select,textarea");
      if (!target) missingTargets.push(`label:${label.htmlFor || label.textContent?.trim() || "unknown"}`);
    });
    document.querySelectorAll<HTMLElement>("[aria-controls],[aria-labelledby]").forEach((element) => {
      for (const attribute of ["aria-controls", "aria-labelledby"]) {
        const value = element.getAttribute(attribute);
        if (value && !document.getElementById(value)) missingTargets.push(`${attribute}:${value}`);
      }
    });
    return { duplicates: duplicateIds, missingTargets, horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth + 1 };
  });
}

export function capturePageErrors(page: Page) {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}
