/* ExamPrep shared browser helpers. No build step and no framework. */
(function () {
    "use strict";

    const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const csrfToken = csrfTokenMeta ? csrfTokenMeta.getAttribute("content") : "";
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute("content") : "X-CSRF-TOKEN";

    let toastTimer = null;

    function toast(message, isError) {
        const element = document.getElementById("toast");
        if (!element) {
            return;
        }
        element.textContent = message;
        element.classList.toggle("is-error", Boolean(isError));
        element.hidden = false;
        if (toastTimer) {
            window.clearTimeout(toastTimer);
        }
        toastTimer = window.setTimeout(function () {
            element.hidden = true;
        }, 5200);
    }

    function headers(extra) {
        const result = Object.assign({}, extra || {});
        if (csrfToken) {
            result[csrfHeader] = csrfToken;
        }
        return result;
    }

    async function readJson(response) {
        const text = await response.text();
        if (!text) {
            return {};
        }
        try {
            return JSON.parse(text);
        } catch (error) {
            return {};
        }
    }

    async function request(url, options) {
        const response = await fetch(url, Object.assign({
            credentials: "same-origin",
            headers: headers({ "Accept": "application/json" })
        }, options || {}));
        const body = await readJson(response);
        if (!response.ok) {
            throw new Error(body.message || "The request could not be completed.");
        }
        return body;
    }

    document.addEventListener("submit", function (event) {
        const form = event.target.closest("form[data-confirm]");
        if (form && !window.confirm(form.getAttribute("data-confirm"))) {
            event.preventDefault();
        }
    });

    window.ExamPrep = {
        headers: headers,
        request: request,
        readJson: readJson,
        toast: toast
    };
})();
