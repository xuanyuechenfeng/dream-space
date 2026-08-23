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
  use: { baseURL: "http://127.0.0.1:3100", trace: "retain-on-failure", video: "retain-on-failure", ...devices["Desktop Chrome"] },
  projects: [
    { name: "web-desktop", testMatch: "web.spec.ts", use: { baseURL: "http://127.0.0.1:3100", viewport: { width: 1440, height: 900 } } },
    { name: "web-tablet", testMatch: "web.spec.ts", use: { baseURL: "http://127.0.0.1:3100", viewport: { width: 1024, height: 768 } } },
    { name: "web-tablet-portrait", testMatch: "web.spec.ts", use: { baseURL: "http://127.0.0.1:3100", viewport: { width: 800, height: 1024 } } },
    { name: "web-mobile", testMatch: "web.spec.ts", use: { baseURL: "http://127.0.0.1:3100", viewport: { width: 390, height: 844 }, isMobile: true } },
  ],
  webServer: process.env.RUN_REAL_E2E === "1"
    ? { command: "vite --host 127.0.0.1 --port 3100", url: "http://127.0.0.1:3100", reuseExistingServer: !process.env.CI, timeout: 120_000 }
    : undefined,
});
