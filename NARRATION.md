# Steel Thread Demo — Narration

Spoken script to pair with the [Demo guide](DEMO.md). Read naturally; skip lines in brackets — those are stage directions.

**In the Launch UI:** open **Demo guide** → switch to the **Narration** tab.

Estimated runtime: **20–30 minutes**.

---

## Opening (30–45 seconds)

Welcome. This is the FINOS **AI Steel Thread** demo for loan approval — use case three from the AI Governance Framework.

What we’re going to show is not a chatbot bolted onto a form. It’s an end-to-end **agentic process**: Fluxnova orchestrates document intelligence and credit-risk agents, with real FINOS governance controls in the runtime — data leakage prevention, observability, acceptance testing, and a human feedback loop — plus cost visibility through tokenomics.

We’ll go from the **use case and risks**, to the **CALM architecture**, then **run live instances** with different LLM providers, and finish on **spend and budgets**.

---

## Act 1 — Use case & AIGF (~5 min)

[Open Catalog → Loan Approval]

Loan approval is a classic high-stakes AI surface: customer PII, credit decisions, fair-lending expectations. The catalog card is adapted from the FINOS AIGF use case — business value, who uses it, what data is sensitive, and which regulations apply.

[Expand Business value / End users / Data sensitivity / Regulatory briefly]

Faster decisions only matter if they’re consistent, auditable, and governable. That’s why this steel thread starts from the framework, not from a model picker.

[Open Risks]

These are the AIGF risks we’re actively confronting in this demo.

**Information leaked to a hosted model** — because document extraction and credit-risk reasoning call external LLMs.

**Hallucination and inaccurate outputs** — an ID field or a risk narrative can be wrong, and that can change an underwriting path.

**Non-deterministic behaviour** — the agent may choose different tools or ordering on similar applications.

**Inadequate system alignment** — so we need acceptance tests against policy, not vibes.

**Lack of explainability** — so we keep traces, conversation history, and a human review path.

**Data quality and drift** — sample documents and extraction quality are part of the risk story.

[Open Controls / runtime badges]

And here are the mitigations as **live runtime controls**, not a slide:

**mi-1** — we constrain what leaves the process toward the model and redact sensitive material on status APIs.

**mi-4** — every LLM call is observable: tokens, latency, errors, into Prometheus and Grafana.

**mi-5** — we can run a deterministic acceptance suite against the use case before or beside live traffic.

**mi-11** — when the process asks for manual review, a loan officer records a decision and structured feedback on the AI recommendation.

[Optional: toggle a control, then leave all on]

You can turn these on and off at runtime. For the rest of the demo we’ll keep them enforced.

[Optional: Run acceptance tests]

Before we burn tokens, we can show the system still meets the acceptance bar.

---

## Act 2 — Architecture with CALM (~5 min)

[Click View CALM architecture]

Risks and controls only stick if they’re grounded in architecture. This is **CALM** — Architecture as Code for the steel thread.

What you’re seeing is the same model: the Spring Boot host, Fluxnova engine surfaces, the credit-risk agentic subprocess, document intelligence, workers, the LLM providers, and the observability stack.

[Click a node with an mi-* chip]

Notice the **mi** chips on nodes and edges. Click one — the control policy is attached to that architectural element. Governance is addressable in the architecture graph, not stuck in a wiki.

[Open CALM Hub]

And this is the official CALM Hub visualizer for the same document. One source of truth, two views — in-app for the demo flow, Hub for architecture stakeholders.

So the point of this act: **mitigations aren’t slideware**. They’re declared on the architecture and enforced in the running process.

[Close visualizer]

---

## Act 3 — Live instances (~10 min)

Now we run the process.

[Open Launch / start form]

### Variation A — OpenAI baseline

We’ll start with a straightforward application — say twenty-five thousand for a car — OpenAI for both document processing and credit risk.

[Send; watch status log]

Behind the scenes Fluxnova is driving the BPMN. Document intelligence extracts identity fields. The credit-risk agent decides which tools to call — credit score, employment verification — inside an agentic ad-hoc subprocess.

[Open Advanced: Monitoring if useful]

In Monitoring you can see the process instance itself — the operational view for the engine.

[Point at compliance evidence]

And here’s compliance evidence for this run: which controls were in force while AI work happened.

### Variation B — Anthropic with caching

Same steel thread. Different provider.

[Start second instance: Anthropic, Claude, SYSTEM_AND_TOOLS cache, higher amount optional]

Document processing and credit risk on Anthropic with a cache strategy. We’re not rewriting the BPMN — we’re swapping model and cache posture per agent task.

That matters for both **capability** and **cost**: Anthropic exposes cache read and write economics; OpenAI has its own prompt-cache behaviour. Same process definition, different economic fingerprint — which we’ll see in tokenomics.

### Variation C — Mix providers (optional if time)

[Document OpenAI, credit risk Anthropic]

And yes — you can mix. Extraction on one provider, credit reasoning on another. The steel thread is provider-pluggable at the agent boundary.

### Variation D — Human feedback

[If a manual-review task appears]

When the decision path needs a human, mi-11 shows up as a real task. The loan officer doesn’t just click approve — they can say whether they agree with the AI and leave a comment. That feedback is part of the control story.

[If no manual task]

Even if this run auto-decides, the control is wired into the architecture and the use-case card — the human loop is ready when policy says MANUAL_REVIEW.

[Glance at Grafana if time]

While those run, Grafana under **AI Steel Thread Demo** shows mi-4 observability, business outcomes, and task execution — the operational twin of what we just launched.

---

## Act 4 — Tokenomics (~5 min)

[Open Tokenomics in the top nav → filter Loan Approval]

Governance without cost visibility is incomplete. Tokenomics is the economic control plane for the same steel thread.

**Summary** — what we just spent in tokens and dollars across the instances we ran.

**Breakdown** — group by provider and model. Here’s the OpenAI baseline versus the Anthropic cached run — different shapes of spend.

**Rates** — including cache input and cache write columns, so the pricing story matches what the providers actually bill.

**Budgets** — a monthly envelope for loan approval, with a warning threshold. Soft limit by default so a demo doesn’t hard-stop mid-sentence — but the lever is there.

**Insights** — tips from recent usage: when caching helps, when a model mix changes the curve.

[Optional: tweak budget]

You can change the budget posture live — same idea as toggling mi-1: controls are operable, not frozen in a PDF.

So to close this act: AIGF gives us leakage, observability, acceptance, and human feedback. Tokenomics adds **spend** as a first-class signal on the same path.

---

## Closing (~30 seconds)

In twenty-odd minutes we’ve gone from an AIGF use case, through risks and runtime mitigations, a CALM architecture with controls on the graph, live multi-provider process instances, and cost visibility.

That’s the steel thread: **governed agentic process automation** you can run, observe, and account for — not just describe.

I’m happy to go deeper on CALM, a specific control, or the Fluxnova agent loop. Questions?

---

## Short version (~8–10 min)

If time is tight, say this arc and click once per beat:

1. **Catalog → Loan Approval** — “High-stakes UC-3; risks ri-1 through explainability; mitigations mi-1, mi-4, mi-5, mi-11 live in the runtime.”
2. **View CALM** — “Architecture as Code; controls on nodes and edges; open Hub.”
3. **One OpenAI instance + one Anthropic cached instance** — “Same BPMN, different provider economics.”
4. **Tokenomics** — “Breakdown by provider/model; budgets and cache rates.”
5. **Close** — “Governed agentic process — architecture, controls, execution, cost.”
