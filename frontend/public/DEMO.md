# Steel Thread Demo Guide

Presenter script for the **AI Steel Thread** loan-approval demo (FINOS AIGF [UC-3](https://github.com/finos/ai-governance-framework/blob/main/docs/_usecases/uc-3_loan-approval.md)). Aim for **20–30 minutes**. Times below are suggestions.

> Also available in the Launch UI under **Demo guide** (top nav): **Walkthrough** and **Narration** tabs. Repo copies: [`DEMO.md`](DEMO.md), [`NARRATION.md`](NARRATION.md).

## Before you start

```bash
cp .env.example .env   # OPENAI_API_KEY + ANTHROPIC_API_KEY
docker compose up --build
```

| Surface | URL |
|---------|-----|
| Launch UI | http://localhost:5173 |
| Fluxnova Monitoring | http://localhost:8080/fluxnova/app/monitoring/default/ |
| Grafana | http://localhost:3000 |
| CALM Hub | http://localhost:8090 |

Have one browser window on the Launch UI and a second tab ready for Grafana / CALM Hub / Monitoring.

---

## Act 1 — Use case & AIGF (≈ 5 min)

**Goal:** Frame *why* this steel thread exists: a governed AI loan-approval workflow, not just a chatbot.

1. Open http://localhost:5173 → **Use cases**.
2. Select **Loan Approval** (Risk and Compliance).
3. Expand governance sections and narrate:

| Talking point | What to show |
|---------------|--------------|
| Business value | Faster cycle time, consistent policy, auditability |
| End users | Loan officers / credit risk; compliance & fraud; MRM / audit |
| Data sensitivity | PII, financial data, AI scores, proprietary policy |
| Regulatory | ECOA / fair lending, GDPR Art. 22, EU AI Act high-risk credit |

4. Open **Risks** and map to AIGF catalog links (same cards deep-link to [air-governance-framework.finos.org](https://air-governance-framework.finos.org/)):

| Risk | Demo angle |
|------|------------|
| **ri-1** Information leaked to hosted model | Document + credit-risk agents call external LLMs |
| **ri-4** Hallucination / inaccurate outputs | Extracted ID fields and risk narratives can be wrong |
| **ri-6** Non-deterministic behaviour | Same application → different agent tool paths |
| **ri-14** Inadequate system alignment | Need acceptance tests against policy bands |
| **ri-17** Lack of explainability | Need traces, conversation history, human review |
| **ri-19** Data quality and drift | Sample ID cards / extraction quality |

5. Open **Controls (runtime)** and show live enforcement badges:

| Control | What it does in this demo |
|---------|---------------------------|
| **mi-1** Data leakage prevention | Allowlists credit-risk context; redacts PII/traces on status APIs |
| **mi-4** AI system observability | Token/call/latency metrics → Prometheus / Grafana |
| **mi-5** System acceptance testing | Deterministic SAT suite; run from the UI |
| **mi-11** Human feedback loop | Loan-officer task when decision is `MANUAL_REVIEW` |

**Optional beat:** Toggle a control off/on (e.g. mi-1) to show governance is runtime-configurable, then leave **all enabled** for the rest of the demo.

**Optional beat:** Click **Run acceptance tests** (mi-5) and show pass rate before any live LLM traffic.

---

## Act 2 — Architecture with CALM (≈ 5 min)

**Goal:** Show *Architecture as Code* — the steel thread is a machine-readable CALM model tied to AIGF controls.

1. On the use-case card, click **View CALM architecture**.
2. In the in-app canvas:
   - Pan/zoom the graph (Spring Boot host, Fluxnova, agents, workers, OpenAI/Anthropic, observability).
   - Click a node with an **mi-*** chip → side panel shows attached control policy.
   - Call out edges such as document-intelligence → LLM and credit-risk agent → tools.
3. Click **Open CALM Hub** → official Hub visualizer at  
   http://localhost:8090/#/finos/architectures/loan-approval/1.0.0  
   (Hub is started and seeded by `docker compose`; seed job exits after success — that is normal.)
4. Optional: **Download JSON** and mention validation via `@finos/calm-cli` (see `architecture/README.md`).

**Line to close the act:** *Risks and mitigations are not slideware — they are attached to the architecture and enforced in the running process.*

---

## Act 3 — Run loan instances (variations) (≈ 10 min)

**Goal:** Start real BPMN instances and contrast providers, models, caching, and human review.

Click **Launch** / open the loan approval form.

### Variation A — Baseline (OpenAI)

| Field | Suggested value |
|-------|-----------------|
| Sample ID document | Any sample (e.g. `sample-id-card-2.png`) |
| Requested amount | `25000` |
| Loan purpose | `Car` |
| Document processing agent | `openai` / `gpt-5.4` |
| Credit risk agent | `openai` / `gpt-5.4` |

**Send**, then show:

- Instance appears in the list with live status log.
- Document extraction + credit-risk agentic tools (credit score, employment).
- **Advanced: Monitoring** opens Fluxnova process instance view.
- **Compliance evidence** report (mi-* posture for this run).

### Variation B — Anthropic + prompt caching

Start another instance:

| Field | Suggested value |
|-------|-----------------|
| Document processing | `anthropic` / `claude-sonnet-5`, cache `SYSTEM_AND_TOOLS` |
| Credit risk | `anthropic` / `claude-sonnet-5`, cache `SYSTEM_AND_TOOLS` |
| Amount | `75000` (more likely to stress decision paths) |

**Talk track:** Same BPMN; different LLM provider and cache strategy. Anthropic bills cache read/write buckets; OpenAI uses prompt-cache keys / discounted cached input.

### Variation C — Cross-provider mix

| Agent | Provider |
|-------|----------|
| Document processing | OpenAI |
| Credit risk | Anthropic |

Shows the steel thread is provider-pluggable per agent task, not a single-vendor lock-in.

### Variation D — Human feedback (mi-11)

If an instance lands on **manual review**:

1. Open the **Human task inbox** on the result / instance view.
2. Complete the loan-officer form: decision + agree/disagree with AI + comment.
3. Point back to **mi-11** on the use-case card — structured human feedback closes the loop.

If no manual-review task appears, open Grafana **Loan Approval Business** outcomes or start another amount/document combination; narration can still cover the control from the governance panel.

### What to watch while instances run

- Launch UI status log and task inbox  
- Fluxnova Monitoring process diagram  
- Grafana folder **AI Steel Thread Demo**:
  - **Observability** (mi-4 LLM calls, latency, DLP egress)
  - **Loan Approval Business** (approve / reject / review)
  - **Fluxnova Task Execution** (worker throughput, tokens)

---

## Act 4 — Tokenomics (≈ 5 min)

**Goal:** Show AI cost as a first-class operational signal.

1. In the Launch UI top nav, open **Tokenomics**.
2. Filter use case → **Loan Approval**.
3. Walk the panels:

| Panel | Demo point |
|-------|------------|
| Summary | Spend / tokens since the demos you just ran |
| Breakdown | Group by provider+model — compare OpenAI vs Anthropic runs |
| Rates | List price card including **cache input / cache write** columns |
| Budgets | Monthly loan-approval budget, warn threshold (default soft limit) |
| Insights | Tips driven by recent usage (e.g. caching, model mix) |

4. Optional: adjust budget warn/limit in the UI to show governance of spend (not only accuracy).
5. Optional: Grafana **Observability** dashboard → estimated LLM cost / token increase panels.

**Close:** *AIGF covers leakage, observability, acceptance, and human feedback; tokenomics adds the economic control plane for the same steel thread.*

---

## Suggested closing checklist

- [ ] UC-3 framed with risks **ri-*** and controls **mi-1 / mi-4 / mi-5 / mi-11**
- [ ] CALM architecture shown in-app + Hub
- [ ] At least two process instances (e.g. OpenAI baseline + Anthropic cached)
- [ ] One Monitoring or Grafana glance
- [ ] Tokenomics breakdown by provider/model

---

## Troubleshooting (live demos)

| Symptom | Fix |
|---------|-----|
| Launch UI API errors | `docker compose ps` — `app` should be healthy; check API keys in `.env` |
| Open CALM Hub empty / down | Ensure `calmhub` is up; `calmhub-seed` **Exited (0)** is success |
| Anthropic document extract MIME error | Use a sample card after the magic-byte MIME fix (rebuild `app` if image is old) |
| Button/links stale | Hard-refresh the Launch UI after `frontend` rebuilds |
| No human task | Decision may be auto-approve/reject — still show mi-11 on the use-case card |

For install details see [README.md](README.md). For Hub-only ops see [architecture/README.md](architecture/README.md). For metrics stack see [observability/README.md](observability/README.md).
