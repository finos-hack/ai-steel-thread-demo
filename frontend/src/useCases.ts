export type FrameworkLink = {
  id: string
  title: string
  href: string
}

export type RegulatoryConcern = {
  name: string
  href: string
  jurisdiction: string
}

export type UseCaseCatalogEntry = {
  id: 'loan-approval'
  title: string
  category: string
  shortDescription: string
  description: string
  sourceHref: string
  catalogHref: string
  architectureHref: string
  calmHubVisualizerHref: string
  keyFunctions: string[]
  businessValue: {
    summary: string
    benefits: string[]
  }
  endUsers: {
    primary: string
    secondary: string
    tertiary: string
  }
  dataSensitivity: string[]
  regulatoryConcerns: RegulatoryConcern[]
  risks: FrameworkLink[]
  mitigations: FrameworkLink[]
}

const CATALOG = 'https://air-governance-framework.finos.org'

function risk(id: string, title: string, slug: string): FrameworkLink {
  return {
    id,
    title,
    href: `${CATALOG}/risks/${slug}.html`,
  }
}

function mitigation(id: string, title: string, slug: string): FrameworkLink {
  return {
    id,
    title,
    href: `${CATALOG}/mitigations/${slug}.html`,
  }
}

/** Content adapted from FINOS AI Governance Framework UC-3 Loan Approval. */
export const loanApprovalUseCase: UseCaseCatalogEntry = {
  id: 'loan-approval',
  title: 'Loan Approval',
  category: 'Risk and Compliance',
  shortDescription:
    'A multi-agent loan approval workflow that automates document intake, fraud detection, credit risk assessment, decisioning, pricing, human review, agreement generation, e-signature, and disbursement.',
  description:
    'Automates and optimizes evaluating, approving, pricing, documenting, and disbursing loans. A loan approval orchestrator coordinates specialist agents for document intelligence, fraud detection, compliance review, credit risk assessment, dynamic pricing, and document generation, with deterministic policy gates and human review checkpoints.',
  sourceHref:
    'https://github.com/finos/ai-governance-framework/blob/main/docs/_usecases/uc-3_loan-approval.md',
  catalogHref: CATALOG,
  architectureHref: '/architecture/loan-approval.calm.json',
  calmHubVisualizerHref:
    'http://localhost:8090/#/finos/architectures/loan-approval/1.0.0',
  keyFunctions: [
    'Application Intake — accept applications and supporting documents',
    'Document Intelligence — extract, validate, and normalize applicant data',
    'Automated Validation — catch missing, inconsistent, or low-confidence fields',
    'Fraud Detection — flag suspicious applications with AI anomaly signals',
    'Compliance Review — route high-risk cases with supporting evidence',
    'Credit Risk Assessment — combine bureau data, policy rules, and AI risk scores',
    'Decision Logic — auto-approve, reject, or send to manual review',
    'Dynamic Pricing — adjust rate and terms from risk and policy',
    'Human Review — loan officer oversight with AI recommendations',
    'Document Generation and E-Signature — agreements and digital signing',
    'Disbursement — fund release after approval and signature',
  ],
  businessValue: {
    summary:
      'Reduces loan approval cycle time while improving consistency, auditability, fraud detection, and risk-based pricing.',
    benefits: [
      'Faster turnaround across extraction, scoring, routing, and agreements',
      'Operational efficiency for intake, fraud, credit, and document prep',
      'Consistent policy enforcement via deterministic underwriting gates',
      'Earlier fraud detection in the workflow',
      'Risk-based pricing aligned to credit risk and policy',
      'Auditability with traceable decision records',
    ],
  },
  endUsers: {
    primary: 'Loan officers and credit risk analysts',
    secondary: 'Compliance officers, fraud investigators, operations teams, and applicants',
    tertiary: 'Model risk management, internal audit, and regulators reviewing decision controls',
  },
  dataSensitivity: [
    'Customer PII — identity, contact, government-issued documents, employment and address history',
    'Sensitive financial data — applications, income, liabilities, assets, bank statements, credit scores',
    'AI-generated underwriting outputs — risk scores, pricing recommendations, and decision rationale',
    'Internal proprietary data — lending policies, fraud typologies, pricing models, risk appetite',
  ],
  regulatoryConcerns: [
    {
      name: 'ECOA / Fair Lending',
      href: 'https://www.consumerfinance.gov/compliance/compliance-resources/other-applicable-requirements/equal-credit-opportunity-act/',
      jurisdiction: 'US',
    },
    {
      name: 'GDPR Article 22',
      href: 'https://gdpr-info.eu/art-22-gdpr/',
      jurisdiction: 'EU',
    },
    {
      name: 'EU AI Act (high-risk creditworthiness assessment)',
      href: 'https://air-governance-framework.finos.org/',
      jurisdiction: 'EU',
    },
  ],
  risks: [
    risk('ri-1', 'Information Leaked To Hosted Model', 'ri-1_information-leaked-to-hosted-model'),
    risk(
      'ri-4',
      'Hallucination and Inaccurate Outputs',
      'ri-4_hallucination-and-inaccurate-outputs',
    ),
    risk('ri-6', 'Non-Deterministic Behaviour', 'ri-6_non-deterministic-behaviour'),
    risk('ri-14', 'Inadequate System Alignment', 'ri-14_inadequate-system-alignment'),
    risk('ri-17', 'Lack of Explainability', 'ri-17_lack-of-explainability'),
    risk('ri-19', 'Data Quality and Drift', 'ri-19_data-quality-and-drift'),
  ],
  mitigations: [
    mitigation(
      'mi-1',
      'AI Data Leakage Prevention and Detection',
      'mi-1_ai-data-leakage-prevention-and-detection',
    ),
    mitigation('mi-4', 'AI System Observability', 'mi-4_ai-system-observability'),
    mitigation('mi-5', 'System Acceptance Testing', 'mi-5_system-acceptance-testing'),
    mitigation(
      'mi-11',
      'Human Feedback Loop for AI Systems',
      'mi-11_human-feedback-loop-for-ai-systems',
    ),
  ],
}

/** Demo catalog — extend as additional steel-thread use cases are wired. */
export const useCaseCatalog: UseCaseCatalogEntry[] = [loanApprovalUseCase]

export function useCaseById(id: string): UseCaseCatalogEntry | undefined {
  return useCaseCatalog.find((entry) => entry.id === id)
}
