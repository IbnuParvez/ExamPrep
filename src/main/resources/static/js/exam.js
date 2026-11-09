(function () {
    'use strict';

    var exam = document.querySelector('[data-exam]');
    if (!exam || !window.ExamPrep) {
        return;
    }

    var packId = exam.getAttribute('data-study-pack');
    var resultUrl = exam.getAttribute('data-result-url');
    var questions = Array.prototype.slice.call(exam.querySelectorAll('[data-exam-question]'));
    var navHost = exam.querySelector('[data-exam-nav]');
    var timerLabel = exam.querySelector('[data-exam-timer]');
    var submitButton = exam.querySelector('[data-exam-submit]');
    var prevButton = exam.querySelector('[data-exam-prev]');
    var nextButton = exam.querySelector('[data-exam-next]');
    var durationSeconds = Number(exam.getAttribute('data-duration-seconds') || 1800);
    var remaining = durationSeconds;
    var current = 0;
    var startedAt = Date.now();
    var submitted = false;

    if (!questions.length) {
        return;
    }

    function isAnswered(question) {
        if (question.getAttribute('data-type') === 'SHORT_ANSWER') {
            var textarea = question.querySelector('textarea');
            return textarea && textarea.value.trim().length > 0;
        }
        return !!question.querySelector('input[type="radio"]:checked');
    }

    function render() {
        questions.forEach(function (question, index) {
            question.classList.toggle('is-hidden', index !== current);
        });
        if (navHost) {
            Array.prototype.slice.call(navHost.querySelectorAll('button')).forEach(function (button, index) {
                button.classList.toggle('is-current', index === current);
                button.classList.toggle('is-answered', isAnswered(questions[index]));
            });
        }
        if (prevButton) {
            prevButton.disabled = current === 0;
        }
        if (nextButton) {
            nextButton.disabled = current === questions.length - 1;
        }
    }

    function formatTime(seconds) {
        var safe = Math.max(0, seconds);
        var minutes = Math.floor(safe / 60);
        var secs = safe % 60;
        return (minutes < 10 ? '0' : '') + minutes + ':' + (secs < 10 ? '0' : '') + secs;
    }

    function tick() {
        remaining -= 1;
        if (timerLabel) {
            timerLabel.textContent = formatTime(remaining);
            timerLabel.classList.toggle('is-urgent', remaining <= 300);
        }
        if (remaining <= 0) {
            window.clearInterval(intervalId);
            window.alert('Time is up. Your examination will be submitted.');
            send(true);
        }
    }

    function buildNav() {
        if (!navHost) {
            return;
        }
        navHost.innerHTML = '';
        questions.forEach(function (question, index) {
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'exam-nav__btn';
            button.textContent = String(index + 1);
            button.setAttribute('aria-label', 'Go to question ' + (index + 1));
            button.addEventListener('click', function () {
                current = index;
                render();
            });
            navHost.appendChild(button);
        });
    }

    function collectAnswers() {
        return questions.map(function (question) {
            var id = question.getAttribute('data-question-id');
            var type = question.getAttribute('data-type');
            var answer = null;
            if (type === 'SHORT_ANSWER') {
                var textarea = question.querySelector('textarea');
                answer = textarea && textarea.value.trim() ? textarea.value.trim() : null;
            } else {
                var selected = question.querySelector('input[type="radio"]:checked');
                answer = selected ? selected.value : null;
            }
            return {questionId: id, answer: answer};
        });
    }

    async function send(autoSubmitted) {
        if (submitted) {
            return;
        }
        var answered = questions.filter(isAnswered).length;
        if (!autoSubmitted) {
            var message = 'You have answered ' + answered + ' of ' + questions.length
                + ' questions. Submit the examination now?';
            if (!window.confirm(message)) {
                return;
            }
        }
        submitted = true;
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = 'Submitting...';
        }
        try {
            var result = await window.ExamPrep.postJson(
                '/api/study/' + encodeURIComponent(packId) + '/exam',
                {answers: collectAnswers(), timeTakenSeconds: Math.round((Date.now() - startedAt) / 1000)}
            );
            window.location.href = resultUrl + result.attemptId;
        } catch (error) {
            submitted = false;
            if (submitButton) {
                submitButton.disabled = false;
                submitButton.textContent = 'Submit examination';
            }
            window.alert(error.message);
        }
    }

    if (timerLabel) {
        timerLabel.textContent = formatTime(remaining);
        timerLabel.classList.toggle('is-urgent', remaining <= 300);
    }
    buildNav();
    render();
    var intervalId = window.setInterval(tick, 1000);

    if (prevButton) {
        prevButton.addEventListener('click', function () {
            current = Math.max(0, current - 1);
            render();
        });
    }
    if (nextButton) {
        nextButton.addEventListener('click', function () {
            current = Math.min(questions.length - 1, current + 1);
            render();
        });
    }
    if (submitButton) {
        submitButton.addEventListener('click', function () {
            send(false);
        });
    }

    exam.addEventListener('change', function (event) {
        if (event.target.matches('input[type="radio"], textarea')) {
            render();
        }
    });
    exam.addEventListener('input', function (event) {
        if (event.target.matches('textarea')) {
            render();
        }
    });

    window.addEventListener('beforeunload', function (event) {
        if (!submitted) {
            event.preventDefault();
            event.returnValue = '';
        }
    });
})();
