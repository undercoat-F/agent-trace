-- Read-only user for the `search` service: search never writes and never
-- runs migrations, so it should not be able to.
-- NOTE: files under /docker-entrypoint-initdb.d only run once, when the
-- data directory is first created. On an existing mysql_data volume this
-- has no effect; apply it by hand instead (see docs/environment.md).
CREATE USER IF NOT EXISTS 'agenttrace_ro'@'%' IDENTIFIED BY 'JtdiJzIUEN2Fep6ZOJubaFFP';
GRANT SELECT ON agent_trace.* TO 'agenttrace_ro'@'%';
FLUSH PRIVILEGES;
