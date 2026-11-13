import { useEffect, useState } from 'react';
import '../../TraderXWorkflows.css';
import { TraderXLink } from '../TraderXLink';
export type AdvancedKind = 'stale';
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
    const kind = 'stale' as const;
    const [options, setOptions] = useState<Options | null>(null);
    const [people, setPeople] = useState<Person[]>([]);
    const [search, setSearch] = useState('user');
    const [requester, setRequester] = useState('');
    const [accountId, setAccountId] = useState('');
    const [targets, ] = useState<Target[]>([{ security: '', percent: 100 }]);
    const [, setSuggestions] = useState<Suggestions | null>(null);
    const [chosenStrategy, setChosenStrategy] = useState('');
    const [ageMinutes, setAgeMinutes] = useState(60);
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
    function changeOrder(index: number, update: Partial<OrderRow>) { setPlan(p => p && ({ ...p, orders: p.orders?.map((o, i) => i === index ? { ...o, ...update } : o) })); }
    const shown = detail?.plan ?? plan;
    const editing = !selected && !busy;
    const budgetLegs = detail?.plan.legs ?? plan?.legs ?? [];
    const limitBuys = budgetLegs.filter(l => l.side === 'Buy').reduce((n, l) => n + l.quantity * l.limitPrice, 0);
    const limitSells = budgetLegs.filter(l => l.side === 'Sell').reduce((n, l) => n + l.quantity * l.limitPrice, 0);
    const overBudget = limitBuys > limitSells + 0.01;
    const ready = !!plan?.orders?.some(o => o.include !== false);
    const validReview = reviewer && reviewer !== detail?.requester && comment.trim();
    const results = detail?.results ?? [];
    return <section className="tx-workflows tx-advanced enter">
    <div className="tx-heading"><button className="panel-back" onClick={onBack}>Back to catalog</button><TraderXLink /></div>
    <p className="use-case-label">TRADERX · HUMAN-GOVERNED OPERATIONS</p><h1>{titles[kind]}</h1>
    <p className="lede">{'Find aged open orders, decide which to keep or cancel, and have another person review the plan before it changes TraderX.'}</p>
    <div className="tx-demo-note">TraderX simulation · Directory selectors are demo identities, not authentication. {'Age is measured from order creation. Cancellation affects only what remains open; existing fills are never reversed.'}</div>
    <ol className="tx-flow">{(['Scan aged orders', 'Keep / Cancel plan', 'Human approval', 'Recheck & act']).map((s, i) => <li key={s}><span>{i + 1}</span>{s}</li>)}</ol>
    {error && <div className="tx-error" role="alert">{error}</div>}{syncError && <div className="tx-error" role="status">Reconnecting: {syncError}. Retrying automatically.</div>}
    <div className="tx-columns">
      <div className="tx-card">
        <div className="tx-heading"><h2>{selected ? 'Approved scope & progress' : 'Find orders to review'}</h2>{selected && <button className="btn ghost" onClick={() => { choose(null); setPlan(null); }}>New request</button>}</div>
        {!selected && <fieldset className="tx-form tx-inputs" disabled={busy}>
          <label>Find a directory person<input value={search} onChange={e => setSearch(e.target.value)} placeholder="Name or logon ID"/></label>{directoryError && <p role="status">{directoryError}</p>}
          <label>Requester<select value={requester} onChange={e => { setRequester(e.target.value); setAccountId(''); setPlan(null); resetAi(); }}><option value="">Choose a person</option>{people.map(p => <option key={p.logonId} value={p.logonId}>{p.fullName} · {p.logonId}</option>)}</select></label>
          <label>TraderX account<select disabled={!requester || !options} value={accountId} onChange={e => { setAccountId(e.target.value); setPlan(null); resetAi(); }}><option value="">Choose a linked account</option>{accounts.map(a => <option key={a.id} value={a.id}>{a.displayName} · #{a.id}</option>)}</select></label>
          {requester && options && !accounts.length && <p className="tx-muted">No linked accounts for this person. Open an account or select another requester.</p>}
          {<label>Minimum order age (minutes)<input type="number" min="0" max="525600" value={ageMinutes} onChange={e => { setAgeMinutes(Number(e.target.value)); setPlan(null); }}/><small>Use 0 to inspect all currently open orders.</small></label>}
          <button className="btn ghost" disabled={busy || !accountId} onClick={preview}>{busy ? 'Working…' : 'Scan open orders'}</button>
        </fieldset>}
        {selected && !detail && <p>Loading workflow…</p>}
        {detail && <><span className="tx-status">{label(detail.status)}</span><h3>{detail.title}</h3><dl className="tx-details"><dt>Requester</dt><dd>{detail.requester}</dd><dt>Requested</dt><dd>{time(detail.startedAt)}</dd><dt>Rationale</dt><dd>{String(detail.variables.reason)}</dd>{detail.variables.reviewer && <><dt>Reviewer</dt><dd>{String(detail.variables.reviewer)}</dd><dt>Review note</dt><dd>{String(detail.variables.reviewComment)}</dd></>}</dl></>}
        {shown && <div className="tx-proposal">
          <h3>{'Order decisions'}</h3><p className="tx-muted">{shown.accountName} · Snapshot {time(shown.snapshotAt)}</p>
          {<>
            {!shown.orders?.length && <p className="tx-empty">No open orders meet this age threshold.</p>}
            {shown.orders?.map((r, i) => <div className="tx-leg" key={r.order.orderId}><div className="tx-heading"><strong>{r.order.side} {r.order.security} · {r.order.orderId}</strong><span className="tx-status">{r.order.status}</span></div><p>{r.order.remainingQuantity} remaining of {r.order.quantity} shares · {r.ageMinutes} minutes old</p><p className="tx-muted">Limit {money(r.order.limitPrice)} · current quote {money(r.quote?.price)}{r.quoteError ? ` · ${r.quoteError}` : ''}</p>{!selected && <label className="tx-checkbox"><input type="checkbox" checked={r.include !== false} disabled={busy} onChange={e => changeOrder(i, { include: e.target.checked })}/>Include in this review</label>}<label>Decision · {r.order.orderId}<select disabled={!editing || r.include === false} value={r.action} onChange={e => changeOrder(i, { action: e.target.value as 'KEEP' | 'CANCEL' })}><option value="KEEP">Keep open</option><option value="CANCEL">Cancel remaining quantity</option></select></label></div>)}
          </>}
        </div>}
        {!selected && plan && ready && <div className="tx-form"><label>Request rationale<textarea maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} placeholder="Explain the proposal for the reviewer"/></label><button className="btn primary" disabled={busy || !reason.trim() || (false && overBudget)} onClick={submit}>Send plan for human approval</button><p className="tx-muted">No orders are submitted or cancelled until a different reviewer approves the final plan.</p></div>}
        {detail?.status === 'WAITING_APPROVAL' && <div className="tx-review"><h3>Human review</h3><label>Find a reviewer<input value={search} onChange={e => setSearch(e.target.value)}/></label><label>Reviewer<select value={reviewer} onChange={e => { setReviewer(e.target.value); setConfirm(false); }}><option value="">Choose another person</option>{people.filter(p => p.logonId !== detail.requester).map(p => <option key={p.logonId} value={p.logonId}>{p.fullName} · {p.logonId}</option>)}</select></label><label>Review note<textarea maxLength={1000} value={comment} onChange={e => setComment(e.target.value)}/></label><label className="tx-checkbox"><input type="checkbox" checked={confirm} onChange={e => setConfirm(e.target.checked)}/>I approve the exact {'Keep / Cancel decisions'} shown above for configured TraderX.</label><div className="tx-tabs"><button className="btn primary" disabled={busy || !validReview || !confirm} onClick={() => decide('APPROVE')}>Approve & execute plan</button><button className="btn ghost" disabled={busy || !validReview} onClick={() => decide('REJECT')}>Reject plan</button></div></div>}
        {detail && !['WAITING_APPROVAL', 'REJECTED'].includes(detail.status) && <div className="tx-proposal"><h3>Per-item results</h3><p className="tx-muted">Each receipt commits before the next action. Submitted orders may still be open or partially filled.</p>{(detail.plan.orders ?? []).map((_, i) => {
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
