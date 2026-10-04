# ADR-032: Two Audiences, and the Search

## Status
Accepted

## Context
The point of a knowledge base is that the person who meets an incident again can help themselves, but most of what IT writes is for IT. And a base nobody can search is a graveyard.

## Decision
- Each article has a `visibility`: `INTERNAL` (staff only) or `PUBLIC` (staff, and requesters once it is published). A **REQUESTER reads only a PUBLISHED, PUBLIC article**: anything else (a draft, an article in review, a retired one, an internal one) answers **404**, so it is not even confirmed to exist. The collection is forced to those two filters whatever they ask for, and the author filter is dropped. A requester's response leaves out the author, the reviewer, the review comment and the links to problems and incidents.
- **Every other route refuses a REQUESTER** with 403 `ERR-KNB-00403` (write, update, submit, review, publish, retire, version, versions, audit log). `X-Tenant-Id` is mandatory on every route and scopes every lookup.
- **Search is MongoDB's own text index**, no new infrastructure: `GET /retrieve?q=` over title, body and keywords (weighted 10, 1 and 5), with the organisation as an equality prefix of the index, so a search is tenant-scoped by the index itself. It combines with the filters (status, category, visibility, exact keyword, problem, incident, author). The index uses `default_language: none`: articles are written in whatever language the team uses, so no stemming or stop words are assumed. Keywords are stored lower-cased and trimmed, and the keyword filter does the same, so people do not have to match capitalisation.

## Consequences
- Positive: requesters can self-serve from reviewed public articles without a second API; nothing internal can leak through a draft or a link; real search without a search engine to run.
- Negative: relevance is basic (no typo correction, synonyms or stemming; a word must appear whole); no ranking is exposed (results are not sorted by score); the text index must exist for `q` to work (it is created at startup, fail-open).
