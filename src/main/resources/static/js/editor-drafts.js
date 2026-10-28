(function () {
    'use strict';
    const prefix = 'ranobe:draft:v1:';
    const read = key => {
        try {
            const value = JSON.parse(localStorage.getItem(prefix + key) || 'null');
            return value && typeof value.revision === 'string' && typeof value.html === 'string'
                && typeof value.title === 'string' && typeof value.completed === 'string' ? value : null;
        } catch (_) { return null; }
    };
    const acknowledged = document.getElementById('draftSaved');
    if (acknowledged) {
        const key = acknowledged.dataset.draftKey;
        const current = read(key);
        if (current && current.revision === acknowledged.dataset.draftRevision) {
            try { localStorage.removeItem(prefix + key); } catch (_) { /* Giu ban nhap neu trinh duyet chan luu. */ }
        }
    }
    const form = document.getElementById('ceForm');
    if (!form || !form.dataset.draftKey) return;
    const key = form.dataset.draftKey;
    const body = document.getElementById('ceBody');
    const title = document.getElementById('ceTitle');
    const status = document.getElementById('draftStatus');
    const recovery = document.getElementById('draftRecovery');
    const conflict = document.getElementById('draftConflict');
    const snapshot = () => ({
        title: title.value, html: body.innerHTML,
        completed: (form.querySelector('[name="completed"]:checked') || {}).value || 'true'
    });
    const signature = value => JSON.stringify([value.title, value.html, value.completed]);
    const initial = signature(snapshot());
    // Chi de nhan biet noi dung may chu da thay doi, khong dung cho bao mat.
    const fingerprint = value => {
        let result = 2166136261;
        for (let i = 0; i < value.length; i++) result = Math.imul(result ^ value.charCodeAt(i), 16777619);
        return (result >>> 0).toString(16);
    };
    const baseFingerprint = fingerprint(initial);
    let pending = read(key);
    let knownRevision = pending ? pending.revision : null;
    let paused = false;
    let submitting = false;
    let timer;
    let unavailable = false;

    function hasChanges() { return signature(snapshot()) !== initial; }
    function cleanHtml(html) {
        const template = document.createElement('template');
        template.innerHTML = html;
        const allowed = new Set(['P', 'B', 'STRONG', 'I', 'EM', 'U', 'S', 'BLOCKQUOTE', 'BR', 'IMG', 'DIV']);
        function clean(parent) {
            Array.from(parent.childNodes).forEach(node => {
                if (node.nodeType === Node.COMMENT_NODE) { node.remove(); return; }
                if (node.nodeType !== Node.ELEMENT_NODE) return;
                if (['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT', 'SVG', 'MATH', 'TEMPLATE'].includes(node.tagName)) {
                    node.remove(); return;
                }
                clean(node);
                if (!allowed.has(node.tagName)) { node.replaceWith(...node.childNodes); return; }
                const source = node.tagName === 'IMG' ? node.getAttribute('src') : null;
                Array.from(node.attributes).forEach(attribute => node.removeAttribute(attribute.name));
                if (node.tagName === 'IMG') {
                    try {
                        const url = new URL(source || '', location.origin);
                        if (!source || !['http:', 'https:'].includes(url.protocol)) { node.remove(); return; }
                        node.src = url.href;
                    } catch (_) { node.remove(); }
                }
            });
        }
        clean(template.content);
        return template.innerHTML;
    }
    function showSaved(value) {
        status.textContent = 'Đã lưu bản nháp lúc ' + new Date(value.updatedAt).toLocaleTimeString('vi-VN');
    }
    function save() {
        clearTimeout(timer);
        if (paused || submitting || unavailable) return null;
        const state = snapshot();
        if (!hasChanges()) {
            status.textContent = 'Chưa có thay đổi';
            return read(key);
        }
        const current = read(key);
        if (current && current.revision !== knownRevision) {
            paused = true; conflict.hidden = false;
            status.textContent = 'Tạm dừng tự lưu: có bản nháp ở cửa sổ khác.';
            return null;
        }
        if (current && signature(current) === signature(state)) { showSaved(current); return current; }
        if (state.html.length > 2_000_000) {
            status.textContent = 'Bản nháp quá lớn để tự lưu. Hãy lưu chương trước khi thoát.';
            return null;
        }
        const value = { ...state, baseFingerprint, updatedAt: Date.now(),
            revision: window.crypto && crypto.randomUUID ? crypto.randomUUID()
                : Date.now().toString(36) + Math.random().toString(36).slice(2) };
        try {
            localStorage.setItem(prefix + key, JSON.stringify(value));
            knownRevision = value.revision;
            showSaved(value);
            return value;
        } catch (_) {
            status.textContent = 'Không thể lưu bản nháp trên trình duyệt. Hãy lưu chương trước khi thoát.';
            return null;
        }
    }
    function changed() {
        if (paused) return;
        status.textContent = 'Đang chờ lưu bản nháp…';
        clearTimeout(timer);
        timer = setTimeout(save, 900);
    }
    function restore(value) {
        if (!value) return;
        title.value = value.title;
        body.innerHTML = cleanHtml(value.html);
        form.querySelectorAll('[name="completed"]').forEach(input =>
            input.checked = input.value === value.completed);
        knownRevision = value.revision;
        pending = null; paused = false;
        recovery.hidden = true; conflict.hidden = true;
        body.dispatchEvent(new Event('draft:restored'));
        showSaved(value);
        changed();
    }
    if (pending && signature(pending) === initial) {
        try { localStorage.removeItem(prefix + key); } catch (_) { /* Khong anh huong viec soan. */ }
        pending = null; knownRevision = null;
    }
    if (pending) {
        paused = true;
        recovery.hidden = false;
        document.getElementById('draftRecoveryText').textContent =
            'Có bản nháp lúc ' + new Date(pending.updatedAt).toLocaleString('vi-VN') + '.'
            + (pending.baseFingerprint && pending.baseFingerprint !== baseFingerprint
                ? ' Nội dung trên máy chủ đã thay đổi từ lần soạn đó.' : '');
        status.textContent = 'Chờ lựa chọn khôi phục bản nháp.';
    }
    document.getElementById('restoreDraft').addEventListener('click', () => {
        if (hasChanges() && !confirm('Khôi phục sẽ thay nội dung đang soạn. Tiếp tục?')) return;
        restore(read(key));
    });
    document.getElementById('discardDraft').addEventListener('click', () => {
        try { localStorage.removeItem(prefix + key); knownRevision = null; }
        catch (_) { unavailable = true; }
        pending = null; paused = false; recovery.hidden = true;
        if (hasChanges()) save();
        else status.textContent = 'Chưa có thay đổi';
    });
    document.getElementById('loadOtherDraft').addEventListener('click', () => {
        if (hasChanges() && !confirm('Thay nội dung đang soạn bằng bản nháp ở cửa sổ khác?')) return;
        restore(read(key));
    });
    document.getElementById('keepThisDraft').addEventListener('click', () => {
        const current = read(key);
        knownRevision = current ? current.revision : null;
        paused = false; conflict.hidden = true; save();
    });
    title.addEventListener('input', changed);
    form.querySelectorAll('[name="completed"]').forEach(input => input.addEventListener('change', changed));
    body.addEventListener('input', changed);
    new MutationObserver(changed).observe(body, { childList: true, characterData: true, subtree: true, attributes: true, attributeFilter: ['src'] });
    window.addEventListener('storage', event => {
        if (event.key !== prefix + key) return;
        const current = read(key);
        if (current && current.revision !== knownRevision) {
            paused = true; conflict.hidden = false;
            status.textContent = 'Tạm dừng tự lưu: có bản nháp ở cửa sổ khác.';
        }
    });
    window.addEventListener('beforeunload', event => {
        if (submitting) return;
        if (hasChanges() || form.dataset.uploading === 'true') {
            save(); event.preventDefault(); event.returnValue = '';
        }
    });
    window.addEventListener('pagehide', () => { if (!submitting) save(); });
    document.addEventListener('visibilitychange', () => { if (document.hidden && !submitting) save(); });
    form.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        if (paused && !confirm('Có bản nháp chưa khôi phục. Lưu nội dung đang mở?')) {
            event.preventDefault(); return;
        }
        if (paused) {
            const current = read(key);
            knownRevision = current ? current.revision : null;
            paused = false;
        }
        const value = save();
        document.getElementById('draftRevision').value = value ? value.revision : '';
        submitting = true;
    });
})();
