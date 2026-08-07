export type FinosProject = {
  id: string
  name: string
  description: string
  href: string
  logoSrc: string
}

/** FINOS projects featured beside the use-case card on the home screen. */
export const finosProjects: FinosProject[] = [
  {
    id: 'aigf',
    name: 'AI Governance Framework',
    description:
      'Open catalogue of AI risks and mitigations for financial services — including agentic controls — mapped to regulations and standards so institutions can govern GenAI safely.',
    href: 'https://air-governance-framework.finos.org/',
    logoSrc: '/logos/aigf.png',
  },
  {
    id: 'ccc',
    name: 'Common Cloud Controls',
    description:
      'Industry standard for consistent security, resiliency, and compliance controls across public cloud services, written once and reusable across major cloud providers.',
    href: 'https://ccc.finos.org/',
    logoSrc: '/logos/ccc.png',
  },
  {
    id: 'calm',
    name: 'Common Architecture Language Model',
    description:
      'Architecture-as-code specification for describing systems in a machine-readable format that can be validated, versioned, and visualized — including attached governance controls.',
    href: 'https://calm.finos.org/',
    logoSrc: '/logos/calm.png',
  },
  {
    id: 'fluxnova',
    name: 'Fluxnova',
    description:
      'Open-source BPMN process automation platform for embedding workflow orchestration in Java applications, with modeling, operations, and agentic extension points used by this demo.',
    href: 'https://github.com/finos/fluxnova-bpm-platform',
    logoSrc: '/logos/fluxnova.png',
  },
]
