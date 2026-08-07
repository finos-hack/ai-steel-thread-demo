import { useEffect, useState } from 'react'
import {
  fetchTokenomicsBreakdown,
  fetchTokenomicsBudgets,
  fetchTokenomicsInsights,
  fetchTokenomicsRates,
  fetchTokenomicsSummary,
  updateTokenomicsBudget,
  type TokenomicsBreakdown,
  type TokenomicsBudget,
  type TokenomicsBudgets,
  type TokenomicsInsightTip,
  type TokenomicsInsights,
  type TokenomicsRates,
  type TokenomicsSummary,
} from './api'

const POLL_MS = 5000

const USE_CASE_LABELS: Record<string, string> = {
  all: 'All use cases',
  'loan-approval': 'Loan Approval',
}

type Props = {
  /** When true, render as a full page (no fold). */
  page?: boolean
}

export function TokenomicsPanel({ page = false }: Props) {
  const [useCase, setUseCase] = useState('all')
  const [groupBy, setGroupBy] = useState('providerModel')
  const [summary, setSummary] = useState<TokenomicsSummary | null>(null)
  const [breakdown, setBreakdown] = useState<TokenomicsBreakdown | null>(null)
  const [rates, setRates] = useState<TokenomicsRates | null>(null)
  const [budgets, setBudgets] = useState<TokenomicsBudgets | null>(null)
  const [insights, setInsights] = useState<TokenomicsInsights | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [expanded, setExpanded] = useState(true)

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    async function poll() {
      try {
        const filter = useCase === 'all' ? null : useCase
        const [nextSummary, nextBreakdown, nextRates, nextBudgets, nextInsights] =
          await Promise.all([
            fetchTokenomicsSummary(filter),
            fetchTokenomicsBreakdown(filter, groupBy),
            fetchTokenomicsRates(),
            fetchTokenomicsBudgets(filter),
            fetchTokenomicsInsights(filter),
          ])
        if (cancelled) {
          return
        }
        setSummary(nextSummary)
        setBreakdown(nextBreakdown)
        setRates(nextRates)
        setBudgets(nextBudgets)
        setInsights(nextInsights)
        setError(null)
      } catch (err: unknown) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load tokenomics')
        }
      } finally {
        if (!cancelled) {
          timer = window.setTimeout(poll, POLL_MS)
        }
      }
    }

    void poll()
    return () => {
      cancelled = true
      if (timer !== undefined) {
        window.clearTimeout(timer)
      }
    }
  }, [useCase, groupBy])

  const useCaseOptions =
    summary?.availableUseCases?.length
      ? summary.availableUseCases
      : ['all', 'loan-approval']

  const primaryBudget = budgets?.budgets?.[0] ?? null
  const budgetBadge = worstBudgetStatus(budgets?.budgets ?? [])
  const showBody = page || expanded

  async function patchBudget(update: {
    useCase: string
    limitUsd?: number
    hardLimit?: boolean
  }) {
    setBusy(true)
    try {
      const next = await updateTokenomicsBudget(update)
      setBudgets(next)
      setError(null)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update budget')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section
      className={`tokenomics-panel${page ? ' is-page' : ''}${showBody ? ' is-expanded' : ''}`}
    >
      {!page ? (
        <div className="tokenomics-panel-head">
          <button
            type="button"
            className="governance-fold"
            aria-expanded={expanded}
            aria-controls="tokenomics-body"
            onClick={() => setExpanded((value) => !value)}
          >
            <span className="sample-preview-label">AI tokenomics</span>
            <span className="governance-chevron" aria-hidden="true">
              {expanded ? '−' : '+'}
            </span>
          </button>
          <span className="tokenomics-head-meta">
            {summary ? (
              <span className="hint">
                ~{formatUsd(summary.estimatedCostUsd)} · {summary.totalTokens.toLocaleString()}{' '}
                tokens
              </span>
            ) : null}
            {budgetBadge ? (
              <span className={`control-badge tokenomics-budget-badge status-${budgetBadge}`}>
                budget {budgetBadge}
              </span>
            ) : null}
          </span>
        </div>
      ) : null}

      {showBody ? (
        <div id="tokenomics-body" className="tokenomics-body">
          <p className="hint">
            Live estimated spend from instance LLM calls (provider/model from start parameters and
            document intelligence). Filter by use case; costs update as calls complete in this JVM
            session.
          </p>
          {error ? <p className="hint danger">{error}</p> : null}

          <div className="tokenomics-toolbar">
            <div className="tokenomics-filters">
              <label>
                Use case
                <select value={useCase} onChange={(event) => setUseCase(event.target.value)}>
                  {useCaseOptions.map((id) => (
                    <option key={id} value={id}>
                      {USE_CASE_LABELS[id] ?? id}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Break down by
                <select value={groupBy} onChange={(event) => setGroupBy(event.target.value)}>
                  <option value="providerModel">Provider / model</option>
                  <option value="model">Model</option>
                  <option value="provider">Provider</option>
                  <option value="task">Task</option>
                  <option value="useCase">Use case</option>
                  <option value="workflow">Workflow</option>
                </select>
              </label>
            </div>
            <div className="tokenomics-toolbar-actions">
              {insights?.grafanaUrl ? (
                <a
                  className="control-chip"
                  href={insights.grafanaUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  Open Grafana mi-4
                </a>
              ) : null}
            </div>
          </div>

          {summary ? (
            <div className="tokenomics-kpis">
              <Kpi label="Est. cost" value={formatUsd(summary.estimatedCostUsd)} />
              <Kpi label="Calls" value={summary.calls.toLocaleString()} />
              <Kpi label="Tokens" value={summary.totalTokens.toLocaleString()} />
              <Kpi label="Avg $/call" value={formatUsd(summary.avgCostPerCallUsd)} />
              <Kpi label="$/1k tokens" value={formatUsd(summary.costPer1kTokensUsd)} />
              {primaryBudget ? (
                <Kpi
                  label="Budget burn"
                  value={`${Math.round(primaryBudget.burnRatio * 100)}%`}
                />
              ) : null}
              {insights?.providers?.length ? (
                <Kpi label="Providers" value={insights.providers.join(', ')} />
              ) : null}
            </div>
          ) : (
            <p className="hint">Loading spend…</p>
          )}

          {insights && insights.tips.length > 0 ? (
            <div className="tokenomics-insights">
              <p className="governance-subhead">Optimization tips</p>
              <ul className="tokenomics-insight-list">
                {insights.tips.map((tip) => (
                  <InsightRow key={tip.id} tip={tip} />
                ))}
              </ul>
            </div>
          ) : null}

          {budgets && budgets.budgets.length > 0 ? (
            <div className="tokenomics-budgets">
              <p className="governance-subhead">Budgets & forecast</p>
              {budgets.budgets.map((budget) => (
                <BudgetCard
                  key={budget.useCase}
                  budget={budget}
                  busy={busy}
                  onToggleHard={(hardLimit) =>
                    void patchBudget({ useCase: budget.useCase, hardLimit })
                  }
                  onLimitChange={(limitUsd) =>
                    void patchBudget({ useCase: budget.useCase, limitUsd })
                  }
                />
              ))}
            </div>
          ) : null}

          {breakdown && breakdown.rows.length > 0 ? (
            <table className="human-task-context-table tokenomics-table">
              <thead>
                <tr>
                  <th scope="col">{groupByLabel(groupBy)}</th>
                  <th scope="col">Calls</th>
                  <th scope="col">Tokens</th>
                  <th scope="col">Est. cost</th>
                </tr>
              </thead>
              <tbody>
                {breakdown.rows.map((row) => (
                  <tr key={row.key}>
                    <th scope="row">{row.key}</th>
                    <td>{row.calls.toLocaleString()}</td>
                    <td>{row.totalTokens.toLocaleString()}</td>
                    <td>{formatUsd(row.estimatedCostUsd)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="hint">
              No LLM spend recorded yet. Start a loan instance — spend is attributed to the
              provider/model chosen on the form (and document intelligence).
            </p>
          )}

          {rates && rates.rates.length > 0 ? (
            <>
              <p className="governance-subhead">
                Rate card ({rates.currency} / 1M tokens)
              </p>
              <table className="human-task-context-table tokenomics-table">
                <thead>
                  <tr>
                    <th scope="col">Provider</th>
                    <th scope="col">Model</th>
                    <th scope="col">Prompt</th>
                    <th scope="col">Completion</th>
                    <th scope="col">Cache input</th>
                    <th scope="col">Cache write</th>
                  </tr>
                </thead>
                <tbody>
                  {rates.rates.map((rate) => (
                    <tr key={`${rate.provider}-${rate.model}`}>
                      <th scope="row">{rate.provider}</th>
                      <td>{rate.model}</td>
                      <td>${rate.promptPer1m.toFixed(2)}</td>
                      <td>${rate.completionPer1m.toFixed(2)}</td>
                      <td>${(rate.cacheInputPer1m ?? 0).toFixed(2)}</td>
                      <td>${(rate.cacheWritePer1m ?? 0).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}

function InsightRow({ tip }: { tip: TokenomicsInsightTip }) {
  return (
    <li className={`tokenomics-insight severity-${tip.severity}`}>
      <strong>{tip.title}</strong>
      <span>{tip.detail}</span>
    </li>
  )
}

function BudgetCard({
  budget,
  busy,
  onToggleHard,
  onLimitChange,
}: {
  budget: TokenomicsBudget
  busy: boolean
  onToggleHard: (hardLimit: boolean) => void
  onLimitChange: (limitUsd: number) => void
}) {
  const [limitDraft, setLimitDraft] = useState(String(budget.limitUsd))

  useEffect(() => {
    setLimitDraft(String(budget.limitUsd))
  }, [budget.limitUsd])

  return (
    <div className={`tokenomics-budget status-${budget.status}`}>
      <div className="tokenomics-budget-top">
        <strong>{USE_CASE_LABELS[budget.useCase] ?? budget.useCase}</strong>
        <span className={`control-badge tokenomics-budget-badge status-${budget.status}`}>
          {budget.status}
          {budget.hardLimit ? ' · hard' : ' · soft'}
        </span>
      </div>
      <div className="tokenomics-budget-metrics">
        <span>
          Spent {formatUsd(budget.spentUsd)} / {formatUsd(budget.limitUsd)} (
          {Math.round(budget.burnRatio * 100)}%)
        </span>
        <span>
          Forecast EOP {formatUsd(budget.forecastEndOfPeriodUsd)}
          {budget.forecastExceedsLimit ? ' · on track to exceed' : ''}
        </span>
        <span className="hint">
          {budget.period} · {budget.remainingDays.toFixed(1)}d remaining
        </span>
      </div>
      {(budget.status === 'warn' || budget.status === 'exceeded') && (
        <p className={`hint ${budget.status === 'exceeded' ? 'danger' : ''}`}>
          {budget.status === 'exceeded'
            ? budget.hardLimit
              ? 'Hard limit reached — new LLM calls for this use case are blocked.'
              : 'Budget exceeded (soft). Toggle hard limit to block further LLM calls.'
            : 'Spend has crossed the warn threshold for this period.'}
        </p>
      )}
      <div className="tokenomics-budget-controls">
        <label>
          Limit (USD)
          <input
            type="number"
            min={0}
            step="0.01"
            value={limitDraft}
            disabled={busy}
            onChange={(event) => setLimitDraft(event.target.value)}
            onBlur={() => {
              const next = Number(limitDraft)
              if (Number.isFinite(next) && next !== budget.limitUsd) {
                onLimitChange(next)
              } else {
                setLimitDraft(String(budget.limitUsd))
              }
            }}
          />
        </label>
        <label className="tokenomics-hard-toggle">
          <input
            type="checkbox"
            checked={budget.hardLimit}
            disabled={busy}
            onChange={(event) => onToggleHard(event.target.checked)}
          />
          Hard limit (block LLM when exceeded)
        </label>
      </div>
    </div>
  )
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="tokenomics-kpi">
      <span className="tokenomics-kpi-label">{label}</span>
      <strong className="tokenomics-kpi-value">{value}</strong>
    </div>
  )
}

function worstBudgetStatus(budgets: TokenomicsBudget[]): string | null {
  if (budgets.length === 0) {
    return null
  }
  if (budgets.some((b) => b.status === 'exceeded')) {
    return 'exceeded'
  }
  if (budgets.some((b) => b.status === 'warn')) {
    return 'warn'
  }
  return 'ok'
}

function formatUsd(value: number): string {
  if (!Number.isFinite(value)) {
    return '$0.00'
  }
  if (value === 0) {
    return '$0.00'
  }
  if (value < 0.01) {
    return `$${value.toFixed(6)}`
  }
  return `$${value.toFixed(4)}`
}

function groupByLabel(groupBy: string): string {
  switch (groupBy) {
    case 'providerModel':
      return 'Provider / model'
    case 'provider':
      return 'Provider'
    case 'model':
      return 'Model'
    case 'useCase':
      return 'Use case'
    case 'workflow':
      return 'Workflow'
    default:
      return 'Task'
  }
}
