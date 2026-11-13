import type { ComponentType } from 'react'
export type Workflow = { id: string; title: string; description: string; component: ComponentType<{ onBack: () => void }> }
const modules = import.meta.glob<{ default: Workflow }>('./*/workflow.tsx', { eager: true })
export const traderXWorkflows = Object.values(modules).map(m => m.default).sort((a,b) => a.title.localeCompare(b.title))
