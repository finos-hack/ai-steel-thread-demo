import { useEffect, useState } from 'react'
import {
  fetchLoanApprovalInstance,
  fetchLoanApprovalInstances,
  fetchLoanApprovalOptions,
  startLoanApproval,
  toStartResponse,
  type LoanApprovalInstanceList,
  type LoanApprovalInstanceSummary,
  type LoanApprovalOptions,
  type StartLoanApprovalResponse,
  type UseCaseId,
} from './api'
import { CalmArchitectureVisualizer } from './CalmArchitectureVisualizer'
import { FinosProjectsPanel } from './FinosProjectsPanel'
import { LoanApprovalForm } from './LoanApprovalForm'
import { ComplianceEvidenceReport } from './ComplianceEvidenceReport'
import { HumanTaskInbox } from './HumanTaskInbox'
import { ProcessStatusLog } from './ProcessStatusLog'
import { UseCaseGovernance } from './UseCaseGovernance'
import { DemoGuide, type DemoGuideTab } from './DemoGuide'
import { TokenomicsPanel } from './TokenomicsPanel'
import { loanApprovalUseCase, useCaseCatalog, useCaseById } from './useCases'
import './App.css'

type Screen = 'catalog' | 'use-case' | 'loan-form' | 'loan-result' | 'tokenomics' | 'demo-guide'
type StringLike = string | null

const INSTANCE_POLL_MS = 4000

function readDemoGuideTab(): DemoGuideTab {
  const tab = new URLSearchParams(window.location.search).get('tab')
  return tab === 'narration' ? 'narration' : 'guide'
}

function readPresenterMode(): boolean {
  return new URLSearchParams(window.location.search).get('presenter') === '1'
}

export default function App() {
  const [presenterMode] = useState(readPresenterMode)
  const [demoGuideTab] = useState<DemoGuideTab>(readDemoGuideTab)
  const [screen, setScreen] = useState<Screen>(() =>
    presenterMode || new URLSearchParams(window.location.search).get('screen') === 'demo-guide'
      ? 'demo-guide'
      : 'catalog',
  )
  const [selectedUseCaseId, setSelectedUseCaseId] = useState<UseCaseId | null>(null)
  const [showCalmVisualizer, setShowCalmVisualizer] = useState(false)
  const [options, setOptions] = useState<LoanApprovalOptions | null>(null)
  const [optionsError, setOptionsError] = useState<StringLike>(null)
  const [optionsLoading, setOptionsLoading] = useState(true)
  const [instances, setInstances] = useState<LoanApprovalInstanceList | null>(null)
  const [instancesError, setInstancesError] = useState<StringLike>(null)
  const [result, setResult] = useState<StartLoanApprovalResponse | null>(null)
  const [resultState, setResultState] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<StringLike>(null)
  const [submitting, setSubmitting] = useState(false)
  const [instancesExpanded, setInstancesExpanded] = useState(false)

  const selectedUseCase =
    selectedUseCaseId != null ? useCaseById(selectedUseCaseId) ?? loanApprovalUseCase : null

  function loadOptions() {
    setOptionsLoading(true)
    fetchLoanApprovalOptions()
      .then((payload) => {
        setOptions(payload)
        setOptionsError(null)
      })
      .catch((error: unknown) => {
        setOptionsError(error instanceof Error ? error.message : 'Failed to load options')
      })
      .finally(() => {
        setOptionsLoading(false)
      })
  }

  function loadInstances() {
    fetchLoanApprovalInstances()
      .then((payload) => {
        setInstances(payload)
        setInstancesError(null)
      })
      .catch((error: unknown) => {
        setInstancesError(error instanceof Error ? error.message : 'Failed to load instances')
      })
  }

  useEffect(() => {
    loadOptions()
    loadInstances()
  }, [])

  useEffect(() => {
    if (screen !== 'use-case' && screen !== 'catalog') {
      return
    }
    const timer = window.setInterval(loadInstances, INSTANCE_POLL_MS)
    return () => window.clearInterval(timer)
  }, [screen])

  function openCatalog() {
    setScreen('catalog')
    setSelectedUseCaseId(null)
    loadInstances()
  }

  function openUseCaseCard(id: UseCaseId) {
    setSelectedUseCaseId(id)
    setSubmitError(null)
    setResult(null)
    setResultState(null)
    if (!options) {
      loadOptions()
    }
    setScreen('use-case')
    loadInstances()
  }

  function startNewInstance(id: UseCaseId) {
    if (id === 'loan-approval') {
      setSubmitError(null)
      setResult(null)
      setResultState(null)
      if (!options) {
        loadOptions()
      }
      setSelectedUseCaseId(id)
      setScreen('loan-form')
    }
  }

  function openInstance(summary: LoanApprovalInstanceSummary) {
    setResult(toStartResponse(summary))
    setResultState(summary.state)
    setSelectedUseCaseId((current) => current ?? 'loan-approval')
    setScreen('loan-result')
  }

  async function handleSubmit(values: {
    businessKey: string
    requestedAmount: string
    loanPurpose: string
    sampleDocument: string
    creditRiskAgentProvider: string
    creditRiskAgentModel: string
    creditRiskAgentCacheStrategy: string
    creditRiskAgentPromptCacheKey: string
    documentProcessingAgentProvider: string
    documentProcessingAgentModel: string
    documentProcessingAgentCacheStrategy: string
    documentProcessingAgentPromptCacheKey: string
  }) {
    setSubmitting(true)
    setSubmitError(null)
    try {
      const response = await startLoanApproval({
        businessKey: values.businessKey.trim() || null,
        requestedAmount: values.requestedAmount ? Number(values.requestedAmount) : null,
        loanPurpose: values.loanPurpose || null,
        sampleDocument: values.sampleDocument || null,
        creditRiskAgentProvider: values.creditRiskAgentProvider || null,
        creditRiskAgentModel: values.creditRiskAgentModel || null,
        creditRiskAgentCacheStrategy: values.creditRiskAgentCacheStrategy || null,
        creditRiskAgentPromptCacheKey: values.creditRiskAgentPromptCacheKey || null,
        documentProcessingAgentProvider: values.documentProcessingAgentProvider || null,
        documentProcessingAgentModel: values.documentProcessingAgentModel || null,
        documentProcessingAgentCacheStrategy:
          values.documentProcessingAgentCacheStrategy || null,
        documentProcessingAgentPromptCacheKey:
          values.documentProcessingAgentPromptCacheKey || null,
      })
      setResult(response)
      setResultState('ACTIVE')
      setScreen('loan-result')
      loadInstances()
    } catch (error: unknown) {
      setSubmitError(error instanceof Error ? error.message : 'Failed to start process')
    } finally {
      setSubmitting(false)
    }
  }

  function backToUseCase() {
    if (selectedUseCaseId) {
      setScreen('use-case')
      loadInstances()
      return
    }
    openCatalog()
  }

  const resultEyebrow =
    resultState === 'COMPLETED'
      ? 'Completed'
      : resultState === 'ACTIVE'
        ? 'Active'
        : resultState
          ? resultState.replaceAll('_', ' ')
          : 'Started'

  return (
    <div className={`app-shell${presenterMode ? ' is-presenter-shell' : ''}`}>
      <div className="mesh" aria-hidden="true" />
      <header className="topbar">
        <button
          type="button"
          className="brand"
          onClick={presenterMode ? undefined : openCatalog}
          aria-label={presenterMode ? 'Presenter window' : 'Use case catalog'}
        >
          <img className="brand-logo" src="/logos/finos.png" alt="FINOS" />
          <span className="brand-title">
            {presenterMode ? 'AI Steel Thread · Presenter' : 'AI Steel Thread Demo'}
          </span>
        </button>
        {!presenterMode ? (
          <nav className="topbar-nav" aria-label="Primary">
            <button
              type="button"
              className={`topbar-nav-link${screen === 'catalog' ? ' is-active' : ''}`}
              onClick={openCatalog}
            >
              Catalog
            </button>
            <button
              type="button"
              className={`topbar-nav-link${screen === 'tokenomics' ? ' is-active' : ''}`}
              onClick={() => setScreen('tokenomics')}
            >
              Tokenomics
            </button>
            <button
              type="button"
              className={`topbar-nav-link${screen === 'demo-guide' ? ' is-active' : ''}`}
              onClick={() => setScreen('demo-guide')}
            >
              Demo guide
            </button>
          </nav>
        ) : (
          <p className="presenter-banner">Keep beside the main demo window</p>
        )}
        <img
          className="brand-meta-logo"
          src="/logos/finos-ai-fund.png"
          alt="FINOS AI Fund"
        />
      </header>

      <main className="stage">
        {presenterMode ? (
          <DemoGuide presenterWindow initialTab={demoGuideTab} />
        ) : (
          <>
        {screen === 'catalog' && (
          <section className="hero enter">
            <h1 className="brand-hero">AI Steel Thread Demo</h1>
            <p className="lede">
              Choose a FINOS AI Governance Framework use case to launch agentic process instances
              with the controls that use case needs. New here? Open the{' '}
              <button type="button" className="text-link" onClick={() => setScreen('demo-guide')}>
                Demo guide
              </button>
              .
            </p>
            <div className="home-columns">
              <div className="use-case-catalog">
                <p className="sample-preview-label">Use case catalog</p>
                <ul className="catalog-list">
                  {useCaseCatalog.map((entry) => (
                    <li key={entry.id}>
                      <button
                        type="button"
                        className="catalog-card"
                        onClick={() => openUseCaseCard(entry.id)}
                      >
                        <p className="use-case-label">
                          {entry.category.replaceAll('_', ' ')}
                        </p>
                        <h2 className="catalog-card-title">{entry.title}</h2>
                        <p className="catalog-card-copy">{entry.shortDescription}</p>
                        <span className="catalog-card-meta">
                          {entry.id === 'loan-approval' && instances
                            ? `${instances.activeCount} active · ${instances.totalCount} total`
                            : 'Open use case'}
                          <span aria-hidden="true"> →</span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
              <FinosProjectsPanel />
            </div>
          </section>
        )}

        {screen === 'use-case' && selectedUseCase && (
          <section className="hero enter">
            <div className="panel-head-row use-case-back-row">
              <button type="button" className="panel-back" onClick={openCatalog}>
                Back to catalog
              </button>
            </div>
            <div className="home-columns">
              <div className="use-cases">
                <article className="use-case-card">
                  <header className="use-case-card-head">
                    <p className="use-case-label">
                      {selectedUseCase.category.replaceAll('_', ' ')}
                    </p>
                    <div className="use-case-title-row">
                      <h2 className="use-case-title">{selectedUseCase.title}</h2>
                      <p className="use-case-count">
                        {instances
                          ? `${instances.activeCount} active · ${instances.totalCount} total`
                          : '…'}
                      </p>
                    </div>
                    <p className="use-case-copy">{selectedUseCase.shortDescription}</p>
                    <p className="use-case-description">{selectedUseCase.description}</p>
                    <div className="use-case-actions">
                      <button
                        type="button"
                        className="btn primary use-case-start"
                        onClick={() => startNewInstance(selectedUseCase.id)}
                      >
                        Start new instance
                      </button>
                      <button
                        type="button"
                        className="btn ghost"
                        onClick={() => setShowCalmVisualizer(true)}
                      >
                        View CALM architecture
                      </button>
                    </div>
                  </header>

                  <UseCaseGovernance
                    useCase={selectedUseCase}
                    mi1Dlp={options?.mi1Dlp}
                    mi4Observability={options?.mi4Observability}
                    mi5Acceptance={options?.mi5Acceptance}
                    mi11Feedback={options?.mi11Feedback}
                    onControlsUpdated={setOptions}
                  />

                  <HumanTaskInbox
                    onOpenInstance={(processInstanceId) => {
                      const match = instances?.instances.find(
                        (item) => item.processInstanceId === processInstanceId,
                      )
                      if (match) {
                        openInstance(match)
                        return
                      }
                      void fetchLoanApprovalInstance(processInstanceId)
                        .then((summary) => openInstance(summary))
                        .catch(() => {
                          setResult({
                            processInstanceId,
                            businessKey: '',
                            processDefinitionKey:
                              options?.processDefinitionKey ?? 'LoanApprovalProcess_AI',
                            attachedFileVariable: 'attachedFile',
                            sampleDocument: '',
                            requestedAmount: 0,
                            loanPurpose: '',
                            creditRiskAgentProvider: '',
                            creditRiskAgentModel: '',
                            creditRiskAgentCacheStrategy: '',
                            creditRiskAgentPromptCacheKey: '',
                            documentProcessingAgentProvider: '',
                            documentProcessingAgentModel: '',
                            documentProcessingAgentCacheStrategy: '',
                            documentProcessingAgentPromptCacheKey: '',
                          })
                          setResultState('ACTIVE')
                          setSelectedUseCaseId((current) => current ?? 'loan-approval')
                          setScreen('loan-result')
                        })
                    }}
                    onTaskCompleted={loadInstances}
                  />

                  <div className={`instance-list${instancesExpanded ? ' is-expanded' : ''}`}>
                    <div className="instance-list-head">
                      <button
                        type="button"
                        className="governance-fold"
                        aria-expanded={instancesExpanded}
                        aria-controls="instance-list-body"
                        onClick={() => setInstancesExpanded((value) => !value)}
                      >
                        <span className="sample-preview-label">
                          Instances
                          {instances
                            ? ` · ${instances.activeCount} active · ${instances.totalCount} total`
                            : ''}
                        </span>
                        <span className="governance-chevron" aria-hidden="true">
                          {instancesExpanded ? '−' : '+'}
                        </span>
                      </button>
                      {instancesExpanded ? (
                        <button type="button" className="panel-back" onClick={loadInstances}>
                          Refresh
                        </button>
                      ) : null}
                    </div>
                    {instancesExpanded ? (
                      <div id="instance-list-body" className="instance-list-body">
                        {instancesError && <p className="hint danger">{String(instancesError)}</p>}
                        {!instances && !instancesError && (
                          <p className="hint">Loading process instances…</p>
                        )}
                        {instances && instances.instances.length === 0 && (
                          <p className="hint">No instances yet. Start one above.</p>
                        )}
                        <ul className="instance-entries">
                          {(instances?.instances ?? []).map((instance) => (
                            <li key={instance.processInstanceId}>
                              <button
                                type="button"
                                className="instance-entry"
                                onClick={() => openInstance(instance)}
                              >
                                <span className="instance-entry-main">
                                  <span className="instance-entry-key">
                                    {instance.businessKey || instance.processInstanceId}
                                  </span>
                                  <span className="instance-entry-badges">
                                    <span
                                      className={`instance-compliance status-${(instance.complianceOverallStatus ?? 'unknown').toLowerCase()}`}
                                    >
                                      {formatComplianceLevel(instance.complianceOverallStatus)}
                                      {instance.complianceOverallStatus === 'IN_PROGRESS' ? (
                                        <span className="progress-dots" aria-hidden="true">
                                          <span />
                                          <span />
                                          <span />
                                        </span>
                                      ) : null}
                                    </span>
                                    <span
                                      className={`instance-entry-state status-${instance.state.toLowerCase()}`}
                                    >
                                      {formatInstanceState(instance.state)}
                                      {instance.state === 'ACTIVE' ? (
                                        <span className="progress-dots" aria-hidden="true">
                                          <span />
                                          <span />
                                          <span />
                                        </span>
                                      ) : null}
                                    </span>
                                  </span>
                                </span>
                                <span className="instance-entry-meta">
                                  <span>
                                    {instance.requestedAmount != null
                                      ? instance.requestedAmount.toLocaleString()
                                      : '—'}
                                    {instance.loanPurpose ? ` · ${instance.loanPurpose}` : ''}
                                    {instance.state === 'ACTIVE' &&
                                    instance.currentActivityNames.length
                                      ? ` · ${instance.currentActivityNames.join(', ')}`
                                      : ''}
                                  </span>
                                  <span className="instance-entry-when">
                                    {formatInstanceTime(instance.endTime ?? instance.startTime)}
                                  </span>
                                </span>
                              </button>
                            </li>
                          ))}
                        </ul>
                      </div>
                    ) : null}
                  </div>
                </article>
              </div>
              <FinosProjectsPanel />
            </div>
            {optionsError && <p className="hint danger">{String(optionsError)}</p>}
          </section>
        )}

        {screen === 'tokenomics' && (
          <section className="panel enter tokenomics-page">
            <div className="panel-head">
              <p className="eyebrow">Control plane</p>
              <h2>AI tokenomics</h2>
              <p className="panel-copy">
                Cost visibility, allocation, budgets, forecasting, and optimization across
                providers — filtered by use case.
              </p>
            </div>
            <TokenomicsPanel page />
          </section>
        )}

        {screen === 'demo-guide' && (
          <DemoGuide onBack={openCatalog} initialTab={demoGuideTab} />
        )}

        {screen === 'loan-form' && (
          <section className="panel enter">
            <div className="panel-head">
              <p className="eyebrow">Loan Approval</p>
              <h2>Start process instance</h2>
              <p className="panel-copy">
                Leave fields blank to let the demo choose random values. Cancel returns to the use
                case.
              </p>
            </div>
            <LoanApprovalForm
              options={options}
              optionsLoading={optionsLoading}
              optionsError={optionsError ? String(optionsError) : null}
              submitting={submitting}
              error={submitError ? String(submitError) : null}
              onRetryOptions={loadOptions}
              onCancel={backToUseCase}
              onSubmit={handleSubmit}
            />
          </section>
        )}

        {screen === 'loan-result' && result && (
          <section className="panel enter">
            <div className="panel-head">
              <div className="panel-head-row">
                <p
                  className={`eyebrow ${
                    resultState === 'COMPLETED' ? 'success-tone' : ''
                  }`}
                >
                  {resultEyebrow}
                </p>
                <button type="button" className="panel-back" onClick={backToUseCase}>
                  Back to use case
                </button>
              </div>
              <h2>Process instance</h2>
            </div>
            <div className="result-layout">
              <div className="result-main">
                <dl className="result-grid">
                  <div>
                    <dt>Process instance</dt>
                    <dd>
                      <code>{result.processInstanceId}</code>
                    </dd>
                  </div>
                  <div>
                    <dt>Business key</dt>
                    <dd>{result.businessKey || '—'}</dd>
                  </div>
                  <div>
                    <dt>Definition</dt>
                    <dd>{result.processDefinitionKey}</dd>
                  </div>
                  <div>
                    <dt>Amount</dt>
                    <dd>
                      {result.requestedAmount
                        ? result.requestedAmount.toLocaleString()
                        : '—'}
                    </dd>
                  </div>
                  <div>
                    <dt>Purpose</dt>
                    <dd>{result.loanPurpose || '—'}</dd>
                  </div>
                  <div>
                    <dt>Sample document</dt>
                    <dd>{result.sampleDocument || '—'}</dd>
                  </div>
                  <div>
                    <dt>Document Processing Agent</dt>
                    <dd>
                      {result.documentProcessingAgentProvider || '—'}
                      {result.documentProcessingAgentProvider ||
                      result.documentProcessingAgentModel
                        ? ` · ${result.documentProcessingAgentModel || '—'}`
                        : ''}
                      {result.documentProcessingAgentProvider === 'anthropic' &&
                      result.documentProcessingAgentCacheStrategy
                        ? ` · cache ${result.documentProcessingAgentCacheStrategy}`
                        : ''}
                      {result.documentProcessingAgentProvider === 'openai' &&
                      result.documentProcessingAgentPromptCacheKey
                        ? ` · cache-key ${result.documentProcessingAgentPromptCacheKey}`
                        : ''}
                    </dd>
                  </div>
                  <div>
                    <dt>Credit Risk Agent</dt>
                    <dd>
                      {result.creditRiskAgentProvider || '—'}
                      {result.creditRiskAgentProvider || result.creditRiskAgentModel
                        ? ` · ${result.creditRiskAgentModel || '—'}`
                        : ''}
                      {result.creditRiskAgentProvider === 'anthropic' &&
                      result.creditRiskAgentCacheStrategy
                        ? ` · cache ${result.creditRiskAgentCacheStrategy}`
                        : ''}
                      {result.creditRiskAgentProvider === 'openai' &&
                      result.creditRiskAgentPromptCacheKey
                        ? ` · cache-key ${result.creditRiskAgentPromptCacheKey}`
                        : ''}
                    </dd>
                  </div>
                </dl>
                <div className="actions">
                  <button
                    type="button"
                    className="btn primary"
                    onClick={() => setScreen('loan-form')}
                  >
                    Launch another
                  </button>
                  <a
                    className="btn ghost"
                    href={`http://localhost:8080/fluxnova/app/monitoring/default/#/process-instance/${result.processInstanceId}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Advanced: Monitoring
                  </a>
                </div>
              </div>
              <div className="result-side">
                <HumanTaskInbox
                  processInstanceId={result.processInstanceId}
                  onTaskCompleted={() => {
                    loadInstances()
                  }}
                />
                <ComplianceEvidenceReport processInstanceId={result.processInstanceId} />
                <ProcessStatusLog processInstanceId={result.processInstanceId} />
              </div>
            </div>
          </section>
        )}
          </>
        )}
      </main>

      {!presenterMode && showCalmVisualizer && selectedUseCase ? (
        <CalmArchitectureVisualizer
          architectureHref={selectedUseCase.architectureHref}
          calmHubHref={selectedUseCase.calmHubVisualizerHref}
          onClose={() => setShowCalmVisualizer(false)}
        />
      ) : null}
    </div>
  )
}

function formatInstanceState(state: string): string {
  return state.replaceAll('_', ' ').toLowerCase()
}

function formatInstanceTime(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString([], {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function formatComplianceLevel(status: string | null | undefined): string {
  if (!status) {
    return '—'
  }
  if (status === 'IN_PROGRESS') {
    return 'In progress'
  }
  if (status === 'UNKNOWN') {
    return 'Unknown'
  }
  return status
}
