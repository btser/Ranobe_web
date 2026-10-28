(function () {
    'use strict';
    const button = document.getElementById('continueReading');
    if (!button || !window.RanobeReading) return;
    const last = RanobeReading.history(button.dataset.productId);
    if (!last || !Number.isInteger(last.chapterId)) return;
    const chapter = Array.from(document.querySelectorAll('[data-chapter-id]'))
        .find(link => Number(link.dataset.chapterId) === last.chapterId);
    if (!chapter) return;
    button.href = chapter.href;
    button.textContent = 'Đọc tiếp';
    button.title = chapter.textContent.trim();
})();
