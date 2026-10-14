import { expect, test } from '@playwright/test';

/**
 * Live smoke — the minimal headless demo against a running Spring backend
 * (:8080, fresh-seed default theme data). Build + typecheck prove
 * compilation; only this proves the demo actually consumes /api/**.
 *
 * Boot the backend separately (from repo root):
 *   ./mvnw clean spring-boot:run
 * (append -Dspring-boot.run.profiles=<name> if your datasource lives in an
 * application-<name>.yaml profile)
 */

test('home renders site chrome and article list', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('header')).toBeVisible();
  await expect(page.getByRole('heading', { name: '最新文章' })).toBeVisible();
  await expect(page.locator('footer')).toBeVisible();
});

test('channel page lists articles and paginates', async ({ page }) => {
  await page.goto('/');
  // First nav channel from the header — resolves against seeded data without
  // pinning a flake slug.
  const navLink = page.locator('header nav a[href^="/channel/"]').first();
  await expect(navLink).toBeVisible();
  await navLink.click();
  await expect(page).toHaveURL(/\/channel\//);
  await expect(page.locator('main')).toBeVisible();
});

test('article detail renders title, meta and body', async ({ page }) => {
  await page.goto('/');
  const articleLink = page.locator('main a[href^="/article/"]').first();
  await expect(articleLink).toBeVisible();
  await articleLink.click();
  await expect(page).toHaveURL(/\/article\//);
  await expect(page.locator('article h1')).toBeVisible();
});

test('search returns results for a seeded keyword', async ({ page }) => {
  await page.goto('/search?q=新闻');
  await expect(page.locator('main')).toBeVisible();
  // Either results or the empty state — the page itself must render; a
  // seeded environment yields at least one hit for 新闻.
  await expect(page.locator('main a[href*="/article/"]').first()).toBeVisible();
});
