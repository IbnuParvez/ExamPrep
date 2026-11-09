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
                if (title) {
                    title.textContent = "Generation did not finish";
                }
                if (message) {
                    message.textContent = "The package could not be generated.";
                }
                if (errorLive) {
                    errorLive.textContent = status.errorMessage || "Generation failed. Please try again.";
                    errorLive.hidden = false;
                }
                if (bar && bar.parentElement) {
                    bar.parentElement.hidden = true;
                }
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
