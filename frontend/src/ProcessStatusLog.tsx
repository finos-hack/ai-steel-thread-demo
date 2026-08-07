import { useEffect, useState } from 'react'
import {
  fetchProcessInstanceStatus,
  type ActivityStatusEntry,
  type ProcessInstanceStatus,
  type VariableEntry,
} from './api'
import { Mi1Badge } from './Mi1Badge'
import { Mi4Badge } from './Mi4Badge'

type Props = {
  processInstanceId: string
}

const POLL_MS = 2000
const TERMINAL_STATES = new Set(['COMPLETED', 'EXTERNALLY_TERMINATED', 'INTERNALLY_TERMINATED'])

export function ProcessStatusLog({ processInstanceId }: Props) {
  const [status, setStatus] = useState<ProcessInstanceStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sectionOpen, setSectionOpen] = useState(true)
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    async function poll() {
      try {
        const next = await fetchProcessInstanceStatus(processInstanceId)
        if (cancelled) {
          return
        }
        setStatus(next)
        setError(null)
        setExpandedIds((current) => {
          const nextExpanded = new Set(current)
          for (const activity of next.activities) {
            if (activity.status === 'active' && hasVariables(activity)) {
              nextExpanded.add(activity.id)
            }
          }
          return nextExpanded
        })
        if (!TERMINAL_STATES.has(next.state)) {
          timer = window.setTimeout(poll, POLL_MS)
        }
      } catch (err: unknown) {
        if (cancelled) {
          return
        }
        setError(err instanceof Error ? err.message : 'Failed to load process status')
        timer = window.setTimeout(poll, POLL_MS * 2)
      }
    }

    void poll()

    return () => {
      cancelled = true
      if (timer !== undefined) {
        window.clearTimeout(timer)
      }
    }
  }, [processInstanceId])

  const stateLabel = status?.state ?? 'LOADING'
  const mi1RedactStatusApi =
    status?.controls?.mi1Enabled === true && status?.controls?.mi1RedactStatusApi === true
  const mi4Enabled = status?.controls?.mi4Enabled === true
  const current =
    status?.currentActivityNames?.length
      ? status.currentActivityNames.join(', ')
      : status && TERMINAL_STATES.has(status.state)
        ? 'Process ended'
        : 'Waiting for first activity…'

  function toggleExpanded(activityId: string) {
    setExpandedIds((current) => {
      const next = new Set(current)
      if (next.has(activityId)) {
        next.delete(activityId)
      } else {
        next.add(activityId)
      }
      return next
    })
  }

  return (
    <aside className="status-log" aria-live="polite">
      <div className="status-log-head">
        <button
          type="button"
          className="governance-fold"
          aria-expanded={sectionOpen}
          onClick={() => setSectionOpen((value) => !value)}
        >
          <span className="sample-preview-label">Execution log</span>
          <span className="governance-chevron" aria-hidden="true">
            {sectionOpen ? '−' : '+'}
          </span>
        </button>
        <p className={`status-pill status-${stateLabel.toLowerCase()}`}>
          {formatState(stateLabel)}
          {stateLabel === 'ACTIVE' ? <ProgressDots /> : null}
        </p>
      </div>

      {sectionOpen ? (
        <>
          <p className="status-current">
            <span>Current</span>
            <strong>{current}</strong>
          </p>

          {error && <p className="hint danger">{error}</p>}
          {mi1RedactStatusApi ? (
            <p className="hint">
              <Mi1Badge compact>mi-1</Mi1Badge> Sensitive fields in activity variables are redacted
              for display
              {status?.controls?.instanceSnapshot ? ' (instance posture)' : ''}.
            </p>
          ) : status?.controls && !status.controls.mi1Enabled ? (
            <p className="hint">
              <Mi1Badge compact className="is-off">
                mi-1
              </Mi1Badge>{' '}
              Off for this instance — sensitive fields may appear in variables
              {status.controls.instanceSnapshot ? ' (instance posture)' : ''}.
            </p>
          ) : null}
          {mi4Enabled ? (
            <p className="hint">
              <Mi4Badge compact>mi-4</Mi4Badge> LLM token, latency, and model metadata are recorded
              for agent activities
              {status?.controls?.instanceSnapshot ? ' (instance posture)' : ''}.
            </p>
          ) : null}

          <ol className="status-entries">
            {(status?.activities ?? []).map((activity) => {
              const expanded = expandedIds.has(activity.id)
              const variableCount =
                (activity.inputs?.length ?? 0) + (activity.outputs?.length ?? 0)
              return (
                <li key={activity.id} className={`status-entry status-${activity.status}`}>
                  <div className="status-entry-main">
                    <span className="status-entry-name">{activity.activityName}</span>
                    <span className="status-entry-state">
                      {activity.status}
                      {activity.status === 'active' ? <ProgressDots /> : null}
                    </span>
                  </div>
                  <div className="status-entry-meta">
                    <span>{formatActivityType(activity.activityType)}</span>
                    <span>{formatTime(activity.startTime)}</span>
                  </div>
                  {activity.tokenUsage && (
                    <div className="token-usage" aria-label="LLM token usage">
                      <span className="token-usage-label">
                        LLM tokens
                        {mi4Enabled ? (
                          <>
                            {' '}
                            <Mi4Badge compact>mi-4</Mi4Badge>
                          </>
                        ) : null}
                      </span>
                      <div className="token-usage-metrics">
                        <div>
                          <span className="token-usage-value">
                            {activity.tokenUsage.promptTokens.toLocaleString()}
                          </span>
                          <span className="token-usage-key">prompt</span>
                        </div>
                        <div>
                          <span className="token-usage-value">
                            {activity.tokenUsage.completionTokens.toLocaleString()}
                          </span>
                          <span className="token-usage-key">completion</span>
                        </div>
                        <div>
                          <span className="token-usage-value">
                            {activity.tokenUsage.totalTokens.toLocaleString()}
                          </span>
                          <span className="token-usage-key">total</span>
                        </div>
                        {activity.tokenUsage.estimatedCostUsd != null ? (
                          <div>
                            <span className="token-usage-value">
                              {formatEstimatedCost(activity.tokenUsage.estimatedCostUsd)}
                            </span>
                            <span className="token-usage-key">est. $</span>
                          </div>
                        ) : null}
                        {activity.tokenUsage.durationMs != null ? (
                          <div>
                            <span className="token-usage-value">
                              {activity.tokenUsage.durationMs.toLocaleString()}
                            </span>
                            <span className="token-usage-key">ms</span>
                          </div>
                        ) : null}
                      </div>
                      {(activity.tokenUsage.model || activity.tokenUsage.provider) && (
                        <p className="token-usage-meta hint">
                          {[activity.tokenUsage.provider, activity.tokenUsage.model]
                            .filter(Boolean)
                            .join(' · ')}
                          {activity.tokenUsage.status ? ` · ${activity.tokenUsage.status}` : ''}
                        </p>
                      )}
                    </div>
                  )}
                  {variableCount > 0 && (
                    <button
                      type="button"
                      className="status-vars-toggle"
                      onClick={() => toggleExpanded(activity.id)}
                      aria-expanded={expanded}
                    >
                      {expanded ? 'Hide' : 'Show'} variables ({variableCount})
                    </button>
                  )}
                  {expanded && (
                    <div className="status-vars">
                      <VariableGroup title="Input" variables={activity.inputs ?? []} />
                      <VariableGroup title="Output" variables={activity.outputs ?? []} />
                    </div>
                  )}
                </li>
              )
            })}
            {!status && !error && <li className="hint">Loading activity history…</li>}
            {status && status.activities.length === 0 && (
              <li className="hint">No task activity recorded yet.</li>
            )}
          </ol>
        </>
      ) : null}
    </aside>
  )
}

function ProgressDots() {
  return (
    <span className="progress-dots" aria-hidden="true">
      <span />
      <span />
      <span />
    </span>
  )
}

function VariableGroup({ title, variables }: { title: string; variables: VariableEntry[] }) {
  if (variables.length === 0) {
    return (
      <div className="status-var-group">
        <p className="status-var-title">{title}</p>
        <p className="hint">None recorded</p>
      </div>
    )
  }

  return (
    <div className="status-var-group">
      <p className="status-var-title">{title}</p>
      <dl className="status-var-list">
        {variables.map((variable) => (
          <div key={`${title}-${variable.name}`}>
            <dt>
              {variable.name}
              {variable.type ? <span className="status-var-type">{variable.type}</span> : null}
            </dt>
            <dd>
              <pre>{formatVariableValue(variable.value)}</pre>
            </dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

function hasVariables(activity: ActivityStatusEntry): boolean {
  return (activity.inputs?.length ?? 0) + (activity.outputs?.length ?? 0) > 0
}

function formatState(state: string): string {
  return state.replaceAll('_', ' ').toLowerCase()
}

function formatActivityType(type: string): string {
  return type.replace(/([A-Z])/g, ' $1').trim()
}

function formatTime(value: string | number | null): string {
  if (value == null || value === '') {
    return ''
  }
  const date = typeof value === 'number' ? new Date(value) : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function formatVariableValue(value: unknown): string {
  if (value === null || value === undefined) {
    return 'null'
  }
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function formatEstimatedCost(value: number): string {
  if (value < 0.01) {
    return value.toFixed(6)
  }
  return value.toFixed(4)
}
