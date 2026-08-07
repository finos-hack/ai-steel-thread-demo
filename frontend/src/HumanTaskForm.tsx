import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import {
  claimHumanTask,
  completeHumanTask,
  fetchHumanTask,
  type HumanTaskAgentConversation,
  type HumanTaskDetail,
  type HumanTaskFormField,
} from './api'

type Props = {
  taskId: string
  onCompleted?: (detail: HumanTaskDetail) => void
  onCancel?: () => void
}

export function HumanTaskForm({ taskId, onCompleted, onCancel }: Props) {
  const [detail, setDetail] = useState<HumanTaskDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [claiming, setClaiming] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [values, setValues] = useState<Record<string, string | boolean>>({})

  useEffect(() => {
    let cancelled = false
    setDetail(null)
    setError(null)
    fetchHumanTask(taskId)
      .then((next) => {
        if (!cancelled) {
          setDetail(next)
          setValues(initialValues(next.form.fields))
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load task')
        }
      })
    return () => {
      cancelled = true
    }
  }, [taskId])

  const contextRows = useMemo(() => {
    if (!detail) {
      return []
    }
    return Object.entries(detail.context)
      .filter(([, value]) => value != null && String(value).length > 0)
      .map(([key, value]) => ({
        property: key,
        value: formatContextValue(value),
      }))
  }, [detail])

  async function handleClaim() {
    setClaiming(true)
    setError(null)
    try {
      const next = await claimHumanTask(taskId)
      setDetail(next)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to claim task')
    } finally {
      setClaiming(false)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!detail) {
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      let current = detail
      if (!current.claimed) {
        current = await claimHumanTask(taskId)
        setDetail(current)
      }
      const variables: Record<string, unknown> = {}
      for (const field of current.form.fields) {
        const value = values[field.id]
        if (value === undefined || value === '') {
          if (field.required) {
            throw new Error(`${field.label} is required`)
          }
          continue
        }
        variables[field.id] = value
      }
      const completed = await completeHumanTask(taskId, variables)
      onCompleted?.(completed)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to complete task')
    } finally {
      setSubmitting(false)
    }
  }

  if (!detail && !error) {
    return (
      <aside className="human-task-form">
        <p className="hint">Loading task…</p>
      </aside>
    )
  }

  if (!detail) {
    return (
      <aside className="human-task-form">
        <p className="hint danger">{error}</p>
        {onCancel ? (
          <button type="button" className="panel-back" onClick={onCancel}>
            Back
          </button>
        ) : null}
      </aside>
    )
  }

  return (
    <aside className="human-task-form is-open">
      <div className="human-task-form-head">
        <div>
          <p className="sample-preview-label">{detail.form.title}</p>
          <h3 className="human-task-form-title">{detail.name}</h3>
        </div>
        <span className={`human-task-claim-state ${detail.claimed ? 'is-claimed' : ''}`}>
          {detail.claimed ? `Claimed · ${detail.assignee}` : 'Unclaimed'}
        </span>
      </div>
      <p className="hint">{detail.form.description}</p>

      {detail.agentConversation ? (
        <AgentConversationPanel conversation={detail.agentConversation} />
      ) : detail.taskDefinitionKey === 'UserTask_LoanOfficerReview' ? (
        <p className="hint">
          Agent conversation not available yet for this instance (no credit-risk agent traces
          recorded).
        </p>
      ) : null}

      {contextRows.length > 0 ? (
        <table className="human-task-context-table">
          <thead>
            <tr>
              <th scope="col">Property</th>
              <th scope="col">Value</th>
            </tr>
          </thead>
          <tbody>
            {contextRows.map((row) => (
              <tr key={row.property}>
                <th scope="row">{row.property}</th>
                <td>{row.value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
      {error ? <p className="hint danger">{error}</p> : null}

      {!detail.claimed ? (
        <div className="human-task-actions">
          <button
            type="button"
            className="btn primary"
            disabled={claiming}
            onClick={() => void handleClaim()}
          >
            {claiming ? 'Claiming…' : 'Claim task'}
          </button>
          {onCancel ? (
            <button type="button" className="btn ghost" onClick={onCancel}>
              Cancel
            </button>
          ) : null}
        </div>
      ) : (
        <form className="human-feedback-form" onSubmit={(event) => void handleSubmit(event)}>
          {detail.form.fields.map((field) => (
            <FieldInput
              key={field.id}
              field={field}
              value={values[field.id]}
              onChange={(next) => setValues((current) => ({ ...current, [field.id]: next }))}
            />
          ))}
          <div className="human-task-actions">
            <button type="submit" className="btn primary" disabled={submitting}>
              {submitting ? 'Completing…' : 'Complete task'}
            </button>
            {onCancel ? (
              <button type="button" className="btn ghost" onClick={onCancel} disabled={submitting}>
                Cancel
              </button>
            ) : null}
          </div>
        </form>
      )}
    </aside>
  )
}

function AgentConversationPanel({
  conversation,
}: {
  conversation: HumanTaskAgentConversation
}) {
  const historyEntries = parseConversationHistory(conversation.history)
  const rawHistoryFallback =
    conversation.history != null && historyEntries.length === 0 && !conversation.trace
      ? typeof conversation.history === 'string'
        ? conversation.history
        : JSON.stringify(conversation.history, null, 2)
      : null

  return (
    <section className="agent-conversation-panel" aria-label="Credit-risk agent conversation">
      <p className="governance-subhead">Credit-risk agent conversation</p>
      <p className="hint">
        Shown unredacted for this review task. The execution log still redacts these traces under
        mi-1.
      </p>
      {conversation.lastResponse ? (
        <CollapsibleConversationBlock
          id="agent-last-response"
          label="Last response"
          defaultOpen
        >
          <pre className="agent-conversation-text">{conversation.lastResponse}</pre>
        </CollapsibleConversationBlock>
      ) : null}
      {conversation.trace ? (
        <CollapsibleConversationBlock id="agent-conversation-trace" label="Conversation trace">
          <pre className="agent-conversation-text">{conversation.trace}</pre>
        </CollapsibleConversationBlock>
      ) : null}
      {historyEntries.length > 0 ? (
        <CollapsibleConversationBlock id="agent-conversation-history" label="History">
          <ol className="agent-conversation-history">
            {historyEntries.map((entry, index) => (
              <li key={`${entry.role}-${index}`}>
                <strong>{entry.role}</strong>
                <span>{entry.content}</span>
              </li>
            ))}
          </ol>
        </CollapsibleConversationBlock>
      ) : rawHistoryFallback ? (
        <CollapsibleConversationBlock id="agent-conversation-history" label="History">
          <pre className="agent-conversation-text">{rawHistoryFallback}</pre>
        </CollapsibleConversationBlock>
      ) : null}
    </section>
  )
}

function CollapsibleConversationBlock({
  id,
  label,
  defaultOpen = false,
  children,
}: {
  id: string
  label: string
  defaultOpen?: boolean
  children: ReactNode
}) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className={`agent-conversation-block${open ? ' is-open' : ''}`}>
      <button
        type="button"
        className="governance-fold agent-conversation-fold"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((value) => !value)}
      >
        <span className="sample-preview-label">{label}</span>
        <span className="governance-chevron" aria-hidden="true">
          {open ? '−' : '+'}
        </span>
      </button>
      {open ? (
        <div id={id} className="agent-conversation-body">
          {children}
        </div>
      ) : null}
    </div>
  )
}

function parseConversationHistory(
  history: unknown,
): Array<{ role: string; content: string }> {
  let raw: unknown = history
  if (typeof history === 'string') {
    try {
      raw = JSON.parse(history)
    } catch {
      return []
    }
  }
  if (!Array.isArray(raw)) {
    return []
  }
  const entries: Array<{ role: string; content: string }> = []
  for (const item of raw) {
    if (!item || typeof item !== 'object') {
      continue
    }
    const record = item as Record<string, unknown>
    const role = String(record.role ?? record.type ?? record.speaker ?? 'message')
    const content =
      record.content ??
      record.text ??
      record.message ??
      record.assistantText ??
      (record.toolCalls != null ? JSON.stringify(record.toolCalls) : null)
    if (content == null) {
      continue
    }
    const text =
      typeof content === 'string' ? content : JSON.stringify(content, null, 2)
    if (text.trim()) {
      entries.push({ role, content: text })
    }
  }
  return entries
}

function FieldInput({
  field,
  value,
  onChange,
}: {
  field: HumanTaskFormField
  value: string | boolean | undefined
  onChange: (next: string | boolean) => void
}) {
  if (field.type === 'enum' && field.options) {
    return (
      <fieldset>
        <legend>
          {field.label}
          {field.required ? ' *' : ''}
        </legend>
        {field.options.map((option) => (
          <label key={option}>
            <input
              type="radio"
              name={field.id}
              value={option}
              checked={value === option}
              onChange={() => onChange(option)}
            />
            {option}
          </label>
        ))}
        {field.help ? <p className="hint">{field.help}</p> : null}
      </fieldset>
    )
  }

  if (field.type === 'boolean') {
    return (
      <fieldset>
        <legend>
          {field.label}
          {field.required ? ' *' : ''}
        </legend>
        <label>
          <input
            type="radio"
            name={field.id}
            checked={value === true}
            onChange={() => onChange(true)}
          />
          Yes
        </label>
        <label>
          <input
            type="radio"
            name={field.id}
            checked={value === false}
            onChange={() => onChange(false)}
          />
          No
        </label>
        {field.help ? <p className="hint">{field.help}</p> : null}
      </fieldset>
    )
  }

  if (field.type === 'textarea') {
    return (
      <label className="human-feedback-comment">
        {field.label}
        {field.required ? ' *' : ''}
        <textarea
          value={typeof value === 'string' ? value : ''}
          rows={3}
          maxLength={500}
          required={field.required}
          onChange={(event) => onChange(event.target.value)}
          placeholder={field.help ?? undefined}
        />
      </label>
    )
  }

  return (
    <label className="human-feedback-comment">
      {field.label}
      {field.required ? ' *' : ''}
      <input
        type="text"
        value={typeof value === 'string' ? value : ''}
        required={field.required}
        onChange={(event) => onChange(event.target.value)}
      />
      {field.help ? <span className="hint">{field.help}</span> : null}
    </label>
  )
}

function initialValues(fields: HumanTaskFormField[]): Record<string, string | boolean> {
  const values: Record<string, string | boolean> = {}
  for (const field of fields) {
    if (field.type === 'enum' && field.options?.[0]) {
      values[field.id] = field.options[0]
    } else if (field.type === 'boolean') {
      values[field.id] = true
    } else {
      values[field.id] = ''
    }
  }
  return values
}

function formatContextValue(value: unknown): string {
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false'
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value.toLocaleString() : String(value)
  }
  return String(value)
}
