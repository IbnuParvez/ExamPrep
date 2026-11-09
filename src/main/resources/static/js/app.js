(function () {
    'use strict';

    function meta(name) {
        var element = document.querySelector('meta[name="' + name + '"]');
        return element ? element.getAttribute('content') : '';
    }

    function csrfToken() {
        return meta('_csrf');
    }

    function csrfHeader() {
        return meta('_csrf_header') || 'X-CSRF-TOKEN';
    }

    async function readError(response) {
        try {
            var payload = await response.json();
            if (payload && payload.message) {
                return payload.message;
            }
        } catch (ignored) {
            // fall through to the generic message
        }
        return 'The request could not be completed. Please try again.';
    }

    async function requestJson(url, options) {
        var headers = Object.assign({
            'Accept': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        }, options && options.headers ? options.headers : {});
        if (options && options.method && options.method !== 'GET') {
            headers[csrfHeader()] = csrfToken();
            headers['Content-Type'] = 'application/json';
        }
        var response = await fetch(url, Object.assign({credentials: 'same-origin'}, options || {}, {headers: headers}));
        if (!response.ok) {
            var error = new Error(await readError(response));
            error.status = response.status;
            throw error;
        }
        if (response.status === 204) {
            return null;
        }
        return response.json();
    }

    function postJson(url, body) {
        return requestJson(url, {
            method: 'POST',
            body: JSON.stringify(body === undefined ? {} : body)
        });
    }

    function pollStatus(url, options) {
        var settings = options || {};
        var interval = settings.interval || 3000;
        var terminal = settings.terminal || ['READY', 'FAILED'];
        var attempts = 0;
        var maxAttempts = settings.maxAttempts || 240;

        async function tick() {
            attempts += 1;
            if (attempts > maxAttempts) {
                return;
            }
            try {
                var data = await requestJson(url, {method: 'GET'});
                if (typeof settings.onUpdate === 'function') {
                    settings.onUpdate(data);
                }
                if (terminal.indexOf(data.status) !== -1) {
                    if (typeof settings.onTerminal === 'function') {
                        settings.onTerminal(data);
                    }
                    return;
                }
            } catch (ignored) {
                // transient network issue: keep polling
            }
            window.setTimeout(tick, interval);
        }

        window.setTimeout(tick, settings.delay === undefined ? interval : settings.delay);
    }

    window.ExamPrep = {
        csrfToken: csrfToken,
        csrfHeader: csrfHeader,
        requestJson: requestJson,
        postJson: postJson,
        pollStatus: pollStatus
    };
})();
