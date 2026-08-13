# AI Steel Thread Demo

Standalone Fluxnova Spring Boot demo for AI agentic ad-hoc subprocesses (loan approval).

**Presenter materials:** [DEMO.md](DEMO.md) (click path) and [NARRATION.md](NARRATION.md) (spoken script), also under **Demo guide** in the Launch UI.

**Roadmap:** [ROADMAP.md](ROADMAP.md) — work is tracked as [GitHub Issues](https://github.com/finos-hack/ai-steel-thread-demo/issues) with `type:*` labels and a release [milestone](https://github.com/finos-hack/ai-steel-thread-demo/milestones) (`v1.1.0`, `v1.2.0`, or `Backlog`).

## Requirements

- Java 21+
- Maven 3.9+
- Node.js 20+ (for the launch UI)
- Locally installed agentic plugins from [finos/fluxnova-plugins](https://github.com/finos/fluxnova-plugins) (`1.0.0-SNAPSHOT`) — or use Docker (builds the `llm-token-usage` branch automatically)
- `OPENAI_API_KEY` for OpenAI document intelligence / agent LLM calls
- `ANTHROPIC_API_KEY` for Anthropic (Claude) provider calls

## Docker (full stack)

Builds the app against `fluxnova-plugins` branch `llm-token-usage` (override with `PLUGINS_REF`), then runs API + Launch UI + Prometheus/Grafana/Tempo + CALM Hub.

```bash
cp .env.example .env
# edit .env — set OPENAI_API_KEY and ANTHROPIC_API_KEY

docker compose up --build
```

| Service | URL |
|---------|-----|
| Launch UI | http://localhost:5173 |
| API / Cockpit | http://localhost:8080 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| CALM Hub | http://localhost:8090 |

First build is slow (clones plugins + Maven download). Rebuild app only after code changes:

```bash
docker compose up --build app frontend
```

Pin plugins to a commit instead of the branch tip:

```bash
PLUGINS_REF=<commit-sha> docker compose build app
```

Observability-only (app running on the host) remains available via `observability/docker-compose.yml`.

## Build

```bash
# Install plugins locally first (from fluxnova-plugins / llm-token-usage):
#   cd ../fluxnova-plugins/agentic-subprocess && mvn -DskipTests install

mvn clean package
```

## Run

```bash
export OPENAI_API_KEY=...
export ANTHROPIC_API_KEY=...
mvn spring-boot:run
```

`LoanApprovalProcess_AI` and the Fluxnova form `credit_approval_review` are auto-deployed from
`src/main/resources/bpmn/` and `src/main/resources/forms/` on startup
(`fluxnova.bpm.auto-deployment-enabled` + `deployment-resource-pattern` includes `*.form`).
You can still redeploy from Fluxnova Modeler when iterating on the diagram or form.

### Launch UI (React)

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — choose **Loan Approval**, fill the form, then **Send**.

The Vite dev server proxies `/api` to `http://localhost:8080`.

### API (optional)

```bash
curl -X POST 'http://localhost:8080/api/demo/loan-approval/start' \
  -H 'Content-Type: application/json' \
  -d '{"requestedAmount":25000,"loanPurpose":"Car"}'
```

Legacy query-param endpoint: `POST /api/demo/loan-approval/start-with-sample-id`

## Architecture (FINOS CALM)

Machine-readable architecture for this loan-approval steel thread:

[`architecture/loan-approval.calm.json`](architecture/loan-approval.calm.json)

From the Launch UI use-case card, click **View CALM architecture** for an in-app React Flow canvas (same CALM nodes / relationships model as [CALM Hub](https://calm.finos.org/calm-hub/)). Optionally run Hub on port 8090 and open the official visualizer — see [`architecture/README.md`](architecture/README.md).

```bash
npx @finos/calm-cli validate \
  -a architecture/loan-approval.calm.json \
  -u architecture/url-to-local-file-mapping.json \
  -f pretty
```

## Observability

See `observability/README.md` for Prometheus / Grafana / Tempo via Docker Compose.
