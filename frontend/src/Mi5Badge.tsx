import type { ReactNode } from 'react'
import { MI5_TOOLTIP, MI5_TOOLTIP_SHORT } from './mi5Tooltip'

type Props = {
  children: ReactNode
  /** Shorter tip for dense places like the status log. */
  compact?: boolean
  className?: string
}

/** Accessible mi-5 badge with hover/focus tooltip. */
export function Mi5Badge({ children, compact = false, className }: Props) {
  const tip = compact ? MI5_TOOLTIP_SHORT : MI5_TOOLTIP
  const off = Boolean(className?.split(/\s+/).includes('is-off'))
  const extra = (className ?? '')
    .split(/\s+/)
    .filter((part) => part && part !== 'is-off')
    .join(' ')
  return (
    <span
      className={`control-badge has-tooltip ${off ? 'is-off' : 'is-enforced'}${extra ? ` ${extra}` : ''}`}
      tabIndex={0}
      title={tip}
      aria-label={tip}
      data-tooltip={tip}
    >
      {children}
    </span>
  )
}
