export type UseCaseId = 'loan-approval'

export type Mi1DlpStatus = {
  enabled: boolean
  strict: boolean
  redactStatusApi: boolean
  creditRiskContextAllowlist: string[]
  forbiddenVariableNames: string[]
}

export type Mi4ObservabilityStatus = {
  enabled: boolean
  sanitizedAuditLog: boolean
  persistCallMetadata: boolean
  modelVersionLabel: string
  grafanaBaseUrl: string
  dashboardPaths: string[]
}

export type Mi5AcceptanceCaseResult = {
  id: string
  category: string
  title: string
  passed: boolean
  detail: string
}

export type Mi5AcceptanceReport = {
  ranAt: string
  accepted: boolean
  passRate: number
  passRateThreshold: number
  total: number
  passed: number
  failed: number
  cases: Mi5AcceptanceCaseResult[]
}

export type Mi5AcceptanceStatus = {
  enabled: boolean
  passRateThreshold: number
  aigfCatalogUrl: string
  criteriaCategories: string[]
  lastReport: Mi5AcceptanceReport | null
}

export type Mi11FeedbackStatus = {
  enabled: boolean
  aigfCatalogUrl: string
  loanOfficerTaskDefinitionKey: string
}

export type LoanApprovalOptions = {
  processDefinitionKey: string
  minRequestedAmount: number
  maxRequestedAmount: number
  loanPurposes: string[]
  sampleDocuments: string[]
  creditRiskAgentProviders: string[]
  creditRiskAgentModels: string[]
  agentModelsByProvider?: Record<string, string[]>
  anthropicCacheStrategies?: string[]
  mi1Dlp: Mi1DlpStatus
  mi4Observability: Mi4ObservabilityStatus
  mi5Acceptance: Mi5AcceptanceStatus
  mi11Feedback: Mi11FeedbackStatus
}

export type StartLoanApprovalRequest = {
  businessKey?: string | null
  requestedAmount?: number | null
  loanPurpose?: string | null
  sampleDocument?: string | null
  creditRiskAgentProvider?: string | null
  creditRiskAgentModel?: string | null
  creditRiskAgentCacheStrategy?: string | null
  creditRiskAgentPromptCacheKey?: string | null
  documentProcessingAgentProvider?: string | null
  documentProcessingAgentModel?: string | null
  documentProcessingAgentCacheStrategy?: string | null
  documentProcessingAgentPromptCacheKey?: string | null
}

export type StartLoanApprovalResponse = {
  processInstanceId: string
  businessKey: string
  processDefinitionKey: string
  attachedFileVariable: string
  sampleDocument: string
  requestedAmount: number
  loanPurpose: string
  creditRiskAgentProvider: string
  creditRiskAgentModel: string
  creditRiskAgentCacheStrategy: string
  creditRiskAgentPromptCacheKey: string
  documentProcessingAgentProvider: string
  documentProcessingAgentModel: string
  documentProcessingAgentCacheStrategy: string
  documentProcessingAgentPromptCacheKey: string
}

export async function fetchLoanApprovalOptions(): Promise<LoanApprovalOptions> {
  const response = await fetch('/api/demo/loan-approval/options')
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export type ControlEnforcementUpdate = {
  mi1Enabled?: boolean
  mi4Enabled?: boolean
  mi5Enabled?: boolean
  mi11Enabled?: boolean
}

export async function updateControlEnforcement(
  update: ControlEnforcementUpdate,
): Promise<LoanApprovalOptions> {
  const response = await fetch('/api/demo/loan-approval/controls', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(update),
  })
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function fetchAcceptanceTests(): Promise<Mi5AcceptanceReport | null> {
  const response = await fetch('/api/demo/loan-approval/acceptance-tests')
  if (response.status === 204 || response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  const payload = await response.json()
  return payload ?? null
}

export async function runAcceptanceTests(): Promise<Mi5AcceptanceReport> {
  const response = await fetch('/api/demo/loan-approval/acceptance-tests/run', {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export function sampleDocumentUrl(filename: string): string {
  return `/sample/${encodeURIComponent(filename)}`
}

export function generateBusinessKey(): string {
  return `loan-demo-${Date.now()}`
}

export async function startLoanApproval(
  body: StartLoanApprovalRequest,
): Promise<StartLoanApprovalResponse> {
  const response = await fetch('/api/demo/loan-approval/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export type LoanApprovalInstanceSummary = {
  processInstanceId: string
  businessKey: string | null
  processDefinitionKey: string
  state: string
  startTime: string | null
  endTime: string | null
  requestedAmount: number | null
  loanPurpose: string | null
  sampleDocument: string | null
  creditRiskAgentProvider: string | null
  creditRiskAgentModel: string | null
  creditRiskAgentCacheStrategy: string | null
  creditRiskAgentPromptCacheKey: string | null
  documentProcessingAgentProvider: string | null
  documentProcessingAgentModel: string | null
  documentProcessingAgentCacheStrategy: string | null
  documentProcessingAgentPromptCacheKey: string | null
  currentActivityNames: string[]
  complianceOverallStatus: string | null
}

export type LoanApprovalInstanceList = {
  processDefinitionKey: string
  activeCount: number
  totalCount: number
  instances: LoanApprovalInstanceSummary[]
}

export async function fetchLoanApprovalInstances(): Promise<LoanApprovalInstanceList> {
  const response = await fetch('/api/demo/loan-approval/instances')
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function fetchLoanApprovalInstance(
  processInstanceId: string,
): Promise<LoanApprovalInstanceSummary> {
  const response = await fetch(
    `/api/demo/loan-approval/instances/${encodeURIComponent(processInstanceId)}`,
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export function toStartResponse(
  summary: LoanApprovalInstanceSummary,
): StartLoanApprovalResponse {
  return {
    processInstanceId: summary.processInstanceId,
    businessKey: summary.businessKey ?? '',
    processDefinitionKey: summary.processDefinitionKey,
    attachedFileVariable: 'attachedFile',
    sampleDocument: summary.sampleDocument ?? '',
    requestedAmount: summary.requestedAmount ?? 0,
    loanPurpose: summary.loanPurpose ?? '',
    creditRiskAgentProvider: summary.creditRiskAgentProvider ?? '',
    creditRiskAgentModel: summary.creditRiskAgentModel ?? '',
    creditRiskAgentCacheStrategy: summary.creditRiskAgentCacheStrategy ?? '',
    creditRiskAgentPromptCacheKey: summary.creditRiskAgentPromptCacheKey ?? '',
    documentProcessingAgentProvider: summary.documentProcessingAgentProvider ?? '',
    documentProcessingAgentModel: summary.documentProcessingAgentModel ?? '',
    documentProcessingAgentCacheStrategy: summary.documentProcessingAgentCacheStrategy ?? '',
    documentProcessingAgentPromptCacheKey:
      summary.documentProcessingAgentPromptCacheKey ?? '',
  }
}

export type ActivityStatusEntry = {
  id: string
  activityId: string
  activityName: string
  activityType: string
  status: 'active' | 'completed' | 'canceled' | string
  startTime: string | null
  endTime: string | null
  durationInMillis: number | null
  tokenUsage: TokenUsageSummary | null
  inputs: VariableEntry[]
  outputs: VariableEntry[]
}

export type TokenUsageSummary = {
  promptTokens: number
  completionTokens: number
  totalTokens: number
  model?: string | null
  provider?: string | null
  modelVersion?: string | null
  durationMs?: number | null
  status?: string | null
  estimatedCostUsd?: number | null
}

export type VariableEntry = {
  name: string
  type: string | null
  value: unknown
}

export type ProcessInstanceStatus = {
  processInstanceId: string
  businessKey: string | null
  state: string
  startTime: string | null
  endTime: string | null
  currentActivityNames: string[]
  activities: ActivityStatusEntry[]
  controls: {
    mi1Enabled: boolean
    mi1RedactStatusApi: boolean
    mi4Enabled: boolean
    instanceSnapshot: boolean
  }
}

export async function fetchProcessInstanceStatus(
  processInstanceId: string,
): Promise<ProcessInstanceStatus> {
  const response = await fetch(
    `/api/demo/loan-approval/instances/${encodeURIComponent(processInstanceId)}/status`,
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export type ComplianceControlPosture = {
  mi1Enabled: boolean
  mi1RedactStatusApi: boolean
  mi4Enabled: boolean
  mi5Enabled: boolean
  mi11Enabled: boolean
  snapshottedAt: string | null
  instanceSnapshot: boolean
}

export type ComplianceDecisionPath = {
  requestedAmount: number | null
  loanPurpose: string | null
  creditScore: number | null
  creditRiskBand: string | null
  aiRiskScore: number | null
  approvalDecision: string | null
  loanOfficerDecision: string | null
  agreesWithAi: boolean | null
  invalidDocument: boolean | null
  fraudulent: boolean | null
}

export type HumanReviewStatus = {
  processInstanceId: string
  taskOpen: boolean
  taskId: string | null
  taskName: string | null
  taskDefinitionKey: string
  created: string | null
  approvalDecision: string | null
  creditScore: number | null
  creditRiskBand: string | null
  aiRiskScore: number | null
  loanOfficerDecision: string | null
  agreesWithAi: boolean | null
  feedbackComment: string | null
  mi11Enabled: boolean
}

export type HumanFeedbackRequest = {
  loanOfficerDecision: string
  agreesWithAi: boolean
  comment?: string | null
  categories?: string[] | null
}

export type CheckFinding = {
  id: string
  control: string
  title: string
  status: 'pass' | 'warn' | 'fail' | 'pending' | 'info' | string
  detail: string
}

export type LlmCallEvidence = {
  activityId: string
  activityName: string
  model: string | null
  provider: string | null
  modelVersion: string | null
  durationMs: number | null
  status: string | null
  contextKeys: string | null
  totalTokens: number | null
  estimatedCostUsd: number | null
}

export type SatSnapshot = {
  suiteEnabled: boolean
  lastAccepted: boolean | null
  lastPassRate: number | null
  lastRanAt: string | null
  note: string
}

export type LoanApprovalComplianceReport = {
  processInstanceId: string
  businessKey: string | null
  state: string
  generatedAt: string
  overallStatus: 'PASS' | 'WARN' | 'FAIL' | 'IN_PROGRESS' | string
  controls: ComplianceControlPosture
  decisionPath: ComplianceDecisionPath
  checks: CheckFinding[]
  llmCalls: LlmCallEvidence[]
  sat: SatSnapshot
}

export async function fetchComplianceReport(
  processInstanceId: string,
): Promise<LoanApprovalComplianceReport> {
  const response = await fetch(
    `/api/demo/loan-approval/instances/${encodeURIComponent(processInstanceId)}/compliance-report`,
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function fetchHumanReview(processInstanceId: string): Promise<HumanReviewStatus> {
  const response = await fetch(
    `/api/demo/loan-approval/instances/${encodeURIComponent(processInstanceId)}/human-review`,
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function submitHumanFeedback(
  processInstanceId: string,
  request: HumanFeedbackRequest,
): Promise<HumanReviewStatus> {
  const response = await fetch(
    `/api/demo/loan-approval/instances/${encodeURIComponent(processInstanceId)}/human-feedback`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    },
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export type HumanTaskFormField = {
  id: string
  label: string
  type: 'enum' | 'boolean' | 'textarea' | 'string' | string
  required: boolean
  options: string[] | null
  help: string | null
}

export type HumanTaskFormSchema = {
  taskDefinitionKey: string
  title: string
  description: string
  fields: HumanTaskFormField[]
}

export type HumanTaskSummary = {
  taskId: string
  name: string
  taskDefinitionKey: string
  processInstanceId: string
  businessKey: string | null
  created: string | null
  assignee: string | null
  claimed: boolean
  formSchemaId: string
}

export type HumanTaskList = {
  totalCount: number
  tasks: HumanTaskSummary[]
}

export type HumanTaskAgentConversation = {
  lastResponse: string | null
  trace: string | null
  history: unknown
}

export type HumanTaskDetail = {
  taskId: string
  name: string
  taskDefinitionKey: string
  processInstanceId: string
  businessKey: string | null
  created: string | null
  assignee: string | null
  claimed: boolean
  form: HumanTaskFormSchema
  context: Record<string, unknown>
  agentConversation: HumanTaskAgentConversation | null
}

export async function fetchHumanTasks(processInstanceId?: string | null): Promise<HumanTaskList> {
  const params = new URLSearchParams()
  if (processInstanceId) {
    params.set('processInstanceId', processInstanceId)
  }
  const query = params.toString()
  const response = await fetch(
    `/api/demo/loan-approval/tasks${query ? `?${query}` : ''}`,
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function fetchHumanTask(taskId: string): Promise<HumanTaskDetail> {
  const response = await fetch(
    `/api/demo/loan-approval/tasks/${encodeURIComponent(taskId)}`,
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function claimHumanTask(taskId: string): Promise<HumanTaskDetail> {
  const response = await fetch(
    `/api/demo/loan-approval/tasks/${encodeURIComponent(taskId)}/claim`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function completeHumanTask(
  taskId: string,
  variables: Record<string, unknown>,
): Promise<HumanTaskDetail> {
  const response = await fetch(
    `/api/demo/loan-approval/tasks/${encodeURIComponent(taskId)}/complete`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ variables }),
    },
  )
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export type TokenomicsSummary = {
  enabled: boolean
  currency: string
  useCase: string
  calls: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  estimatedCostUsd: number
  avgCostPerCallUsd: number
  costPer1kTokensUsd: number
  availableUseCases: string[]
}

export type TokenomicsBreakdownRow = {
  key: string
  calls: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  estimatedCostUsd: number
}

export type TokenomicsBreakdown = {
  currency: string
  useCase: string
  groupBy: string
  rows: TokenomicsBreakdownRow[]
}

export type TokenomicsRate = {
  provider: string
  model: string
  promptPer1m: number
  completionPer1m: number
  cacheInputPer1m: number
  cacheWritePer1m: number
}

export type TokenomicsRates = {
  currency: string
  rates: TokenomicsRate[]
}

export type TokenomicsBudget = {
  useCase: string
  period: string
  limitUsd: number
  warnAt: number
  hardLimit: boolean
  spentUsd: number
  remainingUsd: number
  burnRatio: number
  status: 'ok' | 'warn' | 'exceeded' | string
  forecastEndOfPeriodUsd: number
  forecastExceedsLimit: boolean
  periodStart: string
  periodEnd: string
  elapsedDays: number
  remainingDays: number
}

export type TokenomicsBudgets = {
  currency: string
  budgets: TokenomicsBudget[]
}

export type TokenomicsBudgetUpdate = {
  useCase: string
  limitUsd?: number
  warnAt?: number
  hardLimit?: boolean
  period?: string
}

export async function fetchTokenomicsSummary(useCase?: string | null): Promise<TokenomicsSummary> {
  const params = new URLSearchParams()
  if (useCase) {
    params.set('useCase', useCase)
  }
  const query = params.toString()
  const response = await fetch(`/api/demo/tokenomics/summary${query ? `?${query}` : ''}`)
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function fetchTokenomicsBreakdown(
  useCase?: string | null,
  groupBy: string = 'providerModel',
): Promise<TokenomicsBreakdown> {
  const params = new URLSearchParams()
  if (useCase) {
    params.set('useCase', useCase)
  }
  params.set('groupBy', groupBy)
  const response = await fetch(`/api/demo/tokenomics/breakdown?${params.toString()}`)
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function fetchTokenomicsRates(): Promise<TokenomicsRates> {
  const response = await fetch('/api/demo/tokenomics/rates')
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function fetchTokenomicsBudgets(useCase?: string | null): Promise<TokenomicsBudgets> {
  const params = new URLSearchParams()
  if (useCase) {
    params.set('useCase', useCase)
  }
  const query = params.toString()
  const response = await fetch(`/api/demo/tokenomics/budgets${query ? `?${query}` : ''}`)
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export async function updateTokenomicsBudget(
  update: TokenomicsBudgetUpdate,
): Promise<TokenomicsBudgets> {
  const response = await fetch('/api/demo/tokenomics/budgets', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(update),
  })
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export type TokenomicsInsightTip = {
  id: string
  severity: 'info' | 'suggest' | 'warn' | string
  title: string
  detail: string
}

export type TokenomicsInsights = {
  currency: string
  useCase: string
  tips: TokenomicsInsightTip[]
  providers: string[]
  models: string[]
  grafanaUrl: string
}

export async function fetchTokenomicsInsights(
  useCase?: string | null,
): Promise<TokenomicsInsights> {
  const params = new URLSearchParams()
  if (useCase) {
    params.set('useCase', useCase)
  }
  const query = params.toString()
  const response = await fetch(`/api/demo/tokenomics/insights${query ? `?${query}` : ''}`)
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  return response.json()
}

export function complianceReportPdfUrl(processInstanceId: string): string {
  return `/api/demo/loan-approval/instances/${encodeURIComponent(processInstanceId)}/compliance-report.pdf`
}

export async function downloadComplianceReportPdf(processInstanceId: string): Promise<void> {
  const response = await fetch(complianceReportPdfUrl(processInstanceId))
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = `compliance-evidence-${processInstanceId}.pdf`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(objectUrl)
}

async function readError(response: Response): Promise<string> {
  try {
    const payload = await response.json()
    if (typeof payload?.detail === 'string') {
      return payload.detail
    }
    if (typeof payload?.title === 'string') {
      return payload.title
    }
  } catch {
    // fall through
  }
  return `Request failed (${response.status})`
}
