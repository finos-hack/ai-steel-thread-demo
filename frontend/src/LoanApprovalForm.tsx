import { useEffect, useState, type FormEvent } from 'react'
import { generateBusinessKey, sampleDocumentUrl, type LoanApprovalOptions } from './api'

type Props = {
  options: LoanApprovalOptions | null
  optionsLoading: boolean
  optionsError: string | null
  submitting: boolean
  error: string | null
  onRetryOptions: () => void
  onCancel: () => void
  onSubmit: (values: {
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
  }) => void
}

const RANDOM = ''
const FALLBACK_MODELS_BY_PROVIDER: Record<string, string[]> = {
  openai: ['gpt-5.4', 'gpt-5.6-luna'],
  anthropic: ['claude-sonnet-5'],
}
const FALLBACK_CACHE_STRATEGIES = [
  'NONE',
  'TOOLS_ONLY',
  'SYSTEM_ONLY',
  'SYSTEM_AND_TOOLS',
  'CONVERSATION_HISTORY',
]

function modelsForProvider(
  options: LoanApprovalOptions | null,
  provider: string,
): string[] {
  const byProvider = options?.agentModelsByProvider ?? FALLBACK_MODELS_BY_PROVIDER
  return byProvider[provider] ?? options?.creditRiskAgentModels ?? FALLBACK_MODELS_BY_PROVIDER.openai
}

export function LoanApprovalForm({
  options,
  optionsLoading,
  optionsError,
  submitting,
  error,
  onRetryOptions,
  onCancel,
  onSubmit,
}: Props) {
  const [businessKey, setBusinessKey] = useState('')
  const [requestedAmount, setRequestedAmount] = useState(RANDOM)
  const [loanPurpose, setLoanPurpose] = useState(RANDOM)
  const [sampleDocument, setSampleDocument] = useState(RANDOM)
  const [creditRiskAgentProvider, setCreditRiskAgentProvider] = useState('openai')
  const [creditRiskAgentModel, setCreditRiskAgentModel] = useState('gpt-5.4')
  const [creditRiskAgentCacheStrategy, setCreditRiskAgentCacheStrategy] =
    useState('SYSTEM_AND_TOOLS')
  const [creditRiskAgentPromptCacheKey, setCreditRiskAgentPromptCacheKey] =
    useState('loan-approval')
  const [documentProcessingAgentProvider, setDocumentProcessingAgentProvider] =
    useState('openai')
  const [documentProcessingAgentModel, setDocumentProcessingAgentModel] = useState('gpt-5.4')
  const [documentProcessingAgentCacheStrategy, setDocumentProcessingAgentCacheStrategy] =
    useState('SYSTEM_AND_TOOLS')
  const [documentProcessingAgentPromptCacheKey, setDocumentProcessingAgentPromptCacheKey] =
    useState('loan-approval')

  useEffect(() => {
    setRequestedAmount(RANDOM)
    setLoanPurpose(RANDOM)
    setSampleDocument(RANDOM)
    const defaultProvider = options?.creditRiskAgentProviders?.[0] ?? 'openai'
    const defaultModel = modelsForProvider(options, defaultProvider)[0] ?? 'gpt-5.4'
    const defaultCacheStrategy =
      options?.anthropicCacheStrategies?.includes('SYSTEM_AND_TOOLS')
        ? 'SYSTEM_AND_TOOLS'
        : (options?.anthropicCacheStrategies?.[0] ?? 'SYSTEM_AND_TOOLS')
    setCreditRiskAgentProvider(defaultProvider)
    setCreditRiskAgentModel(defaultModel)
    setCreditRiskAgentCacheStrategy(defaultCacheStrategy)
    setCreditRiskAgentPromptCacheKey('loan-approval')
    setDocumentProcessingAgentProvider(defaultProvider)
    setDocumentProcessingAgentModel(defaultModel)
    setDocumentProcessingAgentCacheStrategy(defaultCacheStrategy)
    setDocumentProcessingAgentPromptCacheKey('loan-approval')
  }, [options])

  function handleCreditRiskProviderChange(provider: string) {
    setCreditRiskAgentProvider(provider)
    const models = modelsForProvider(options, provider)
    if (!models.includes(creditRiskAgentModel)) {
      setCreditRiskAgentModel(models[0] ?? '')
    }
  }

  function handleDocumentProviderChange(provider: string) {
    setDocumentProcessingAgentProvider(provider)
    const models = modelsForProvider(options, provider)
    if (!models.includes(documentProcessingAgentModel)) {
      setDocumentProcessingAgentModel(models[0] ?? '')
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onSubmit({
      businessKey,
      requestedAmount,
      loanPurpose,
      sampleDocument,
      creditRiskAgentProvider,
      creditRiskAgentModel,
      creditRiskAgentCacheStrategy,
      creditRiskAgentPromptCacheKey,
      documentProcessingAgentProvider,
      documentProcessingAgentModel,
      documentProcessingAgentCacheStrategy,
      documentProcessingAgentPromptCacheKey,
    })
  }

  const min = options?.minRequestedAmount ?? 10_000
  const max = options?.maxRequestedAmount ?? 100_000
  const canSend = !submitting && !optionsLoading && !!options
  const previewSrc = sampleDocument ? sampleDocumentUrl(sampleDocument) : null
  const providers = options?.creditRiskAgentProviders ?? ['openai', 'anthropic']
  const creditRiskModels = modelsForProvider(options, creditRiskAgentProvider)
  const documentModels = modelsForProvider(options, documentProcessingAgentProvider)
  const cacheStrategies = options?.anthropicCacheStrategies ?? FALLBACK_CACHE_STRATEGIES

  return (
    <div className="form-layout">
      <form className="form" onSubmit={handleSubmit}>
        <div className="form-group-row">
          <label className="field">
            <span>Business key</span>
            <div className="field-inline">
              <input
                value={businessKey}
                onChange={(event) => setBusinessKey(event.target.value)}
                placeholder="Optional — generated if empty"
                autoComplete="off"
              />
              <button
                type="button"
                className="btn ghost compact"
                onClick={() => setBusinessKey(generateBusinessKey())}
                disabled={submitting}
              >
                Generate
              </button>
            </div>
          </label>

          <label className="field">
            <span>Requested amount</span>
            <select
              value={requestedAmount}
              onChange={(event) => setRequestedAmount(event.target.value)}
            >
              <option value={RANDOM}>
                Random ({min.toLocaleString()}–{max.toLocaleString()})
              </option>
              {[15_000, 25_000, 40_000, 55_000, 75_000, 100_000].map((amount) => (
                <option key={amount} value={String(amount)}>
                  {amount.toLocaleString()}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="form-group-row">
          <label className="field">
            <span>Loan purpose</span>
            <select value={loanPurpose} onChange={(event) => setLoanPurpose(event.target.value)}>
              <option value={RANDOM}>Random</option>
              {(options?.loanPurposes ?? []).map((purpose) => (
                <option key={purpose} value={purpose}>
                  {purpose}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>Sample ID document</span>
            <select
              value={sampleDocument}
              onChange={(event) => setSampleDocument(event.target.value)}
            >
              <option value={RANDOM}>Random sample</option>
              {(options?.sampleDocuments ?? []).map((document) => (
                <option key={document} value={document}>
                  {document}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="form-group-row">
          <fieldset className="form-group">
            <legend>Document Processing Agent</legend>
            <label className="field">
              <span>Provider</span>
              <select
                value={documentProcessingAgentProvider}
                onChange={(event) => handleDocumentProviderChange(event.target.value)}
              >
                {providers.map((provider) => (
                  <option key={provider} value={provider}>
                    {provider}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Model</span>
              <select
                value={documentProcessingAgentModel}
                onChange={(event) => setDocumentProcessingAgentModel(event.target.value)}
              >
                {documentModels.map((model) => (
                  <option key={model} value={model}>
                    {model}
                  </option>
                ))}
              </select>
            </label>
            {documentProcessingAgentProvider === 'anthropic' ? (
              <label className="field">
                <span>Cache strategy</span>
                <select
                  value={documentProcessingAgentCacheStrategy}
                  onChange={(event) =>
                    setDocumentProcessingAgentCacheStrategy(event.target.value)
                  }
                >
                  {cacheStrategies.map((strategy) => (
                    <option key={strategy} value={strategy}>
                      {strategy}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <label className="field">
                <span>Prompt cache key</span>
                <input
                  value={documentProcessingAgentPromptCacheKey}
                  onChange={(event) =>
                    setDocumentProcessingAgentPromptCacheKey(event.target.value)
                  }
                  placeholder="loan-approval"
                  autoComplete="off"
                />
              </label>
            )}
          </fieldset>

          <fieldset className="form-group">
            <legend>Credit Risk Agent</legend>
            <label className="field">
              <span>Provider</span>
              <select
                value={creditRiskAgentProvider}
                onChange={(event) => handleCreditRiskProviderChange(event.target.value)}
              >
                {providers.map((provider) => (
                  <option key={provider} value={provider}>
                    {provider}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Model</span>
              <select
                value={creditRiskAgentModel}
                onChange={(event) => setCreditRiskAgentModel(event.target.value)}
              >
                {creditRiskModels.map((model) => (
                  <option key={model} value={model}>
                    {model}
                  </option>
                ))}
              </select>
            </label>
            {creditRiskAgentProvider === 'anthropic' ? (
              <label className="field">
                <span>Cache strategy</span>
                <select
                  value={creditRiskAgentCacheStrategy}
                  onChange={(event) => setCreditRiskAgentCacheStrategy(event.target.value)}
                >
                  {cacheStrategies.map((strategy) => (
                    <option key={strategy} value={strategy}>
                      {strategy}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <label className="field">
                <span>Prompt cache key</span>
                <input
                  value={creditRiskAgentPromptCacheKey}
                  onChange={(event) => setCreditRiskAgentPromptCacheKey(event.target.value)}
                  placeholder="loan-approval"
                  autoComplete="off"
                />
              </label>
            )}
          </fieldset>
        </div>

        {optionsLoading && <p className="hint">Loading form options from the demo API…</p>}
        {optionsError && (
          <p className="hint danger">
            {optionsError}{' '}
            <button type="button" className="linkish" onClick={onRetryOptions}>
              Retry
            </button>
          </p>
        )}
        {error && <p className="hint danger">{error}</p>}

        <div className="actions">
          <button type="button" className="btn ghost" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={!canSend}>
            {submitting ? 'Sending…' : optionsLoading ? 'Loading…' : 'Send'}
          </button>
        </div>
      </form>

      <aside className="sample-preview" aria-live="polite">
        <p className="sample-preview-label">ID preview</p>
        {previewSrc ? (
          <img key={previewSrc} src={previewSrc} alt={`Preview of ${sampleDocument}`} />
        ) : (
          <p className="hint">Pick a sample document to preview the ID card.</p>
        )}
      </aside>
    </div>
  )
}
