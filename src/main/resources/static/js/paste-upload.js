/*
 * Dán ảnh (Ctrl+V), kéo thả hoặc chọn file cho <input type="file" data-paste>.
 *  - input có "multiple": nhiều ảnh được cộng dồn. Không có "multiple": chỉ giữ ảnh mới nhất.
 *  - data-paste-zone (trên thẻ bao ngoài): vùng chọn ô nhận ảnh khi trang có nhiều ô (rê chuột / click vào vùng).
 *  - data-paste-own-preview: trang tự vẽ xem trước qua sự kiện "change", script không vẽ thêm.
 */
(function () {
    'use strict';

    var store = new WeakMap();   // input -> mảng File đang chờ upload
    var boxes = new WeakMap();   // input -> khung xem trước
    var active = null;
    var internal = false;        // true khi chính script đổi input.files
    var counter = 0;

    var css = document.createElement('style');
    css.textContent =
        '.paste-active{outline:1px dashed #4da6ff;outline-offset:3px;}' +
        '.paste-hint{display:block;color:#8ab4f8;font-size:12px;margin-top:4px;}' +
        '.paste-preview{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px;}' +
        '.paste-item{position:relative;width:90px;}' +
        '.paste-item img{width:90px;height:90px;object-fit:cover;border-radius:4px;display:block;border:1px solid #444;}' +
        '.paste-item button{position:absolute;top:3px;right:3px;width:20px;height:20px;border:0;border-radius:4px;' +
        'background:rgba(220,53,69,.9);color:#fff;cursor:pointer;font-size:11px;line-height:1;}' +
        '.paste-toast{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:#28a745;color:#fff;' +
        'padding:9px 16px;border-radius:6px;font-weight:bold;font-size:14px;z-index:9999;box-shadow:0 4px 12px rgba(0,0,0,.4);}';
    document.head.appendChild(css);

    function isImage(f) { return f && /^image\//.test(f.type); }
    function zoneOf(input) { return input.closest('[data-paste-zone]') || input.parentElement; }
    function visible(el) { return !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length); }
    function allInputs() { return Array.prototype.slice.call(document.querySelectorAll('input[type=file][data-paste]')); }

    function toast(msg) {
        var t = document.createElement('div');
        t.className = 'paste-toast';
        t.textContent = msg;
        document.body.appendChild(t);
        setTimeout(function () { t.remove(); }, 1800);
    }

    function setActive(input) {
        if (active === input) return;
        if (active) zoneOf(active).classList.remove('paste-active');
        active = input;
        if (active) zoneOf(active).classList.add('paste-active');
    }

    // Ảnh dán thường tên chung "image.png" -> đặt tên riêng cho dễ phân biệt
    function rename(f) {
        if (/^image\.\w+$/i.test(f.name)) {
            var ext = (f.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
            return new File([f], 'dan-' + Date.now() + '-' + (++counter) + '.' + ext, { type: f.type });
        }
        return f;
    }

    // Đặt danh sách file cho input (nguồn sự thật duy nhất)
    function apply(input, files) {
        store.set(input, files);

        var dt = new DataTransfer();
        files.forEach(function (f) { dt.items.add(f); });
        internal = true;
        input.files = dt.files;
        input.dispatchEvent(new Event('change', { bubbles: true }));
        internal = false;

        if (!input.hasAttribute('data-paste-own-preview')) renderPreview(input, files);
    }

    function addFiles(input, fileList) {
        var imgs = Array.prototype.slice.call(fileList).filter(isImage).map(rename);
        if (!imgs.length) return false;

        var files = input.multiple ? (store.get(input) || []).concat(imgs) : [imgs[0]];

        // Form bìa tập đang ẩn -> mở ra để thấy ảnh và bấm Lưu
        var form = input.closest('form');
        if (form && form.classList.contains('cover-form')) form.classList.add('show');

        apply(input, files);
        return true;
    }

    function renderPreview(input, files) {
        var box = boxes.get(input);
        box.innerHTML = '';
        files.forEach(function (file, i) {
            var item = document.createElement('div');
            item.className = 'paste-item';

            var img = document.createElement('img');
            img.src = URL.createObjectURL(file);
            img.onload = function () { URL.revokeObjectURL(img.src); };

            var rm = document.createElement('button');
            rm.type = 'button';
            rm.textContent = '✕';
            rm.title = 'Bỏ ảnh này';
            rm.addEventListener('click', function () {
                apply(input, (store.get(input) || []).filter(function (_, k) { return k !== i; }));
            });

            item.appendChild(img);
            item.appendChild(rm);
            box.appendChild(item);
        });
    }

    function init(input) {
        var zone = zoneOf(input);
        store.set(input, []);

        var hint = document.createElement('small');
        hint.className = 'paste-hint';
        hint.textContent = '📋 Hoặc dán ảnh bằng Ctrl+V, kéo thả ảnh vào đây';
        input.insertAdjacentElement('afterend', hint);

        var box = document.createElement('div');
        box.className = 'paste-preview';
        hint.insertAdjacentElement('afterend', box);
        boxes.set(input, box);

        ['mouseenter', 'focusin', 'click'].forEach(function (ev) {
            zone.addEventListener(ev, function () { setActive(input); });
        });

        // Chọn bằng hộp thoại file: cộng dồn (multiple) hoặc thay (single), hủy hộp thoại thì giữ nguyên
        input.addEventListener('change', function () {
            if (internal) return;
            var picked = Array.prototype.slice.call(input.files).filter(isImage);
            var current = store.get(input) || [];
            if (!picked.length) { apply(input, current); return; }
            apply(input, input.multiple ? current.concat(picked) : [picked[0]]);
        });

        // Kéo thả file từ máy (chỉ khi đang kéo file, không đụng tới kéo thả sắp xếp)
        zone.addEventListener('dragover', function (e) {
            if (e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types, 'Files') !== -1) e.preventDefault();
        });
        zone.addEventListener('drop', function (e) {
            if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
                e.preventDefault();
                setActive(input);
                if (addFiles(input, e.dataTransfer.files)) toast('Đã thêm ảnh');
            }
        });
    }

    document.addEventListener('paste', function (e) {
        var files = e.clipboardData ? Array.prototype.slice.call(e.clipboardData.files) : [];
        if (!files.some(isImage)) return; // dán chữ bình thường thì để yên

        var target = (active && visible(zoneOf(active))) ? active : null;
        if (!target) {
            target = allInputs().filter(function (i) { return visible(zoneOf(i)); })[0] || null;
        }
        if (!target) return;

        e.preventDefault();
        if (addFiles(target, files)) {
            setActive(target);
            zoneOf(target).scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            toast('Đã dán ảnh, bấm nút Lưu để upload');
        }
    });

    document.addEventListener('DOMContentLoaded', function () { allInputs().forEach(init); });
})();