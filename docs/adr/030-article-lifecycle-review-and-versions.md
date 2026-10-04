# ADR-030: The Article Lifecycle, Review by Another Person, and Versions

## Status
Accepted

## Context
A knowledge article is only worth reading if people can trust it, and an outdated or wrong workaround served to someone who hits an incident again does harm. Letting an author publish their own text, or editing a published article in place, makes both easy.

## Decision
- Status `DRAFT -> IN_REVIEW -> PUBLISHED -> RETIRED`. Content is editable **only in DRAFT**. An author **submits** it for review; a reviewer **publishes** it or **returns** it to DRAFT with a mandatory comment (kept on the article until it is submitted again).
- **Segregation of duties:** the reviewer must not be the author (the author is the `X-Executor` who drafted it). Publishing or returning one's own article is refused with 409 `ERR-KNB-00409` ("Compliance Violation"), the same way workflow-approval refuses an ineligible approver. The platform has no per-article reviewer list: any other staff member can review.
- **A published article is never edited in place.** `version/initiate` on a PUBLISHED article creates a **new document** with the same `articleKey`, `version + 1`, the same content, in DRAFT, authored by whoever asked (so the author of the published one can review the new one). The published version keeps serving until the new one is published; **publishing it retires the older published versions** (`SUPERSEDED`). That second step is **best effort**: a failure is logged and never undoes the publication (an older version left published can be retired by hand).
- A unique `(organisationId, articleKey, version)` index is the atomic backstop of "one document per version": two people starting the same new version at once, one wins and the other gets a 409.
- `retire` withdraws a draft, an article in review or a published one (terminal). `GET /{id}/versions/retrieve` lists every version of an article, oldest first (staff).

## Consequences
- Positive: the served text was reviewed by somebody else; a published text never changes under the reader; the history of versions is explicit.
- Negative: a typo in a published article needs a whole new version through review; for a short time after a publish both versions may be published if the supersede step fails; there is no diff between versions, only the text of each.
