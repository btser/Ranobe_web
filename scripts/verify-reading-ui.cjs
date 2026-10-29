const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.webp': 'image/webp' };
const output = path.join(root, 'target', 'ui-checks');
fs.mkdirSync(output, { recursive: true });
const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const relative = url.pathname === '/' ? 'index.html' : url.pathname === '/tim-kiem'
        ? 'search.html' : decodeURIComponent(url.pathname).slice(1);
    const base = relative.endsWith('.html') ? path.join(root, 'target', 'ui-preview') : path.join(root, 'src', 'main', 'resources', 'static');
    const file = path.resolve(base, relative);
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
        res.writeHead(404); return res.end('Not found');
    }
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
});
let browser;
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = 'http://127.0.0.1:' + server.address().port;
    browser = await chromium.launch({ headless: true, channel: 'msedge' });
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage();
    page.setDefaultTimeout(10000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('dialog', dialog => dialog.accept());
    const historyKey = 'ranobe:reading:v1:product:101';
    const draftKey = 'ranobe:draft:v1:user:7:chapter:1';

    for (const width of [320, 390, 1280]) {
        await page.setViewportSize({ width, height: 844 });
        for (const name of ['index', 'search', 'chapter-detail', 'chapter-edit', 'product-detail']) {
            await page.goto(base + '/' + name + '.html');
            assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), name + ' overflows at ' + width);
        }
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(base + '/index.html');
    await page.screenshot({ path: path.join(output, 'home-mobile.png'), fullPage: true });
    await page.getByRole('searchbox').fill('mưa');
    assert.equal(await page.locator('select').count(), 0, 'Home must only have quick title search');
    await page.getByRole('button', { name: 'Tìm truyện' }).click();
    assert(new URL(page.url()).searchParams.get('q') === 'mưa');
    await page.getByRole('link', { name: 'Tìm kiếm nâng cao' }).click();
    await page.getByLabel('Tên truyện', { exact: true }).fill('mưa');
    await page.getByLabel('Tác giả', { exact: true }).fill('Tác giả mẫu');
    await page.getByLabel('Họa sĩ', { exact: true }).fill('Họa sĩ mẫu');
    await page.getByLabel('Người đăng', { exact: true }).fill('Tên hiển thị mẫu');
    await page.locator('input[name="categoryId"][value="1"]').check();
    await page.getByLabel('Sắp xếp', { exact: true }).selectOption('title');
    await page.getByRole('button', { name: 'Tìm truyện' }).click();
    assert(new URL(page.url()).searchParams.get('q') === 'mưa');
    assert(new URL(page.url()).searchParams.get('categoryId') === '1');
    assert(new URL(page.url()).pathname === '/tim-kiem');
    assert(new URL(page.url()).searchParams.get('author') === 'Tác giả mẫu');
    assert(new URL(page.url()).searchParams.get('artist') === 'Họa sĩ mẫu');
    assert(new URL(page.url()).searchParams.get('uploader') === 'Tên hiển thị mẫu');
    await page.screenshot({ path: path.join(output, 'advanced-search-mobile.png'), fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.screenshot({ path: path.join(output, 'advanced-search-desktop.png'), fullPage: true });
    await page.goto(base + '/index.html');
    const searchRect = await page.locator('.quick-search-wrap').boundingBox();
    const headingRect = await page.locator('.section-titles').boundingBox();
    assert(searchRect.x > headingRect.x + headingRect.width, 'Quick search must be right aligned');
    await page.screenshot({ path: path.join(output, 'home-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });

    await page.goto(base + '/chapter-detail.html');
    await page.waitForFunction(key => !!localStorage.getItem(key), historyKey);
    await page.getByRole('button', { name: 'Tùy chỉnh trang đọc' }).click();
    await page.locator('#fontSize').evaluate(el => { el.value = '24'; el.dispatchEvent(new Event('input')); });
    await page.locator('#lineHeight').evaluate(el => { el.value = '2.1'; el.dispatchEvent(new Event('input')); });
    await page.getByRole('button', { name: 'Nền sáng' }).click();
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#readerSettings').isVisible(), false);
    assert.equal(await page.locator('#readerContent').evaluate(el => getComputedStyle(el).fontSize), '24px');
    await page.evaluate(() => scrollTo(0, 1900));
    await page.waitForTimeout(650);
    const saved = await page.evaluate(key => JSON.parse(localStorage.getItem(key)), historyKey);
    assert(saved.anchorIndex > 0);
    await page.reload();
    await page.waitForFunction(() => scrollY > 1000);
    assert(Math.abs((await page.evaluate(() => scrollY)) - 1900) < 100, 'Reading position not restored');
    assert.equal(await page.locator('#readerContent').evaluate(el => getComputedStyle(el).fontSize), '24px');
    await page.getByRole('button', { name: 'Mục lục', exact: true }).click();
    await page.getByRole('searchbox').fill('chuong 28');
    assert.equal(await page.locator('.toc-list li:visible').count(), 1);
    await page.screenshot({ path: path.join(output, 'toc-mobile.png') });
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#readerToc').isVisible(), false);
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({ path: path.join(output, 'reader-mobile.png') });
    await page.goto(base + '/product-detail.html');
    assert.equal(await page.locator('#continueReading').textContent(), 'Đọc tiếp');
    await page.evaluate(key => localStorage.setItem(key, JSON.stringify({ chapterId: 999, path: 'https://invalid.example' })), historyKey);
    await page.reload();
    assert.equal((await page.locator('#continueReading').textContent()).trim(), 'Đọc từ đầu');

    await page.goto(base + '/chapter-edit.html');
    await page.locator('#ceTitle').fill('Bản nháp của tôi');
    await page.locator('#ceBody').evaluate(el => { el.innerHTML = '<p>Nội dung chưa công bố.</p>'; el.dispatchEvent(new Event('input', { bubbles: true })); });
    await page.waitForFunction(key => localStorage.getItem(key)?.includes('Nội dung chưa công bố'), draftKey);
    await page.reload();
    await page.locator('#restoreDraft').click();
    assert.equal(await page.locator('#ceTitle').inputValue(), 'Bản nháp của tôi');
    assert((await page.locator('#ceBody').textContent()).includes('chưa công bố'));
    await page.waitForFunction(() => document.getElementById('draftStatus').textContent.startsWith('Đã lưu'));
    await page.screenshot({ path: path.join(output, 'editor-mobile.png'), fullPage: true });

    const second = await context.newPage();
    second.on('dialog', dialog => dialog.accept());
    await second.goto(base + '/chapter-edit.html');
    await second.locator('#restoreDraft').click();
    await page.locator('#ceTitle').fill('Thay đổi ở cửa sổ một');
    await page.waitForFunction(key => localStorage.getItem(key)?.includes('cửa sổ một'), draftKey);
    await second.waitForFunction(() => !document.getElementById('draftConflict').hidden);
    await second.close();

    await page.route('**/truyen/**/edit', async route => {
        if (route.request().method() !== 'POST') return route.continue();
        await route.fulfill({ status: 500, contentType: 'text/html', body: '<html><body>Lưu thất bại</body></html>' });
    });
    await page.locator('#ceSubmit').click();
    await page.waitForURL('**/truyen/**/edit');
    assert(await page.evaluate(key => !!localStorage.getItem(key), draftKey), 'Draft removed after failed submit');
    await page.unroute('**/truyen/**/edit');
    await page.goto(base + '/chapter-edit.html');
    await page.locator('#restoreDraft').click();
    await page.route('**/truyen/**/edit', async route => {
        if (route.request().method() !== 'POST') return route.continue();
        const revision = new URLSearchParams(route.request().postData()).get('draftRevision');
        assert(revision, 'Submit has no draft revision');
        await route.fulfill({ contentType: 'text/html', body: '<html><body><div id="draftSaved" data-draft-key="user:7:chapter:1" data-draft-revision="' + revision + '"></div><script src="/js/editor-drafts.js"></script></body></html>' });
    });
    await page.locator('#ceSubmit').click();
    await page.waitForURL('**/truyen/**/edit');
    await page.waitForFunction(key => !localStorage.getItem(key), draftKey);

    const blocked = await browser.newContext({ viewport: { width: 390, height: 844 } });
    await blocked.addInitScript(() => { Storage.prototype.setItem = function () { throw new Error('Blocked storage'); }; });
    const blockedPage = await blocked.newPage();
    await blockedPage.goto(base + '/chapter-detail.html');
    await blockedPage.waitForFunction(() => !document.getElementById('readingNotice').hidden);
    await blocked.close();

    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto(base + '/chapter-detail.html');
    await page.screenshot({ path: path.join(output, 'reader-desktop.png') });
    assert.deepEqual(errors, [], 'Browser script errors');
    console.log('PASS: responsive layouts; filters; preferences; reading restore; table of contents; resume validation; draft recovery, cross-tab conflict, failed/successful save; blocked storage.');
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
    if (browser) await browser.close();
    server.close();
});
