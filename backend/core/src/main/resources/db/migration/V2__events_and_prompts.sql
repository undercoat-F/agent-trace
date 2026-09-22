-- Claude Code's own OTel output is log-shaped, not span-shaped: real capture
-- (backend/ingest/src/test/resources/claude-code-real-sample.jsonl, redacted)
-- shows LogRecords with traceId/spanId always absent and a `prompt.id`
-- attribute (a UUID) as the actual correlation key, confirming concept doc
-- §12-1. `event.sequence` is a per-session monotonic counter (not per-prompt),
-- so (session_id, event_sequence) is the natural, content-stable identity for
-- one event -- used as the primary key instead of inventing a random id,
-- which would break idempotency across redelivery.

CREATE TABLE events (
    event_id       VARCHAR(80)  NOT NULL,   -- session_id ':' event_sequence
    session_id     CHAR(36)     NOT NULL,
    event_sequence INT          NOT NULL,
    prompt_id      CHAR(36)     NULL,       -- absent on pre-turn events (managed_settings_resolved, plugin_loaded, ...)
    event_name     VARCHAR(64)  NOT NULL,   -- user_prompt | assistant_response | tool_decision | tool_result | api_request | ...
    occurred_at    DATETIME(6)  NOT NULL,
    attributes     JSON         NOT NULL,
    raw_payload_id BIGINT       NOT NULL,
    tool_name      VARCHAR(128) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.tool_name'))) STORED,
    -- "true"/"false" as a JSON *string*, not a JSON boolean, in the real payload.
    tool_success   VARCHAR(8)   GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.success'))) STORED,
    PRIMARY KEY (event_id),
    KEY ix_events_prompt (prompt_id),
    KEY ix_events_session (session_id),
    KEY ix_events_name (event_name),
    KEY ix_events_occurred (occurred_at),
    CONSTRAINT fk_events_raw FOREIGN KEY (raw_payload_id) REFERENCES raw_payloads (id)
);

-- One row per prompt_id, kept in sync with `events` by re-aggregating on every
-- ingest of a new event for that prompt_id (see PromptAggregator). Treat this
-- as a rebuildable projection of `events`, same spirit as spans' generated
-- columns -- never hand-edited, always derivable from raw_payloads again.
CREATE TABLE prompts (
    prompt_id           CHAR(36)     NOT NULL,
    session_id           CHAR(36)     NOT NULL,
    user_prompt          TEXT         NULL,   -- only present if OTEL_LOG_USER_PROMPTS=1
    assistant_response   TEXT         NULL,   -- only present if OTEL_LOG_ASSISTANT_RESPONSES=1
    started_at           DATETIME(6)  NOT NULL,
    ended_at             DATETIME(6)  NOT NULL,
    tool_calls           INT          NOT NULL DEFAULT 0,
    failures             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (prompt_id),
    KEY ix_prompts_session (session_id),
    KEY ix_prompts_started (started_at)
);
