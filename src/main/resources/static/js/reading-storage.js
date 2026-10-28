(function () {
    'use strict';
    const prefix = 'ranobe:reading:v1:';
    const read = key => {
        try { return JSON.parse(localStorage.getItem(prefix + key) || 'null'); }
        catch (_) { return null; }
    };
    const write = (key, value) => {
        try { localStorage.setItem(prefix + key, JSON.stringify(value)); return true; }
        catch (_) { return false; }
    };
    window.RanobeReading = {
        history: id => read('product:' + id),
        save: (id, value) => write('product:' + id, value),
        preferences: () => read('preferences'),
        setPreferences: value => write('preferences', value)
    };
})();
