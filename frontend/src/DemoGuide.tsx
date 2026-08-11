import { useEffect, useState } from 'react'
import { marked } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: false,
})

export type DemoGuideTab = 'guide' | 'narration'

type Props = {
  onBack?: () => void
  /** When true, omit Back and size for a side-by-side presenter popup. */
  presenterWindow?: boolean
  initialTab?: DemoGuideTab
}

const DOCS: Record<
  DemoGuideTab,
  { href: string; download: string; eyebrow: string; title: string; blurb: string }
> = {
  guide: {
    href: '/DEMO.md',
    download: 'AI-Steel-Thread-DEMO.md',
    eyebrow: 'Walkthrough',
    title: 'How to demonstrate',
    blurb:
      'Step-by-step runbook: AIGF governance, CALM, instance variations, and tokenomics.',
  },
  narration: {
    href: '/NARRATION.md',
    download: 'AI-Steel-Thread-NARRATION.md',
    eyebrow: 'Spoken script',
    title: 'Narration',
    blurb:
      'What to say while you click. Bracketed lines are stage directions — skip them aloud.',
  },
}

function syncPresenterQuery(tab: DemoGuideTab, presenterWindow: boolean) {
  const url = new URL(window.location.href)
  if (presenterWindow) {
    url.searchParams.set('presenter', '1')
  }
  url.searchParams.set('tab', tab)
  window.history.replaceState({}, '', url)
}

export function buildPresenterWindowUrl(tab: DemoGuideTab): string {
  const url = new URL(window.location.href)
  url.search = ''
  url.searchParams.set('presenter', '1')
  url.searchParams.set('tab', tab)
  return url.toString()
}

export function DemoGuide({
  onBack,
  presenterWindow = false,
  initialTab = 'guide',
}: Props) {
  const [tab, setTab] = useState<DemoGuideTab>(initialTab)
  const [html, setHtml] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const doc = DOCS[tab]

  useEffect(() => {
    setTab(initialTab)
  }, [initialTab])

  useEffect(() => {
    syncPresenterQuery(tab, presenterWindow)
  }, [tab, presenterWindow])

  useEffect(() => {
    let cancelled = false
    setHtml(null)
    setError(null)
    fetch(doc.href)
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Failed to load ${doc.href} (${response.status})`)
        }
        return response.text()
      })
      .then((markdown) => {
        if (cancelled) return
        setHtml(marked.parse(markdown, { async: false }) as string)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load document')
        }
      })
    return () => {
      cancelled = true
    }
  }, [doc.href])

  return (
    <section
      className={`panel enter demo-guide-page${presenterWindow ? ' is-presenter' : ''}`}
      aria-label="Demo guide"
    >
      <div className="panel-head">
        <div className="panel-head-row demo-guide-head">
          <div>
            <p className="eyebrow">{doc.eyebrow}</p>
            <h2>{doc.title}</h2>
          </div>
          <div className="actions demo-guide-actions">
            <a className="btn ghost" href={doc.href} download={doc.download}>
              Download Markdown
            </a>
            {!presenterWindow && onBack ? (
              <button type="button" className="btn ghost" onClick={onBack}>
                Back
              </button>
            ) : null}
          </div>
        </div>
        <p className="panel-copy">
          {presenterWindow
            ? 'Presenter window — keep this beside the Launch UI. Switch tabs below as you move through the demo.'
            : doc.blurb}
        </p>
        <div className="demo-guide-toolbar">
          <div className="demo-guide-tabs" role="tablist" aria-label="Demo documentation">
            <button
              type="button"
              role="tab"
              id="demo-tab-guide"
              aria-selected={tab === 'guide'}
              aria-controls="demo-tab-panel"
              className={`demo-guide-tab${tab === 'guide' ? ' is-active' : ''}`}
              onClick={() => setTab('guide')}
            >
              Walkthrough
            </button>
            <button
              type="button"
              role="tab"
              id="demo-tab-narration"
              aria-selected={tab === 'narration'}
              aria-controls="demo-tab-panel"
              className={`demo-guide-tab${tab === 'narration' ? ' is-active' : ''}`}
              onClick={() => setTab('narration')}
            >
              Narration
            </button>
          </div>
          {!presenterWindow ? (
            <a
              className="btn demo-guide-popout"
              href={buildPresenterWindowUrl(tab)}
              target="_blank"
              rel="noopener noreferrer"
              title="Open this tab in a separate window beside the demo"
              onClick={(event) => {
                const popup = window.open(
                  buildPresenterWindowUrl(tab),
                  'steel-thread-demo-guide',
                  'noopener,noreferrer,width=760,height=960',
                )
                if (popup) {
                  event.preventDefault()
                }
              }}
            >
              Open in new window
            </a>
          ) : null}
        </div>
      </div>

      <div
        id="demo-tab-panel"
        role="tabpanel"
        aria-labelledby={tab === 'guide' ? 'demo-tab-guide' : 'demo-tab-narration'}
      >
        {error ? <p className="hint danger">{error}</p> : null}
        {!error && !html ? <p className="hint">Loading…</p> : null}
        {html ? (
          <article
            className="demo-guide-markdown"
            dangerouslySetInnerHTML={{ __html: html }}
          />
        ) : null}
      </div>
    </section>
  )
}
