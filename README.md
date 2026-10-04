# micronaut-it-knowledge-base-service

BIAN-aligned Service Domain **it-knowledge-base** (Control Record: `Article`), port `8101`.

What IT knows, written down so that the next person who hits the same thing can find it: typically the workaround of a known error. The
fourth service of the ITSM core (Journey 12), after incidents, service requests and problems. An article has a title, a body, a category,
keywords, a **visibility** (internal or public), links to the problems and incidents it relates to, and a status that follows a review
workflow, with versions.

## What it guarantees, and what it does not

- **Reviewed by somebody else** (ADR-030): `DRAFT -> IN_REVIEW -> PUBLISHED -> RETIRED`. The author submits; a different person publishes
  or returns it with a comment (the author cannot publish their own article: 409). Content is editable only in DRAFT.
- **Versions, never edited in place** (ADR-030): a new version of a published article is a new draft with the same key; the published one
  keeps serving until the new one is published, which retires the older versions (best effort).
- **Two audiences** (ADR-032): a REQUESTER reads only published **public** articles (anything else is a 404) and sees them without the
  people and links; every other route refuses them with 403 `ERR-KNB-00403`.
- **Search** (ADR-032): `GET /retrieve?q=` over title, body and keywords with MongoDB's text index, tenant-scoped by the index itself,
  combined with filters (status, category, visibility, keyword, problem, incident, author).
- **Links are references** (ADR-031): problem and incident ids are stored, not validated, and **the workaround is written in the article,
  not read from the problem**, so publishing never depends on another service and the text is what was reviewed.
- **No lost updates** (ADR-033): every change is one guarded write that also appends its audit entry; the loser of a race gets 409.
- **Not here yet:** feedback on whether an article helped, attachments, an event when a problem is resolved, ranking or typo-tolerant
  search, expiry or review reminders, events, and external ticketing connectors.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call and scopes it (another tenant's article answers 404); `X-Executor` is mandatory on the actions
(the author of a draft, the reviewer of a publication); `X-Role` is optional and, with platform security on, comes from the verified token.

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-knowledge-base/v1/initiate` `{"title":"Switch drops packets","body":"Reboot it every Sunday","category":"NETWORK","keywords":["switch","firmware"],"visibility":"PUBLIC","relatedProblemIds":["<uuid>"],"relatedIncidentIds":[]}` (staff; the caller is the author) |
| retrieve | `GET /it-knowledge-base/v1/{id}/retrieve` (a REQUESTER: published public only) |
| retrieve (collection) | `GET /it-knowledge-base/v1/retrieve?q=&status=&category=&visibility=&keyword=&problemId=&incidentId=&authorId=` (a REQUESTER is forced to published public) |
| versions/retrieve | `GET /it-knowledge-base/v1/{id}/versions/retrieve` (every version of the article, oldest first; staff) |
| update | `PUT /it-knowledge-base/v1/{id}/update` (title, body, category, keywords, visibility, links; DRAFT only) |
| control/submit | `PUT /it-knowledge-base/v1/{id}/control/submit` (DRAFT -> IN_REVIEW) |
| control/publish | `PUT /it-knowledge-base/v1/{id}/control/publish` (IN_REVIEW -> PUBLISHED; the caller must not be the author) |
| control/return | `PUT /it-knowledge-base/v1/{id}/control/return` `{"comment":"Say which firmware"}` (IN_REVIEW -> DRAFT; the caller must not be the author) |
| control/retire | `PUT /it-knowledge-base/v1/{id}/control/retire` (DRAFT, IN_REVIEW or PUBLISHED -> RETIRED, terminal) |
| version/initiate | `POST /it-knowledge-base/v1/{id}/version/initiate` (PUBLISHED -> a new DRAFT version, 201) |
| audit-log/retrieve | `GET /it-knowledge-base/v1/{id}/audit-log/retrieve` (staff only) |

```text
DRAFT -> IN_REVIEW -> PUBLISHED -> RETIRED (terminal)         IN_REVIEW --return--> DRAFT
DRAFT | IN_REVIEW | PUBLISHED --retire--> RETIRED            PUBLISHED --a newer version is published--> RETIRED (SUPERSEDED)
```

```bash
curl "http://localhost:8101/it-knowledge-base/v1/retrieve?q=firmware" -H "X-Tenant-Id: <organisationId>" -H "X-Role: REQUESTER" -H "X-Executor: <userId>"
# [{"title":"Switch drops packets","status":"PUBLISHED","visibility":"PUBLIC","version":2,"body":"Reboot it every Sunday",...}]
```

Do not put personal data or credentials in an article: it is stored with the article and a public one is read by every requester.

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-KNB-00403` | 403 | A REQUESTER tried a staff action (ADR-032) |
| `ERR-KNB-00404` | 404 | Article not found (another tenant's, or one a requester may not read, answers the same) |
| `ERR-KNB-00409` | 409 | Illegal transition, the author reviewing their own article, a version that already exists, or the article changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure (blank title or body, a bad keyword, a return without a comment...) |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 the article lifecycle, review by another person, and versions · 031 links are references · 032 two audiences and the search ·
033 guarded writes.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
