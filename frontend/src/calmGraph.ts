import dagre from 'dagre'
import {
  MarkerType,
  type Edge,
  type Node,
} from '@xyflow/react'

export type CalmControlRequirement = {
  'requirement-url'?: string
  config?: Record<string, unknown>
}

export type CalmControl = {
  description?: string
  'aigf-mitigations'?: string[]
  'aigf-risks'?: string[]
  requirements?: CalmControlRequirement[]
}

export type CalmControls = Record<string, CalmControl>

export type CalmPolicySummary = {
  key: string
  controlIds: string[]
  name: string
  description: string
  catalogUrl?: string
  mitigations: string[]
  risks: string[]
  /** Notable config fields for the side panel (arrays / booleans / short strings). */
  highlights: Array<{ label: string; value: string }>
}

export type CalmNode = {
  'unique-id': string
  'node-type': string
  name: string
  description?: string
  metadata?: Record<string, unknown>
  controls?: CalmControls
}

export type CalmRelationship = {
  'unique-id': string
  description?: string
  protocol?: string
  controls?: CalmControls
  'relationship-type': {
    'composed-of'?: { container: string; nodes: string[] }
    'deployed-in'?: { container: string; nodes: string[] }
    connects?: {
      source: { node: string; interfaces?: string[] }
      destination: { node: string; interfaces?: string[] }
    }
    interacts?: { actor: string; nodes: string[] }
  }
}

export type CalmArchitecture = {
  name?: string
  description?: string
  nodes?: CalmNode[]
  relationships?: CalmRelationship[]
  flows?: Array<{ 'unique-id': string; name: string; description?: string }>
}

const NODE_W = 200
const NODE_H = 72
const GROUP_PAD = 40

const TYPE_COLORS: Record<string, string> = {
  actor: '#c4a574',
  ecosystem: '#6b8f71',
  system: '#5b7c99',
  service: '#8a9bb0',
  webclient: '#9a7b6a',
  database: '#7a8f6b',
  network: '#6a7a8f',
  'data-asset': '#a07878',
}

const HIGHLIGHT_KEYS = [
  'context-allowlist',
  'forbidden-egress-categories',
  'allowed-egress-categories',
  'session-data-classification',
  'egress-allowlist-hosts',
  'metrics',
  'dashboard-ids',
  'purpose',
  'edge-type',
  'scope',
  'data-minimization',
  'tls-required',
  'egress-audit',
  'sanitize-conversation-traces',
  'forbid-logging-raw-media',
  'forbid-logging-raw-prompts',
  'sanitized-audit-log',
  'persist-call-metadata',
  'track-tokens',
  'track-latency',
  'track-errors',
  'track-model-version',
  'tsp-no-training',
  'tsp-zero-or-minimal-persistence',
  'prefer-private-endpoint',
  'forbid-unapproved-callers',
] as const

function formatHighlightValue(value: unknown): string | null {
  if (value == null) return null
  if (typeof value === 'boolean') return value ? 'yes' : 'no'
  if (typeof value === 'number') return String(value)
  if (typeof value === 'string') return value.length > 80 ? `${value.slice(0, 77)}…` : value
  if (Array.isArray(value)) {
    if (value.length === 0) return '—'
    return value.map(String).join(', ')
  }
  return null
}

function humanizeKey(key: string): string {
  return key.replace(/-/g, ' ')
}

/** Normalize CALM `controls` into side-panel friendly policy summaries. */
export function summarizeControls(controls?: CalmControls): CalmPolicySummary[] {
  if (!controls) return []
  return Object.entries(controls).map(([key, control]) => {
    const req = control.requirements?.[0]
    const config = req?.config ?? {}
    const controlId = typeof config['control-id'] === 'string' ? config['control-id'] : undefined
    const name =
      typeof config.name === 'string'
        ? config.name
        : key
            .split('-')
            .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
            .join(' ')
    const description =
      (typeof config.description === 'string' && config.description) ||
      control.description ||
      ''
    const catalogUrl =
      typeof config['aigf-catalog-url'] === 'string' ? config['aigf-catalog-url'] : undefined
    const mitigations = [
      ...(control['aigf-mitigations'] ?? []),
      ...(controlId && !(control['aigf-mitigations'] ?? []).includes(controlId) ? [controlId] : []),
    ]
    const highlights: CalmPolicySummary['highlights'] = []
    for (const field of HIGHLIGHT_KEYS) {
      if (!(field in config)) continue
      const formatted = formatHighlightValue(config[field])
      if (formatted == null) continue
      highlights.push({ label: humanizeKey(field), value: formatted })
    }
    return {
      key,
      controlIds: mitigations.length > 0 ? mitigations : controlId ? [controlId] : [],
      name,
      description,
      catalogUrl,
      mitigations,
      risks: control['aigf-risks'] ?? [],
      highlights,
    }
  })
}

function controlIdsFrom(policies: CalmPolicySummary[]): string[] {
  return [...new Set(policies.flatMap((p) => p.controlIds))]
}

function layoutWithDagre(
  nodes: Node[],
  edges: Edge[],
  direction: 'TB' | 'LR' = 'TB',
): Map<string, { x: number; y: number }> {
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({ rankdir: direction, nodesep: 36, ranksep: 56, marginx: 24, marginy: 24 })

  for (const node of nodes) {
    const width = Number(node.style?.width ?? node.width ?? NODE_W)
    const height = Number(node.style?.height ?? node.height ?? NODE_H)
    g.setNode(node.id, { width, height })
  }
  for (const edge of edges) {
    g.setEdge(edge.source, edge.target)
  }
  dagre.layout(g)

  const positions = new Map<string, { x: number; y: number }>()
  for (const node of nodes) {
    const laid = g.node(node.id)
    if (!laid) continue
    const width = Number(node.style?.width ?? node.width ?? NODE_W)
    const height = Number(node.style?.height ?? node.height ?? NODE_H)
    positions.set(node.id, {
      x: laid.x - width / 2,
      y: laid.y - height / 2,
    })
  }
  return positions
}

/** Parse a CALM architecture into React Flow elements (CALM Hub visualizer style). */
export function calmToReactFlow(architecture: CalmArchitecture): {
  nodes: Node[]
  edges: Edge[]
} {
  const calmNodes = architecture.nodes ?? []
  const relationships = architecture.relationships ?? []

  const parentOf = new Map<string, string>()
  const containerIds = new Set<string>()

  for (const rel of relationships) {
    const composed = rel['relationship-type']['composed-of']
    const deployed = rel['relationship-type']['deployed-in']
    const nesting = composed ?? deployed
    if (!nesting) continue
    containerIds.add(nesting.container)
    for (const child of nesting.nodes) {
      parentOf.set(child, nesting.container)
    }
  }

  const flowNodes: Node[] = calmNodes.map((n) => {
    const id = n['unique-id']
    const isGroup = containerIds.has(id)
    const parentId = parentOf.get(id)
    const accent = TYPE_COLORS[n['node-type']] ?? '#8a9bb0'
    const policies = summarizeControls(n.controls)
    const controlIds = controlIdsFrom(policies)
    const hasControls = policies.length > 0

    if (isGroup) {
      return {
        id,
        type: 'calmGroup',
        draggable: true,
        data: {
          label: n.name,
          nodeType: n['node-type'],
          description: n.description ?? '',
          accent,
          policies,
          controlIds,
        },
        position: { x: 0, y: 0 },
        style: {
          width: 320,
          height: 200,
          background: 'rgba(232, 237, 244, 0.04)',
          border: hasControls ? `1.5px solid ${accent}` : `1px solid ${accent}66`,
          borderRadius: 12,
        },
        ...(parentId ? { parentId, extent: 'parent' as const } : {}),
      }
    }

    return {
      id,
      type: 'calmNode',
      draggable: true,
      data: {
        label: n.name,
        nodeType: n['node-type'],
        description: n.description ?? '',
        accent,
        policies,
        controlIds,
      },
      position: { x: 0, y: 0 },
      style: { width: NODE_W, height: NODE_H },
      className: hasControls ? 'has-controls' : undefined,
      ...(parentId ? { parentId, extent: 'parent' as const } : {}),
    }
  })

  const edges: Edge[] = []
  for (const rel of relationships) {
    const rt = rel['relationship-type']
    const policies = summarizeControls(rel.controls)
    const controlIds = controlIdsFrom(policies)
    const hasControls = policies.length > 0
    const stroke = hasControls ? 'rgba(109, 184, 146, 0.75)' : 'rgba(232, 237, 244, 0.45)'

    if (rt.connects) {
      edges.push({
        id: rel['unique-id'],
        source: rt.connects.source.node,
        target: rt.connects.destination.node,
        label: rel.protocol ?? (hasControls ? controlIds.join(' · ') || 'policy' : undefined),
        markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16, color: stroke },
        style: { stroke, strokeWidth: hasControls ? 2 : 1 },
        labelStyle: { fill: '#e8edf4', fontSize: 10, fontWeight: 600 },
        labelBgStyle: {
          fill: 'rgba(14, 18, 26, 0.92)',
          stroke: hasControls ? 'rgba(109, 184, 146, 0.35)' : 'rgba(232, 237, 244, 0.22)',
        },
        labelBgPadding: [5, 8] as [number, number],
        labelBgBorderRadius: 4,
        data: { description: rel.description ?? '', policies, controlIds },
      })
    }
    if (rt.interacts) {
      for (const target of rt.interacts.nodes) {
        edges.push({
          id: `${rel['unique-id']}__${target}`,
          source: rt.interacts.actor,
          target,
          label: 'interacts',
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: 16,
            height: 16,
            color: 'rgba(196, 165, 116, 0.75)',
          },
          style: { stroke: 'rgba(196, 165, 116, 0.55)', strokeDasharray: '4 3' },
          labelStyle: { fill: '#e8edf4', fontSize: 10, fontWeight: 600 },
          labelBgStyle: {
            fill: 'rgba(14, 18, 26, 0.92)',
            stroke: 'rgba(196, 165, 116, 0.35)',
          },
          labelBgPadding: [5, 8] as [number, number],
          labelBgBorderRadius: 4,
          data: { description: rel.description ?? '', policies, controlIds },
        })
      }
    }
  }

  // Layout deepest containers first
  const depth = (id: string): number => {
    let d = 0
    let cur = parentOf.get(id)
    while (cur) {
      d += 1
      cur = parentOf.get(cur)
    }
    return d
  }

  const groups = [...containerIds].sort((a, b) => depth(b) - depth(a))

  for (const groupId of groups) {
    const children = flowNodes.filter((n) => n.parentId === groupId)
    if (children.length === 0) continue
    const childIds = new Set(children.map((c) => c.id))
    const childEdges = edges.filter((e) => childIds.has(e.source) && childIds.has(e.target))
    const positions = layoutWithDagre(children, childEdges, 'TB')

    let maxX = 0
    let maxY = 0
    for (const child of children) {
      const pos = positions.get(child.id) ?? { x: 0, y: 0 }
      child.position = { x: pos.x + GROUP_PAD, y: pos.y + GROUP_PAD + 28 }
      const w = Number(child.style?.width ?? NODE_W)
      const h = Number(child.style?.height ?? NODE_H)
      maxX = Math.max(maxX, child.position.x + w)
      maxY = Math.max(maxY, child.position.y + h)
    }

    const group = flowNodes.find((n) => n.id === groupId)
    if (group) {
      const width = Math.max(280, maxX + GROUP_PAD)
      const height = Math.max(160, maxY + GROUP_PAD)
      group.width = width
      group.height = height
      group.style = { ...group.style, width, height }
    }
  }

  const topLevel = flowNodes.filter((n) => !n.parentId)
  const topIds = new Set(topLevel.map((n) => n.id))
  const topEdges = edges.filter((e) => topIds.has(e.source) && topIds.has(e.target))

  const topPositions = layoutWithDagre(topLevel, topEdges, 'LR')
  for (const node of topLevel) {
    const pos = topPositions.get(node.id)
    if (pos) node.position = pos
  }

  // Parents before children for React Flow
  const ordered = [
    ...flowNodes.filter((n) => containerIds.has(n.id) && !n.parentId),
    ...flowNodes.filter((n) => containerIds.has(n.id) && n.parentId),
    ...flowNodes.filter((n) => !containerIds.has(n.id)),
  ]

  return { nodes: ordered, edges }
}
