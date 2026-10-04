# ADR-031: Links Are References, Held by the Article

## Status
Accepted

## Context
An article usually explains a known error (a problem) or helps with a kind of incident. Validating the ids against the services that own them would make publishing knowledge depend on those services being up, and would require a call to read the workaround from the problem on every read.

## Decision
- The article stores `relatedProblemIds` and `relatedIncidentIds` as ids (up to 100 of each kind). This service calls no other service (only the hash registry for sovereign ids); an id that does not exist is not refused.
- **The workaround is written in the article, not read from the problem.** The article is a reviewed, versioned text; reading the problem's current workaround would bypass the review and make the article change under the reader.
- The question "what do we know about this problem / incident" is answered from this side: `GET /retrieve?problemId=` and `?incidentId=`, served by multikey indexes. Neither the problem service nor the incident service changed.
- For a REQUESTER the links, like the people, are left out of the response (ADR-032): internal ids are not their business.

## Consequences
- Positive: publishing never fails because another service is down; no service had to change; the text is exactly what was reviewed.
- Negative: when a problem's workaround changes, someone must update the article (a new version); a mistyped id is accepted; there is no event telling an article that its problem was resolved (notification is a later journey).
