(function () {
    "use strict";

    const blocks = document.querySelectorAll("[data-answer-url]");
    if (!blocks.length || !window.ExamPrep) {
        return;
    }

    blocks.forEach(function (block) {
        const button = block.querySelector(".reveal-answer");
        const slot = block.querySelector(".answer-slot");
        if (!button || !slot) {
            return;
        }

        button.addEventListener("click", async function () {
            const url = block.getAttribute("data-answer-url");
            button.disabled = true;
            button.textContent = "Loading answer…";
            try {
                const answer = await window.ExamPrep.request(url, {
                    method: "POST",
                    headers: window.ExamPrep.headers()
                });
                const panel = document.createElement("div");
                panel.className = "answer-panel";

                const heading = document.createElement("p");
                const strong = document.createElement("strong");
                strong.textContent = "Suggested answer: ";
                heading.appendChild(strong);
                heading.appendChild(document.createTextNode(answer.correctAnswer || "Not available"));
                panel.appendChild(heading);

                if (answer.explanation) {
                    const explanation = document.createElement("p");
                    explanation.textContent = answer.explanation;
                    panel.appendChild(explanation);
                }
                if (answer.sourceRef) {
                    const source = document.createElement("span");
                    source.className = "source";
                    source.textContent = answer.sourceRef;
                    panel.appendChild(source);
                }

                slot.innerHTML = "";
                slot.appendChild(panel);
                button.textContent = "Answer shown";
            } catch (error) {
                button.disabled = false;
                button.textContent = "Reveal answer";
                window.ExamPrep.toast(error.message, true);
            }
        });
    });
})();
