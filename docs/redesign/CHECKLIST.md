# Redesign checklist

Legend: [x] done by redesign.pl, [ ] needs a person or a browser.

## Pages and states
| Page | Restyled | Empty | Error | Loading / progress | Long content |
|---|---|---|---|---|---|
| Landing (`/`) | [x] | n/a | [x] AI-not-configured notice | n/a | [x] |
| Privacy (`/privacy`) | [x] | n/a | n/a | n/a | [x] |
| Sign in, Create account | [x] | n/a | [x] | n/a | n/a |
| Dashboard | [x] | [x] | [x] | n/a | [x] table scroll |
| Documents list, Upload | [x] | [x] | [x] | [x] upload bar | [x] |
| Document detail | [x] | [x] | [x] retry | [x] stepper from real status | [x] |
| Study: new, generating | [x] | n/a | [x] | [x] real percent | n/a |
| Study package tabs | [x] | [x] | n/a | n/a | [x] 70ch measure |
| Flashcards, Practice | [x] | [x] | [x] | n/a | [x] |
| Mock exam intro, take, results | [x] | n/a | n/a | n/a | [x] |
| Tutor | [x] | [x] | [x] | [x] | [x] |
| 403 and error pages | [x] | n/a | n/a | n/a | n/a |

## To verify in a browser (not automatable from this script)
- [ ] Screenshots at 375, 768, 1440 px into `docs/redesign/before` (git stash or main) and `docs/redesign/after`
- [ ] axe or Lighthouse: no serious violations on any page
- [ ] Keyboard-only run: sign-up, upload, each study tab, mock exam
- [ ] Lighthouse mobile: LCP under 2.5 s, CLS under 0.1
- [ ] Print preview of a summary and the practice questions
- [ ] `grep -rEi '#[0-9a-f]{3,8}\b' src/main/resources/static/css/app.css` returns nothing
