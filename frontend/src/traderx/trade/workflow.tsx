import { WorkflowPage } from './WorkflowPage'
import type { Workflow } from '../registry'
export default { id: 'trade', title: 'Human-approved trade', description: 'Review an exact trade instruction before submitting it to TraderX.', component: WorkflowPage } satisfies Workflow
