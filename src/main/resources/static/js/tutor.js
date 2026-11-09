(function () {
    'use strict';

    var form = document.querySelector('[data-tutor-form]');
    if (!form || !window.ExamPrep) {
        return;
    }

    var log = document.querySelector('[data-tutor-log]');
    var textarea = form.querySelector('textarea');
    var submit = form.querySelector('button[type="submit"]');
    var suggestionButtons = document.querySelectorAll('[data-suggestion]');

    function escapeHtml(value) {
        return String(value === null || value === undefined ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function scrollDown() {
        if (log) {
            log.scrollTop = log.scrollHeight;
        }
    }

    function appendMessage(role, body, references) {
        var wrapper = document.createElement('div');
        wrapper.className = 'chat-message chat-message--' + (role === 'USER' ? 'user' : 'assistant');
        var label = role === 'USER' ? 'You' : 'Study tutor';
        var sources = '';
        if (references && references.length) {
            sources = '<div class="chat-message__sources">' + references.map(function (reference) {
                return '<span class="source-tag">' + escapeHtml(reference.locationLabel || reference.referenceId) + '</span>';
            }).join('') + '</div>';
        }
        wrapper.innerHTML = '<div class="chat-message__role">' + escapeHtml(label) + '</div>'
            + '<div class="chat-message__body">' + escapeHtml(body) + '</div>' + sources;
        if (log) {
            log.appendChild(wrapper);
            scrollDown();
        }
        return wrapper;
    }

    suggestionButtons.forEach(function (button) {
        button.addEventListener('click', function () {
            if (textarea) {
                textarea.value = button.getAttribute('data-suggestion') || '';
                textarea.focus();
            }
        });
    });

    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        var question = textarea ? textarea.value.trim() : '';
        if (!question) {
            textarea.focus();
            return;
        }
        var url = form.getAttribute('data-url');
        if (submit) {
            submit.disabled = true;
            submit.textContent = 'Asking...';
        }
        textarea.value = '';
        appendMessage('USER', question, []);
        var pending = null;
        if (log) {
            pending = document.createElement('div');
            pending.className = 'chat-message chat-message--assistant chat-message--pending';
            pending.innerHTML = '<div class="chat-message__role">Study tutor</div>'
                + '<div class="chat-message__body">Reading your document...</div>';
            log.appendChild(pending);
            scrollDown();
        }
        try {
            var answer = await window.ExamPrep.postJson(url, {question: question});
            if (pending) {
                pending.remove();
            }
            appendMessage('ASSISTANT', answer.answer, answer.sourceReferences || []);
            if (answer.insufficientInformation) {
                appendMessage('ASSISTANT',
                    'This answer is based only on the uploaded material. Check your course notes for anything the document does not cover.',
                    []);
            }
        } catch (error) {
            if (pending) {
                pending.remove();
            }
            var failure = document.createElement('div');
            failure.className = 'alert alert--error';
            failure.textContent = error.message;
            if (log) {
                log.appendChild(failure);
                scrollDown();
            }
        } finally {
            if (submit) {
                submit.disabled = false;
                submit.textContent = 'Ask';
            }
            if (textarea) {
                textarea.focus();
            }
        }
    });

    scrollDown();
})();
