import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const ACCESSIBILITY_ROUTES = [
  "ask",
  "library/sources",
  "library/sources/2",
  "library/articles/sources--sources-finecontroller",
  "library/quality",
  "activity",
  "reviews",
  "settings/maintenance",
  "developer",
] as const;

const BREAKPOINTS = [1536, 1440, 1280, 1024, 768, 375] as const;

for (const path of ACCESSIBILITY_ROUTES) {
  test(`${path} has no blocking axe or heading violations`, async ({ page }) => {
    await blockMutations(page);
    await page.goto(path);

    const main = page.getByRole("main");
    await expect(main.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(main.locator("h1")).toHaveCount(1);
    expect(await hasHeadingLevelJump(main)).toBe(false);
    await expect(page.getByRole("link", { name: "跳到主内容" })).toHaveAttribute(
      "href",
      "#main-content",
    );
    await expect(page.locator("#global-announcer")).toHaveAttribute("aria-live", "polite");
    expect(await sortableHeadersWithoutState(main)).toEqual([]);

    const results = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();
    const blockingViolations = results.violations
      .filter((violation) => violation.impact === "critical" || violation.impact === "serious")
      .map((violation) => ({
        id: violation.id,
        impact: violation.impact,
        targets: violation.nodes.map((node) => node.target.join(" ")),
      }));
    expect(blockingViolations).toEqual([]);
  });
}

test("desktop keyboard path exposes focus and restores command palette trigger", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "chromium-desktop");
  await blockMutations(page);
  await page.goto("ask");
  await expect(page.getByRole("heading", { level: 1, name: "问答与研究" })).toBeVisible();

  await page.evaluate(() => {
    document.body.tabIndex = -1;
    document.body.focus();
  });
  await page.keyboard.press("Tab");
  const skipLink = page.getByRole("link", { name: "跳到主内容" });
  await expect(skipLink).toBeFocused();
  await expect(skipLink).toBeVisible();
  await page.evaluate(() => document.body.removeAttribute("tabindex"));
  await page.keyboard.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();

  const searchTrigger = page.getByRole("button", { name: "搜索" });
  await searchTrigger.focus();
  expect(await visibleOutline(searchTrigger)).toBe(true);
  await page.keyboard.press(process.platform === "darwin" ? "Meta+K" : "Control+K");
  const searchInput = page.getByRole("textbox", { name: "搜索页面" });
  await expect(searchInput).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(page.getByRole("button", { name: "开发者接入" })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(searchTrigger).toBeFocused();
});

test("mobile navigation restores focus after Escape", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "chromium-mobile");
  await blockMutations(page);
  await page.goto("ask");

  const menuTrigger = page.getByRole("button", { name: "打开导航" });
  await menuTrigger.click();
  await expect(page.getByRole("button", { name: "关闭导航" })).toBeFocused();
  await expect(page.getByLabel("应用导航")).not.toHaveAttribute("aria-hidden", "true");
  await page.keyboard.press("Escape");
  await expect(menuTrigger).toBeFocused();
  await expect(page.getByLabel("应用导航")).toHaveAttribute("aria-hidden", "true");
});

test("six responsive breakpoints preserve layout boundaries", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "chromium-desktop");
  await blockMutations(page);

  for (const width of BREAKPOINTS) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("ask");
    await expect(page.getByRole("heading", { level: 1, name: "问答与研究" })).toBeVisible();
    expect(await hasPageOverflow(page), `viewport ${width}px has horizontal overflow`).toBe(false);

    const layout = page.locator(".app-layout");
    const sidebar = page.getByLabel("应用导航");
    const menuTrigger = page.getByRole("button", { name: "打开导航" });
    if (width >= 1536) {
      await expect(layout).not.toHaveClass(/is-collapsed/);
      await expect(sidebar).toBeVisible();
      await expect(menuTrigger).toBeHidden();
    } else if (width >= 1280) {
      await expect(layout).toHaveClass(/is-collapsed/);
      await expect(sidebar).toBeVisible();
      await expect(menuTrigger).toBeHidden();
    } else {
      await expect(sidebar).toHaveAttribute("aria-hidden", "true");
      await expect(sidebar).toHaveAttribute("inert", "");
      await expect(menuTrigger).toBeVisible();
    }
  }
});

test("200 percent equivalent reflow keeps ask controls readable", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "chromium-desktop");
  await blockMutations(page);

  for (const width of [768, 375] as const) {
    await page.setViewportSize({ width, height: width === 768 ? 480 : 406 });
    await page.goto("ask");
    const question = page.getByRole("textbox", { name: "问题" });
    await expect(question).toBeVisible();
    await expect(page.getByRole("button", { name: "提问" })).toBeVisible();
    expect(await hasPageOverflow(page), `200% equivalent ${width}px reflow overflowed`).toBe(false);
    if (width === 375) {
      expect(await fontSize(question)).toBeGreaterThanOrEqual(16);
      expect(await splitViewSafeAreaRules(page)).toEqual({ bottom: true, top: true });
    }
  }
});

test("forced colors retain visible focus and active navigation", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "chromium-desktop");
  await page.emulateMedia({ forcedColors: "active" });
  await blockMutations(page);
  await page.goto("ask");
  await expect(page.getByRole("heading", { level: 1, name: "问答与研究" })).toBeVisible();

  const searchTrigger = page.getByRole("button", { name: "搜索" });
  await searchTrigger.focus();
  await expect(searchTrigger).toBeFocused();
  expect(await visibleOutline(searchTrigger)).toBe(true);
  expect(await visibleOutline(page.getByRole("link", { name: "问答与研究" }))).toBe(true);
  expect(await hasPageOverflow(page)).toBe(false);
});

async function hasHeadingLevelJump(main: ReturnType<Page["getByRole"]>) {
  const levels = await main.locator("h1, h2, h3, h4, h5, h6").evaluateAll((headings) =>
    headings
      .filter((heading) => (heading as HTMLElement).offsetParent !== null)
      .map((heading) => Number(heading.tagName.slice(1))),
  );
  return levels.some((level, index) => index > 0 && level - levels[index - 1] > 1);
}

async function sortableHeadersWithoutState(main: ReturnType<Page["getByRole"]>) {
  return main.locator("th:has(button)").evaluateAll((headers) =>
    headers
      .filter((header) => !/^(ascending|descending|none|other)$/.test(header.getAttribute("aria-sort") ?? ""))
      .map((header) => header.textContent?.trim() ?? "unnamed header"),
  );
}

async function splitViewSafeAreaRules(page: Page) {
  return page.evaluate(() => {
    const declarations = Array.from(document.styleSheets).flatMap((sheet) => {
      try {
        return collectStyleDeclarations(sheet.cssRules);
      } catch {
        return [];
      }
    });
    return {
      bottom: declarations.some(({ selector, style }) =>
        selector.includes(".split-view-secondary-body")
        && style.includes("safe-area-inset-bottom")),
      top: declarations.some(({ selector, style }) =>
        selector.includes(".split-view-secondary-header")
        && style.includes("safe-area-inset-top")),
    };

    function collectStyleDeclarations(rules: CSSRuleList): Array<{ selector: string; style: string }> {
      return Array.from(rules).flatMap((rule) => {
        if (rule instanceof CSSStyleRule) {
          return [{ selector: rule.selectorText, style: rule.style.cssText }];
        }
        if ("cssRules" in rule) {
          return collectStyleDeclarations((rule as CSSMediaRule).cssRules);
        }
        return [];
      });
    }
  });
}

async function visibleOutline(locator: ReturnType<Page["locator"]>) {
  return locator.evaluate((element) => {
    const style = getComputedStyle(element);
    return style.outlineStyle !== "none" && Number.parseFloat(style.outlineWidth) >= 2;
  });
}

async function fontSize(locator: ReturnType<Page["locator"]>) {
  return locator.evaluate((element) => Number.parseFloat(getComputedStyle(element).fontSize));
}

async function hasPageOverflow(page: Page) {
  return page.evaluate(() =>
    document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
}

async function blockMutations(page: Page) {
  await page.route("**/*", async (route) => {
    const method = route.request().method();
    if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
      await route.abort("blockedbyclient");
      return;
    }
    await route.continue();
  });
}
