# Fluxnova task observability

## Quick start

```bash
# 1. Start Grafana + Prometheus + Tempo
docker compose up -d

# 2. Restart the Spring Boot app (required after code changes)
mvn spring-boot:run

# 3. Redeploy LoanApprovalProcess_AI from Fluxnova Modeler
#    (H2 in-memory DB resets on every app restart)

# 4. Start a process instance and let workers run

# 5. Open Grafana
open http://localhost:3000
```

On macOS, run Compose from the correctly cased path (`/Users/.../Documents/...`). A lowercased cwd (`/users/.../documents/...`) makes Docker Desktop reject bind mounts as “not shared”.

## Verify metrics pipeline

```bash
# After at least one task has run:
curl -s http://localhost:8080/actuator/prometheus | grep fluxnova_task

# Prometheus should scrape the same series:
curl -s 'http://localhost:9090/api/v1/query?query=fluxnova_task_executions_total'
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Mounts denied` / path `... is not shared` | Bind mount resolved with lowercased `/users/.../documents/...`, or path missing from Docker Desktop file sharing | `cd` to `/Users/.../Documents/.../observability` (correct case) and re-run; or add `/Users` (or this repo) under Docker Desktop → Settings → Resources → File Sharing |
| No `fluxnova_task_*` metrics | App not restarted after observability fix, or no tasks executed yet | Restart app, redeploy BPMN, run a process instance |
| Prometheus target down | App not running on port 8080 | Start the app |
| Traces panel empty | Custom spans not exported (`OpenTelemetryTracingSupport` bean missing due to config order), Tempo not running, or no external tasks executed yet | Restart app after observability fix; `docker compose up -d`; run demo process; filter traces panel by `document-intelligence` in Tempo Explore if needed |
| `ConnectException: ... :4318` in app logs | Tempo not running while tracing is enabled | Start observability stack before (or with) the app |
| `OtlpHttpMetricsSendUnsuccessfulException` / HTTP 404 | OTLP metrics export pointed at Tempo (traces-only) | Keep `management.otlp.metrics.export.enabled=false`; use Prometheus for metrics |
| Activity metrics missing | Process deployed before observability plugin loaded | Redeploy BPMN from Modeler after app restart |
| Dashboard filters show no options | Old dashboard used Prometheus-only variables | Re-import dashboard from `grafana/dashboards/` |
| `ACT_FK_VAR_EXE` when credit-score tool runs | Agentic ad-hoc auto-completed after sync tool (no `completionCondition`) | Restart app and **redeploy** BPMN so `AgentAdHocCompletionGuard` applies; or set `<completionCondition>${false}</completionCondition>` on the agent ad-hoc in Modeler |

## Metrics

| Metric | Labels |
|--------|--------|
| `fluxnova_task_executions_total` | `task_type`, `task_name`, `status` |
| `fluxnova_task_duration_seconds` | `task_type`, `task_name` |

**task_type** values: `external-task`, `activity`, `user-task`, `process`

**task_name** for external tasks is the worker topic (e.g. `check-credit-score`).

## Business metrics

| Metric | Labels | Description |
|--------|--------|-------------|
| `fluxnova_loan_processes_started_total` | `workflow` | Loan applications started |
| `fluxnova_loan_processes_completed_total` | `workflow` | Loan applications that reached process end |
| `fluxnova_loan_process_outcomes_total` | `workflow`, `outcome` | `successful`, `invalid_document`, `fraud_detected`, `incomplete`, `auto_approve`, `reject`, `manual_review` |
| `fluxnova_loan_invalid_documents_total` | `workflow` | Applications with `isInvalid=true` |
| `fluxnova_loan_fraud_detected_total` | `workflow` | Applications with `isFraudulent=true` |
| `fluxnova_loan_approval_decisions_total` | `workflow`, `decision` | `AUTO_APPROVE`, `REJECT`, `MANUAL_REVIEW` |
| `fluxnova_loan_loan_officer_decisions_total` | `workflow`, `decision` | `APPROVE`, `REJECT` |
| `fluxnova_loan_credit_score` | `workflow` | Distribution summary for `creditScore` |
| `fluxnova_loan_ai_risk_score` | `workflow` | Distribution summary for `aiRiskScore` |

When an agent ad-hoc subprocess completes, the orchestrator exports trace variables:

| Variable | Description |
|----------|-------------|
| `agentLastResponse` | Last assistant message from the model |
| `agentConversationTrace` | Human-readable summary of assistant messages, tool calls, and tool outputs |
| `agentConversationHistory` | Full JSON conversation history |

These variables are also updated on the process instance **after each agent step** (tool completion or LLM call), so they are visible in Cockpit while the agent is running.

**Note:** Agent **context variables** in the Modeler (`agent:context`) declare which process variables the model **reads as input**. They do not capture model output. Do not add the conversation there — the trace variables above are written automatically.

Open Grafana dashboard **Loan Approval Business** after running demo processes.

Incomplete applications are derived as started minus completed, or `outcome=incomplete` when a process ends without an approval decision.

## LLM token metrics

| Metric | Labels |
|--------|--------|
| `fluxnova_llm_tokens_total` | `workflow`, `task`, `model`, `provider`, `token_type` (`prompt` / `completion`) |
| `fluxnova_llm_calls_total` | `workflow`, `task`, `model`, `provider`, `status` (`success` / `failure`) |
| `fluxnova_llm_duration_seconds` | `workflow`, `task`, `model`, `provider`, `model_version` |
| `fluxnova_llm_errors_total` | `workflow`, `task`, `model`, `provider`, `error_type` |

These implement FINOS AIGF **mi-4** (AI System Observability). Configure with `fluxnova.observability.mi-4.*`. Open Grafana dashboard **Observability**.

Sanitized LLM audit is logged at INFO on each call (no raw prompts/media):

```
LLM audit workflow=LoanApprovalProcess_AI task=AgentTask_CreditRisk provider=... model=... version=... durationMs=... status=success promptTokens=... completionTokens=... totalTokens=... contextKeys=... errorType=-
```