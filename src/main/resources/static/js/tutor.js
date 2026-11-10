(function () {
    "use strict";

    const container = document.querySelector("[data-tutor]");
    if (!container || !window.ExamPrep) {
        return;
    }

    const url = container.getAttribute("data-tutor-url");
    const form = document.getElementById("tutor-form");
    const input = document.getElementById("tutor-message");
    const submit = document.getElementById("tutor-submit");
    const log = document.getElementById("chat-log");
    const typing = document.getElementById("tutor-typing");
    const empty = document.getElementById("chat-empty");

    function appendMessage(role, content, sources) {
        if (empty) {
            empty.hidden = true;
        }
        const message = document.createElement("div");
        message.className = "message" + (role === "user" ? " user" : "");

        const roleElement = document.createElement("div");
        roleElement.className = "message-role";
        roleElement.textContent = role === "user" ? "You" : "ExamPrep tutor";

        const body = document.createElement("div");
        body.className = "message-body";
        body.textContent = content;

        message.appendChild(roleElement);
        message.appendChild(body);

        if (sources && sources.length) {
            const sourceList = document.createElement("div");
            sourceList.className = "message-sources";
            sources.forEach(function (source) {
                const chip = document.createElement("span");
                chip.className = "source";
                chip.textContent = source;
                sourceList.appendChild(chip);
            });
            message.appendChild(sourceList);
        }
        log.appendChild(message);
        log.scrollTop = log.scrollHeight;
    }

    form.addEventListener("submit", async function (event) {
        event.preventDefault();
        // Enter also dispatches a synthetic submit event; without this guard an impatient student could
        // fire overlapping tutor requests while the first one was still running.
        if (submit.disabled) {
            return;
        }
        const message = input.value.trim();
        if (!message) {
            return;
        }
        appendMessage("user", message, []);
        input.value = "";
        submit.disabled = true;
        if (typing) {
            typing.hidden = false;
        }
        try {
            const answer = await window.ExamPrep.request(url, {
                method: "POST",
                headers: window.ExamPrep.headers({ "Content-Type": "application/json" }),
                body: JSON.stringify({ message: message })
            });
            appendMessage("assistant", answer.answer, answer.sources);
            if (answer.answeredFromMaterial === false) {
                window.ExamPrep.toast("The document does not fully cover that question.", false);
            }
        } catch (error) {
            window.ExamPrep.toast(error.message, true);
            appendMessage("assistant", "I could not answer that just now. " + error.message, []);
            // Give the student their question back instead of silently losing what they typed.
            if (!input.value) {
                input.value = message;
            }
        } finally {
            submit.disabled = false;
            if (typing) {
                typing.hidden = true;
            }
            input.focus();
        }
    });

    input.addEventListener("keydown", function (event) {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            if (!submit.disabled) {
                form.dispatchEvent(new Event("submit", { cancelable: true }));
            }
        }
    });

    document.querySelectorAll(".prompt-chip").forEach(function (chip) {
        chip.addEventListener("click", function () {
            input.value = chip.textContent.trim();
            input.focus();
        });
    });

    if (log) {
        log.scrollTop = log.scrollHeight;
    }
})();
