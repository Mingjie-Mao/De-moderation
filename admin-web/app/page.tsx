'use client';

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { apiFetch } from './api-request';

const API = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
type CaseStatus = 'AWAITING_REVIEW' | 'RESOLVED';
type FinalAction = 'NONE' | 'HIDE' | 'DELETE' | 'BAN';
type Tab = 'queue' | 'resolved' | 'appeals';
type ModerationCase = { id:string; targetType:string; targetId:string; status:CaseStatus; reportCount:number; engine?:string; recommendedDecision?:string; confidence?:number; rationale?:string; ruleCodes?:string[]; finalAction?:FinalAction; assignedTo?:string; reviewDueAt?:string; createdAt:string; overdue?:boolean };
type ReportedContent = { title?:string; body?:string; authorId:string; mediaUrl?:string };
type CaseDetail = { moderationCase:ModerationCase; content?:ReportedContent; currentContent?:ReportedContent; contentChanged:boolean; auditTrail:Array<{ action:string; actorId?:string; payload?:Record<string,unknown>; at:string }> };
type Appeal = { id:string; caseId:string; appellantId:string; reason:string; status:string; response?:string; createdAt:string };
type Session = { accessToken:string; refreshToken:string; userId:string; username:string };
type EngineStatus = { activeEngine:string; fallbackEngine:string; llmActive:boolean };
type EvidenceStrength = 'SETTLED' | 'LEANING' | 'OPEN';
type Brief = { outcome:'COMPLETE'|'PARTIAL'|'INCONCLUSIVE'; summary:string; recommendation?:FinalAction; evidenceStrength?:EvidenceStrength; counterEvidence?:string; citedCaseIds:string[]; promptVersion:string; producedAt:string };

const ACTION_LABEL:Record<FinalAction,string> = { NONE:'不处理', HIDE:'隐藏', DELETE:'删除', BAN:'封禁作者' };
// Deliberately not percentages. The band replaced a confidence number precisely
// because nothing calibrates it, and rendering it as "85%" would put the false
// precision straight back.
const EVIDENCE_LABEL:Record<EvidenceStrength,string> = { SETTLED:'证据明确', LEANING:'有倾向', OPEN:'两可' };

function elapsed(value:string) {
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000));
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  return hours < 24 ? `${hours} 小时` : `${Math.floor(hours / 24)} 天`;
}
function errorText(error:unknown) { return error instanceof Error ? error.message : '请求失败，请稍后重试。'; }

function EvidenceImage({path,read}:{path:string;read:(path:string,signal:AbortSignal)=>Promise<Blob>}) {
  const [imageUrl,setImageUrl] = useState<string|null>(null);
  const [failed,setFailed] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    let objectUrl:string|null = null;
    void read(path,controller.signal)
      .then(blob => {
        if (controller.signal.aborted) return;
        objectUrl = URL.createObjectURL(blob);
        setImageUrl(objectUrl);
      })
      .catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path,read]);
  if (failed) return <p className="brief-empty">附件暂不可读，请检查媒体存储。</p>;
  if (!imageUrl) return <p className="brief-empty">正在读取附件…</p>;
  return <img className="evidence" src={imageUrl} alt="举报内容附件" />; // eslint-disable-line @next/next/no-img-element
}

export default function Home() {
  // Restored after the first paint, not during it. Reading sessionStorage in the
  // initial state made the server render a logged-out page and the browser render
  // a logged-in one from the same code, which is a hydration mismatch: React warns
  // in development and, in production, silently keeps whichever tree it built
  // first. The cost is one frame showing the login form to somebody already
  // signed in, which `restoring` covers.
  const [session, setSession] = useState<Session|null>(null);
  const [restoring, setRestoring] = useState(true);
  const sessionRef = useRef<Session|null>(null);
  const sessionGeneration = useRef(0);
  const rotation = useRef<Promise<Session|null>|null>(null);

  useEffect(() => {
    try {
      const saved = sessionStorage.getItem('campusguard.admin.session');
      // react-hooks/set-state-in-effect warns about exactly this line, and the
      // comment above is the answer: sessionStorage does not exist on the server,
      // so reading it anywhere but after mount is the hydration mismatch this
      // effect was written to avoid. Reading an external system on mount is the
      // case the rule itself lists as legitimate; it cannot tell that from here.
      if (saved) {
        const parsed = JSON.parse(saved) as Partial<Session>;
        if (parsed.accessToken && parsed.userId && parsed.username) {
          const restored = {...parsed,refreshToken:parsed.refreshToken||''} as Session;
          sessionRef.current = restored;
          // eslint-disable-next-line react-hooks/set-state-in-effect
          setSession(restored);
        }
      }
    } catch { sessionStorage.removeItem('campusguard.admin.session'); }
    finally { setRestoring(false); }
  }, []);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [tab, setTab] = useState<Tab>('queue');
  const [page, setPage] = useState(0);
  const [cases, setCases] = useState<ModerationCase[]>([]);
  const [appeals, setAppeals] = useState<Appeal[]>([]);
  const [detail, setDetail] = useState<CaseDetail|null>(null);
  const [engine, setEngine] = useState<EngineStatus|null>(null);
  const [note, setNote] = useState('');
  const [brief, setBrief] = useState<Brief|null>(null);
  const [briefBusy, setBriefBusy] = useState(false);
  const [briefOff, setBriefOff] = useState('');
  // The cases a reviewer followed a citation away from, most recent last. A
  // brief's evidence is only worth something if it can be read, and reading it
  // should not lose the case it was evidence for.
  const [trail, setTrail] = useState<string[]>([]);
  const [appealResponses, setAppealResponses] = useState<Record<string,string>>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const logout = useCallback(() => {
    sessionGeneration.current += 1;
    rotation.current = null;
    sessionRef.current = null;
    sessionStorage.removeItem('campusguard.admin.session');
    setSession(null); setDetail(null); setBrief(null); setTrail([]); setCases([]); setAppeals([]);
  }, []);

  const rotateSession = useCallback(():Promise<Session|null> => {
    if (rotation.current) return rotation.current;
    const current = sessionRef.current;
    if (!current?.refreshToken) return Promise.resolve(null);
    const generation = sessionGeneration.current;
    const pending = (async () => {
      try {
        const response = await apiFetch(`${API}/api/auth/refresh`, {
          method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json'},
          body:JSON.stringify({refreshToken:current.refreshToken}),
        });
        if (response.status === 400 || response.status === 401 || response.status === 403) return null;
        if (!response.ok) throw new Error('暂时无法刷新会话，请稍后重试。');
        const body = await response.json() as Partial<Session>;
        if (!body.accessToken || !body.refreshToken || !body.userId || !body.username) return null;
        if (generation !== sessionGeneration.current) return null;
        const next = body as Session;
        sessionRef.current = next;
        sessionStorage.setItem('campusguard.admin.session',JSON.stringify(next));
        setSession(next);
        return next;
      } catch (error) {
        // A network outage must not discard a still-valid refresh token.
        throw error instanceof Error ? error : new Error('暂时无法刷新会话，请稍后重试。');
      }
    })();
    rotation.current = pending;
    const clearRotation = () => { if (rotation.current === pending) rotation.current = null; };
    void pending.then(clearRotation, clearRotation);
    return pending;
  }, []);

  const authorizedFetch = useCallback(async (path:string, init?:RequestInit):Promise<Response> => {
    const send = (token:string|null) => {
      const headers = new Headers(init?.headers);
      if (!headers.has('Accept')) headers.set('Accept','application/json');
      if (init?.body && !headers.has('Content-Type')) headers.set('Content-Type','application/json');
      if (token) headers.set('Authorization',`Bearer ${token}`);
      return apiFetch(`${API}${path}`,{...init,headers});
    };
    const generation = sessionGeneration.current;
    const originalToken = sessionRef.current?.accessToken||null;
    let response = await send(originalToken);
    if (response.status !== 401 || !originalToken) return response;
    if (generation !== sessionGeneration.current) throw new Error('会话已更换，请重试。');
    // A parallel request may already have rotated the one-time refresh token.
    const current = sessionRef.current;
    const next = current?.accessToken !== originalToken ? current : await rotateSession();
    if (!next) {
      logout();
      throw new Error('会话已过期，请重新登录。');
    }
    response = await send(next.accessToken);
    if (response.status === 401) {
      if (generation === sessionGeneration.current) logout();
      throw new Error('会话已失效，请重新登录。');
    }
    return response;
  },[logout,rotateSession]);

  const request = useCallback(async <T,>(path:string, init?:RequestInit):Promise<T> => {
    const response = await authorizedFetch(path,init);
    if (!response.ok) {
      const problem = await response.json().catch(() => ({})) as {detail?:string;title?:string};
      throw new Error(problem.detail || problem.title || `HTTP ${response.status}`);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }, [authorizedFetch]);

  const readEvidence = useCallback(async (path:string,signal:AbortSignal) => {
    const response = await authorizedFetch(path,{signal});
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.blob();
  },[authorizedFetch]);

  const refresh = useCallback(async () => {
    if (!session) return;
    setBusy(true); setMessage('');
    try {
      if (tab === 'appeals') setAppeals(await request<Appeal[]>(`/api/admin/appeals?status=PENDING&size=100&page=${page}`));
      else {
        const status:CaseStatus = tab === 'queue' ? 'AWAITING_REVIEW' : 'RESOLVED';
        const rows = await request<ModerationCase[]>(`/api/admin/moderation-cases?status=${status}&size=100&page=${page}`);
        const checkedAt = Date.now();
        setCases(rows.map(item => ({...item, overdue:!!item.reviewDueAt && new Date(item.reviewDueAt).getTime() < checkedAt})));
      }
      setEngine(await request<EngineStatus>('/api/moderation/status'));
    } catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  }, [request, session, tab, page]);
  useEffect(() => {
    const timer = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(timer);
  }, [refresh]);

  const login = async (event:FormEvent) => {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      const response = await apiFetch(`${API}/api/auth/login`, {method:'POST', headers:{'Content-Type':'application/json',Accept:'application/json'}, body:JSON.stringify({username,password})});
      const body = await response.json().catch(() => ({})) as {detail?:string;accessToken?:string;refreshToken?:string;userId?:string;username?:string};
      if (!response.ok) throw new Error(body.detail || '用户名或密码不正确。');
      if (!body.accessToken || !body.refreshToken || !body.userId || !body.username) throw new Error('登录响应不完整，请检查后端版本。');
      const next = {accessToken:body.accessToken,refreshToken:body.refreshToken,userId:body.userId,username:body.username};
      sessionGeneration.current += 1;
      rotation.current = null;
      sessionRef.current = next;
      sessionStorage.setItem('campusguard.admin.session', JSON.stringify(next));
      setSession(next); setPassword('');
    } catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };

  // `from` is the trail to keep once this case is showing: empty when it was
  // opened from a list, the path so far when it was reached through a citation.
  // Swapped in only after the case has loaded, so a citation that fails to open
  // leaves the reviewer on the case they were reading, brief and all.
  const openCase = async (id:string, from:string[] = []) => {
    setBusy(true); setMessage('');
    try {
      const next = await request<CaseDetail>(`/api/admin/moderation-cases/${id}`);
      // Fetched, not run. Opening a case shows a brief somebody already paid for
      // and never starts one on its own.
      const existing = await request<Brief|undefined>(`/api/admin/moderation-cases/${id}/investigation`);
      setDetail(next); setNote(''); setBrief(existing ?? null); setTrail(from);
    }
    catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };

  const investigate = async (force:boolean) => {
    if (!detail) return;
    setBriefBusy(true); setMessage('');
    try {
      setBrief(await request<Brief>(`/api/admin/moderation-cases/${detail.moderationCase.id}/investigate?force=${force}`, {method:'POST'}));
    } catch (error) {
      // A switched-off assistant is a configuration, not a fault. It greys the
      // button out with a reason rather than showing an error the reviewer
      // would reasonably try to act on.
      const text = errorText(error);
      if (text.includes('not enabled')) setBriefOff(text); else setMessage(text);
    }
    finally { setBriefBusy(false); }
  };

  const mutateCase = async (path:string, init:RequestInit) => {
    setBusy(true); setMessage('');
    try { setDetail(await request<CaseDetail>(path, init)); await refresh(); }
    catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };
  const decide = (action:FinalAction) => detail && mutateCase(`/api/admin/moderation-cases/${detail.moderationCase.id}/decision`, {method:'POST',body:JSON.stringify({action,note})});

  const decideAppeal = async (appeal:Appeal, decision:'UPHOLD'|'OVERTURN') => {
    setBusy(true); setMessage('');
    try {
      await request(`/api/admin/appeals/${appeal.id}/decision`, {method:'POST',body:JSON.stringify({decision,response:appealResponses[appeal.id]||''})});
      setAppealResponses(values => { const next={...values}; delete next[appeal.id]; return next; }); await refresh();
    } catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };

  const metrics = useMemo(() => ({
    overdue:cases.filter(item => item.overdue).length,
    assigned:cases.filter(item => item.assignedTo).length,
  }), [cases]);

  if (restoring) return <main className="login-shell"><div className="login-card"><div className="brand-mark large">D</div><p className="eyebrow">CampusGuard</p></div></main>;
  if (!session) return <main className="login-shell"><form className="login-card" onSubmit={login}>
    <div className="brand-mark large">D</div><p className="eyebrow">CampusGuard / 安全入口</p><h1>管理员登录</h1>
    <p className="login-copy">使用后端创建的管理员账号进入审核工作台。令牌仅保存在当前浏览器会话。</p>
    <label>用户名<input autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)} required /></label>
    <label>密码<input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} required /></label>
    {message && <p className="alert">{message}</p>}
    <button className="primary login-button" disabled={busy}>{busy?'正在验证…':'登录工作台'}</button><Link className="text-link" href="/reset-password">忘记密码？</Link><small>API：{API}</small>
  </form></main>;

  return <main className="app-shell">
    <aside className="sidebar"><div className="brand-mark">D</div><nav aria-label="主导航">
      <button className={`nav-item ${tab==='queue'?'active':''}`} aria-current={tab==='queue'?'page':undefined} onClick={()=>{setTab('queue');setPage(0);setDetail(null);}}>审核工作台</button>
      <button className={`nav-item ${tab==='resolved'?'active':''}`} aria-current={tab==='resolved'?'page':undefined} onClick={()=>{setTab('resolved');setPage(0);setDetail(null);}}>裁决记录</button>
      <button className={`nav-item ${tab==='appeals'?'active':''}`} aria-current={tab==='appeals'?'page':undefined} onClick={()=>{setTab('appeals');setPage(0);setDetail(null);}}>申诉中心</button>
    </nav><button className="nav-item sidebar-logout" onClick={logout}>退出登录</button></aside>
    <section className="workspace">
      <header className="topbar"><div><p className="eyebrow">CampusGuard / De-Moderation</p><h1>{tab==='queue'?'审核工作台':tab==='resolved'?'裁决记录':'申诉中心'}</h1></div>
        <div className={`engine-pill ${engine?.llmActive?'':'fallback'}`}><span />{engine?.llmActive?`${engine.activeEngine} 正常运行`:`当前使用 ${engine?.activeEngine||'规则引擎'}`}</div></header>
      {message && <p className="alert page-alert">{message}</p>}
      <div className="metrics">
        <article><strong>{String(tab==='appeals'?appeals.length:cases.length).padStart(2,'0')}</strong><span>{tab==='appeals'?'本页待处理申诉':'当前列表'}</span><small>来自服务器实时数据</small></article>
        <article><strong>{String(metrics.overdue).padStart(2,'0')}</strong><span>已超过 SLA</span><small>按 reviewDueAt 计算</small></article>
        <article><strong>{String(metrics.assigned).padStart(2,'0')}</strong><span>已被认领</span><small>避免多人重复裁决</small></article>
        <article><strong>{engine?.llmActive?'AI':'规则'}</strong><span>当前审核引擎</span><small>{engine?.activeEngine||'正在读取状态'}</small></article>
      </div>
      <div className={`content-grid ${detail?'with-detail':''}`}>
        <section className="queue-panel"><div className="section-heading"><div><p className="eyebrow">实时数据</p><h2>{tab==='appeals'?'等待裁定的申诉':'需要判断的案件'}</h2></div><button className="filter-button" onClick={refresh} disabled={busy}>{busy?'刷新中…':'刷新'}</button></div>
          {tab==='appeals'?<div className="case-list">{appeals.map(appeal=><article className="case-card" key={appeal.id}>
            <div className="case-head"><span className="risk">申诉</span><span>{elapsed(appeal.createdAt)}前</span></div><p className="case-copy">{appeal.reason}</p>
            <div className="case-meta"><span>案件 {appeal.caseId.slice(0,8)}</span><span>用户 {appeal.appellantId.slice(0,8)}</span></div>
            <textarea placeholder="给申诉人的处理说明（可选）" value={appealResponses[appeal.id]||''} onChange={e=>setAppealResponses(values=>({...values,[appeal.id]:e.target.value}))} />
            <div className="actions"><button className="secondary" onClick={()=>decideAppeal(appeal,'UPHOLD')} disabled={busy}>维持原判</button><button className="primary" onClick={()=>decideAppeal(appeal,'OVERTURN')} disabled={busy}>撤销原判</button></div>
          </article>)}{!appeals.length&&!busy&&<div className="empty">目前没有待处理申诉。</div>}</div>
          :<div className="case-list">{cases.map(item=>{const overdue=item.overdue;return <article className={`case-card ${detail?.moderationCase.id===item.id?'selected':''}`} key={item.id}>
            <div className="case-head"><span className={`risk ${overdue?'danger':''}`}>{overdue?'已超 SLA':item.targetType==='POST'?'帖子':'评论'}</span><span>等待 {elapsed(item.createdAt)}</span></div>
            <p className="case-copy">{item.rationale||'模型尚未给出说明，请打开案件查看上下文。'}</p>
            <div className="case-meta"><span><b>{item.reportCount}</b> 次举报</span><span>{item.recommendedDecision||'待分析'} · {item.confidence==null?'—':`${Math.round(item.confidence*100)}%`}</span><span>{item.engine||'等待 worker'}</span></div>
            <div className="actions"><button className="primary" onClick={()=>openCase(item.id)}>查看并处理 →</button></div>
          </article>;})}{!cases.length&&!busy&&<div className="empty">这个队列目前是空的。</div>}</div>}
          <div className="pagination">
            <button className="secondary" disabled={busy||page===0} onClick={()=>setPage(value=>value-1)}>上一页</button>
            <span>第 {page+1} 页</span>
            <button className="secondary" disabled={busy||(tab==='appeals'?appeals.length:cases.length)<100}
              onClick={()=>setPage(value=>value+1)}>下一页</button>
          </div>
        </section>
        {detail?<aside className="detail-panel"><button className="detail-close" onClick={()=>setDetail(null)}>×</button>
          {trail.length>0&&<button className="back-link" disabled={busy} onClick={()=>openCase(trail[trail.length-1],trail.slice(0,-1))}>← 返回案件 {trail[trail.length-1].slice(0,8)}</button>}
          <p className="eyebrow">案件 {detail.moderationCase.id.slice(0,8)}</p>
          <p className="eyebrow">举报时留存的内容</p>
          <h2>{detail.content?.title||(detail.moderationCase.targetType==='POST'?'举报帖子':'举报评论')}</h2><p className="content-body">{detail.content?.body??'原内容已不可用。'}</p>
          {detail.content?.mediaUrl&&<EvidenceImage key={detail.content.mediaUrl} path={detail.content.mediaUrl} read={readEvidence} />}
          {detail.contentChanged&&<section className="brief"><p className="eyebrow">内容后来发生变更</p>
            {detail.currentContent?<><h3>{detail.currentContent.title||'当前内容'}</h3><p className="content-body">{detail.currentContent.body}</p>
              {detail.currentContent.mediaUrl&&<EvidenceImage key={detail.currentContent.mediaUrl} path={detail.currentContent.mediaUrl} read={readEvidence} />}</>
              :<p>当前内容已不可用；上方是举报时留存的版本。</p>}
          </section>}
          <dl><div><dt>建议</dt><dd>{detail.moderationCase.recommendedDecision||'—'}</dd></div><div><dt>置信度</dt><dd>{detail.moderationCase.confidence==null?'—':`${Math.round(detail.moderationCase.confidence*100)}%`}</dd></div><div><dt>规则</dt><dd>{detail.moderationCase.ruleCodes?.join(', ')||'—'}</dd></div></dl>
          <section className="brief">
            <div className="brief-head"><p className="eyebrow">调查助手</p>
              <button className="secondary" disabled={briefBusy||!!briefOff} onClick={()=>investigate(!!brief)}>{briefBusy?'调查中…':brief?'重新调查':'调查'}</button></div>
            {briefOff?<p className="brief-empty">{briefOff}</p>
              :brief?<>
                {brief.outcome==='COMPLETE'&&brief.recommendation
                  ?<p className="brief-verdict">建议 <b>{ACTION_LABEL[brief.recommendation]}</b>{brief.evidenceStrength&&<span className={`band ${brief.evidenceStrength.toLowerCase()}`}>{EVIDENCE_LABEL[brief.evidenceStrength]}</span>}</p>
                  :<p className="brief-verdict incomplete">{brief.outcome==='PARTIAL'?'调查未完成':'助手没有得出结论'}</p>}
                <p className="brief-summary">{brief.summary}</p>
                {brief.counterEvidence&&<p className="brief-against"><b>反过来说</b>{brief.counterEvidence}</p>}
                {brief.citedCaseIds.length>0&&<div className="brief-cited"><span>{brief.outcome==='COMPLETE'?'引用案件':'已读取案件'}</span>
                  {brief.citedCaseIds.map(id=>id===detail.moderationCase.id
                    ?<button key={id} className="cited-case" disabled title={id}>{id.slice(0,8)} · 本案</button>
                    :<button key={id} className="cited-case" disabled={busy} title={id} onClick={()=>openCase(id,[...trail,detail.moderationCase.id])}>{id.slice(0,8)}</button>)}</div>}
                <p className="brief-meta">{brief.promptVersion} · {brief.outcome==='COMPLETE'?'引用':'读取'} {brief.citedCaseIds.length} 个案件 · {new Date(brief.producedAt).toLocaleString('zh-CN')}</p>
              </>
              :<p className="brief-empty">尚未调查。助手会查作者过往处理记录与同规则先例，只读，不改变案件，最终处置仍由你决定。</p>}
          </section>
          {detail.moderationCase.status==='AWAITING_REVIEW'&&<div className="assignment-row"><span>{detail.moderationCase.assignedTo?`已由 ${detail.moderationCase.assignedTo.slice(0,8)} 认领`:'尚未认领'}</span>
            {detail.moderationCase.assignedTo?<button className="secondary" onClick={()=>mutateCase(`/api/admin/moderation-cases/${detail.moderationCase.id}/assignment`,{method:'DELETE'})}>释放</button>:<button className="secondary" onClick={()=>mutateCase(`/api/admin/moderation-cases/${detail.moderationCase.id}/assignment`,{method:'POST'})}>认领</button>}</div>}
          {detail.moderationCase.status==='RESOLVED'&&<p className="resolved-banner">最终处理：{detail.moderationCase.finalAction}</p>}
          <textarea placeholder={detail.moderationCase.status==='RESOLVED'?'说明为什么修订原裁决':'记录裁决原因，便于审计和申诉复核'} value={note} onChange={e=>setNote(e.target.value)} />
          <div className="decision-grid"><button onClick={()=>decide('NONE')} disabled={busy||detail.moderationCase.finalAction==='NONE'}>不处理</button><button onClick={()=>decide('HIDE')} disabled={busy||detail.moderationCase.finalAction==='HIDE'}>隐藏</button><button onClick={()=>decide('DELETE')} disabled={busy||detail.moderationCase.finalAction==='DELETE'}>删除</button><button className="danger-button" onClick={()=>decide('BAN')} disabled={busy||detail.moderationCase.finalAction==='BAN'}>封禁作者</button></div>
          <details><summary>审计记录（{detail.auditTrail.length}）</summary>{detail.auditTrail.map((entry,index)=><p className="audit" key={`${entry.at}-${index}`}><b>{entry.action}</b><br/><span>{new Date(entry.at).toLocaleString('zh-CN')}</span></p>)}</details>
        </aside>:<aside className="status-panel"><p className="eyebrow">服务健康</p><h2>{engine?'审核链路已连接':'正在检查服务'}</h2>
          <div className="health-row"><span>API 地址</span><b>{API.replace(/^https?:\/\//,'')}</b></div><div className="health-row"><span>当前引擎</span><b>{engine?.activeEngine||'—'}</b></div><div className="health-row"><span>降级引擎</span><b>{engine?.fallbackEngine||'—'}</b></div><div className="health-row"><span>登录管理员</span><b>{session.username}</b></div>
          <p className="status-help">选择一个案件后，这里会显示完整内容、附件、模型依据、认领状态和审计记录。</p></aside>}
      </div>
    </section>
  </main>;
}
