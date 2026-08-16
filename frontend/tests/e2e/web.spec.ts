import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { routeWebApi, auditDom, capturePageErrors } from "./support";

test.describe("web regression matrix", () => {
  let pageErrors: string[];

  test.beforeEach(async ({ page }) => {
    test.skip(!test.info().project.name.startsWith("web-"));
    pageErrors = capturePageErrors(page);
    await routeWebApi(page);
  });

  test.afterEach(() => expect(pageErrors).toEqual([]));

  test("inspiration gallery supports locale/theme and responsive DOM gates", async ({ page }) => {
    await page.addInitScript(() => { localStorage.setItem("dream-space-language", "en"); localStorage.setItem("dream-space-theme", "dark"); });
    await page.goto("/inspiration");
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
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: /登录|Sign in/ })).toBeVisible();
    await expect(page.locator("input[type=checkbox]")).toHaveCount(1);
    await expect(page.getByRole("button", { name: /登录|Sign in/ }).last()).toBeDisabled();
    await expect(page).toHaveScreenshot(`${test.info().project.name}-login.png`);
  });

  test("generation workspace renders an authenticated empty or populated state", async ({ page }) => {
    await page.goto("/generate");
    await expect(page.getByRole("main").first()).toBeVisible();
    await expect(page.getByPlaceholder(/描述你想生成|Describe the image/)).toBeVisible();
    await expect(page.locator(".generation-loading, .timeline > .spin")).toHaveCount(0);
    const audit = await auditDom(page);
    expect(audit.duplicates).toEqual([]);
    expect(audit.horizontalOverflow).toBe(false);
    await expect(page).toHaveScreenshot(`${test.info().project.name}-generate.png`);
  });
});
