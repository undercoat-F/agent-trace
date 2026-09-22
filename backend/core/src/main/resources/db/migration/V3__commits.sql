-- The reverse-lookup chain (concept doc §3): commit SHA -> prompt_id -> the
-- prompts/events that produced it. Captured by a PostToolUse hook
-- (scripts/hook-detect-commit.ps1), NOT from OTel: real Claude Code OTel
-- output never includes a git commit SHA. prompt_id/session_id here are
-- verified (a controlled real run) to be the exact same UUIDs stored in
-- events.prompt_id / events.session_id, so joining works directly.
CREATE TABLE commits (
    sha            CHAR(40)     NOT NULL,
    branch         VARCHAR(255) NULL,
    message        TEXT         NULL,
    -- Nullable: a commit made without an active Claude Code turn (by hand, or
    -- before this hook existed) still deserves a row -- just without a link.
    prompt_id      CHAR(36)     NULL,
    session_id     CHAR(36)     NULL,
    files_changed  JSON         NULL,
    committed_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    raw_payload_id BIGINT       NOT NULL,
    PRIMARY KEY (sha),
    KEY ix_commits_prompt (prompt_id),
    CONSTRAINT fk_commits_raw FOREIGN KEY (raw_payload_id) REFERENCES raw_payloads (id)
);
