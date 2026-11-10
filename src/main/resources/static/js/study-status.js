(function () {
    "use strict";

    const panel = document.querySelector("[data-study-status]");
    if (!panel) {
        return;
    }
    const url = panel.getAttribute("data-status-url");
    const bar = document.getElementById("gen-bar");
    const percentLabel = document.getElementById("gen-percent");
    const message = document.getElementById("gen-message");
    const errorLive = document.getElementById("gen-error-live");
    const title = document.getElementById("gen-title");
    let timer = null;
    let finished = false;

    const initialStatus = panel.getAttribute("data-status");
    if (initialStatus === "FAILED" || initialStatus === "READY") {
        // The server already rendered this state, including its retry and delete actions.
        return;
    }

    function stop() {
        if (timer) {
            window.clearInterval(timer);
            timer = null;
        }
    }

    async function poll() {
        if (finished) {
            return;
        }
        try {
            const response = await fetch(url, {
                credentials: "same-origin",
                headers: { "Accept": "application/json" }
            });
            if (!response.ok) {
                return;
            }
            const status = await response.json();
            if (status.status === "READY") {
                finished = true;
                stop();
                window.location.reload();
                return;
            }
            if (status.status === "FAILED") {
                finished = true;
                stop();
                // Reload so the server renders the failed state, including the "Generate again" and
                // "Delete package" actions that only exist in the Thymeleaf template.
                window.location.reload();
                return;
            }
            if (bar) {
                bar.style.width = Math.max(5, status.progressPercent || 5) + "%";
            }
            if (percentLabel) {
                percentLabel.textContent = (status.progressPercent || 0) + "%";
            }
            if (message && status.progressMessage) {
                message.textContent = status.progressMessage;
            }
        } catch (error) {
            // Keep polling through transient failures.
        }
    }

    timer = window.setInterval(poll, 2500);
    window.setTimeout(poll, 1500);
    window.addEventListener("beforeunload", stop);
})();
