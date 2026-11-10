# ExamPrep design system (navy and gold)

Mood: premium, scholarly, calm. A dark navy hero and sign-in area; light, airy, paper-toned app pages.

## Files
| File | Role |
|---|---|
| `static/css/tokens.css` | Every raw colour, type scale, radius, shadow, z-index, motion value, plus self-hosted `@font-face` |
| `static/css/app.css` | Components, written only with `var(--token)` values |
| `static/js/shell.js` | Header scroll state, mobile menu, reveal, flashcard progress/flip, exam timer announcements |
| `static/js/landing.js` | Landing demo tabs (arrow keys, Home/End) and flashcard flip |
| `static/css/fonts/` | Source Serif 4 and Inter variable WOFF2, Latin subset (SIL Open Font Licence) |

## Rules
- Gold is decoration on light backgrounds; gold text appears on navy only.
- Navy primary buttons; outlined secondary; `button-light` and `button-outline-light` on navy.
- Serif for headings and numerals; Inter for UI and body. Two families only.
- Status is never colour alone: badges carry text and an icon, answers carry a label, chips carry a check.
- Motion is 150-250ms ease-out and off under `prefers-reduced-motion`.
- Keep existing class names and IDs: the page scripts depend on them.

## Components
Buttons, inputs, cards, panels, badges, alerts, tabs, tables, stat tiles, progress bar, stepper (driven by real document status), dropzone, dialog, skeleton, empty state, toast, header, footer, privacy-promise card, demo tabs, flip card.

## Contrast spot checks
White on navy-900 > 15:1. Ink-500 on paper-50 about 5.9:1. Gold-300 on navy-950 > 10:1. Danger on white about 6.5:1. Re-verify with axe or Lighthouse after any token change.
