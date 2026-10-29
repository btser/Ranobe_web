const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');
(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
        const base = process.env.APP_URL || 'http://localhost:8081';
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        const home = await page.goto(base);
        assert.equal(home.status(), 200, 'Home response');
        const links = await page.locator('.novel-card').evaluateAll(nodes =>
            nodes.map(node => ({ href: node.getAttribute('href').replace(/;jsessionid=[^/?#]+/gi, ''), title: node.querySelector('.name').textContent.trim() })));
        if (links.length) {
            const search = await page.goto(base + '/?q=' + encodeURIComponent(links[0].title) + '&sort=title');
            assert.equal(search.status(), 200);
            assert(await page.locator('.novel-card').count() > 0, 'Exact title search returned no books');
        }
        const advanced = await page.goto(base + '/tim-kiem');
        assert.equal(advanced.status(), 200, 'Advanced search page');
        const categories = await page.locator('input[name="categoryId"]').evaluateAll(nodes =>
            nodes.map(node => node.value).filter(Boolean));
        if (categories.length) {
            const filtered = await page.goto(base + '/tim-kiem?categoryId=' + encodeURIComponent(categories[0]) + '&sort=oldest');
            assert.equal(filtered.status(), 200, 'Category filter query');
            assert(await page.locator('input[name="categoryId"][value="' + categories[0] + '"]').isChecked());
        }
        const literalSearch = await page.goto(base + '/?q=%25_!&sort=updated');
        assert.equal(literalSearch.status(), 200, 'Literal wildcard query');
        for (const field of ['author', 'artist', 'uploader']) {
            const response = await page.goto(base + '/tim-kiem?' + field + '=no-match-' + Date.now());
            assert.equal(response.status(), 200, field + ' query');
            assert.equal(await page.locator('.novel-card').count(), 0, field + ' filter ignored');
        }
        let readerChecked = false;
        for (const link of links.slice(0, 5)) {
            const detail = await page.goto(base + link.href);
            assert.equal(detail.status(), 200);
            const metadata = await page.locator('.line').evaluateAll(nodes => Object.fromEntries(nodes.map(node =>
                [(node.querySelector('strong')?.textContent || '').trim(), (node.querySelector('span')?.textContent || '').trim()])));
            const positive = { author: metadata['Tác giả:'], artist: metadata['Họa sĩ:'], uploader: metadata['Người đăng:'] };
            for (const [field, value] of Object.entries(positive)) {
                if (!value || ['Chưa cập nhật', 'Ẩn danh'].includes(value)) continue;
                const response = await page.goto(base + '/tim-kiem?' + field + '=' + encodeURIComponent(value));
                assert.equal(response.status(), 200, field + ' positive query');
                const found = await page.locator('.novel-card').evaluateAll(nodes => nodes.map(node => node.getAttribute('href').replace(/;jsessionid=[^/?#]+/gi, '')));
                assert(found.includes(link.href), field + ' did not find expected book: ' + JSON.stringify({ value, expected: link.href, found }));
                assert.equal(await page.locator('[name="' + field + '"]').inputValue(), value);
                if (field === 'author' && !link.title.toLocaleLowerCase().includes(value.toLocaleLowerCase())) {
                    await page.goto(base + '/?q=' + encodeURIComponent(value));
                    const quick = await page.locator('.novel-card').evaluateAll(nodes => nodes.map(node => node.getAttribute('href').replace(/;jsessionid=[^/?#]+/gi, '')));
                    assert(!quick.includes(link.href), 'Quick search incorrectly searches author');
                }
            }
            await page.goto(base + link.href);
            const chapterLink = page.locator('.chapter-list [data-chapter-id]').first();
            if (!await chapterLink.count()) continue;
            const href = await chapterLink.getAttribute('href');
            const response = await page.goto(base + href);
            assert.equal(response.status(), 200, 'Chapter response');
            await page.locator('#readerContent').waitFor();
            await page.getByRole('button', { name: 'Mục lục', exact: true }).click();
            assert(await page.locator('.toc-list a').count() > 0);
            await page.keyboard.press('Escape');
            assert(!await page.locator('#readerToc').isVisible());
            assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
            await page.screenshot({ path: path.resolve('target/ui-checks/live-reader-mobile.png') });
            readerChecked = true;
            break;
        }
        await page.goto(base);
        assert.equal(await page.locator('select').count(), 0);
        await page.screenshot({ path: path.resolve('target/ui-checks/live-home-mobile.png'), fullPage: true });
        assert.deepEqual(errors, []);
        console.log(JSON.stringify({ liveHome: 'PASS', titleOnlySearch: 'PASS', advancedFields: 'PASS', categories: 'PASS', publicBooksOnFirstPage: links.length, reader: readerChecked ? 'PASS' : 'No public chapter available' }));
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
