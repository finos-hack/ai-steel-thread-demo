import { useEffect, useState } from 'react';
import '../../TraderXWorkflows.css';
import { TraderXLink } from '../TraderXLink';
export type TraderXKind = 'account';
type Person = {
    logonId: string;
    fullName: string;
};
type Account = {
    id: number;
    displayName: string;
};
type Options = {
    accounts: Account[];
    memberships: {
        accountId: number;
        username: string;
    }[];
    securities: {
        ticker: string;
        companyName: string;
    }[];
};
type Summary = {
    id: string;
    title: string;
    kind: TraderXKind;
    requester: string;
    status: string;
    startedAt: string;
    endedAt: string | null;
};
type Detail = Summary & {
    archived?: boolean;
    variables: Record<string, string | number | boolean>;
    activities: {
        name: string;
        type: string;
        startedAt: string;
        endedAt: string | null;
    }[];
    incidents: {
        message: string;
    }[];
    liveOrder?: {
        status: string;
        lastExecutionPrice: number | null;
        remainingQuantity: number;
        orderId: string;
    };
    liveStatusError?: string;
};
type Quote = {
    price: number;
    asOf: string;
};
const titles = { trade: 'Human-approved trade', account: 'Account opening' };
const base = '/api/demo/traderx';
async function api<T>(path: string, body?: unknown): Promise<T> {
    const res = await fetch(base + path, body === undefined ? undefined : {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok)
        throw new Error(data.detail || data.message || `Request failed (${res.status})`);
    return data as T;
}
const message = (e: unknown) => e instanceof Error ? e.message : 'Request failed';
const time = (s: string) => new Date(s).toLocaleString();
const money = (n: number) => n.toLocaleString(undefined, { style: 'currency', currency: 'USD' });
const stateLabel = (s: string) => s.replaceAll('_', ' ').toLowerCase();
export function WorkflowPage({ onBack }: {
    onBack: () => void;
}) {
    const kind = 'account' as const;
    const [options, setOptions] = useState<Options | null>(null);
    const [people, setPeople] = useState<Person[]>([]);
    const [search, setSearch] = useState('user');
    const [requester, setRequester] = useState('');
    const [accountId, setAccountId] = useState('');
    const [security, ] = useState('');
    const [side, ] = useState('Buy');
    const [quantity, ] = useState('1');
    const [limitPrice, ] = useState('');
    const [displayName, setDisplayName] = useState('');
    const [owner, setOwner] = useState('');
    const [reason, setReason] = useState('');
    const [, setQuote] = useState<Quote | null>(null);
    const [error, setError] = useState('');
    const [syncError, setSyncError] = useState('');
    const [connectionError, setConnectionError] = useState('');
    const [peopleError, setPeopleError] = useState('');
    const [busy, setBusy] = useState(false);
    const [list, setList] = useState<Summary[]>([]);
    const [selected, setSelected] = useState<string | null>(null);
    const [detail, setDetail] = useState<Detail | null>(null);
    const [reviewer, setReviewer] = useState('');
    const [comment, setComment] = useState('');
    const [confirmed, setConfirmed] = useState(false);
    function reloadOptions() {
        api<Options>('/options').then(x => { setOptions(x); setConnectionError(''); })
            .catch(e => { setConnectionError(message(e)); setOptions(null); });
    }
    useEffect(reloadOptions, []);
    useEffect(() => {
        let active = true;
        const timer = window.setTimeout(() => {
            if (!search.trim())
                return;
            api<Person[]>(`/people?search=${encodeURIComponent(search)}`).then(p => {
                if (active) {
                    setPeople(prev => [...new Map([...prev, ...p].map(x => [x.logonId, x])).values()]);
                    setPeopleError(p.length ? '' : 'No matching people found.');
                }
            }).catch(e => {
                if (active)
                    setPeopleError(message(e));
            });
        }, 250);
        return () => { active = false; window.clearTimeout(timer); };
    }, [search]);
    useEffect(() => {
        let active = true;
        setQuote(null);
        if (security && false)
            api<Quote>(`/quotes/${encodeURIComponent(security)}`)
                .then(q => {
                if (active)
                    setQuote(q);
            }).catch(() => {
                if (active)
                    setQuote(null);
            });
        return () => { active = false; };
    }, [security, kind]);
    useEffect(() => {
        let active = true;
        const load = () => api<Summary[]>(`/workflows/${kind}`)
            .then(x => {
            if (active) {
                setList(x);
                setSyncError('');
            }
        }).catch(e => {
            if (active)
                setSyncError(message(e));
        });
        void load();
        const timer = window.setInterval(load, 4000);
        return () => { active = false; window.clearInterval(timer); };
    }, [kind]);
    useEffect(() => {
        let active = true;
        setDetail(null);
        setReviewer('');
        setComment('');
        setConfirmed(false);
        if (!selected)
            return;
        const load = () => api<Detail>(`/instances/${selected}`).then(x => {
            if (active) {
                setDetail(x);
                setSyncError('');
            }
        })
            .catch(e => {
            if (active)
                setSyncError(message(e));
        });
        void load();
        const timer = window.setInterval(load, 3000);
        return () => { active = false; window.clearInterval(timer); };
    }, [selected]);
    const v = detail?.variables;
    async function start(e: React.FormEvent) {
        e.preventDefault();
        setBusy(true);
        setError('');
        try {
            const x = await api<Detail>(`/workflows/${kind}`, {
                requester, reason, accountId: accountId ? Number(accountId) : null,
                security, side, quantity: Number(quantity), displayName, owner, limitPrice: limitPrice ? Number(limitPrice) : null,
            });
            setSelected(x.id);
            setDetail(x);
            setList(prev => [x, ...prev]);
            setReason('');
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
            const x = await api<Detail>(`/instances/${detail.id}/decision`, { reviewer, decision, comment });
            setDetail(x);
            setList(prev => prev.map(p => p.id === x.id ? x : p));
            setConfirmed(false);
        }
        catch (e) {
            setError(message(e));
        }
        finally {
            setBusy(false);
        }
    }
    function newRequest() { setSelected(null); setError(''); reloadOptions(); }
    const personOptions = people.map(p => <option key={p.logonId} value={p.logonId}>{p.fullName} · {p.logonId}</option>);
    const validReview = !!reviewer && reviewer !== detail?.requester && !!comment.trim();
    return <section className="tx-workflows enter">
    <div className="tx-heading"><button className="panel-back" onClick={onBack}>Back to catalog</button>
      <TraderXLink /></div>
    <p className="use-case-label">CONNECTED WORKFLOWS · TRADERX</p>
    <h1>{titles[kind]}</h1>
    <p className="lede">{'Turn an account request into a usable TraderX account with a directory-linked owner and a recorded human decision.'}</p>
    <div className="tx-demo-note">TraderX simulation · People are selected from TraderX’s demo directory. Identity selection is not authentication. These workflows call the running TraderX APIs and do not use an LLM.</div>
    <ol className="tx-flow" aria-label="Workflow steps">
      {(['Directory & request', 'Human approval', 'Create account', 'Link owner']).map((s, i) => <li key={s}><span>{i + 1}</span>{s}</li>)}
    </ol>
    {syncError && <div className="tx-error" role="status">Waiting to reconnect: {syncError}. Retrying automatically.</div>}
    {error && <div className="tx-error" role="alert">{error}</div>}
    {connectionError && <div className="tx-error" role="alert">TraderX connection: {connectionError} <button className="text-link" onClick={reloadOptions}>Retry connection</button></div>}
    <div className="tx-columns">
      <div className="tx-card">
        <div className="tx-heading"><h2>{selected ? 'Request & decision' : 'New request'}</h2>{selected && <button className="btn ghost" onClick={newRequest}>New request</button>}</div>
        {!selected ? <form onSubmit={start} className="tx-form">
          <label>Find a directory person<input value={search} onChange={e => setSearch(e.target.value)} placeholder="Name or logon ID"/></label>
          {peopleError && <p role="status">{peopleError}</p>}
          <label>Requester<select required value={requester} onChange={e => {
                setRequester(e.target.value);
                setAccountId('');
                if (!owner)
                    setOwner(e.target.value);
            }}><option value="">Choose a person</option>{personOptions}</select></label>
          {<>
            <label>Account name<input required maxLength={100} value={displayName} onChange={e => setDisplayName(e.target.value)} placeholder="e.g. Demo · Research Portfolio"/></label>
            <label>Account owner<select required value={owner} onChange={e => setOwner(e.target.value)}><option value="">Choose a directory person</option>{personOptions}</select></label>
            <p className="tx-muted">Creates a new account and links this person using TraderX’s account-user API. No KYC, funding, or legal onboarding is implied.</p>
          </>}
          <label>Request rationale<textarea required maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} placeholder="Explain the purpose for the reviewer"/></label>
          <button type="submit" className="btn primary" disabled={busy || !options}>{busy ? 'Submitting…' : 'Send for human approval'}</button>
          <p className="tx-muted">Submitting this request does not yet change TraderX.</p>
        </form> : !detail ? <p>Loading request…</p> : <>
          {detail.archived && <p className="tx-muted">Saved result from the initial demo run. Live BPMN history now persists across restarts.</p>}
          <span className={`tx-status tx-${detail.status.toLowerCase()}`}>{stateLabel(detail.status)}</span>
          <h3>{detail.title}</h3>
          <dl className="tx-details">
            <dt>Requester</dt><dd>{detail.requester}</dd><dt>Requested</dt><dd>{time(detail.startedAt)}</dd>
            {<><dt>New account</dt><dd>{String(v?.displayName)}</dd><dt>Owner</dt><dd>{String(v?.owner)}</dd></>}
            <dt>Rationale</dt><dd>{String(v?.reason)}</dd>
            {v?.reviewer && <><dt>Reviewer</dt><dd>{String(v.reviewer)}</dd><dt>Decision note</dt><dd>{String(v.reviewComment)}</dd><dt>Reviewed</dt><dd>{time(String(v.reviewedAt))}</dd></>}
            {v?.createdAccountId && <><dt>Created account ID</dt><dd>{String(v.createdAccountId)}</dd></>}
          </dl>
          {detail.status === 'WAITING_APPROVAL' && <div className="tx-review">
            <h3>Human review</h3><p>Review the fixed request above. A different directory person must record the decision.</p>
            <label>Reviewer<select value={reviewer} onChange={e => { setReviewer(e.target.value); setConfirmed(false); }}><option value="">Choose a reviewer</option>{people.filter(p => p.logonId !== detail.requester).map(p => <option key={p.logonId} value={p.logonId}>{p.fullName} · {p.logonId}</option>)}</select></label>
            <label>Review note<textarea maxLength={1000} value={comment} onChange={e => setComment(e.target.value)} placeholder="Why approve or reject this request?"/></label>
            <label className="tx-checkbox"><input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)}/>{'I approve creating this account and linking its owner in configured TraderX.'}</label>
            <div className="tx-tabs"><button className="btn primary" disabled={busy || !validReview || !confirmed} onClick={() => decide('APPROVE')}>Approve & {'open account'}</button>
              <button className="btn ghost" disabled={busy || !validReview} onClick={() => decide('REJECT')}>Reject request</button></div>
          </div>}
          {detail.status === 'SUBMITTED' && <div className="tx-success"><strong>TraderX accepted the order submission.</strong><p>{detail.liveOrder ? `Matcher status: ${detail.liveOrder.status} · ${detail.liveOrder.remainingQuantity} shares remaining${detail.liveOrder.lastExecutionPrice != null ? ` · matcher fill price ${money(detail.liveOrder.lastExecutionPrice)}` : ''}` : 'Waiting for TraderX order status. Submission is not a fill confirmation.'}</p></div>}
          {detail.status === 'OPENED' && <div className="tx-success"><strong>Account #{String(v?.createdAccountId)} created and owner linked.</strong><p>Start a trade request as {String(v?.owner)} to find it in the account dropdown.</p></div>}
          {detail.status === 'REJECTED' && <p className="tx-muted">Rejected. No TraderX write was made.</p>}
          {detail.status === 'NEEDS_RECONCILIATION' && <div className="tx-error"><strong>Operations review required · {String(v?.failedStep)}</strong><p>{String(v?.executionError)}</p><p>A write may already have succeeded. Check TraderX using the account/order reference above before taking any further action. No automatic resend will occur. The BPMN operations task remains open.</p></div>}
          {detail.liveStatusError && <p role="status">{detail.liveStatusError}</p>}
          {detail.incidents.map((x, i) => <div className="tx-error" key={i}>{x.message} · Check the BPMN incident before retrying any external write.</div>)}
          <details className="tx-audit"><summary>Workflow activity & API receipt</summary><ol>{detail.activities.map((a, i) => <li key={i}><strong>{a.name || a.type}</strong> · {a.endedAt ? 'finished' : 'active'}<small>{time(a.startedAt)}</small></li>)}</ol>{v?.externalResponse && <pre>{String(v.externalResponse)}</pre>}<p>Process: {detail.id}</p>{!detail.archived && <a className="text-link" target="_blank" rel="noreferrer" href={`${import.meta.env.VITE_APP_BASE_URL || 'http://localhost:8080'}/fluxnova/app/monitoring/default/#/process-instance/${detail.id}`}>Open BPMN monitoring ↗</a>}</details>
        </>}
      </div>
      <aside className="tx-card tx-history"><div className="tx-heading"><h2>Workflow requests</h2><span>{list.length}</span></div><p className="tx-muted">Select a request to review it or follow its outcome. Updates automatically.</p>
        {list.length === 0 ? <div className="tx-empty">No requests yet.<br />Create the first {'account request'}.</div> : <ul>{list.map(x => <li key={x.id}><button className={selected === x.id ? 'is-selected' : ''} onClick={() => { setSelected(x.id); setError(''); }}><span className="tx-status">{stateLabel(x.status)}</span><strong>{x.title}</strong><small>{x.requester} · {time(x.startedAt)}</small></button></li>)}</ul>}
      </aside>
    </div>
  </section>;
}
