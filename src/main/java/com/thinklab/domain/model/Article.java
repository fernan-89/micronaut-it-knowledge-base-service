package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidArticleStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Core Domain Model representing the Article Aggregate Root (BIAN Service Domain: {@code it-knowledge-base}): a piece of knowledge IT
 * wants people to find ("what to do when the floor-3 switch drops packets"), typically the workaround of a known error.
 *
 * <p><b>Lifecycle (ADR-030):</b> {@code DRAFT -> IN_REVIEW -> PUBLISHED -> RETIRED}. An article is written in DRAFT (the only state where
 * its content can be edited), submitted for review, and then <b>published by somebody other than its author</b> (segregation of duties:
 * nobody publishes what they wrote themselves) or returned to DRAFT with the reviewer's comment. A published article is withdrawn with
 * {@code retire}, which is terminal (so is a draft that was never wanted).
 *
 * <p><b>Versions (ADR-030):</b> a published article is never edited in place. A <i>new version</i> is a new document sharing the
 * {@code articleKey} with {@code version + 1}, born in DRAFT with the same content; the published one keeps serving until the new one is
 * published, and publishing it retires the older versions ({@code SUPERSEDED}).
 *
 * <p><b>Two audiences (ADR-032):</b> {@code visibility} is {@code INTERNAL} (staff) or {@code PUBLIC} (staff and requesters, once
 * published). Links to the problems and incidents an article relates to are references (ADR-031), never validated against the services
 * that own them.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class Article {

    public static final int MAX_KEYWORDS = 20;
    public static final int MAX_KEYWORD_LENGTH = 40;
    public static final int MAX_LINKS = 100;

    private final UUID id;
    private final UUID organisationId;
    private final UUID articleKey;
    private final int version;
    private String title;
    private String body;
    private String category;
    private Set<String> keywords;
    private Visibility visibility;
    private ArticleStatus status;
    private final String authorId;
    private String reviewerId;
    private String reviewComment;
    private Instant publishedAt;
    private Set<UUID> relatedProblemIds;
    private Set<UUID> relatedIncidentIds;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<ArticleAuditEntry> auditTrail;

    private Article(UUID id, UUID organisationId, UUID articleKey, int version, String title, String body, String category, Set<String> keywords,
                    Visibility visibility, Set<UUID> problems, Set<UUID> incidents, String authorId, String initialDetail) {
        this.id = id;
        this.organisationId = organisationId;
        this.articleKey = articleKey;
        this.version = version;
        this.title = title;
        this.body = body;
        this.category = category;
        this.keywords = new LinkedHashSet<>(keywords);
        this.visibility = visibility;
        this.status = ArticleStatus.DRAFT;
        this.authorId = authorId;
        this.relatedProblemIds = new LinkedHashSet<>(problems);
        this.relatedIncidentIds = new LinkedHashSet<>(incidents);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new ArticleAuditEntry(this.createdAt, "INITIATED", authorId, null, ArticleStatus.DRAFT, initialDetail));
    }

    private Article(UUID id, UUID organisationId, UUID articleKey, int version, String title, String body, String category, Set<String> keywords,
                    Visibility visibility, ArticleStatus status, String authorId, String reviewerId, String reviewComment, Instant publishedAt,
                    Set<UUID> problems, Set<UUID> incidents, Instant createdAt, Instant updatedAt, List<ArticleAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.articleKey = articleKey;
        this.version = version;
        this.title = title;
        this.body = body;
        this.category = category;
        this.keywords = keywords != null ? new LinkedHashSet<>(keywords) : new LinkedHashSet<>();
        this.visibility = visibility;
        this.status = status != null ? status : ArticleStatus.DRAFT;
        this.authorId = authorId;
        this.reviewerId = reviewerId;
        this.reviewComment = reviewComment;
        this.publishedAt = publishedAt;
        this.relatedProblemIds = problems != null ? new LinkedHashSet<>(problems) : new LinkedHashSet<>();
        this.relatedIncidentIds = incidents != null ? new LinkedHashSet<>(incidents) : new LinkedHashSet<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    /** A first version: the id is also the key shared by every later version. */
    public static Article createNew(UUID id, UUID organisationId, String title, String body, String category, Set<String> keywords, Visibility visibility,
                                    Set<UUID> problemIds, Set<UUID> incidentIds, String author) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for Article creation.");
        }
        validate(title, body, visibility);
        Set<String> normalised = normalise(keywords);
        Set<UUID> problems = links(problemIds);
        Set<UUID> incidents = links(incidentIds);
        requireExecutor(author);
        return new Article(id, organisationId, id, 1, title, body, category, normalised, visibility, problems, incidents, author, "Article drafted.");
    }

    /**
     * A new version of a PUBLISHED article: a new document with the same key, {@code version + 1}, the same content, in DRAFT, authored by
     * whoever asked for it (so the author of the published one may review it).
     */
    public static Article nextVersionOf(Article published, UUID newId, String author) {
        Objects.requireNonNull(published, "The published article is mandatory to start a new version.");
        if (newId == null) {
            throw new IllegalArgumentException("ID is mandatory for Article creation.");
        }
        published.requireStatus(ArticleStatus.PUBLISHED);
        requireExecutor(author);
        return new Article(newId, published.organisationId, published.articleKey, published.version + 1, published.title, published.body, published.category,
                published.keywords, published.visibility, published.relatedProblemIds, published.relatedIncidentIds, author,
                "Version " + (published.version + 1) + " drafted from version " + published.version + ".");
    }

    public static Article reconstitute(UUID id, UUID organisationId, UUID articleKey, int version, String title, String body, String category,
                                       Set<String> keywords, Visibility visibility, ArticleStatus status, String authorId, String reviewerId,
                                       String reviewComment, Instant publishedAt, Set<UUID> problemIds, Set<UUID> incidentIds, Instant createdAt,
                                       Instant updatedAt, List<ArticleAuditEntry> auditTrail) {
        if (id == null || organisationId == null || articleKey == null || title == null || visibility == null || authorId == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Key, Title, Visibility and Author are mandatory to reconstitute an Article.");
        }
        return new Article(id, organisationId, articleKey, version, title, body, category, keywords, visibility, status, authorId, reviewerId,
                reviewComment, publishedAt, problemIds, incidentIds, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. The content and the links, only while the article is a DRAFT. */
    public ArticleAuditEntry updateContent(String newTitle, String newBody, String newCategory, Set<String> newKeywords, Visibility newVisibility,
                                           Set<UUID> problemIds, Set<UUID> incidentIds, String executor) {
        requireStatus(ArticleStatus.DRAFT);
        validate(newTitle, newBody, newVisibility);
        Set<String> normalised = normalise(newKeywords);
        Set<UUID> problems = links(problemIds);
        Set<UUID> incidents = links(incidentIds);
        this.title = newTitle;
        this.body = newBody;
        this.category = newCategory;
        this.keywords = new LinkedHashSet<>(normalised);
        this.visibility = newVisibility;
        this.relatedProblemIds = new LinkedHashSet<>(problems);
        this.relatedIncidentIds = new LinkedHashSet<>(incidents);
        return record("UPDATED", executor, "Content updated.");
    }

    /** Behavior Qualifier: {@code control/submit}. DRAFT -&gt; IN_REVIEW; the earlier review comment is dropped. */
    public ArticleAuditEntry submit(String executor) {
        requireStatus(ArticleStatus.DRAFT);
        this.reviewComment = null;
        return transition(ArticleStatus.IN_REVIEW, "SUBMITTED", executor, "Submitted for review.");
    }

    /** Behavior Qualifier: {@code control/publish}. IN_REVIEW -&gt; PUBLISHED, by somebody other than the author (segregation of duties). */
    public ArticleAuditEntry publish(String reviewer) {
        requireStatus(ArticleStatus.IN_REVIEW);
        requireReviewerIsNotTheAuthor(reviewer, "publish");
        this.reviewerId = reviewer;
        this.publishedAt = Instant.now();
        return transition(ArticleStatus.PUBLISHED, "PUBLISHED", reviewer, "Published by the reviewer.");
    }

    /** Behavior Qualifier: {@code control/return}. IN_REVIEW -&gt; DRAFT with what to change; by somebody other than the author. */
    public ArticleAuditEntry returnForChanges(String comment, String reviewer) {
        requireStatus(ArticleStatus.IN_REVIEW);
        requireReviewerIsNotTheAuthor(reviewer, "return");
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("A comment saying what to change is mandatory to return an Article.");
        }
        this.reviewerId = reviewer;
        this.reviewComment = comment;
        return transition(ArticleStatus.DRAFT, "RETURNED", reviewer, "Returned for changes: " + comment);
    }

    /** Behavior Qualifier: {@code control/retire}. DRAFT, IN_REVIEW or PUBLISHED -&gt; RETIRED (terminal). */
    public ArticleAuditEntry retire(String executor) {
        requireStatus(ArticleStatus.DRAFT, ArticleStatus.IN_REVIEW, ArticleStatus.PUBLISHED);
        return transition(ArticleStatus.RETIRED, "RETIRED", executor, "Retired.");
    }

    /** A newer version was published: this one stops serving. PUBLISHED -&gt; RETIRED, recorded as SUPERSEDED. */
    public ArticleAuditEntry supersede(String executor) {
        requireStatus(ArticleStatus.PUBLISHED);
        return transition(ArticleStatus.RETIRED, "SUPERSEDED", executor, "Superseded by a newer version.");
    }

    /** Whether a REQUESTER may read it: published, and meant for them (ADR-032). */
    public boolean isReadableByRequesters() {
        return status == ArticleStatus.PUBLISHED && visibility == Visibility.PUBLIC;
    }

    // --- Internal helpers ---

    private ArticleAuditEntry transition(ArticleStatus newStatus, String action, String executor, String detail) {
        requireExecutor(executor);
        ArticleStatus previous = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();
        ArticleAuditEntry entry = new ArticleAuditEntry(this.updatedAt, action, executor, previous, newStatus, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private ArticleAuditEntry record(String action, String executor, String detail) {
        requireExecutor(executor);
        this.updatedAt = Instant.now();
        ArticleAuditEntry entry = new ArticleAuditEntry(this.updatedAt, action, executor, this.status, this.status, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(ArticleStatus... allowed) {
        if (Arrays.asList(allowed).contains(this.status)) {
            return;
        }
        throw new InvalidArticleStatusException(String.format(
                "Illegal transition: Article is [%s], expected one of %s.", this.status, Arrays.toString(allowed)));
    }

    private void requireReviewerIsNotTheAuthor(String reviewer, String operation) {
        requireExecutor(reviewer);
        if (reviewer.equals(authorId)) {
            throw new InvalidArticleStatusException(String.format(
                    "Compliance Violation: the author [%s] cannot %s their own article; somebody else must review it.", reviewer, operation));
        }
    }

    private static void validate(String title, String body, Visibility visibility) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is mandatory for an Article.");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Body is mandatory for an Article.");
        }
        if (visibility == null) {
            throw new IllegalArgumentException("Visibility is mandatory for an Article.");
        }
    }

    /** Keywords are compared the way people type them: trimmed, lower-cased, without repeats. */
    private static Set<String> normalise(Set<String> keywords) {
        Set<String> result = new LinkedHashSet<>();
        for (String keyword : keywords == null ? Set.<String>of() : keywords) {
            String clean = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
            if (clean.isEmpty() || clean.length() > MAX_KEYWORD_LENGTH) {
                throw new IllegalArgumentException("A keyword is 1 to " + MAX_KEYWORD_LENGTH + " characters.");
            }
            result.add(clean);
        }
        if (result.size() > MAX_KEYWORDS) {
            throw new IllegalArgumentException("An article can have at most " + MAX_KEYWORDS + " keywords.");
        }
        return result;
    }

    private static Set<UUID> links(Set<UUID> ids) {
        Set<UUID> given = ids == null ? Set.of() : ids;
        if (given.size() > MAX_LINKS) {
            throw new IllegalArgumentException("An article can link at most " + MAX_LINKS + " ids of each kind.");
        }
        return given;
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable Article mutations.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getArticleKey() { return articleKey; }
    public int getVersion() { return version; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getCategory() { return category; }
    public Set<String> getKeywords() { return Collections.unmodifiableSet(keywords); }
    public Visibility getVisibility() { return visibility; }
    public ArticleStatus getStatus() { return status; }
    public String getAuthorId() { return authorId; }
    public String getReviewerId() { return reviewerId; }
    public String getReviewComment() { return reviewComment; }
    public Instant getPublishedAt() { return publishedAt; }
    public Set<UUID> getRelatedProblemIds() { return Collections.unmodifiableSet(relatedProblemIds); }
    public Set<UUID> getRelatedIncidentIds() { return Collections.unmodifiableSet(relatedIncidentIds); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ArticleAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    /**
     * <pre>
     * DRAFT -&gt; IN_REVIEW -&gt; PUBLISHED -&gt; RETIRED (terminal)         IN_REVIEW --return--&gt; DRAFT
     * DRAFT, IN_REVIEW, PUBLISHED --retire--&gt; RETIRED          PUBLISHED --newer version published--&gt; RETIRED (SUPERSEDED)
     * </pre>
     */
    public enum ArticleStatus { DRAFT, IN_REVIEW, PUBLISHED, RETIRED }

    /** INTERNAL is for staff only; PUBLIC is also read by requesters once published. */
    public enum Visibility { INTERNAL, PUBLIC }

    /** Immutable forensic ledger entry, mirroring the platform's established audit-trail pattern. */
    public record ArticleAuditEntry(Instant occurredAt, String action, String executor, ArticleStatus fromStatus, ArticleStatus toStatus, String detail) {}
}
