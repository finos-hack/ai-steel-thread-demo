import { useEffect, useState } from 'react';
import '../../TraderXWorkflows.css';
import { TraderXLink } from '../TraderXLink';
export type AdvancedKind = 'rebalance';
type Person = {
    logonId: string;
    fullName: string;
};
type Options = {
    accounts: {
        id: number;
        displayName: string;
    }[];
    memberships: {
        accountId: number;
        username: string;
    }[];
    securities: {
        ticker: string;
        companyName: string;
    }[];
};
type Target = {
    security: string;
    percent: number;
};
type Leg = {
    security: string;
    side: string;
    quantity: number;
    limitPrice: number;
};
type Order = {
    orderId: string;
    accountId: number;
    security: string;
    side: string;
    quantity: number;
    remainingQuantity: number;
    limitPrice: number;
    status: string;
    createdAt: string;
};
type OrderRow = {
    order: Order;
    ageMinutes: number;
    action: 'KEEP' | 'CANCEL';
    quote?: {
        price: number;
    };
    quoteError?: string;
    include?: boolean;
};
type Plan = {
    budgetBasis?: string;
    accountId: number;
    accountName: string;
    snapshotAt: string;
    portfolioValue?: number;
    estimatedBuys?: number;
    estimatedSells?: number;
    roundingResidual?: number;
    allocations?: {
        security: string;
        currentQuantity: number;
        currentPercent: number;
        targetPercent: number;
        targetQuantity: number;
        quotePrice: number;
    }[];
    legs?: Leg[];
    orders?: OrderRow[];
};
type Strategy = {
    name: string;
    rationale: string;
    tradeoffs: string;
    targets: Target[];
    plan: Plan;
};
type Suggestions = {
    strategies: Strategy[];
    model: string;
    generatedAt: string;
    snapshot: {
        portfolioValue: number;
        holdings: {
            security: string;
            quantity: number;
            value: number;
        }[];
    };
};
type Summary = {
    id: string;
    title: string;
    status: string;
    requester: string;
    startedAt: string;
};
type Result = {
    index: number;
    orderId?: string;
    outcome: string;
    error?: string;
    response?: Order;
};
type Detail = Summary & {
    plan: Plan;
    variables: Record<string, string | number | boolean>;
    results: Result[];
    liveOrders: {
        orderId: string;
        order?: Order;
        error?: string;
    }[];
    activities: {
        name: string;
        startedAt: string;
        endedAt: string | null;
    }[];
    incidents: {
        message: string;
    }[];
};
const titles = { rebalance: 'Portfolio rebalance', stale: 'Stale-order review' };
const money = (n?: number) => n == null ? '—' : n.toLocaleString(undefined, { style: 'currency', currency: 'USD' });
const orderCount = (legs: Leg[] = []) => legs.reduce((n, l) => n + Math.ceil(l.quantity / 10000), 0);
const time = (s: string) => new Date(s).toLocaleString();
const message = (e: unknown) => e instanceof Error ? e.message : 'Request failed';
const label = (s: string) => s.replaceAll('_', ' ').toLowerCase();
async function api<T>(path: string, body?: unknown): Promise<T> {
    const r = await fetch('/api/demo/traderx' + path, body === undefined ? undefined : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    const data = await r.json().catch(() => ({}));
    if (!r.ok)
        throw new Error(data.detail || `Request failed (${r.status})`);
    return data as T;
}
export function WorkflowPage({ onBack }: {
    onBack: () => void;
}) {
    const kind = 'rebalance' as const;
    const [options, setOptions] = useState<Options | null>(null);
    const [people, setPeople] = useState<Person[]>([]);
    const [search, setSearch] = useState('user');
    const [requester, setRequester] = useState('');
    const [accountId, setAccountId] = useState('');
    const [targets, setTargets] = useState<Target[]>([{ security: '', percent: 100 }]);
    const [mode, setMode] = useState<'manual' | 'ai'>('manual');
    const [suggestions, setSuggestions] = useState<Suggestions | null>(null);
    const [chosenStrategy, setChosenStrategy] = useState('');
    const [ageMinutes, ] = useState(60);
    const [reason, setReason] = useState('');
    const [plan, setPlan] = useState<Plan | null>(null);
    const [selected, setSelected] = useState<string | null>(null);
    const [detail, setDetail] = useState<Detail | null>(null);
    const [requests, setRequests] = useState<Summary[]>([]);
    const [reviewer, setReviewer] = useState('');
    const [comment, setComment] = useState('');
    const [confirm, setConfirm] = useState(false);
    const [error, setError] = useState('');
    const [syncError, setSyncError] = useState('');
    const [directoryError, setDirectoryError] = useState('');
    const [busy, setBusy] = useState(false);
    useEffect(() => {
        let active = true;
        api<Options>('/options').then(x => {
            if (active)
                setOptions(x);
        }).catch(e => {
            if (active)
                setError(message(e));
        });
        return () => { active = false; };
    }, []);
    useEffect(() => {
        let active = true;
        const t = window.setTimeout(() => {
            if (search.trim())
                api<Person[]>(`/people?search=${encodeURIComponent(search)}`).then(x => {
                    if (active) {
                        setPeople(prev => [...new Map([...prev, ...x].map(p => [p.logonId, p])).values()]);
                        setDirectoryError(x.length ? '' : 'No matching people found');
                    }
                }).catch(e => {
                    if (active)
                        setDirectoryError(message(e));
                });
        }, 250);
        return () => { active = false; window.clearTimeout(t); };
    }, [search]);
    useEffect(() => {
        let active = true;
        const load = async () => {
            try {
                const rows = await api<Summary[]>(`/workflows/${kind}`);
                const d = selected ? await api<Detail>(`/instances/${selected}`) : null;
                if (active) {
                    setRequests(rows);
                    setDetail(d);
                    setSyncError('');
                }
            }
            catch (e) {
                if (active)
                    setSyncError(message(e));
            }
        };
        void load();
        const timer = window.setInterval(load, 3000);
        return () => { active = false; window.clearInterval(timer); };
    }, [kind, selected]);
    function choose(id: string | null) { setSelected(id); setDetail(null); setReviewer(''); setComment(''); setConfirm(false); setError(''); }
    function resetAi() {
        setSuggestions(null);
        setChosenStrategy('');
        if (chosenStrategy)
            setReason('');
    }
    const accounts = options?.accounts.filter(a => options.memberships.some(m => m.accountId === a.id && m.username === requester)) ?? [];
    function requestBody() {
        return { requester, reason, accountId: Number(accountId), targets, ageMinutes, legs: plan?.legs,
            actions: plan?.orders?.filter(x => x.include !== false).map(x => ({ orderId: x.order.orderId, action: x.action })) };
    }
    async function suggest() {
        setBusy(true);
        setError('');
        setPlan(null);
        resetAi();
        try {
            setSuggestions(await api<Suggestions>('/rebalance/suggestions', { requester, accountId: Number(accountId) }));
        }
        catch (e) {
            setError(message(e));
        }
        finally {
            setBusy(false);
        }
    }
    function useStrategy(s: Strategy) {
        setTargets(s.targets.map(t => ({ ...t })));
        setPlan(s.plan);
        setChosenStrategy(s.name);
        setReason(`AI-assisted draft: ${s.name}. ${s.rationale} Tradeoffs: ${s.tradeoffs}`.slice(0, 1000));
    }
    async function preview() {
        setBusy(true);
        setError('');
        setPlan(null);
        try {
            setPlan(await api<Plan>(`/${kind}/preview`, requestBody()));
        }
        catch (e) {
            setError(message(e));
        }
        finally {
            setBusy(false);
        }
    }
    async function submit() {
        setBusy(true);
        setError('');
        try {
            const d = await api<Detail>(`/${kind}/start`, requestBody());
            choose(d.id);
            setDetail(d);
            setRequests(prev => [d, ...prev]);
            setPlan(null);
        }
        catch (e) {
            setError(message(e));
        }
        finally {
            setBusy(false);
        }
    }
    async function decide(decision: 'APPROVE' | 'REJECT') {
        if (!detail)
            return;
        setBusy(true);
        setError('');
        try {
            setDetail(await api<Detail>(`/instances/${detail.id}/decision`, { reviewer, decision, comment }));
            setConfirm(false);
        }
        catch (e) {
            setError(message(e));
        }
        finally {
            setBusy(false);
        }
    }
    function changeTarget(index: number, update: Partial<Target>) { setTargets(prev => prev.map((t, i) => i === index ? { ...t, ...update } : t)); setPlan(null); }
    function changeLeg(index: number, update: Partial<Leg>) { setPlan(p => p && ({ ...p, legs: p.legs?.map((l, i) => i === index ? { ...l, ...update } : l) })); }
    const shown = detail?.plan ?? plan;
    const editing = !selected && !busy;
    const budgetLegs = detail?.plan.legs ?? plan?.legs ?? [];
    const limitBuys = budgetLegs.filter(l => l.side === 'Buy').reduce((n, l) => n + l.quantity * l.limitPrice, 0);
    const limitSells = budgetLegs.filter(l => l.side === 'Sell').reduce((n, l) => n + l.quantity * l.limitPrice, 0);
    const overBudget = limitBuys > limitSells + 0.01;
    function balanceBuys() {
        const factor = limitBuys > 0 ? Math.min(1, limitSells / limitBuys) : 1;
        setPlan(p => p && ({ ...p, legs: p.legs?.map(l => l.side === 'Buy' ? { ...l, quantity: Math.floor(l.quantity * factor) } : l).filter(l => l.quantity > 0) }));
        setError('');
    }
    const ready = !!plan?.legs?.length && orderCount(plan.legs) <= 500;
    const validReview = reviewer && reviewer !== detail?.requester && comment.trim();
    const results = detail?.results ?? [];
    return <section className="tx-workflows tx-advanced enter">
    <div className="tx-heading"><button className="panel-back" onClick={onBack}>Back to catalog</button><TraderXLink /></div>
    <p className="use-case-label">TRADERX · HUMAN-GOVERNED OPERATIONS</p><h1>{titles[kind]}</h1>
    <p className="lede">{'Turn target allocations into an editable basket. A human approves the exact instructions; each submitted order has its own receipt and live status.'}</p>
    <div className="tx-demo-note">TraderX simulation · Directory selectors are demo identities, not authentication. {'Allocation targets apply to long-only stock holdings, excluding cash. Orders are independent; a basket is not an atomic transaction or a funding guarantee.'}</div>
    <ol className="tx-flow">{(['Holdings & targets', 'Editable proposal', 'Human approval', 'Per-order execution']).map((s, i) => <li key={s}><span>{i + 1}</span>{s}</li>)}</ol>
    {error && <div className="tx-error" role="alert">{error}</div>}{syncError && <div className="tx-error" role="status">Reconnecting: {syncError}. Retrying automatically.</div>}
    <div className="tx-columns">
      <div className="tx-card">
        <div className="tx-heading"><h2>{selected ? 'Approved scope & progress' : 'Build a rebalance proposal'}</h2>{selected && <button className="btn ghost" onClick={() => { choose(null); setPlan(null); }}>New request</button>}</div>
        {!selected && <fieldset className="tx-form tx-inputs" disabled={busy}>
          <label>Find a directory person<input value={search} onChange={e => setSearch(e.target.value)} placeholder="Name or logon ID"/></label>{directoryError && <p role="status">{directoryError}</p>}
          <label>Requester<select value={requester} onChange={e => { setRequester(e.target.value); setAccountId(''); setPlan(null); resetAi(); }}><option value="">Choose a person</option>{people.map(p => <option key={p.logonId} value={p.logonId}>{p.fullName} · {p.logonId}</option>)}</select></label>
          <label>TraderX account<select disabled={!requester || !options} value={accountId} onChange={e => { setAccountId(e.target.value); setPlan(null); resetAi(); }}><option value="">Choose a linked account</option>{accounts.map(a => <option key={a.id} value={a.id}>{a.displayName} · #{a.id}</option>)}</select></label>
          {requester && options && !accounts.length && <p className="tx-muted">No linked accounts for this person. Open an account or select another requester.</p>}
          {<>
            <div className="tx-tabs" aria-label="Target creation mode"><button className={`btn ${mode === 'manual' ? 'primary' : 'ghost'}`} aria-pressed={mode === 'manual'} onClick={() => setMode('manual')}>Manual targets</button><button className={`btn ${mode === 'ai' ? 'primary' : 'ghost'}`} aria-pressed={mode === 'ai'} onClick={() => setMode('ai')}>AI suggestions</button></div>
            {mode === 'ai' && <div className="tx-ai">
              <h3>Explore three rebalance strategies</h3>
              <p className="tx-muted">AI compares concentration reduction, equal weighting, and lighter turnover using your live holdings and available securities. Only holdings, quotes, and the securities catalog are sent to the configured model; account names and identities stay local.</p>
              <button className="btn primary" disabled={!accountId || busy} onClick={suggest}>{busy ? 'Analyzing portfolio…' : suggestions ? 'Regenerate AI suggestions' : 'Analyze portfolio & suggest baskets'}</button>
              <p className="tx-muted">Illustrative alternatives for this simulator. AI has no cash, tax, historical-risk, or investor-profile data. It cannot submit trades. You choose and edit a basket before human approval.</p>
              {suggestions && <>
                <p><strong>{money(suggestions.snapshot.portfolioValue)}</strong> in stocks · {suggestions.snapshot.holdings.map(h => `${h.security} ${(100 * h.value / suggestions.snapshot.portfolioValue).toFixed(1)}%`).join(' · ')}</p>
                <p className="tx-muted">Generated {time(suggestions.generatedAt)} · {suggestions.model}</p>
                <div className="tx-strategies">{suggestions.strategies.map(s => <article className={`tx-strategy ${chosenStrategy === s.name ? 'is-selected' : ''}`} key={s.name}>
                  <h3>{s.name}</h3><p>{s.rationale}</p><p><strong>Tradeoffs:</strong> {s.tradeoffs}</p>
                  <div className="tx-target-chips">{s.targets.map(t => <span key={t.security}>{t.security} <strong>{t.percent}%</strong></span>)}</div>
                  <p className="tx-muted">{orderCount(s.plan.legs)} orders · sells {money(s.plan.estimatedSells)} · buys {money(s.plan.estimatedBuys)}</p>
                  {s.plan.legs?.some(l => l.quantity > 10000) && <p className="tx-muted">Large instructions will be split into orders of at most 10,000 shares, preserving the total shares and limit price.</p>}
                  {!s.plan.legs?.length && <p className="tx-muted">No whole-share trades needed for these targets.</p>}
                  <button className="btn ghost" onClick={() => useStrategy(s)}>{chosenStrategy === s.name ? 'Reload this strategy' : 'Use & edit this basket'}</button>
                </article>)}</div>
              </>}
            </div>}
            {chosenStrategy && <p className="tx-success">Draft based on {chosenStrategy}. Targets and orders below remain editable.</p>}
            <h3>Target allocations <small>({targets.reduce((n, t) => n + (t.percent || 0), 0)}% total)</small></h3>
            {targets.map((t, i) => <div className="tx-target" key={i}><label>Security {i + 1}<select value={t.security} onChange={e => changeTarget(i, { security: e.target.value })}><option value="">Choose a security</option>{options?.securities.map(s => <option key={s.ticker} value={s.ticker}>{s.ticker} · {s.companyName}</option>)}</select></label><label>Weight {i + 1} (%)<input type="number" min="0" max="100" step="0.01" value={t.percent} onChange={e => changeTarget(i, { percent: Number(e.target.value) })}/></label><button className="btn ghost" aria-label={`Remove target ${i + 1}`} disabled={targets.length === 1} onClick={() => { setTargets(prev => prev.filter((_, j) => j !== i)); setPlan(null); }}>×</button></div>)}
            <button className="text-link" disabled={targets.length >= 20} onClick={() => { setTargets(prev => [...prev, { security: '', percent: 0 }]); setPlan(null); }}>+ Add target security</button>
            <p className="tx-muted">Weights must total 100%. Existing holdings omitted from the targets are proposed for sale. Whole shares are rounded down; any residual is shown. Accounts with shorts or open orders cannot be rebalanced here.</p>
          </>}
          <button className="btn ghost" disabled={busy || !accountId} onClick={preview}>{busy ? 'Working…' : 'Generate proposal'}</button>
        </fieldset>}
        {selected && !detail && <p>Loading workflow…</p>}
        {detail && <><span className="tx-status">{label(detail.status)}</span><h3>{detail.title}</h3><dl className="tx-details"><dt>Requester</dt><dd>{detail.requester}</dd><dt>Requested</dt><dd>{time(detail.startedAt)}</dd><dt>Rationale</dt><dd>{String(detail.variables.reason)}</dd>{detail.variables.reviewer && <><dt>Reviewer</dt><dd>{String(detail.variables.reviewer)}</dd><dt>Review note</dt><dd>{String(detail.variables.reviewComment)}</dd></>}</dl></>}
        {shown && <div className="tx-proposal">
          <h3>{'Portfolio snapshot & order basket'}</h3><p className="tx-muted">{shown.accountName} · Snapshot {time(shown.snapshotAt)}</p>
          {<>
            <p>Stock portfolio value <strong>{money(shown.portfolioValue)}</strong></p>
            <div className="tx-table-wrap"><table><thead><tr><th>Security</th><th>Held</th><th>Current %</th><th>Target %</th><th>Target shares</th></tr></thead><tbody>{shown.allocations?.map(a => <tr key={a.security}><td>{a.security}</td><td>{a.currentQuantity}</td><td>{a.currentPercent.toFixed(1)}%</td><td>{a.targetPercent}%</td><td>{a.targetQuantity}</td></tr>)}</tbody></table></div>
            {!shown.legs?.length && <p className="tx-empty">No whole-share changes needed for these targets.</p>}
            {shown.legs?.map((l, i) => <div className="tx-leg" key={`${l.security}-${i}`}><strong>{i + 1}. {l.side} {l.security}</strong>{!selected && l.quantity > 10000 && <p className="tx-muted">{l.quantity.toLocaleString()} shares → {Math.floor(l.quantity / 10000)} × 10,000{l.quantity % 10000 ? ` + ${(l.quantity % 10000).toLocaleString()}` : ''} shares across {Math.ceil(l.quantity / 10000)} orders at the same limit price.</p>}<div className="tx-two"><label>Shares · {l.security}<input disabled={!editing} type="number" min="1" max="2147483647" step="1" value={l.quantity} onChange={e => changeLeg(i, { quantity: Number(e.target.value) })}/></label><label>Limit · {l.security}<input disabled={!editing} type="number" min="0.001" max="1000000" step="0.001" value={l.limitPrice} onChange={e => changeLeg(i, { limitPrice: Number(e.target.value) })}/></label></div>{!selected && <button className="text-link" disabled={busy} onClick={() => setPlan(p => p && ({ ...p, legs: p.legs?.filter((_, j) => j !== i) }))}>Remove this instruction</button>}</div>)}
            {!selected && overBudget && <div className="tx-error"><p>Buy cost exceeds sell proceeds at the entered limits by {money(limitBuys - limitSells)}.</p><button className="btn ghost" disabled={busy} onClick={balanceBuys}>Reduce buys to fit sell budget</button><p>This scales buy quantities down proportionally and rounds to whole shares. Sell quantities and all limits stay unchanged. Review the revised basket before sending.</p></div>}
            {!selected && <p className={orderCount(shown.legs) > 500 ? 'tx-error' : 'tx-muted'}>{orderCount(shown.legs)} orders will go to human review after splitting large instructions. Maximum 500 orders per review; each order is at most 10,000 shares. Totals are preserved.</p>}
            <p className="tx-muted">Basket at entered limits: sells {money(limitSells)} · buys {money(limitBuys)} · residual {money(limitSells - limitBuys)}. This budget uses the exact quantities and limits shown, so market quote movement alone does not invalidate it. Sells are submitted first, but buys do not wait for sell fills. TraderX may reprice downstream.</p>
          </>}
        </div>}
        {!selected && plan && ready && <div className="tx-form"><label>Request rationale<textarea maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} placeholder="Explain the proposal for the reviewer"/></label><button className="btn primary" disabled={busy || !reason.trim() || (true && overBudget)} onClick={submit}>Send plan for human approval</button><p className="tx-muted">No orders are submitted or cancelled until a different reviewer approves the final plan.</p></div>}
        {detail?.status === 'WAITING_APPROVAL' && <div className="tx-review"><h3>Human review</h3><label>Find a reviewer<input value={search} onChange={e => setSearch(e.target.value)}/></label><label>Reviewer<select value={reviewer} onChange={e => { setReviewer(e.target.value); setConfirm(false); }}><option value="">Choose another person</option>{people.filter(p => p.logonId !== detail.requester).map(p => <option key={p.logonId} value={p.logonId}>{p.fullName} · {p.logonId}</option>)}</select></label><label>Review note<textarea maxLength={1000} value={comment} onChange={e => setComment(e.target.value)}/></label><label className="tx-checkbox"><input type="checkbox" checked={confirm} onChange={e => setConfirm(e.target.checked)}/>I approve the exact {'basket instructions'} shown above for configured TraderX.</label><div className="tx-tabs"><button className="btn primary" disabled={busy || !validReview || !confirm} onClick={() => decide('APPROVE')}>Approve & execute plan</button><button className="btn ghost" disabled={busy || !validReview} onClick={() => decide('REJECT')}>Reject plan</button></div></div>}
        {detail && !['WAITING_APPROVAL', 'REJECTED'].includes(detail.status) && <div className="tx-proposal"><h3>Per-item results</h3><p className="tx-muted">Each receipt commits before the next action. Submitted orders may still be open or partially filled.</p>{(detail.plan.legs ?? []).map((_, i) => {
                const receipt = results.find(r => r.index === i);
                const live = detail.liveOrders.find(x => x.orderId === receipt?.orderId);
                return <div className="tx-leg" key={i}><strong>Item {i + 1} · {receipt ? label(receipt.outcome) : 'not attempted'}</strong>{receipt?.orderId && <p>TraderX order: {receipt.orderId}</p>}{live?.order && <p>Live status: <strong>{live.order.status}</strong> · {live.order.remainingQuantity} remaining shares</p>}{(receipt?.error || live?.error) && <p className="tx-error">{receipt?.error || live?.error}</p>}{receipt?.outcome === 'SKIPPED_CLOSED' && <p>No cancel request sent for an already-closed order, or it closed while cancellation was in flight. Existing fills remain.</p>}</div>;
            })}</div>}
        {detail?.status === 'REJECTED' && <p className="tx-success">Plan rejected. No TraderX writes were made.</p>}
        {detail?.status === 'BASKET_SUBMITTED' && <p className="tx-success">All approved basket instructions were submitted. Follow each order above; submission does not mean the portfolio has reached its targets.</p>}
        {detail?.status === 'REVIEWED' && <p className="tx-success">All Keep / Cancel decisions processed. Closed orders were skipped; fills were not reversed.</p>}
        {detail?.status === 'NEEDS_RECONCILIATION' && <div className="tx-error"><strong>Operations reconciliation required</strong><p>{String(detail.variables.failedStep)}: {String(detail.variables.executionError)}</p><p>Earlier successful actions remain in TraderX. UNKNOWN means a write may have succeeded without a response; BLOCKED means no write was attempted for that item. Later items were not attempted. Inspect TraderX before creating another plan.</p></div>}
        {detail?.incidents.map((x, i) => <p className="tx-error" key={i}>{x.message}</p>)}
        {detail && <details className="tx-audit"><summary>Workflow activity & receipts</summary><ol>{detail.activities.map((a, i) => <li key={i}>{a.name} · {a.endedAt ? 'finished' : 'active'}<small>{time(a.startedAt)}</small></li>)}</ol><pre>{JSON.stringify(detail.results, null, 2)}</pre><a className="text-link" href={`${import.meta.env.VITE_APP_BASE_URL || 'http://localhost:8080'}/fluxnova/app/monitoring/default/#/process-instance/${detail.id}`} target="_blank" rel="noreferrer">Open BPMN monitoring ↗</a></details>}
      </div>
      <aside className="tx-card tx-history"><div className="tx-heading"><h2>Workflow requests</h2><span>{requests.length}</span></div><p className="tx-muted">Select a saved plan to review it or follow execution. Updates automatically.</p>{!requests.length ? <p className="tx-empty">No plans yet. Generate your first proposal.</p> : <ul>{requests.map(r => <li key={r.id}><button className={selected === r.id ? 'is-selected' : ''} onClick={() => choose(r.id)}><span className="tx-status">{label(r.status)}</span><strong>{r.title}</strong><small>{r.requester} · {time(r.startedAt)}</small></button></li>)}</ul>}</aside>
    </div>
  </section>;
}
