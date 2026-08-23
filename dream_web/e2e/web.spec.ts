import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { auditDom, capturePageErrors } from "./support";

test.describe("web regression matrix", () => {
  test.skip(process.env.RUN_REAL_E2E !== "1", "Requires running API, Worker, database, queue, storage and real model providers");
  let pageErrors: string[];

  test.beforeEach(async ({ page }) => {
    test.skip(!test.info().project.name.startsWith("web-"));
    pageErrors = capturePageErrors(page);
  });

  test.afterEach(() => expect(pageErrors).toEqual([]));

  test("inspiration gallery supports locale/theme and responsive DOM gates", async ({ page }) => {
    await page.addInitScript(() => { localStorage.setItem("dream-space-language", "en"); localStorage.setItem("dream-space-theme", "dark"); });
    await page.goto("/dream_web/inspiration");
    await expect(page.getByRole("link", { name: /Explore|For you/ }).first()).toBeVisible();
    await expect(page.locator("html")).toHaveAttribute("lang", "en");
    const audit = await auditDom(page);
    expect(audit.duplicates).toEqual([]);
    expect(audit.missingTargets).toEqual([]);
    expect(audit.horizontalOverflow).toBe(false);
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter((item) => item.impact === "critical" || item.impact === "serious")).toEqual([]);
    await expect(page).toHaveScreenshot(`${test.info().project.name}-inspiration-dark-en.png`);
  });

  test("login validates agreement controls", async ({ page }) => {
    await page.goto("/dream_web/login");
    await expect(page.getByRole("heading", { name: /登录|Sign in/ })).toBeVisible();
    await expect(page.locator("input[type=checkbox]")).toHaveCount(1);
    await expect(page.getByRole("button", { name: /登录|Sign in/ }).last()).toBeDisabled();
    await expect(page).toHaveScreenshot(`${test.info().project.name}-login.png`);
  });

  test("generation workspace renders an authenticated empty or populated state", async ({ page }) => {
    await page.goto("/dream_web/generate");
    await expect(page.getByRole("main").first()).toBeVisible();
    await expect(page.getByPlaceholder(/描述画面和素材关系|Describe the image/)).toBeVisible();
    await expect(page.locator(".generation-loading, .timeline > .spin")).toHaveCount(0);
    const audit = await auditDom(page);
    expect(audit.duplicates).toEqual([]);
    expect(audit.horizontalOverflow).toBe(false);
    await expect(page).toHaveScreenshot(`${test.info().project.name}-generate.png`);
  });

  test("generation workspace closes settings when another composer control is selected", async ({ page }) => {
    await page.goto("/dream_web/generate");
    const composer = page.getByRole("region", { name: "Image generation" });
    const settings = composer.getByRole("button", { name: /生成参数|Generation settings/ });
    await expect(composer.locator(".composer-footer")).toBeVisible();
    await expect(settings).toBeVisible();
    await settings.click();
    await expect(page.locator(".parameter-popover")).toBeVisible();
    await composer.getByPlaceholder(/描述画面和素材关系|Describe the image/).click();
    await expect(page.locator(".parameter-popover")).toHaveCount(0);
  });

  test("inspiration composer shares generation controls and closes settings outside", async ({ page }) => {
    await page.goto("/dream_web/inspiration");
    await page.getByRole("link", { name: /做同款|Recreate/ }).first().click();
    await page.getByRole("button", { name: /做同款|Recreate/ }).click();
    const composer = page.getByRole("region", { name: "Image generation" });
    await expect(composer.locator(".composer-footer")).toBeVisible();
    await expect(composer.getByRole("button", { name: /生成参数|Generation settings/ })).toBeVisible();
    await expect(composer.getByPlaceholder(/描述画面和素材关系|Describe the image/)).toBeVisible();
    await composer.getByRole("button", { name: /生成参数|Generation settings/ }).click();
    await expect(page.locator(".parameter-popover")).toBeVisible();
    await composer.getByPlaceholder(/描述画面和素材关系|Describe the image/).click();
    await expect(page.locator(".parameter-popover")).toHaveCount(0);
  });
});
