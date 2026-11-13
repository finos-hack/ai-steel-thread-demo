import { WorkflowPage } from './WorkflowPage'
import type { Workflow } from '../registry'
export default { id: 'stale', title: 'Stale-order review', description: 'Review Keep or Cancel decisions for aged orders before execution.', component: WorkflowPage } satisfies Workflow
