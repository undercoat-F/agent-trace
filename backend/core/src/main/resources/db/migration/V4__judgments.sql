-- concept doc §7/§8: Jev runs at ingest time (principle 2), not at search
-- time. One row per (prompt_id, question_id, question_version): re-judging
-- the same question at the same version overwrites (idempotent, mirrors
-- commits/prompts); a later question_version coexists with the old rows so
-- past judgments stay comparable (§7's stated reason for versioning).
CREATE TABLE judgments (
    prompt_id        CHAR(36)     NOT NULL,
    question_id      VARCHAR(64)  NOT NULL,
    question_version INT          NOT NULL,
    -- Noul: the probability itself. Choice: probability of the chosen option.
    -- Score: the (possibly fractional) score. See jev.Answer.value().
    value            DOUBLE       NOT NULL,
    confidence       DOUBLE       NOT NULL,
    model_version    VARCHAR(64)  NOT NULL,
    -- The type-specific extras (choice label, score legend, full probability
    -- distribution) -- principle 1 applied to judgments too: never discard
    -- what Jev actually returned just because `value`/`confidence` summarize it.
    raw_answer       JSON         NOT NULL,
    judged_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (prompt_id, question_id, question_version),
    KEY ix_judgments_question (question_id, question_version)
);
