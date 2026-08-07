import { useId, useState, type ReactNode } from 'react'
import type {
  LoanApprovalOptions,
  Mi1DlpStatus,
  Mi4ObservabilityStatus,
  Mi5AcceptanceReport,
  Mi5AcceptanceStatus,
  Mi11FeedbackStatus,
} from './api'
import { fetchLoanApprovalOptions, runAcceptanceTests, updateControlEnforcement } from './api'
import { Mi1Badge } from './Mi1Badge'
import { Mi4Badge } from './Mi4Badge'
import { Mi5Badge } from './Mi5Badge'
import { Mi11Badge } from './Mi11Badge'
import type { UseCaseCatalogEntry } from './useCases'

type Props = {
  useCase: UseCaseCatalogEntry
  mi1Dlp?: Mi1DlpStatus | null
  mi4Observability?: Mi4ObservabilityStatus | null
  mi5Acceptance?: Mi5AcceptanceStatus | null
  mi11Feedback?: Mi11FeedbackStatus | null
  onControlsUpdated?: (options: LoanApprovalOptions) => void
}

type SectionId =
  | 'functions'
  | 'business-value'
  | 'end-users'
  | 'data-sensitivity'
  | 'regulatory'
  | 'risks'

export function UseCaseGovernance({
  useCase,
  mi1Dlp,
  mi4Observability,
  mi5Acceptance,
  mi11Feedback,
  onControlsUpdated,
}: Props) {
  const [expanded, setExpanded] = useState(false)
  const [controlsExpanded, setControlsExpanded] = useState(false)
  const [openSection, setOpenSection] = useState<SectionId | null>('risks')
  const [saving, setSaving] = useState<'mi1' | 'mi4' | 'mi5' | 'mi11' | null>(null)
  const [runningSat, setRunningSat] = useState(false)
  const [toggleError, setToggleError] = useState<string | null>(null)
  const [satReport, setSatReport] = useState<Mi5AcceptanceReport | null>(
    mi5Acceptance?.lastReport ?? null,
  )
  const baseId = useId()
  const bodyId = `${baseId}-body`
  const controlsBodyId = `${baseId}-controls`
  const hasControls = Boolean(mi1Dlp || mi4Observability || mi5Acceptance || mi11Feedback)
  const mi1Enforced = mi1Dlp?.enabled === true
  const mi4Enforced = mi4Observability?.enabled === true
  const mi5Enforced = mi5Acceptance?.enabled === true
  const mi11Enforced = mi11Feedback?.enabled === true
  const lastReport = satReport ?? mi5Acceptance?.lastReport ?? null

  function toggle(section: SectionId) {
    setOpenSection((current) => (current === section ? null : section))
  }

  async function setControl(control: 'mi1' | 'mi4' | 'mi5' | 'mi11', enabled: boolean) {
    setSaving(control)
    setToggleError(null)
    try {
      const payload =
        control === 'mi1'
          ? { mi1Enabled: enabled }
          : control === 'mi4'
            ? { mi4Enabled: enabled }
            : control === 'mi5'
              ? { mi5Enabled: enabled }
              : { mi11Enabled: enabled }
      const next = await updateControlEnforcement(payload)
      onControlsUpdated?.(next)
    } catch (error: unknown) {
      setToggleError(error instanceof Error ? error.message : 'Failed to update controls')
    } finally {
      setSaving(null)
    }
  }

  async function runSat() {
    setRunningSat(true)
    setToggleError(null)
    try {
      const report = await runAcceptanceTests()
      setSatReport(report)
      const next = await fetchLoanApprovalOptions()
      onControlsUpdated?.(next)
    } catch (error: unknown) {
      setToggleError(error instanceof Error ? error.message : 'Failed to run acceptance suite')
    } finally {
      setRunningSat(false)
    }
  }

  return (
    <div className={`governance ${expanded ? 'is-expanded' : ''}`}>
      <div className="governance-head">
        <button
          type="button"
          className="governance-fold"
          aria-expanded={expanded}
          aria-controls={bodyId}
          onClick={() => setExpanded((value) => !value)}
        >
          <span className="sample-preview-label">FINOS AI Governance</span>
          <span className="governance-chevron" aria-hidden="true">
            {expanded ? '−' : '+'}
          </span>
        </button>
        <div className="governance-head-actions">
          {mi1Dlp ? (
            <Mi1Badge className={mi1Enforced ? undefined : 'is-off'}>
              {mi1Enforced ? 'mi-1 on' : 'mi-1 off'}
            </Mi1Badge>
          ) : null}
          {mi4Observability ? (
            <Mi4Badge className={mi4Enforced ? undefined : 'is-off'}>
              {mi4Enforced ? 'mi-4 on' : 'mi-4 off'}
            </Mi4Badge>
          ) : null}
          {mi5Acceptance ? (
            <Mi5Badge className={mi5Enforced ? undefined : 'is-off'}>
              {mi5Enforced ? 'mi-5 on' : 'mi-5 off'}
            </Mi5Badge>
          ) : null}
          {mi11Feedback ? (
            <Mi11Badge className={mi11Enforced ? undefined : 'is-off'}>
              {mi11Enforced ? 'mi-11 on' : 'mi-11 off'}
            </Mi11Badge>
          ) : null}
          <a
            className="governance-catalog-link"
            href={useCase.catalogHref}
            target="_blank"
            rel="noreferrer"
            onClick={(event) => event.stopPropagation()}
          >
            Open catalog
          </a>
        </div>
      </div>

      {expanded ? (
        <div id={bodyId} className="governance-body">
          <p className="governance-meta">
            Category · {useCase.category.replaceAll('_', ' ')} ·{' '}
            <a href={useCase.sourceHref} target="_blank" rel="noreferrer">
              UC-3 source
            </a>
          </p>

          {toggleError ? <p className="hint danger">{toggleError}</p> : null}

          {hasControls ? (
            <div className={`governance-controls${controlsExpanded ? ' is-expanded' : ''}`}>
              <div className="governance-controls-head">
                <button
                  type="button"
                  className="governance-fold"
                  aria-expanded={controlsExpanded}
                  aria-controls={controlsBodyId}
                  onClick={() => setControlsExpanded((value) => !value)}
                >
                  <span className="sample-preview-label">Mitigation controls</span>
                  <span className="governance-chevron" aria-hidden="true">
                    {controlsExpanded ? '−' : '+'}
                  </span>
                </button>
              </div>
              {controlsExpanded ? (
                <div id={controlsBodyId} className="governance-controls-body">
                  {mi1Dlp ? (
                    <Mi1EnforcementCallout
                      status={mi1Dlp}
                      saving={saving === 'mi1'}
                      onToggle={(enabled) => void setControl('mi1', enabled)}
                    />
                  ) : null}
                  {mi4Observability ? (
                    <Mi4EnforcementCallout
                      status={mi4Observability}
                      saving={saving === 'mi4'}
                      onToggle={(enabled) => void setControl('mi4', enabled)}
                    />
                  ) : null}
                  {mi5Acceptance ? (
                    <Mi5EnforcementCallout
                      status={mi5Acceptance}
                      lastReport={lastReport}
                      saving={saving === 'mi5'}
                      running={runningSat}
                      onToggle={(enabled) => void setControl('mi5', enabled)}
                      onRun={() => void runSat()}
                    />
                  ) : null}
                  {mi11Feedback ? (
                    <Mi11EnforcementCallout
                      status={mi11Feedback}
                      saving={saving === 'mi11'}
                      onToggle={(enabled) => void setControl('mi11', enabled)}
                    />
                  ) : null}
                </div>
              ) : null}
            </div>
          ) : null}

          <div className="governance-sections">
            <GovernanceSection
              id={`${baseId}-functions`}
              title="Key functions"
              open={openSection === 'functions'}
              onToggle={() => toggle('functions')}
            >
              <ul className="governance-list">
                {useCase.keyFunctions.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </GovernanceSection>

            <GovernanceSection
              id={`${baseId}-business`}
              title="Business value"
              open={openSection === 'business-value'}
              onToggle={() => toggle('business-value')}
            >
              <p className="governance-copy">{useCase.businessValue.summary}</p>
              <ul className="governance-list">
                {useCase.businessValue.benefits.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </GovernanceSection>

            <GovernanceSection
              id={`${baseId}-users`}
              title="End users"
              open={openSection === 'end-users'}
              onToggle={() => toggle('end-users')}
            >
              <dl className="governance-dl">
                <div>
                  <dt>Primary</dt>
                  <dd>{useCase.endUsers.primary}</dd>
                </div>
                <div>
                  <dt>Secondary</dt>
                  <dd>{useCase.endUsers.secondary}</dd>
                </div>
                <div>
                  <dt>Tertiary</dt>
                  <dd>{useCase.endUsers.tertiary}</dd>
                </div>
              </dl>
            </GovernanceSection>

            <GovernanceSection
              id={`${baseId}-data`}
              title="Data sensitivity"
              open={openSection === 'data-sensitivity'}
              onToggle={() => toggle('data-sensitivity')}
            >
              <ul className="governance-list">
                {useCase.dataSensitivity.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </GovernanceSection>

            <GovernanceSection
              id={`${baseId}-regulatory`}
              title="Regulatory concerns"
              open={openSection === 'regulatory'}
              onToggle={() => toggle('regulatory')}
            >
              <ul className="governance-links">
                {useCase.regulatoryConcerns.map((item) => (
                  <li key={item.name}>
                    <a href={item.href} target="_blank" rel="noreferrer">
                      {item.name}
                    </a>
                    <span>{item.jurisdiction}</span>
                  </li>
                ))}
              </ul>
            </GovernanceSection>

            <GovernanceSection
              id={`${baseId}-risks`}
              title="AI risks & mitigations"
              open={openSection === 'risks'}
              onToggle={() => toggle('risks')}
            >
              <p className="governance-subhead">Related risks</p>
              <ul className="governance-links">
                {useCase.risks.map((item) => (
                  <li key={item.id}>
                    <a href={item.href} target="_blank" rel="noreferrer">
                      <code>{item.id}</code> {item.title}
                    </a>
                  </li>
                ))}
              </ul>
              <p className="governance-subhead">Related mitigations</p>
              <ul className="governance-links">
                {useCase.mitigations.map((item) => (
                  <li
                    key={item.id}
                    className={
                      (item.id === 'mi-1' && mi1Enforced) ||
                      (item.id === 'mi-4' && mi4Enforced) ||
                      (item.id === 'mi-5' && mi5Enforced) ||
                      (item.id === 'mi-11' && mi11Enforced)
                        ? 'has-control'
                        : undefined
                    }
                  >
                    <span className="governance-link-row">
                      <a href={item.href} target="_blank" rel="noreferrer">
                        <code>{item.id}</code> {item.title}
                      </a>
                      {item.id === 'mi-1' && mi1Enforced ? <Mi1Badge>On</Mi1Badge> : null}
                      {item.id === 'mi-4' && mi4Enforced ? <Mi4Badge>On</Mi4Badge> : null}
                      {item.id === 'mi-5' && mi5Enforced ? <Mi5Badge>On</Mi5Badge> : null}
                      {item.id === 'mi-11' && mi11Enforced ? <Mi11Badge>On</Mi11Badge> : null}
                    </span>
                  </li>
                ))}
              </ul>
              <p className="governance-footnote">
                Browse the full{' '}
                <a href={useCase.catalogHref} target="_blank" rel="noreferrer">
                  FINOS AI Governance Framework catalog
                </a>
                .
              </p>
            </GovernanceSection>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function ControlToggle({
  id,
  label,
  checked,
  disabled,
  onChange,
}: {
  id: string
  label: string
  checked: boolean
  disabled?: boolean
  onChange: (next: boolean) => void
}) {
  return (
    <label className="control-toggle" htmlFor={id}>
      <input
        id={id}
        type="checkbox"
        role="switch"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="control-toggle-track" aria-hidden="true" />
      <span className="control-toggle-label">{label}</span>
    </label>
  )
}

function Mi1EnforcementCallout({
  status,
  saving,
  onToggle,
}: {
  status: Mi1DlpStatus
  saving: boolean
  onToggle: (enabled: boolean) => void
}) {
  const enabled = status.enabled
  return (
    <div className={`control-callout${enabled ? '' : ' is-off'}`}>
      <div className="control-callout-head">
        <Mi1Badge className={enabled ? undefined : 'is-off'}>mi-1 runtime</Mi1Badge>
        <p className="control-callout-title">
          {enabled ? 'AI data leakage prevention is active' : 'AI data leakage prevention is off'}
        </p>
        <ControlToggle
          id="mi1-enforcement"
          label={saving ? 'Saving…' : enabled ? 'Enforced' : 'Optional'}
          checked={enabled}
          disabled={saving}
          onChange={onToggle}
        />
      </div>
      {enabled ? (
        <>
          <p className="governance-copy">
            Credit-risk agent context is allowlisted before OpenAI calls; identity media is limited
            to document intelligence; status API
            {status.redactStatusApi ? ' redacts ' : ' can expose '}
            forbidden PII and conversation traces.
            {status.strict ? ' Strict mode blocks disallowed context.' : ''}
          </p>
          <p className="governance-subhead">Agent context allowlist</p>
          <p className="control-chip-row">
            {status.creditRiskContextAllowlist.map((name) => (
              <code key={name} className="control-chip">
                {name}
              </code>
            ))}
          </p>
          <p className="governance-subhead">Blocked / redacted</p>
          <p className="control-chip-row">
            {status.forbiddenVariableNames.slice(0, 8).map((name) => (
              <code key={name} className="control-chip is-blocked">
                {name}
              </code>
            ))}
            {status.forbiddenVariableNames.length > 8 ? (
              <span className="hint">+{status.forbiddenVariableNames.length - 8} more</span>
            ) : null}
          </p>
        </>
      ) : (
        <p className="governance-copy">
          Turn on to enforce allowlisting before hosted-model egress and redact sensitive fields in
          the execution log. Change applies immediately for new LLM calls (in-memory until app
          restart).
        </p>
      )}
    </div>
  )
}

function Mi4EnforcementCallout({
  status,
  saving,
  onToggle,
}: {
  status: Mi4ObservabilityStatus
  saving: boolean
  onToggle: (enabled: boolean) => void
}) {
  const enabled = status.enabled
  const grafanaHref = status.grafanaBaseUrl?.replace(/\/$/, '') ?? 'http://localhost:3000'
  return (
    <div className={`control-callout${enabled ? '' : ' is-off'}`}>
      <div className="control-callout-head">
        <Mi4Badge className={enabled ? undefined : 'is-off'}>mi-4 runtime</Mi4Badge>
        <p className="control-callout-title">
          {enabled ? 'AI system observability is active' : 'AI system observability is off'}
        </p>
        <ControlToggle
          id="mi4-enforcement"
          label={saving ? 'Saving…' : enabled ? 'Enforced' : 'Optional'}
          checked={enabled}
          disabled={saving}
          onChange={onToggle}
        />
      </div>
      {enabled ? (
        <>
          <p className="governance-copy">
            LLM calls emit token, latency, and error metrics with model version{' '}
            <code>{status.modelVersionLabel}</code>
            {status.sanitizedAuditLog
              ? '; sanitized audit logs omit raw prompts and media'
              : ''}
            {status.persistCallMetadata
              ? '; last-call metadata is stored on the process instance'
              : ''}
            .
          </p>
          <p className="governance-subhead">Operator dashboards</p>
          <p className="control-chip-row">
            {status.dashboardPaths.map((path) => {
              const href = `${grafanaHref}${path.startsWith('/') ? path : `/${path}`}`
              const label = path.includes('ai-observability')
                ? 'AI observability'
                : path.includes('task-execution')
                  ? 'Task execution'
                  : path
              return (
                <a key={path} className="control-chip" href={href} target="_blank" rel="noreferrer">
                  {label}
                </a>
              )
            })}
          </p>
        </>
      ) : (
        <p className="governance-copy">
          Turn on for LLM latency/error metrics, sanitized audit lines, and process call metadata.
          Basic token counters may still emit. Change applies immediately (in-memory until app
          restart).
        </p>
      )}
    </div>
  )
}

function Mi5EnforcementCallout({
  status,
  lastReport,
  saving,
  running,
  onToggle,
  onRun,
}: {
  status: Mi5AcceptanceStatus
  lastReport: Mi5AcceptanceReport | null
  saving: boolean
  running: boolean
  onToggle: (enabled: boolean) => void
  onRun: () => void
}) {
  const enabled = status.enabled
  const passPct = lastReport ? Math.round(lastReport.passRate * 100) : null
  const thresholdPct = Math.round(status.passRateThreshold * 100)
  return (
    <div className={`control-callout${enabled ? '' : ' is-off'}`}>
      <div className="control-callout-head">
        <Mi5Badge className={enabled ? undefined : 'is-off'}>mi-5 runtime</Mi5Badge>
        <p className="control-callout-title">
          {enabled ? 'System acceptance testing is available' : 'System acceptance testing is off'}
        </p>
        <ControlToggle
          id="mi5-enforcement"
          label={saving ? 'Saving…' : enabled ? 'Enforced' : 'Optional'}
          checked={enabled}
          disabled={saving || running}
          onChange={onToggle}
        />
      </div>
      {enabled ? (
        <>
          <p className="governance-copy">
            Deterministic SAT suite checks credit-risk bands, approval decisions, mi-1 DLP
            invariants, loan bounds, and document validity. Acceptance requires ≥{thresholdPct}%
            pass rate (tolerates LLM output variability via soft/band assertions).
          </p>
          <p className="governance-subhead">Criteria categories</p>
          <p className="control-chip-row">
            {status.criteriaCategories.map((name) => (
              <code key={name} className="control-chip">
                {name}
              </code>
            ))}
          </p>
          <div className="control-callout-actions">
            <button
              type="button"
              className="panel-back"
              disabled={running}
              onClick={onRun}
            >
              {running ? 'Running SAT…' : 'Run SAT'}
            </button>
            {lastReport ? (
              <span className="hint">
                Last run{' '}
                <Mi5Badge className={lastReport.accepted ? undefined : 'is-off'}>
                  {lastReport.accepted ? 'accepted' : 'not accepted'}
                </Mi5Badge>{' '}
                · {passPct}% ({lastReport.passed}/{lastReport.total})
              </span>
            ) : (
              <span className="hint">No suite run yet in this session.</span>
            )}
          </div>
          {status.aigfCatalogUrl ? (
            <p className="governance-footnote">
              <a href={status.aigfCatalogUrl} target="_blank" rel="noreferrer">
                mi-5 catalog page
              </a>
            </p>
          ) : null}
        </>
      ) : (
        <p className="governance-copy">
          Turn on to enable the system acceptance suite for AI decision paths. Change applies
          immediately (in-memory until app restart).
        </p>
      )}
    </div>
  )
}

function Mi11EnforcementCallout({
  status,
  saving,
  onToggle,
}: {
  status: Mi11FeedbackStatus
  saving: boolean
  onToggle: (enabled: boolean) => void
}) {
  const enabled = status.enabled
  return (
    <div className={`control-callout${enabled ? '' : ' is-off'}`}>
      <div className="control-callout-head">
        <Mi11Badge className={enabled ? undefined : 'is-off'}>mi-11 runtime</Mi11Badge>
        <p className="control-callout-title">
          {enabled ? 'Human feedback loop is available' : 'Human feedback loop is off'}
        </p>
        <ControlToggle
          id="mi11-enforcement"
          label={saving ? 'Saving…' : enabled ? 'Enforced' : 'Optional'}
          checked={enabled}
          disabled={saving}
          onChange={onToggle}
        />
      </div>
      {enabled ? (
        <>
          <p className="governance-copy">
            When <code>approvalDecision</code> is <code>MANUAL_REVIEW</code>, BPMN waits on{' '}
            <code>{status.loanOfficerTaskDefinitionKey}</code>. Launch UI (or Cockpit form{' '}
            <code>credit_approval_review</code>) records <code>loanOfficerDecision</code> and
            structured agree/disagree feedback.
          </p>
          {status.aigfCatalogUrl ? (
            <p className="governance-footnote">
              <a href={status.aigfCatalogUrl} target="_blank" rel="noreferrer">
                mi-11 catalog page
              </a>
            </p>
          ) : null}
        </>
      ) : (
        <p className="governance-copy">
          Turn on to assert structured loan-officer feedback in evidence and enable Launch UI
          completion. The BPMN user task still waits on MANUAL_REVIEW regardless. Change applies
          immediately (in-memory until app restart).
        </p>
      )}
    </div>
  )
}

function GovernanceSection({
  id,
  title,
  open,
  onToggle,
  children,
}: {
  id: string
  title: string
  open: boolean
  onToggle: () => void
  children: ReactNode
}) {
  return (
    <section className={`governance-section${open ? ' is-open' : ''}`}>
      <button
        type="button"
        className="governance-section-toggle"
        aria-expanded={open}
        aria-controls={id}
        onClick={onToggle}
      >
        <span>{title}</span>
        <span className="governance-chevron" aria-hidden="true">
          {open ? '−' : '+'}
        </span>
      </button>
      {open ? (
        <div id={id} className="governance-section-body">
          {children}
        </div>
      ) : null}
    </section>
  )
}
