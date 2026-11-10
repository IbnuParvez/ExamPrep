/* Landing page demo: ARIA tabs with arrow-key navigation, and a flip card. Static sample content only. */
(function () {
    "use strict";
    var demo = document.querySelector("[data-demo]");
    if (!demo) { return; }

    var tabs = Array.prototype.slice.call(demo.querySelectorAll('[role="tab"]'));

    function select(tab, focus) {
        tabs.forEach(function (t) {
            var on = t === tab;
            t.setAttribute("aria-selected", String(on));
            t.tabIndex = on ? 0 : -1;
            var panel = document.getElementById(t.getAttribute("aria-controls"));
            if (panel) { panel.hidden = !on; }
        });
        if (focus) { tab.focus(); }
    }

    tabs.forEach(function (tab, i) {
        tab.addEventListener("click", function () { select(tab, false); });
        tab.addEventListener("keydown", function (event) {
            var next = null;
            if (event.key === "ArrowRight") { next = tabs[(i + 1) % tabs.length]; }
            else if (event.key === "ArrowLeft") { next = tabs[(i - 1 + tabs.length) % tabs.length]; }
            else if (event.key === "Home") { next = tabs[0]; }
            else if (event.key === "End") { next = tabs[tabs.length - 1]; }
            if (next) { event.preventDefault(); select(next, true); }
        });
    });

    var flip = demo.querySelector(".flip");
    if (flip) {
        var front = flip.querySelector(".front");
        var back = flip.querySelector(".back");
        flip.addEventListener("click", function () {
            var on = !flip.classList.contains("is-flipped");
            flip.classList.toggle("is-flipped", on);
            flip.setAttribute("aria-pressed", String(on));
            if (front) { front.setAttribute("aria-hidden", String(on)); }
            if (back) { back.setAttribute("aria-hidden", String(!on)); }
        });
    }
})();
