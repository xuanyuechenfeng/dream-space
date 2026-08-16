import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { routeAdminApi, auditDom, capturePageErrors } from "./support";

test.describe("admin regression matrix", () => {
  let pageErrors: string[];

  test.beforeEach(async ({ page }) => {
    test.skip(!test.info().project.name.startsWith("admin-"));
    pageErrors = capturePageErrors(page);
    await routeAdminApi(page);
  });

  test.afterEach(() => expect(pageErrors).toEqual([]));

  test("tasks list, drawer and DOM/accessibility gates", async ({ page }) => {
    await page.goto("/tasks");
    await expect(page.getByRole("heading", { name: "生成任务" })).toBeVisible();
    await expect(page.getByRole("row", { name: /自然光下的安静编辑肖像/ })).toBeVisible();
    const before = await auditDom(page);
    expect(before.duplicates).toEqual([]);
    expect(before.missingTargets).toEqual([]);
    expect(before.horizontalOverflow).toBe(false);
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter((item) => item.impact === "critical" || item.impact === "serious")).toEqual([]);
    await page.getByRole("button", { name: /查看任务/ }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByRole("button", { name: "关闭详情" })).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toBeHidden();
    await page.evaluate(() => window.scrollTo(0, 0));
    await expect(page).toHaveScreenshot(`${test.info().project.name}-tasks.png`);
  });

  test("inspiration editor and optimistic write controls", async ({ page }) => {
    await page.goto("/inspirations");
    await expect(page.getByRole("heading", { name: "灵感管理" })).toBeVisible();
    await page.getByRole("button", { name: /编辑 雨夜霓虹街景/ }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByLabel("slug")).toHaveValue("neon-city");
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toBeHidden();
    await page.evaluate(() => window.scrollTo(0, 0));
    await expect(page).toHaveScreenshot(`${test.info().project.name}-inspirations.png`);
  });
});
