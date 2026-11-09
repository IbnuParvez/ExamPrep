(function () {
    "use strict";

    const panel = document.querySelector("[data-document-status]");
    if (!panel) {
        return;
    }
    const url = panel.getAttribute("data-status-url");
    const initialStatus = panel.getAttribute("data-status");
    if (initialStatus !== "UPLOADED" && initialStatus !== "PROCESSING") {
        return;
    }

    let timer = null;

    function stop() {
        if (timer) {
            window.clearInterval(timer);
            timer = null;
        }
    }

    async function poll() {
        try {
            const response = await fetch(url, {
                credentials: "same-origin",
                headers: { "Accept": "application/json" }
            });
            if (!response.ok) {
                return;
            }
            const status = await response.json();
            if (status.status === "READY" || status.status === "FAILED") {
                stop();
                window.location.reload();
            }
        } catch (error) {
            // Transient network failures are ignored; polling continues.
        }
    }

    timer = window.setInterval(poll, 2500);
    window.setTimeout(poll, 1200);
    window.addEventListener("beforeunload", stop);
})();
