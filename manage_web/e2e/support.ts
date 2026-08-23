import type { Page } from "@playwright/test";

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
