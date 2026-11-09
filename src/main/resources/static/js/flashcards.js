(function () {
    'use strict';

    var deck = document.querySelector('[data-flashcard-deck]');
    if (!deck || !window.ExamPrep) {
        return;
    }

    var cards = Array.prototype.slice.call(deck.querySelectorAll('[data-flashcard]'));
    if (!cards.length) {
        return;
    }

    var positionLabel = document.querySelector('[data-deck-position]');
    var prevButton = document.querySelector('[data-flashcard-prev]');
    var nextButton = document.querySelector('[data-flashcard-next]');
    var revealButton = document.querySelector('[data-flashcard-reveal]');
    var current = 0;
    var reviewed = {};

    function render() {
        cards.forEach(function (card, index) {
            card.classList.toggle('is-hidden', index !== current);
        });
        if (positionLabel) {
            positionLabel.textContent = (current + 1) + ' / ' + cards.length;
        }
        if (prevButton) {
            prevButton.disabled = current === 0;
        }
        if (nextButton) {
            nextButton.disabled = current === cards.length - 1;
        }
        if (revealButton) {
            var isRevealed = cards[current].classList.contains('is-revealed');
            revealButton.textContent = isRevealed ? 'Hide answer' : 'Reveal answer';
            revealButton.setAttribute('aria-expanded', isRevealed ? 'true' : 'false');
        }
    }

    function recordReview(card) {
        var cardId = card.getAttribute('data-card-id');
        var template = deck.getAttribute('data-review-url');
        if (!cardId || !template || reviewed[cardId]) {
            return;
        }
        reviewed[cardId] = true;
        var url = template.replace('{flashcardId}', cardId);
        window.ExamPrep.requestJson(url, {method: 'POST', body: JSON.stringify({})})
            .catch(function () {
                // review tracking is best effort and must not interrupt studying
            });
    }

    function reveal() {
        var card = cards[current];
        card.classList.toggle('is-revealed');
        if (card.classList.contains('is-revealed')) {
            recordReview(card);
        }
        render();
    }

    function move(step) {
        current = Math.max(0, Math.min(cards.length - 1, current + step));
        render();
    }

    if (revealButton) {
        revealButton.addEventListener('click', reveal);
    }
    if (prevButton) {
        prevButton.addEventListener('click', function () { move(-1); });
    }
    if (nextButton) {
        nextButton.addEventListener('click', function () { move(1); });
    }

    deck.addEventListener('click', function (event) {
        var card = event.target.closest('[data-flashcard]');
        if (card && !event.target.closest('a')) {
            reveal();
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.target.matches('input, textarea, select')) {
            return;
        }
        if (event.key === 'ArrowRight') {
            move(1);
        } else if (event.key === 'ArrowLeft') {
            move(-1);
        } else if (event.key === ' ' || event.key === 'Enter') {
            event.preventDefault();
            reveal();
        }
    });

    render();
})();
