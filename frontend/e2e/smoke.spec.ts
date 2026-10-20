import { expect, test } from '@playwright/test';

test('mock login → dashboard balance → logout', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();

  await page.getByLabel('Email or account number').fill('jane@demo.bank');
  await page.getByLabel('Password').fill('Demo@1234');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  await expect(page.getByTestId('balance')).toHaveText('$48,200.00');
  await expect(page.getByTestId('topbar-user')).toContainText('Jane Doe');

  await page.getByRole('link', { name: 'Security', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Security', exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => sessionStorage.getItem('onestopbank.token'))).toBeNull();

  await page.goto('/transactions');
  await expect(page).toHaveURL(/\/login$/);
});
