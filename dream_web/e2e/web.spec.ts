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

  test("generation workspace renders a centered new-session state", async ({ page }) => {
    await page.goto("/dream_web/generate");
    await expect(page.getByRole("main").first()).toBeVisible();
    const prompt = page.getByPlaceholder(/描述画面和素材关系|Describe the image/);
    await expect(page.getByRole("heading", { name: /开始一段新的创作|Start a new creation/ })).toBeVisible();
    await expect(prompt).toBeVisible();
    await expect(page.locator(".generation-loading, .timeline > .spin")).toHaveCount(0);
    await expect(page.locator(".generation-main .task")).toHaveCount(0);
    const starters = page.locator(".starter-prompt");
    await expect(starters).toHaveCount(3);
    for (const starter of await starters.all()) await expect(starter).toBeVisible();
    const geometry = await page.evaluate(() => {
      const empty = document.querySelector<HTMLElement>(".empty-session")?.getBoundingClientRect();
      const composer = document.querySelector<HTMLElement>(".generation-main > .composer")?.getBoundingClientRect();
      const footer = document.querySelector<HTMLElement>(".composer-footer");
      const submit = document.querySelector<HTMLElement>(".submit-btn")?.getBoundingClientRect();
      return {
        centerDelta: empty && composer ? Math.abs(empty.x + empty.width / 2 - composer.x - composer.width / 2) : Number.POSITIVE_INFINITY,
        overlaps: empty && composer ? empty.bottom > composer.top : true,
        footerOverflows: footer ? footer.scrollWidth > footer.clientWidth : true,
        submitOutsideViewport: submit ? submit.left < 0 || submit.right > document.documentElement.clientWidth : true,
      };
    });
    expect(geometry.centerDelta).toBeLessThanOrEqual(1);
    expect(geometry.overlaps).toBe(false);
    expect(geometry.footerOverflows).toBe(false);
    expect(geometry.submitOutsideViewport).toBe(false);
    const audit = await auditDom(page);
    expect(audit.duplicates).toEqual([]);
    expect(audit.horizontalOverflow).toBe(false);
    await expect(page).toHaveScreenshot(`${test.info().project.name}-generate.png`);
    const starterText = (await starters.first().innerText()).trim();
    await starters.first().click();
    await expect(prompt).toHaveValue(starterText);
  });

  test("generation new-session layout keeps English controls accessible on narrow screens", async ({ page }) => {
    const sizes = test.info().project.name === "web-tablet-portrait"
      ? [{ width: 800, height: 1024 }, { width: 768, height: 1024 }]
      : test.info().project.name === "web-mobile"
        ? [{ width: 320, height: 568 }, { width: 844, height: 390 }]
        : [];
    test.skip(sizes.length === 0, "Runs only on the narrow responsive projects");
    await page.addInitScript(() => localStorage.setItem("dream-space-language", "en"));

    for (const size of sizes) {
      await page.setViewportSize(size);
      await page.goto("/dream_web/generate");
      await expect(page.getByRole("heading", { name: "Start a new creation" })).toBeVisible();
      for (const starter of await page.locator(".starter-prompt").all()) await expect(starter).toBeVisible();
      const geometry = await page.evaluate(() => {
        const empty = document.querySelector<HTMLElement>(".empty-session")?.getBoundingClientRect();
        const composer = document.querySelector<HTMLElement>(".generation-main > .composer")?.getBoundingClientRect();
        const footer = document.querySelector<HTMLElement>(".composer-footer");
        const submit = document.querySelector<HTMLElement>(".submit-btn")?.getBoundingClientRect();
        return {
          centerDelta: empty && composer ? Math.abs(empty.x + empty.width / 2 - composer.x - composer.width / 2) : Number.POSITIVE_INFINITY,
          overlaps: empty && composer ? empty.bottom > composer.top : true,
          footerOverflows: footer ? footer.scrollWidth > footer.clientWidth : true,
          submitOutsideViewport: submit ? submit.left < 0 || submit.right > document.documentElement.clientWidth : true,
          pageOverflows: document.documentElement.scrollWidth > window.innerWidth + 1,
        };
      });
      expect(geometry.centerDelta).toBeLessThanOrEqual(1);
      expect(geometry.overlaps).toBe(false);
      expect(geometry.footerOverflows).toBe(false);
      expect(geometry.submitOutsideViewport).toBe(false);
      expect(geometry.pageOverflows).toBe(false);
    }
  });

  test("generation mobile history isolates the workspace and restores focus", async ({ page }) => {
    test.skip(test.info().project.name !== "web-mobile", "Runs only on the mobile project");
    await page.goto("/dream_web/generate");
    const toggle = page.getByRole("button", { name: /历史会话|Conversation history/ });
    await toggle.click();
    const dialog = page.getByRole("dialog", { name: /历史会话|Conversation history/ });
    await expect(dialog).toBeVisible();
    await expect(page.locator(".generation-main")).toHaveAttribute("aria-hidden", "true");
    expect(await page.locator(".generation-main").evaluate(element => (element as HTMLElement).inert)).toBe(true);
    expect(await dialog.evaluate(element => element.contains(document.activeElement))).toBe(true);
    await page.keyboard.press("Escape");
    await expect(dialog).toHaveCount(0);
    await expect(toggle).toBeFocused();
  });

  test("generation prompt focus does not show recent prompts", async ({ page }) => {
    test.skip(test.info().project.name !== "web-mobile", "Runs only on the mobile project");
    await page.addInitScript(() => localStorage.setItem("dream-space-prompt-history", JSON.stringify(["Keyboard prompt"])));
    await page.goto("/dream_web/generate");
    const prompt = page.getByPlaceholder(/描述画面和素材关系|Describe the image/);
    await prompt.focus();
    await expect(page.locator(".prompt-history")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Keyboard prompt" })).toHaveCount(0);
    await expect(prompt).toBeFocused();
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
