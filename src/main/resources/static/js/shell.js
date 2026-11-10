/* ExamPrep presentation helpers. Header, menu, reveal and small enhancements. No framework. */
(function () {
    "use strict";
    document.documentElement.classList.add("js");

    function ready(fn) {
        if (document.readyState === "loading") { document.addEventListener("DOMContentLoaded", fn); } else { fn(); }
    }

    ready(function () {
        /* Header turns solid once the page scrolls (it is transparent over the landing hero). */
        var header = document.getElementById("site-header");
        if (header) {
            var onScroll = function () { header.classList.toggle("is-scrolled", window.scrollY > 8); };
            onScroll();
            window.addEventListener("scroll", onScroll, { passive: true });
        }

        /* Mobile menu as an accessible disclosure. */
        var toggle = document.querySelector(".menu-toggle");
        var menu = document.getElementById("site-menu");
        if (toggle && menu) {
            var setOpen = function (open) {
                toggle.setAttribute("aria-expanded", String(open));
                menu.classList.toggle("is-open", open);
            };
            toggle.addEventListener("click", function () {
                setOpen(toggle.getAttribute("aria-expanded") !== "true");
            });
            document.addEventListener("keydown", function (event) {
                if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") {
                    setOpen(false);
                    toggle.focus();
                }
            });
            menu.addEventListener("click", function (event) {
                if (event.target.closest("a")) { setOpen(false); }
            });
        }

        /* Subtle reveal on scroll; everything is visible without JS or with reduced motion. */
        var items = Array.prototype.slice.call(document.querySelectorAll("[data-reveal]"));
        var show = function (el) { el.classList.add("is-visible"); };
        if (!("IntersectionObserver" in window) || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            items.forEach(show);
        } else {
            var observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) { show(entry.target); observer.unobserve(entry.target); }
                });
            }, { rootMargin: "0px 0px -8% 0px", threshold: 0.05 });
            items.forEach(function (el) { observer.observe(el); });
        }

        /* Flashcards: progress bar and flip animation, driven by the existing script's output. */
        var counter = document.getElementById("card-counter");
        var card = document.getElementById("flashcard");
        var label = document.getElementById("card-label");
        if (counter && card) {
            var bar = document.createElement("div");
            bar.className = "progress flashcard-progress";
            bar.setAttribute("role", "progressbar");
            bar.setAttribute("aria-label", "Flashcard progress");
            bar.setAttribute("aria-valuemin", "0");
            bar.setAttribute("aria-valuemax", "100");
            var fill = document.createElement("div");
            fill.className = "progress-bar";
            bar.appendChild(fill);
            card.parentNode.insertBefore(bar, card);
            var sync = function () {
                var m = /(\d+)\s*\/\s*(\d+)/.exec(counter.textContent);
                if (m) {
                    var pct = Math.round((Number(m[1]) / Number(m[2])) * 100);
                    fill.style.width = pct + "%";
                    bar.setAttribute("aria-valuenow", String(pct));
                }
            };
            sync();
            new MutationObserver(sync).observe(counter, { childList: true, characterData: true, subtree: true });
            if (label) {
                new MutationObserver(function () {
                    card.classList.remove("is-flipping");
                    void card.offsetWidth;
                    card.classList.add("is-flipping");
                }).observe(label, { childList: true, characterData: true, subtree: true });
            }
        }

        /* Exam timer: announce only at key thresholds, never every second. */
        var timer = document.getElementById("exam-timer");
        var live = document.getElementById("sr-live");
        if (timer && live) {
            var marks = { 300: "5 minutes remaining.", 60: "1 minute remaining.", 30: "30 seconds remaining." };
            var said = {};
            new MutationObserver(function () {
                var m = /(\d+):(\d+)/.exec(timer.textContent);
                if (!m) { return; }
                var secs = Number(m[1]) * 60 + Number(m[2]);
                if (marks[secs] && !said[secs]) { said[secs] = true; live.textContent = marks[secs]; }
            }).observe(timer, { childList: true, characterData: true, subtree: true });
        }
    });
})();
