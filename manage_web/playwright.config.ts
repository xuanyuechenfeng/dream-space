import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  expect: { timeout: 5_000, toHaveScreenshot: { animations: "disabled", maxDiffPixelRatio: 0.02 } },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  snapshotPathTemplate: "{testDir}/__screenshots__/{projectName}/{arg}{ext}",
  use: { baseURL: "http://127.0.0.1:3101", trace: "retain-on-failure", video: "retain-on-failure", ...devices["Desktop Chrome"] },
  projects: [
    { name: "admin-desktop", testMatch: "admin.spec.ts", use: { baseURL: "http://127.0.0.1:3101", viewport: { width: 1440, height: 900 } } },
    { name: "admin-tablet", testMatch: "admin.spec.ts", use: { baseURL: "http://127.0.0.1:3101", viewport: { width: 800, height: 1024 } } },
    { name: "admin-mobile", testMatch: "admin.spec.ts", use: { baseURL: "http://127.0.0.1:3101", viewport: { width: 390, height: 844 }, isMobile: true } },
  ],
  webServer: process.env.RUN_REAL_E2E === "1"
    ? { command: "vite --host 127.0.0.1 --port 3101", url: "http://127.0.0.1:3101", reuseExistingServer: !process.env.CI, timeout: 120_000 }
    : undefined,
});
