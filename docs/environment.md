# 環境の記録(このPCに入れたもの・変えたもの)

「何が入っているか分からなくなる」のを防ぐための一覧。導入・変更のたびに追記する。
最終更新: 2026-09-22

## PC本体

| 項目 | 内容 | 入れ方 | 削除 |
|---|---|---|---|
| JDK | Eclipse Temurin JDK 21.0.12.1 (LTS) | `winget install EclipseAdoptium.Temurin.21.JDK`(Adoptium公式のMSI、ハッシュ検証済み) | `winget uninstall EclipseAdoptium.Temurin.21.JDK` |
| 環境変数 | `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\` と、その `bin` を `Path` に追加(マシン全体) | 上のインストーラー | アンインストールで戻る |

Maven はPCに入れていない。`backend/mvnw`(Mavenラッパー)を使うか、Dockerの中でビルドする。
ビルドの正本はDocker(`backend/Dockerfile`)で、PCのJDKはエディタ補完・デバッグ用。

## VS Code のグローバル設定(`%APPDATA%\Code\User\settings.json`)

`scripts/setup-vscode-otel.ps1` が追記する(手で直さない。既存キーは上書きしない)。

| キー | 値 | 備考 |
|---|---|---|
| `github.copilot.chat.otel.enabled` | `true` | 既定の出力先=OTLP(コレクタ `localhost:4318`) |
| `github.copilot.chat.otel.captureContent` | `true` | プロンプト・引数・結果の本文も送る |

- 追記前に `settings.json.bak-<日時>` を同じフォルダへ作る(現在3つ)。不要なら消してよい。
- `-FileExport` で `exporterType: file` + `outfile` を足せる。**file 出力はスパンが `{}` になる不具合があるため通常は使わない**(#319993 相当)。戻すのは `-Otlp`。
- 上記は application スコープの設定で、ワークスペースの `.vscode/settings.json` には書けない。

## Docker

| 名前 | 中身 | ポート | 保存先 |
|---|---|---|---|
| `agent-trace-mysql` | `mysql:latest`(現在 26.7。タグ未固定) | `0.0.0.0:3306` | volume `agent-trace_mysql_data` |
| `agent-trace-otel-collector` | `otel/opentelemetry-collector-contrib:latest`(v0.161系) | `0.0.0.0:4317`, `0.0.0.0:4318` | `otel-data/`(bind) |
| `agent-trace-ingest` | 自作(`backend/`、Spring Boot 4.1.1 / Java 21) | `127.0.0.1:8081` | MySQL |

- ビルド時に `eclipse-temurin:21-jdk`(ビルド用)と `21-jre`(実行用)を取得する。
- 起動: `scripts/up.ps1`(設定反映 → コレクタ設定生成 → `docker compose up -d`)。
- 設定ファイルの中身だけ変えた場合、`docker compose up -d` は再作成しない。`--force-recreate otel-collector` が必要。
- `university-comparison-site_*`(caddy / redis)は別プロジェクトのもので、このリポジトリとは無関係。

## データの置き場所

| 場所 | 内容 | git |
|---|---|---|
| `otel-data/<agent>/{traces,logs,metrics}.jsonl` | コレクタが受けた生ペイロード(原本)。追記モード | 対象外 |
| `otel-data/claude/hook-logs.jsonl` | Claude のフックログ(`jsonoutputtest/hook-log.jsonl` から) | 対象外 |
| `otel-data/_checkpoints/` | フックログの読み取り位置(再起動で重複させないため) | 対象外 |
| MySQL `agent_trace` | `raw_payloads`(生JSON、SHA-256で重複排除)、`spans` | volume |

## 既知の注意点

- **コレクタの `file` エクスポータは既定で起動時にファイルを空にする。** 生成設定は全て `append: true` にしてある。この設定を外さないこと。
- MySQL(3306)とコレクタ(4317/4318)は `0.0.0.0` に公開されている。パスワードは `agenttrace`/`root` なので、他人と同じネットワークでは `127.0.0.1:` 付きに変更したほうがよい(未対応)。
- `metrics.jsonl` は繰り返しのスナップショットで肥大化しやすい(以前は約116MB/日)。保管期間の方針は未決定。
