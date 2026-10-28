(function () {
    'use strict';
    const reader = document.getElementById('reader');
    if (!reader || !window.RanobeReading) return;
    const content = document.getElementById('readerContent');
    const productId = reader.dataset.productId;
    const chapterId = Number(reader.dataset.chapterId);
    const notice = document.getElementById('readingNotice');
    const defaults = { fontSize: 20, lineHeight: 1.8, theme: 'dark' };
    const clamp = (v, min, max, fallback) => Number.isFinite(Number(v))
        ? Math.min(max, Math.max(min, Number(v))) : fallback;
    let preferences = RanobeReading.preferences() || defaults;
    let ready = false;
    let saveTimer;
    let interacted = false;
    const previous = RanobeReading.history(productId);
    const readingLine = () => Math.min(100, innerHeight * 0.18);
    const blocks = () => Array.from(content.querySelectorAll('p, img, blockquote'))
        .filter(node => node.getBoundingClientRect().height > 0);
    const maxScroll = () => Math.max(0, document.documentElement.scrollHeight - innerHeight);

    function storageNotice() {
        notice.hidden = false;
        notice.textContent = 'Trình duyệt hiện không cho phép lưu vị trí đọc hoặc tùy chỉnh.';
    }
    function position() {
        const elements = blocks();
        let index = elements.findIndex(node => node.getBoundingClientRect().bottom > readingLine());
        if (index < 0) index = Math.max(0, elements.length - 1);
        const rect = elements[index] && elements[index].getBoundingClientRect();
        return {
            chapterId, title: document.querySelector('.reader-head h1').textContent,
            path: location.pathname, atStart: scrollY < 8,
            anchorIndex: index,
            anchorProgress: rect ? clamp((readingLine() - rect.top) / rect.height, 0, 1, 0) : 0,
            ratio: maxScroll() > 0 ? scrollY / maxScroll() : 0,
            updatedAt: Date.now()
        };
    }
    function restore(saved) {
        if (!saved || saved.atStart) { scrollTo(0, 0); return; }
        const elements = blocks();
        const index = Number.isInteger(saved.anchorIndex) ? saved.anchorIndex : -1;
        if (index >= 0 && index < elements.length) {
            const rect = elements[index].getBoundingClientRect();
            scrollTo(0, scrollY + rect.top + rect.height * clamp(saved.anchorProgress, 0, 1, 0) - readingLine());
        } else {
            scrollTo(0, maxScroll() * clamp(saved.ratio, 0, 1, 0));
        }
    }
    function progress() {
        const percent = Math.round(maxScroll() ? scrollY / maxScroll() * 100 : 0);
        document.getElementById('readProgress').style.width = percent + '%';
        document.querySelector('.read-progress').setAttribute('aria-valuenow', String(percent));
    }
    function save() {
        if (!ready) return;
        if (!RanobeReading.save(productId, position())) storageNotice();
    }
    function applyPreferences(keepPlace) {
        const saved = keepPlace ? position() : null;
        preferences = {
            fontSize: clamp(preferences.fontSize, 14, 30, 20),
            lineHeight: clamp(preferences.lineHeight, 1.4, 2.4, 1.8),
            theme: ['dark', 'light', 'warm'].includes(preferences.theme) ? preferences.theme : 'dark'
        };
        document.documentElement.dataset.readerTheme = preferences.theme;
        document.documentElement.style.setProperty('--read-size', preferences.fontSize + 'px');
        document.documentElement.style.setProperty('--read-line', preferences.lineHeight);
        document.getElementById('fontSize').value = preferences.fontSize;
        document.getElementById('lineHeight').value = preferences.lineHeight;
        document.getElementById('fontSizeValue').value = preferences.fontSize + ' px';
        document.getElementById('lineHeightValue').value = preferences.lineHeight.toFixed(1);
        document.querySelectorAll('[data-theme]').forEach(button =>
            button.setAttribute('aria-pressed', String(button.dataset.theme === preferences.theme)));
        if (saved) { restore(saved); progress(); }
    }
    function updatePreferences(update) {
        preferences = { ...preferences, ...update };
        applyPreferences(true);
        if (!RanobeReading.setPreferences(preferences)) storageNotice();
        save();
    }
    applyPreferences(false);
    document.getElementById('fontSize').addEventListener('input', event =>
        updatePreferences({ fontSize: Number(event.target.value) }));
    document.getElementById('lineHeight').addEventListener('input', event =>
        updatePreferences({ lineHeight: Number(event.target.value) }));
    document.querySelectorAll('[data-theme]').forEach(button =>
        button.addEventListener('click', () => updatePreferences({ theme: button.dataset.theme })));
    document.getElementById('resetReader').addEventListener('click', () => updatePreferences(defaults));

    document.querySelectorAll('[data-open-dialog]').forEach(button => button.addEventListener('click', () => {
        const dialog = document.getElementById(button.dataset.openDialog);
        dialog.showModal();
        if (dialog.id === 'readerToc') {
            const current = dialog.querySelector('[aria-current="page"]');
            if (current) current.scrollIntoView({ block: 'nearest' });
            document.getElementById('tocSearch').focus({ preventScroll: true });
        }
    }));
    document.querySelectorAll('[data-close-dialog]').forEach(button =>
        button.addEventListener('click', () => button.closest('dialog').close()));
    document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            event.preventDefault(); event.stopPropagation(); dialog.close();
        }
    }));
    document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('click', event => {
        if (event.target !== dialog) return;
        const rect = dialog.getBoundingClientRect();
        if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
    }));
    const normalize = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    document.getElementById('tocSearch').addEventListener('input', event => {
        const keyword = normalize(event.target.value.trim());
        let count = 0;
        document.querySelectorAll('.toc-list li').forEach(item => {
            item.hidden = !normalize(item.textContent).includes(keyword);
            if (!item.hidden) count++;
        });
        document.getElementById('tocEmpty').hidden = count > 0;
    });

    ['wheel', 'touchstart', 'pointerdown', 'keydown'].forEach(name => window.addEventListener(name, () => {
        interacted = true;
        if (!ready) ready = true;
    }, { passive: true }));
    window.addEventListener('scroll', () => {
        progress();
        clearTimeout(saveTimer);
        saveTimer = setTimeout(save, 400);
    }, { passive: true });
    window.addEventListener('pagehide', save);
    document.addEventListener('visibilitychange', () => { if (document.hidden) save(); });

    // Doi anh va phong chu de khoi phuc theo doan van, khong chi theo so pixel.
    const imagesReady = Promise.all(Array.from(content.querySelectorAll('img')).map(img =>
        img.complete ? Promise.resolve() : new Promise(resolve => {
            img.addEventListener('load', resolve, { once: true });
            img.addEventListener('error', resolve, { once: true });
        })));
    const fontsReady = document.fonts ? document.fonts.ready : Promise.resolve();
    content.querySelectorAll('img').forEach(img => img.addEventListener('load', () => {
        if (ready && !interacted && previous && previous.chapterId === chapterId && !location.hash) {
            restore(previous); progress(); save();
        }
    }, { once: true }));
    Promise.race([Promise.all([imagesReady, fontsReady]), new Promise(resolve => setTimeout(resolve, 4000))])
        .then(() => {
            if (!interacted && previous && previous.chapterId === chapterId && !location.hash) restore(previous);
            ready = true;
            progress();
            save();
        });
})();
