(function () {
    "use strict";

    const form = document.getElementById("exam-form");
    if (!form) {
        return;
    }

    const timerElement = document.getElementById("exam-timer");
    const timeTakenInput = document.getElementById("time-taken");
    const answeredCounter = document.getElementById("answered-count");
    const questions = Array.prototype.slice.call(document.querySelectorAll(".exam-question"));
    const chips = Array.prototype.slice.call(document.querySelectorAll(".question-chip"));
    const initialRemaining = Math.max(0, Number(form.getAttribute("data-remaining") || 0));
    // Count down from a wall-clock deadline: a plain decrement in setInterval drifts badly while the
    // tab is backgrounded, so students gained or lost time depending on device throttling.
    const deadline = Date.now() + initialRemaining * 1000;
    let remaining = initialRemaining;
    let submitted = false;

    function pad(value) {
        return value < 10 ? "0" + value : String(value);
    }

    function renderTimer() {
        if (!timerElement) {
            return;
        }
        const minutes = Math.floor(Math.max(0, remaining) / 60);
        const seconds = Math.max(0, remaining) % 60;
        timerElement.textContent = pad(minutes) + ":" + pad(seconds);
        timerElement.classList.toggle("is-warning", remaining <= 120);
    }

    function answeredState() {
        questions.forEach(function (question) {
            const checked = question.querySelector("input[type=radio]:checked");
            const answered = Boolean(checked);
            question.classList.toggle("is-answered", answered);
            const position = question.id.replace("question-", "");
            const chip = chips.find(function (candidate) {
                return candidate.getAttribute("data-target") === question.id;
            });
            if (chip) {
                chip.classList.toggle("is-answered", answered);
            }
            if (position && chip) {
                chip.setAttribute("aria-label", "Question " + position + (answered ? ", answered" : ", unanswered"));
            }
        });
        if (answeredCounter) {
            const count = questions.filter(function (question) {
                return Boolean(question.querySelector("input[type=radio]:checked"));
            }).length;
            answeredCounter.textContent = String(count);
        }
    }

    function tick() {
        if (submitted) {
            return;
        }
        remaining = Math.max(0, Math.round((deadline - Date.now()) / 1000));
        if (remaining <= 0) {
            renderTimer();
            submitted = true;
            if (timeTakenInput) {
                timeTakenInput.value = String(initialRemaining);
            }
            window.alert("Time is up. Your examination will now be submitted with the answers selected so far.");
            form.submit();
            return;
        }
        renderTimer();
    }

    form.addEventListener("change", answeredState);

    chips.forEach(function (chip) {
        chip.addEventListener("click", function () {
            const target = document.getElementById(chip.getAttribute("data-target"));
            if (target) {
                target.scrollIntoView({ behavior: "smooth", block: "start" });
                const firstInput = target.querySelector("input[type=radio]");
                if (firstInput) {
                    window.setTimeout(function () {
                        firstInput.focus({ preventScroll: true });
                    }, 280);
                }
            }
        });
    });

    form.addEventListener("submit", function (event) {
        if (submitted) {
            return;
        }
        const unanswered = questions.filter(function (question) {
            return !question.querySelector("input[type=radio]:checked");
        }).length;
        const message = unanswered === 0
            ? "Submit your examination? You cannot change answers afterwards."
            : "You have " + unanswered + " unanswered question" + (unanswered === 1 ? "" : "s")
                + ". Submit anyway? You cannot change answers afterwards.";
        if (!window.confirm(message)) {
            event.preventDefault();
            return;
        }
        submitted = true;
        if (timeTakenInput) {
            // Informational only: the server recomputes the duration from the attempt's startedAt.
            timeTakenInput.value = String(Math.max(0, initialRemaining - remaining));
        }
    });

    renderTimer();
    answeredState();
    window.setInterval(tick, 1000);
})();
