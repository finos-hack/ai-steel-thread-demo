import type { ReactNode } from 'react'
import { MI11_TOOLTIP, MI11_TOOLTIP_SHORT } from './mi11Tooltip'

type Props = {
  children: ReactNode
  /** Shorter tip for dense places like the status log. */
  compact?: boolean
  className?: string
}

/** Accessible mi-11 badge with hover/focus tooltip. */
export function Mi11Badge({ children, compact = false, className }: Props) {
  const tip = compact ? MI11_TOOLTIP_SHORT : MI11_TOOLTIP
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
