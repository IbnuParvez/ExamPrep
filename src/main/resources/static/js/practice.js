(function () {
    'use strict';

    var container = document.querySelector('[data-practice]');
    if (!container || !window.ExamPrep) {
        return;
    }

    var packId = container.getAttribute('data-study-pack');
    var cards = Array.prototype.slice.call(container.querySelectorAll('[data-question]'));

    function escapeHtml(value) {
        return String(value === null || value === undefined ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    cards.forEach(function (card) {
        var button = card.querySelector('[data-check-answer]');
        var feedback = card.querySelector('[data-feedback]');
        var type = card.getAttribute('data-type');

        card.querySelectorAll('input[type="radio"]').forEach(function (radio) {
            radio.addEventListener('change', function () {
                card.querySelectorAll('.option').forEach(function (option) {
                    option.classList.remove('is-selected');
                });
                var label = radio.closest('.option');
                if (label) {
                    label.classList.add('is-selected');
                }
            });
        });

        if (!button || !feedback) {
            return;
        }

        button.addEventListener('click', async function () {
            var answer = '';
            if (type === 'SHORT_ANSWER') {
                var textarea = card.querySelector('textarea');
                answer = textarea ? textarea.value.trim() : '';
            } else {
                var selected = card.querySelector('input[type="radio"]:checked');
                answer = selected ? selected.value : '';
            }
            if (!answer) {
                feedback.hidden = false;
                feedback.className = 'feedback feedback--incorrect';
                feedback.innerHTML = '<p>Choose or write an answer before checking.</p>';
                return;
            }

            button.disabled = true;
            button.textContent = 'Checking...';
            try {
                var result = await window.ExamPrep.postJson(
                    '/api/study/' + encodeURIComponent(packId) + '/practice/'
                    + encodeURIComponent(card.getAttribute('data-question-id')),
                    {answer: answer}
                );
                feedback.hidden = false;
                if (!result.autoGraded) {
                    feedback.className = 'feedback';
                    feedback.innerHTML = '<p><strong>Suggested answer</strong></p><p>'
                        + escapeHtml(result.correctAnswer) + '</p><p>' + escapeHtml(result.explanation) + '</p>'
                        + (result.sourceReference ? '<p class="source-tag">' + escapeHtml(result.sourceReference) + '</p>' : '');
                } else {
                    feedback.className = 'feedback ' + (result.correct ? 'feedback--correct' : 'feedback--incorrect');
                    feedback.innerHTML = '<p><strong>' + (result.correct ? 'Correct' : 'Not quite') + '</strong></p>'
                        + '<p>Correct answer: ' + escapeHtml(result.correctAnswer) + '</p>'
                        + '<p>' + escapeHtml(result.explanation) + '</p>'
                        + (result.sourceReference ? '<p class="source-tag">' + escapeHtml(result.sourceReference) + '</p>' : '');
                }
            } catch (error) {
                feedback.hidden = false;
                feedback.className = 'feedback feedback--incorrect';
                feedback.innerHTML = '<p>' + escapeHtml(error.message) + '</p>';
            } finally {
                button.disabled = false;
                button.textContent = 'Check answer';
            }
        });
    });
})();
