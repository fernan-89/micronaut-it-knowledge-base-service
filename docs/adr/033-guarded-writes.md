# ADR-033: Every Change Is a Guarded Write

## Status
Accepted

## Context
Two people could silently overwrite each other: an author submits while an editor retires, or two reviewers publish and return the same article.

## Decision
- The repository saves the state the aggregate reached together with its audit entry in **one atomic update, and only while the article still has the status it had when it was loaded**. If someone else moved it in between, the write matches nothing and the caller gets 409 `ERR-KNB-00409` (read again and retry), never a lost update.
- Creating a version is guarded by the unique `(organisationId, articleKey, version)` index, mapped from the duplicate-key error to the same 409.
- Indexes are created at startup (fail-open, idempotent, `thinklab.mongo.create-indexes=false` turns them off): the unique version index, `(organisationId, status)`, the multikey `relatedProblemIds`, `relatedIncidentIds` and `keywords`, and the text index (ADR-032).

## Consequences
- Positive: no lost updates, no partial writes, no duplicate version.
- Negative: the guard is on the status, so two edits that do not change it (two updates of a draft at the same instant) are last-writer-wins, which is acceptable for fields meant to be overwritten.
