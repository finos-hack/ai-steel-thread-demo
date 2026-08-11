import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  Handle,
  Position,
  useEdgesState,
  useNodesState,
  type Edge,
  type Node,
  type NodeProps,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import {
  calmToReactFlow,
  type CalmArchitecture,
  type CalmPolicySummary,
} from './calmGraph'

type Props = {
  architectureHref: string
  calmHubHref: string
  onClose: () => void
}

type Selection = {
  title: string
  subtitle: string
  description: string
  policies: CalmPolicySummary[]
}

function CalmNodeView({ data }: NodeProps) {
  const d = data as {
    label: string
    nodeType: string
    description: string
    accent: string
    controlIds?: string[]
  }
  const controlIds = d.controlIds ?? []
  return (
    <div
      className={`calm-rf-node${controlIds.length ? ' has-controls' : ''}`}
      style={{ borderColor: d.accent }}
    >
      <Handle type="target" position={Position.Left} />
      <p className="calm-rf-type">{d.nodeType}</p>
      <p className="calm-rf-label">{d.label}</p>
      {controlIds.length > 0 ? (
        <p className="calm-rf-controls" aria-label={`Controls ${controlIds.join(', ')}`}>
          {controlIds.map((id) => (
            <span key={id} className="calm-rf-control-chip">
              {id}
            </span>
          ))}
        </p>
      ) : null}
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

function CalmGroupView({ data }: NodeProps) {
  const d = data as {
    label: string
    nodeType: string
    accent: string
    controlIds?: string[]
  }
  const controlIds = d.controlIds ?? []
  return (
    <div className="calm-rf-group-label" style={{ color: d.accent }}>
      <span>{d.nodeType}</span>
      <strong>{d.label}</strong>
      {controlIds.length > 0 ? (
        <span className="calm-rf-controls">
          {controlIds.map((id) => (
            <span key={id} className="calm-rf-control-chip">
              {id}
            </span>
          ))}
        </span>
      ) : null}
    </div>
  )
}

const nodeTypes = {
  calmNode: CalmNodeView,
  calmGroup: CalmGroupView,
}

export function CalmArchitectureVisualizer({
  architectureHref,
  calmHubHref,
  onClose,
}: Props) {
  const [architecture, setArchitecture] = useState<CalmArchitecture | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [hubAvailable, setHubAvailable] = useState<boolean | null>(null)
  const [selected, setSelected] = useState<Selection | null>(null)
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])

  useEffect(() => {
    let cancelled = false
    fetch(architectureHref)
      .then(async (response) => {
        if (!response.ok) throw new Error(`Failed to load architecture (${response.status})`)
        return (await response.json()) as CalmArchitecture
      })
      .then((json) => {
        if (cancelled) return
        setArchitecture(json)
        const graph = calmToReactFlow(json)
        setNodes(graph.nodes)
        setEdges(graph.edges)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err))
      })
    return () => {
      cancelled = true
    }
  }, [architectureHref, setNodes, setEdges])

  useEffect(() => {
    let cancelled = false
    // Same-origin probe via nginx (/calm-hub-status) or Vite proxy — direct
    // fetch to :8090 fails CORS from the Launch UI origin and falsely disables the button.
    fetch('/calm-hub-status', { method: 'GET' })
      .then((r) => {
        if (!cancelled) setHubAvailable(r.ok)
      })
      .catch(() => {
        if (!cancelled) setHubAvailable(false)
      })
    return () => {
      cancelled = true
    }
  }, [calmHubHref])

  return createPortal(
    <div className="calm-viz-backdrop" role="presentation" onClick={onClose}>
      <div
        className="calm-viz-modal"
        role="dialog"
        aria-modal="true"
        aria-label="CALM architecture visualizer"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="calm-viz-head">
          <div>
            <p className="sample-preview-label">FINOS CALM visualizer</p>
            <h2>{architecture?.name ?? 'Loan Approval architecture'}</h2>
          </div>
          <div className="calm-viz-actions">
            <a className="btn ghost" href={architectureHref} download>
              Download JSON
            </a>
            <a
              className="btn ghost"
              href={calmHubHref}
              target="_blank"
              rel="noreferrer"
              title={
                hubAvailable === false
                  ? 'Hub probe failed — link still opens localhost:8090 if calmhub is running'
                  : 'Open architecture in local CALM Hub'
              }
            >
              Open CALM Hub
            </a>
            <button type="button" className="btn primary" onClick={onClose}>
              Close
            </button>
          </div>
        </header>

        {error && <p className="hint danger">{error}</p>}
        {!error && !architecture && <p className="hint">Loading CALM architecture…</p>}

        {architecture && (
          <div className="calm-viz-body">
            <div className="calm-viz-canvas">
              <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                nodeTypes={nodeTypes}
                nodesDraggable
                nodesConnectable={false}
                elementsSelectable
                panOnDrag
                zoomOnScroll
                fitView
                minZoom={0.15}
                maxZoom={1.5}
                proOptions={{ hideAttribution: true }}
                onNodeClick={(_, node) => {
                  const d = node.data as {
                    label?: string
                    nodeType?: string
                    description?: string
                    policies?: CalmPolicySummary[]
                  }
                  setSelected({
                    title: d.label ?? node.id,
                    subtitle: d.nodeType ?? node.type ?? 'node',
                    description: d.description ?? '',
                    policies: d.policies ?? [],
                  })
                }}
                onEdgeClick={(_, edge) => {
                  const d = edge.data as
                    | { description?: string; policies?: CalmPolicySummary[] }
                    | undefined
                  setSelected({
                    title: edge.id,
                    subtitle: edge.label ? String(edge.label) : 'relationship',
                    description: d?.description ?? '',
                    policies: d?.policies ?? [],
                  })
                }}
              >
                <Background gap={18} color="rgba(232,237,244,0.08)" />
                <Controls showInteractive={false} />
                <MiniMap
                  pannable
                  zoomable
                  maskColor="rgba(10, 14, 20, 0.7)"
                  nodeColor={(node) => {
                    const ids = (node.data as { controlIds?: string[] } | undefined)?.controlIds
                    return ids && ids.length > 0
                      ? 'rgba(109, 184, 146, 0.7)'
                      : 'rgba(196, 165, 116, 0.55)'
                  }}
                />
              </ReactFlow>
            </div>
            <aside className="calm-viz-side">
              <p className="sample-preview-label">Details</p>
              {selected ? (
                <>
                  <p className="calm-viz-side-type">{selected.subtitle}</p>
                  <h3>{selected.title}</h3>
                  <p>{selected.description || 'No description.'}</p>
                  <PolicyPanel policies={selected.policies} />
                </>
              ) : (
                <p className="hint">
                  Drag nodes to rearrange. Click a node or relationship with an{' '}
                  <code>mi-*</code> chip to inspect attached CALM control policies (allowlists,
                  egress rules, observability).
                </p>
              )}
              {architecture.description && (
                <>
                  <p className="sample-preview-label" style={{ marginTop: '1.25rem' }}>
                    Architecture
                  </p>
                  <p className="hint">{architecture.description}</p>
                </>
              )}
              {hubAvailable === false && (
                <p className="hint" style={{ marginTop: '1rem' }}>
                  Local CALM Hub is not reachable on port 8090. With the full stack it starts
                  automatically (`calmhub` in <code>docker compose up</code>). Standalone:{' '}
                  <code>docker compose -f architecture/docker-compose.yml up</code>, then push the
                  architecture (see architecture/README.md).
                </p>
              )}
            </aside>
          </div>
        )}
      </div>
    </div>,
    document.body,
  )
}

function PolicyPanel({ policies }: { policies: CalmPolicySummary[] }) {
  if (policies.length === 0) {
    return (
      <>
        <p className="sample-preview-label" style={{ marginTop: '1.1rem' }}>
          Controls
        </p>
        <p className="hint">No CALM controls attached to this element.</p>
      </>
    )
  }

  return (
    <>
      <p className="sample-preview-label" style={{ marginTop: '1.1rem' }}>
        Controls / policies
      </p>
      <div className="calm-policy-list">
        {policies.map((policy) => (
          <article key={policy.key} className="calm-policy-card">
            <div className="calm-policy-head">
              {policy.controlIds.map((id) => (
                <span key={id} className="calm-rf-control-chip">
                  {id}
                </span>
              ))}
              <h4>{policy.name}</h4>
            </div>
            <p className="hint">{policy.description}</p>
            {policy.risks.length > 0 ? (
              <p className="calm-policy-meta">
                Risks · {policy.risks.map((r) => <code key={r}>{r}</code>)}
              </p>
            ) : null}
            {policy.highlights.length > 0 ? (
              <dl className="calm-policy-dl">
                {policy.highlights.slice(0, 8).map((item) => (
                  <div key={item.label}>
                    <dt>{item.label}</dt>
                    <dd>{item.value}</dd>
                  </div>
                ))}
              </dl>
            ) : null}
            {policy.catalogUrl ? (
              <a
                className="calm-policy-link"
                href={policy.catalogUrl}
                target="_blank"
                rel="noreferrer"
              >
                Open in AIGF catalog
              </a>
            ) : null}
          </article>
        ))}
      </div>
    </>
  )
}
