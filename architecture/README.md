# CALM Hub (local visualizer)

Runs [FINOS CALM Hub](https://calm.finos.org/calm-hub/) on **port 8090** so it does not collide with the Fluxnova demo on 8080.

With the full demo stack, Hub starts automatically and `loan-approval.calm.json` is seeded:

```bash
docker compose up   # from repo root — includes calmhub + calmhub-seed
```

Standalone (Hub only):

```bash
docker compose -f architecture/docker-compose.yml up
```

UI: http://localhost:8090  
Deep link: http://localhost:8090/#/finos/architectures/loan-approval/1.0.0

## Publish this architecture (standalone Hub)

If you started Hub via `architecture/docker-compose.yml` only (no root seed job):

```bash
# once Hub is up
npx @finos/calm-cli hub create namespace --name finos -c http://localhost:8090
npx @finos/calm-cli hub push architecture architecture/loan-approval.calm.json \
  -c http://localhost:8090 \
  --name "AI Steel Thread — Loan Approval" \
  -f pretty
```

Or from the Launch UI use-case card: **View CALM architecture** (in-app canvas) / **Open CALM Hub**.

Keep `frontend/public/architecture/loan-approval.calm.json` in sync when editing the source file:

```bash
cp architecture/loan-approval.calm.json frontend/public/architecture/loan-approval.calm.json
```

```bash
npx @finos/calm-cli validate \
  -a architecture/loan-approval.calm.json \
  -u architecture/url-to-local-file-mapping.json \
  -f pretty
```

## Governance controls

`loan-approval.calm.json` attaches FINOS AIGF **mi-1** (AI data leakage prevention & detection) to:

- Nodes: `credit-risk-agent`, `document-intelligence-service`, `openai-platform`, `openai-chat-api`
- Egress relationships: `provider-registry-to-openai`, `doc-intel-to-openai`

Control requirements use schema [`controls/mi-1-ai-data-leakage-prevention.requirement.json`](controls/mi-1-ai-data-leakage-prevention.requirement.json) (mapped via `url-to-local-file-mapping.json`). Inline `config` encodes allow/forbid categories, TLS, and TSP posture; `aigf-catalog-url` / `aigf-mitigations` link to the [mi-1 catalog page](https://air-governance-framework.finos.org/mitigations/mi-1_ai-data-leakage-prevention-and-detection.html). **Runtime enforcement** lives in `org.finos.ai.demo.dlp`: credit-risk context allowlisting before OpenAI calls, document-intelligence egress audit, status-API PII/trace redaction, and metric `fluxnova.dlp.mi1.egress`. Configure with `fluxnova.dlp.mi-1.*` in `application.yaml`.
It also attaches FINOS AIGF **mi-4** (AI system observability) to:

- Nodes: `credit-risk-agent`, `document-intelligence-service`, `actuator-prometheus`, `grafana`, `observability-stack`

Control requirements use schema [`controls/mi-4-ai-system-observability.requirement.json`](controls/mi-4-ai-system-observability.requirement.json). **Runtime** lives in `org.finos.ai.demo.observability`: token/call/latency/error metrics, sanitized LLM audit logs, process-scoped call metadata, and the **Observability** Grafana dashboard. Configure with `fluxnova.observability.mi-4.*`.

It also attaches FINOS AIGF **mi-5** (System Acceptance Testing) to:

- Nodes: `loan-approval-demo-api`, `credit-risk-agent`, `document-intelligence-service`

Control requirements use schema [`controls/mi-5-system-acceptance-testing.requirement.json`](controls/mi-5-system-acceptance-testing.requirement.json). **Runtime** lives in `org.finos.ai.demo.acceptance`: a deterministic SAT suite (credit-risk bands, approval decisions, mi-1 DLP invariants, loan bounds, document validity) with a configurable pass-rate threshold. Expose/run via `GET|POST /api/demo/loan-approval/acceptance-tests`. Configure with `fluxnova.acceptance.mi-5.*`.

It also attaches FINOS AIGF **mi-11** (Human Feedback Loop for AI Systems) to:

- Nodes: `loan-approval-demo-api`, `launch-ui`, `fluxnova-cockpit`, `loan-officer`

Control requirements use schema [`controls/mi-11-human-feedback-loop.requirement.json`](controls/mi-11-human-feedback-loop.requirement.json). **Runtime** lives in `org.finos.ai.demo.feedback`: Launch UI / demo API completes existing BPMN `UserTask_LoanOfficerReview` (gated by `Gateway_5` on `MANUAL_REVIEW`) via TaskService with `loanOfficerDecision` plus structured agree/disagree feedback. Alternate completion remains available in Cockpit form `credit_approval_review`. Configure with `fluxnova.feedback.mi-11.*`. Expose via `GET|POST /api/demo/loan-approval/instances/{id}/human-review` and `.../human-feedback`.
