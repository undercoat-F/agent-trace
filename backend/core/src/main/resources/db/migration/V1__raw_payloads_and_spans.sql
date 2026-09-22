-- Principle 1 (docs/agent-trace-concept.md): never throw the raw payload away.
-- raw_payloads keeps every OTLP/JSON request body as received; spans is a
-- derived, re-buildable projection of it. body_sha256 makes re-delivery of the
-- same payload (collector restarts, retries, several ingest instances) a no-op.
CREATE TABLE raw_payloads (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    received_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    signal_type VARCHAR(16)  NOT NULL,          -- traces | logs (not "signal": reserved word in MySQL)
    body_sha256 CHAR(64)     NOT NULL,          -- sha-256 of the request body text
    body        JSON         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_raw_payloads_sha (body_sha256)
);

-- One row per OTLP span. attributes is a flat JSON object {key: value}.
-- Times are UTC. status_code: 0 UNSET, 1 OK, 2 ERROR.
CREATE TABLE spans (
    trace_id       CHAR(32)     NOT NULL,
    span_id        CHAR(16)     NOT NULL,
    parent_span_id CHAR(16)     NULL,
    name           VARCHAR(255) NOT NULL,
    kind           INT          NOT NULL DEFAULT 0,
    service_name   VARCHAR(128) NULL,
    start_time     DATETIME(6)  NOT NULL,
    end_time       DATETIME(6)  NULL,
    duration_ms    BIGINT       NULL,
    status_code    INT          NOT NULL DEFAULT 0,
    status_message TEXT         NULL,
    attributes     JSON         NOT NULL,
    raw_payload_id BIGINT       NOT NULL,
    -- deterministic filter columns lifted out of attributes (cheap to add more later)
    tool_name      VARCHAR(128) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(attributes, '$."gen_ai.tool.name"'))) STORED,
    error_type     VARCHAR(128) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(attributes, '$."error.type"'))) STORED,
    PRIMARY KEY (trace_id, span_id),
    KEY ix_spans_start (start_time),
    KEY ix_spans_name (name),
    KEY ix_spans_tool (tool_name),
    KEY ix_spans_status (status_code),
    CONSTRAINT fk_spans_raw FOREIGN KEY (raw_payload_id) REFERENCES raw_payloads (id)
);
