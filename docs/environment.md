# 環境の記録(このPCに入れたもの・変えたもの)

「何が入っているか分からなくなる」のを防ぐための一覧。導入・変更のたびに追記する。
最終更新: 2026-09-22

## PC本体

| 項目 | 内容 | 入れ方 | 削除 |
|---|---|---|---|
| JDK | Eclipse Temurin JDK 21.0.12.1 (LTS) | `winget install EclipseAdoptium.Temurin.21.JDK`(Adoptium公式のMSI、ハッシュ検証済み) | `winget uninstall EclipseAdoptium.Temurin.21.JDK` |
| 環境変数 | `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\` と、その `bin` を `Path` に追加(マシン全体) | 上のインストーラー | アンインストールで戻る |
| Node.js | LTS 24.19.0 | `winget install OpenJS.NodeJS.LTS`(公式MSI、ハッシュ検証済み)。`PATH` は自動追加 | `winget uninstall OpenJS.NodeJS.LTS` |

Maven はPCに入れていない。`backend/mvnw`(Mavenラッパー)を使うか、Dockerの中でビルドする。
ビルドの正本はDocker(`backend/Dockerfile`)で、PCのJDKはエディタ補完・デバッグ用。
VS Code の Java 拡張は各モジュールごとに `bin/` へ影のビルドを作る(`.gitignore` 済み)。重い/固まる場合は安全に削除してよい(再生成される)。

### Python の標準入出力(`PYTHONUTF8`)

`scripts/setup-python-utf8.ps1` がユーザー環境変数として設定する(`PYTHONUTF8=1`、`PYTHONIOENCODING=utf-8`。`up.ps1` からも呼ばれる)。

- **背景:** Windows の日本語ロケールでは、Python の標準入出力が既定でコンソールのコードページ(`cp932`)を使う。API のレスポンス(UTF-8で正しい)を `python -m json.tool` などで覗くと文字化けして見え、「アプリ側のバグ」と誤認しやすい。実際に本セッションでこれが起き、原因調査に時間を使った(サーバー側は最初から正しかった)。
- **対処:** システム全体のコードページ(`chcp`)は変えず、Python だけに絞って UTF-8 を強制する(影響範囲を最小にするため)。
- 反映は新しい端末から。既に開いている端末には効かない。
- 別PCでこのリポジトリを使うときも、`scripts\setup-python-utf8.ps1` を実行しておくと同じ誤診断を避けられる。

## VS Code のグローバル設定(`%APPDATA%\Code\User\settings.json`)

`scripts/setup-vscode-otel.ps1` が追記する(手で直さない。既存キーは上書きしない)。

| キー | 値 | 備考 |
|---|---|---|
| `github.copilot.chat.otel.enabled` | `true` | 既定の出力先=OTLP(コレクタ `localhost:4318`) |
| `github.copilot.chat.otel.captureContent` | `true` | プロンプト・引数・結果の本文も送る |

- 追記前に `settings.json.bak-<日時>` を同じフォルダへ作る。不要なら消してよい。
- `-FileExport` で `exporterType: file` + `outfile` を足せる。**file 出力はスパンが `{}` になる不具合があるため通常は使わない**(#319993 相当)。戻すのは `-Otlp`。
- 上記は application スコープの設定で、ワークスペースの `.vscode/settings.json` には書けない。

## Claude Code のグローバル設定(`~/.claude/settings.json`)

`scripts/setup-claude-otel.ps1` が `env` ブロックへ追記する(既存キーは上書きしない。`-Off` で元に戻す)。

| キー | 値 |
|---|---|
| `CLAUDE_CODE_ENABLE_TELEMETRY` | `1` |
| `OTEL_LOGS_EXPORTER` | `otlp` |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `http/protobuf` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` |
| `OTEL_LOG_USER_PROMPTS` | `1` |
| `OTEL_LOG_ASSISTANT_RESPONSES` | `1` |
| `OTEL_LOG_TOOL_DETAILS` | `1` |

- **プライバシー注意:** 下3つを有効にすると、プロンプト本文・応答本文・ツール引数がコレクタへ送られる。コレクタは `127.0.0.1` 限定なのでこのPCの外には出ない。
- 反映は次回の `claude` 起動から。実行中のセッションには効かない。
- settings.json 反映後の実データで確認済み: `event.name`(`user_prompt`/`tool_decision`/`tool_result`/`api_request`/`assistant_response` など)、束ねる鍵は `prompt.id`(公式ドキュメントの記述と食い違う項目あり。実データを正とする。`backend/ingest/src/test/resources/claude-code-real-sample.jsonl` に個人情報を伏せた実例あり)。

## Docker

| 名前 | 中身 | ポート | 保存先 |
|---|---|---|---|
| `agent-trace-mysql` | `mysql`(digest固定。表示バージョン26.7.0) | `127.0.0.1:3306` | volume `agent-trace_mysql_data` |
| `agent-trace-otel-collector` | `otel/opentelemetry-collector-contrib`(digest固定。表示バージョン0.161.0) | `127.0.0.1:4317`, `127.0.0.1:4318` | `otel-data/`(bind) |
| `agent-trace-ingest` | 自作(`backend/ingest`、Spring Boot 4.1.1 / Java 21) | `127.0.0.1:8081` | MySQL(`agenttrace` ユーザー、読み書き) |
| `agent-trace-search` | 自作(`backend/search`) | `127.0.0.1:8082` | MySQL(`agenttrace_ro` ユーザー、読み取り専用) |

- タグではなくダイジェストで固定している。`docker manifest inspect` がこの環境から使えなかったため、`docker buildx imagetools inspect <image>:<tag>` で解決した値を使う。バージョンを上げる場合は同じ手順で新しいダイジェストを取得し、`compose.yaml`/`backend/Dockerfile` を書き換える。
- ビルド用ベースイメージ(`eclipse-temurin` の `21-jdk`/`21-jre`)も同様にダイジェスト固定(`backend/Dockerfile`)。
- `agenttrace_ro` は `infra/mysql/init/01-search-readonly-user.sql` で定義。**この init スクリプトは新規(空)ボリュームでしか自動実行されない。** 既存ボリュームには手動適用が必要:
  ```
  docker exec -i agent-trace-mysql mysql -uroot -proot < infra/mysql/init/01-search-readonly-user.sql
  ```
  (今回のボリュームには2026-09-22に手動適用済み)
- 起動: `scripts/up.ps1`(VS Code/Claude Code の設定反映 → コレクタ設定生成 → `docker compose up -d`)。
- 設定ファイルの中身だけ変えた場合、`docker compose up -d` は再作成しない。`--force-recreate <service>` が必要。
- ビルドを繰り返すと未使用イメージ/キャッシュが溜まる。`docker image prune -f` と `docker builder prune -f` で回収可能(稼働中のコンテナには影響しない)。
- `fastapi_...`、`university-comparison-*`、`redis` などは別プロジェクトのイメージ/コンテナで、このリポジトリとは無関係。

## データの置き場所

| 場所 | 内容 | git |
|---|---|---|
| `otel-data/<agent>/{traces,logs,metrics}.jsonl` | コレクタが受けた生ペイロード(原本)。追記モード | 対象外 |
| `otel-data/claude/hook-logs.jsonl` | Claude のフックログ(`jsonoutputtest/hook-log.jsonl` から) | 対象外 |
| `otel-data/_checkpoints/` | フックログの読み取り位置(再起動で重複させないため) | 対象外 |
| MySQL `agent_trace`.`raw_payloads` | 生JSON、SHA-256で重複排除。全ての元データ | volume |
| MySQL `agent_trace`.`spans` | Copilot などのOTLPトレースを展開したもの(`trace_id`/`span_id` 単位) | volume |
| MySQL `agent_trace`.`events`/`prompts` | Claude Code のログ形式テレメトリを展開したもの(`prompt_id` 単位で束ねる)。`prompts` は `events` からの導出データで、都度再集計される | volume |
| MySQL `agent_trace`.`commits` | `PostToolUse` フック(`scripts/hook-detect-commit.ps1`)で検出した git commit。`prompt_id` で `events`/`prompts` に繋がる | volume |
| リポジトリ直下 `.env` | `jevkey=` のプレースホルダー(Jev用と思われる)。`.gitignore` 済み、中身は触っていない | 対象外 |

## 既知の注意点

- **コレクタの `file` エクスポータは既定で起動時にファイルを空にする。** 生成設定は全て `append: true` にしてある。この設定を外さないこと。
- `metrics.jsonl` は繰り返しのスナップショットで肥大化しやすい(以前は約116MB/日)。保管期間の方針は未決定。
- **重複排除と再展開は別物。** `raw_payloads` は本文のSHA-256で重複排除するので、ingest 側のパーサーを直したあとに同じ内容を再送しても、重複と判定されて再展開されない。過去分に新しいパーサーを当て直す「リプレイ」の仕組みは未実装。
- 読み取り専用ユーザーのパスワード(`agenttrace_ro`)は `compose.yaml` に平文で書いてある。既存の `agenttrace`/`root` と同じ扱い(ローカル1人利用が前提)。
