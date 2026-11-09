(function () {
    'use strict';

    var host = document.querySelector('[data-poll-url]');
    if (!host || !window.ExamPrep) {
        return;
    }

    var message = document.querySelector('[data-poll-message]');
    var reloadOnTerminal = host.getAttribute('data-poll-reload') !== 'false';

    window.ExamPrep.pollStatus(host.getAttribute('data-poll-url'), {
        terminal: (host.getAttribute('data-terminal') || 'READY,FAILED').split(','),
        interval: Number(host.getAttribute('data-poll-interval') || 3000),
        onUpdate: function (data) {
            if (!message) {
                return;
            }
            var text = data.progressMessage || data.failureReason || '';
            if (!text) {
                text = data.status === 'PROCESSING' ? 'Working on it...' : 'Waiting to start...';
            }
            message.textContent = text;
        },
        onTerminal: function () {
            if (reloadOnTerminal) {
                window.location.reload();
            }
        }
    });
})();
