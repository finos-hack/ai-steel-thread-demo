import { useEffect, useState } from 'react'
import {
  downloadComplianceReportPdf,
  fetchComplianceReport,
  type CheckFinding,
  type LoanApprovalComplianceReport,
} from './api'
import { Mi1Badge } from './Mi1Badge'
import { Mi4Badge } from './Mi4Badge'
import { Mi5Badge } from './Mi5Badge'
import { Mi11Badge } from './Mi11Badge'

type Props = {
  processInstanceId: string
}

const POLL_MS = 4000
const TERMINAL = new Set(['COMPLETED', 'EXTERNALLY_TERMINATED', 'INTERNALLY_TERMINATED'])

export function ComplianceEvidenceReport({ processInstanceId }: Props) {
  const [report, setReport] = useState<LoanApprovalComplianceReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [expanded, setExpanded] = useState(false)
  const [downloading, setDownloading] = useState(false)

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    async function poll() {
      try {
        const next = await fetchComplianceReport(processInstanceId)
        if (cancelled) {
          return
        }
        setReport(next)
        setError(null)
        if (!TERMINAL.has(next.state) || next.overallStatus === 'IN_PROGRESS') {
          timer = window.setTimeout(poll, POLL_MS)
        }
      } catch (err: unknown) {
        if (cancelled) {
          return
        }
        setError(err instanceof Error ? err.message : 'Failed to load compliance report')
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

  async function onDownloadPdf() {
    setDownloading(true)
    setError(null)
    try {
      await downloadComplianceReportPdf(processInstanceId)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to download PDF')
    } finally {
      setDownloading(false)
    }
  }

  const overall = report?.overallStatus ?? 'LOADING'
  const overallLabel = formatOverallStatus(overall)
  const overallInProgress = overall === 'IN_PROGRESS'

  return (
    <aside className="compliance-report" aria-live="polite">
      <div className="compliance-report-head">
        <button
          type="button"
          className="governance-fold"
          aria-expanded={expanded}
          onClick={() => setExpanded((value) => !value)}
        >
          <span className="sample-preview-label">Compliance evidence</span>
          <span className="governance-chevron" aria-hidden="true">
            {expanded ? '−' : '+'}
          </span>
        </button>
        <div className="compliance-report-actions">
          <button
            type="button"
            className="compliance-download-icon"
            disabled={!report || downloading}
            aria-label={downloading ? 'Preparing PDF' : 'Download compliance evidence PDF'}
            title={downloading ? 'Preparing PDF…' : 'Download PDF'}
            onClick={(event) => {
              event.stopPropagation()
              void onDownloadPdf()
            }}
          >
            {downloading ? (
              <span className="compliance-download-spinner" aria-hidden="true" />
            ) : (
              <DownloadPdfIcon />
            )}
          </button>
          <p className={`compliance-overall status-${overall.toLowerCase()}`}>
            {overallLabel}
            {overallInProgress ? <ProgressDots /> : null}
          </p>
        </div>
      </div>

      {error ? <p className="hint danger">{error}</p> : null}

      {expanded && report ? (
        <div className="compliance-report-body">
          <p className="hint">
            Per-instance evidence from mi-1 / mi-4 / mi-11 controls and decision variables. Does not
            re-run the mi-5 SAT suite.
          </p>

          <div className="compliance-control-row">
            <Mi1Badge className={report.controls.mi1Enabled ? undefined : 'is-off'}>
              mi-1 {report.controls.mi1Enabled ? 'on' : 'off'}
            </Mi1Badge>
            <Mi4Badge className={report.controls.mi4Enabled ? undefined : 'is-off'}>
              mi-4 {report.controls.mi4Enabled ? 'on' : 'off'}
            </Mi4Badge>
            <Mi5Badge className={report.controls.mi5Enabled ? undefined : 'is-off'}>
              mi-5 {report.controls.mi5Enabled ? 'on' : 'off'}
            </Mi5Badge>
            <Mi11Badge className={report.controls.mi11Enabled ? undefined : 'is-off'}>
              mi-11 {report.controls.mi11Enabled ? 'on' : 'off'}
            </Mi11Badge>
          </div>
          <p className="hint">
            {report.controls.instanceSnapshot
              ? `Control posture frozen at instance start${
                  report.controls.snapshottedAt
                    ? ` · ${new Date(report.controls.snapshottedAt).toLocaleString()}`
                    : ''
                }`
              : 'Control posture from live environment (this instance predates snapshots).'}
          </p>

          <dl className="compliance-decision">
            <div>
              <dt>Amount</dt>
              <dd>
                {report.decisionPath.requestedAmount != null
                  ? report.decisionPath.requestedAmount.toLocaleString()
                  : '—'}
              </dd>
            </div>
            <div>
              <dt>Purpose</dt>
              <dd>{report.decisionPath.loanPurpose ?? '—'}</dd>
            </div>
            <div>
              <dt>Credit</dt>
              <dd>
                {report.decisionPath.creditScore != null
                  ? `${report.decisionPath.creditScore}${
                      report.decisionPath.creditRiskBand
                        ? ` · ${report.decisionPath.creditRiskBand}`
                        : ''
                    }`
                  : '—'}
              </dd>
            </div>
            <div>
              <dt>Decision</dt>
              <dd>{report.decisionPath.approvalDecision ?? '—'}</dd>
            </div>
            <div>
              <dt>Loan officer</dt>
              <dd>{report.decisionPath.loanOfficerDecision ?? '—'}</dd>
            </div>
            <div>
              <dt>Agrees with AI</dt>
              <dd>
                {report.decisionPath.agreesWithAi == null
                  ? '—'
                  : report.decisionPath.agreesWithAi
                    ? 'Yes'
                    : 'No'}
              </dd>
            </div>
          </dl>

          <p className="governance-subhead">Checks</p>
          <ul className="compliance-checks">
            {report.checks.map((check) => (
              <CheckRow key={check.id} check={check} />
            ))}
          </ul>

          {report.llmCalls.length > 0 ? (
            <>
              <p className="governance-subhead">LLM calls</p>
              <ul className="compliance-llm-calls">
                {report.llmCalls.map((call) => (
                  <li key={`${call.activityId}-${call.model ?? 'unknown'}`}>
                    <strong>{call.activityName}</strong>
                    <span>
                      {[call.provider, call.model, call.modelVersion].filter(Boolean).join(' · ') ||
                        'model unknown'}
                      {call.totalTokens != null ? ` · ${call.totalTokens} tokens` : ''}
                      {call.estimatedCostUsd != null
                        ? ` · ~$${
                            call.estimatedCostUsd < 0.01
                              ? call.estimatedCostUsd.toFixed(6)
                              : call.estimatedCostUsd.toFixed(4)
                          }`
                        : ''}
                      {call.durationMs != null ? ` · ${call.durationMs} ms` : ''}
                    </span>
                    {call.contextKeys ? (
                      <span className="hint">context keys: {call.contextKeys}</span>
                    ) : null}
                  </li>
                ))}
              </ul>
            </>
          ) : null}

          <p className="governance-subhead">System acceptance (mi-5)</p>
          <p className="governance-copy">
            {report.sat.note}
            {report.sat.lastRanAt
              ? ` · last run ${new Date(report.sat.lastRanAt).toLocaleString()}`
              : ''}
          </p>
        </div>
      ) : null}

      {expanded && !report && !error ? <p className="hint">Building evidence…</p> : null}
    </aside>
  )
}

function CheckRow({ check }: { check: CheckFinding }) {
  return (
    <li className={`compliance-check status-${check.status}`}>
      <span className="compliance-check-status">{check.status}</span>
      <span className="compliance-check-main">
        <strong>
          <code>{check.control}</code> {check.title}
        </strong>
        <span className="hint">{check.detail}</span>
      </span>
    </li>
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

function formatOverallStatus(status: string): string {
  if (status === 'IN_PROGRESS') {
    return 'In progress'
  }
  if (status === 'LOADING') {
    return 'Loading'
  }
  return status
}

function DownloadPdfIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false">
      <path
        fill="currentColor"
        d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm1 7V3.5L18.5 9H15zM8 13h2.5a1.5 1.5 0 0 1 0 3H9v2H8v-5zm1 1v2h1.5a.5.5 0 0 0 0-1H9zm3.25 0H13a.75.75 0 0 1 .75.75v3.5a.75.75 0 0 1-.75.75h-.75A.75.75 0 0 1 11.5 18.25v-3.5a.75.75 0 0 1 .75-.75zm.75 1v2.5h.25V15h-.25zM16 13h2v1h-1v1h1v1h-1v2h-1v-5z"
      />
    </svg>
  )
}
