import { useEffect, useState } from 'react'
import { fetchHumanTasks, type HumanTaskSummary } from './api'
import { HumanTaskForm } from './HumanTaskForm'

type Props = {
  processInstanceId?: string | null
  onOpenInstance?: (processInstanceId: string) => void
  onTaskCompleted?: () => void
}

const POLL_MS = 4000

export function HumanTaskInbox({ processInstanceId, onOpenInstance, onTaskCompleted }: Props) {
  const [tasks, setTasks] = useState<HumanTaskSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [activeTaskId, setActiveTaskId] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    async function poll() {
      try {
        const list = await fetchHumanTasks(processInstanceId)
        if (cancelled) {
          return
        }
        setTasks(list.tasks)
        setError(null)
        if (activeTaskId && !list.tasks.some((task) => task.taskId === activeTaskId)) {
          setActiveTaskId(null)
        }
      } catch (err: unknown) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load tasks')
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
  }, [processInstanceId, activeTaskId])

  function refreshSoon() {
    void fetchHumanTasks(processInstanceId)
      .then((list) => {
        setTasks(list.tasks)
        setError(null)
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load tasks')
      })
  }

  if (activeTaskId) {
    return (
      <div className="human-task-inbox">
        <HumanTaskForm
          taskId={activeTaskId}
          onCancel={() => setActiveTaskId(null)}
          onCompleted={() => {
            setActiveTaskId(null)
            refreshSoon()
            onTaskCompleted?.()
          }}
        />
      </div>
    )
  }

  if (!error && (!tasks || tasks.length === 0)) {
    return null
  }

  return (
    <div className="human-task-inbox">
      <div className="instance-list-head">
        <p className="sample-preview-label">
          Human tasks{tasks ? ` · ${tasks.length}` : ''}
        </p>
        <button type="button" className="panel-back" onClick={refreshSoon}>
          Refresh
        </button>
      </div>
      {error ? <p className="hint danger">{error}</p> : null}
      {tasks && tasks.length > 0 ? (
        <ul className="human-task-entries">
          {tasks.map((task) => (
            <li key={task.taskId}>
              <div className="human-task-entry">
                <div className="human-task-entry-main">
                  <strong>{task.name}</strong>
                  <span className="hint">
                    {task.businessKey || task.processInstanceId}
                    {task.claimed ? ` · claimed by ${task.assignee}` : ' · unclaimed'}
                    {task.created ? ` · ${new Date(task.created).toLocaleString()}` : ''}
                  </span>
                </div>
                <div className="human-task-entry-actions">
                  {onOpenInstance ? (
                    <button
                      type="button"
                      className="panel-back"
                      onClick={() => onOpenInstance(task.processInstanceId)}
                    >
                      Instance
                    </button>
                  ) : null}
                  <a
                    className="panel-back"
                    href={`#task-${task.taskId}`}
                    onClick={(event) => {
                      event.preventDefault()
                      setActiveTaskId(task.taskId)
                    }}
                  >
                    {task.claimed ? 'Open' : 'Claim & open'}
                  </a>
                </div>
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
