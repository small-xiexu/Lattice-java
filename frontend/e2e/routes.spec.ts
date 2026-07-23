import { expect, test, type Page } from "@playwright/test";

const ROUTES = [
  { path: "ask", title: "问答与研究", heading: "问答与研究" },
  { path: "library/sources", title: "资料源", heading: "资料源" },
  { path: "library/sources/2", title: "资料源详情" },
  { path: "library/articles", title: "知识文章", heading: "知识文章" },
  { path: "library/articles/sources--sources-finecontroller", title: "文章详情" },
  { path: "library/quality", title: "知识质量", heading: "知识质量" },
  { path: "activity", title: "处理中心", heading: "处理中心" },
  { path: "reviews", title: "人工审核", heading: "人工审核" },
  { path: "feedback", title: "结果反馈", heading: "结果反馈" },
  { path: "settings/models", title: "模型与绑定", heading: "模型与绑定" },
  { path: "settings/vector", title: "向量索引", heading: "向量索引" },
  { path: "settings/parsing", title: "文档解析", heading: "文档解析" },
  { path: "settings/retrieval", title: "检索参数", heading: "检索参数" },
  { path: "settings/maintenance", title: "系统维护", heading: "系统维护" },
  { path: "developer", title: "开发者接入", heading: "开发者接入" },
] as const;

for (const route of ROUTES) {
  test(`${route.path} renders ${route.title} without a browser exception`, async ({ page }) => {
    const browserErrors = collectBrowserErrors(page);
    await blockMutations(page);

    await page.goto(route.path);
    const main = page.getByRole("main");
    const heading = "heading" in route
      ? main.getByRole("heading", { level: 1, name: route.heading })
      : main.getByRole("heading", { level: 1 });
    await expect(heading).toBeVisible();
    await expect(page.getByRole("heading", { name: "页面暂时无法显示" })).toHaveCount(0);
    await expect.poll(async () => page.evaluate(() => (
      document.documentElement.scrollWidth > document.documentElement.clientWidth
    ))).toBe(false);
    expect(browserErrors).toEqual([]);
  });
}

function collectBrowserErrors(page: Page) {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
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
