(function () {
    "use strict";

    const stage = document.querySelector("[data-flashcards]");
    if (!stage) {
        return;
    }

    const cards = Array.prototype.map.call(
        document.querySelectorAll("#flashcard-source-data [data-front]"),
        function (node) {
            return {
                front: node.getAttribute("data-front") || "",
                back: node.getAttribute("data-back") || "",
                source: node.getAttribute("data-source") || ""
            };
        }
    );
    if (!cards.length) {
        return;
    }

    const textElement = document.getElementById("card-text");
    const labelElement = document.getElementById("card-label");
    const sourceElement = document.getElementById("card-source");
    const counter = document.getElementById("card-counter");
    const revealButton = document.getElementById("reveal-card");
    const prevButton = document.getElementById("prev-card");
    const nextButton = document.getElementById("next-card");
    const summary = document.getElementById("review-summary");
    const summaryText = document.getElementById("review-summary-text");
    const reviewUrl = stage.getAttribute("data-review-url");

    let index = 0;
    let revealed = false;
    let viewed = new Set();
    let recorded = false;

    function render() {
        const card = cards[index];
        revealed = false;
        if (labelElement) {
            labelElement.textContent = "Prompt";
        }
        textElement.textContent = card.front;
        textElement.classList.remove("answer");
        if (sourceElement) {
            sourceElement.textContent = card.source ? "Source: " + card.source : "";
        }
        if (counter) {
            counter.textContent = (index + 1) + " / " + cards.length;
        }
        if (revealButton) {
            revealButton.textContent = "Reveal answer";
            revealButton.disabled = false;
        }
        viewed.add(index);
        if (prevButton) {
            prevButton.disabled = index === 0;
        }
        if (nextButton) {
            nextButton.textContent = index === cards.length - 1 ? "Finish review" : "Next";
        }
    }

    function reveal() {
        if (revealed) {
            return;
        }
        const card = cards[index];
        revealed = true;
        textElement.textContent = card.front + "\n\n" + card.back;
        textElement.classList.add("answer");
        if (labelElement) {
            labelElement.textContent = "Answer";
        }
        if (revealButton) {
            revealButton.textContent = "Answer shown";
            revealButton.disabled = true;
        }
    }

    function next() {
        if (index < cards.length - 1) {
            index += 1;
            render();
            return;
        }
        finish();
    }

    function previous() {
        if (index > 0) {
            index -= 1;
            render();
        }
    }

    async function finish() {
        if (!recorded && reviewUrl && window.ExamPrep) {
            recorded = true;
            try {
                await window.ExamPrep.request(reviewUrl, {
                    method: "POST",
                    headers: window.ExamPrep.headers({ "Content-Type": "application/json" }),
                    body: JSON.stringify({ reviewed: viewed.size, total: cards.length })
                });
            } catch (error) {
                window.ExamPrep.toast("Your review was not saved: " + error.message, true);
            }
        }
        if (summary) {
            summary.hidden = false;
            if (summaryText) {
                summaryText.textContent = "You reviewed " + viewed.size + " of " + cards.length + " cards.";
            }
            summary.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }

    if (revealButton) {
        revealButton.addEventListener("click", reveal);
    }
    if (nextButton) {
        nextButton.addEventListener("click", next);
    }
    if (prevButton) {
        prevButton.addEventListener("click", previous);
    }

    document.addEventListener("keydown", function (event) {
        if (event.target && /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName)) {
            return;
        }
        if (event.key === "ArrowRight") {
            event.preventDefault();
            next();
        } else if (event.key === "ArrowLeft") {
            event.preventDefault();
            previous();
        } else if (event.key === " " || event.key === "Enter") {
            // After clicking Next with the mouse, focus stays on that button, so "target === body" was
            // false and Space/Enter skipped to the next card instead of revealing the answer.
            event.preventDefault();
            reveal();
        }
    });

    render();
})();
