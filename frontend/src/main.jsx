import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const API = '/api/auth';
const RECORDS_PER_PAGE = 10;

function parseSpanFile(xmlText) {
  const documentNode = new DOMParser().parseFromString(xmlText, 'application/xml');
  if (documentNode.querySelector('parsererror')) throw new Error('This file is not valid XML.');

  const root = documentNode.documentElement;
  if (root.localName !== 'spanFile') throw new Error('The XML root element must be <spanFile>.');

  const categoryMap = new Map();
  function visit(parent, parentPath) {
    const children = Array.from(parent.children);
    const groups = new Map();
    children.forEach((child) => {
      if (child.children.length === 0) return;
      const group = groups.get(child.localName) || [];
      group.push(child);
      groups.set(child.localName, group);
    });

    groups.forEach((records, tagName) => {
      // SPN uses repeated sibling tags for record collections and *Def tags for definitions.
      if (records.length > 1 || tagName.endsWith('Def')) {
        const path = [...parentPath, tagName];
        const key = path.join('/');
        const category = categoryMap.get(key) || { key, label: path.slice(1).join(' / '), records: [], columns: new Set() };
        category.records.push(...records);
        records.forEach((record) => Array.from(record.children).forEach((child) => category.columns.add(child.localName)));
        categoryMap.set(key, category);
      }
    });

    children.forEach((child) => {
      if (child.children.length > 0) visit(child, [...parentPath, child.localName]);
    });
  }

  visit(root, [root.localName]);
  const categories = Array.from(categoryMap.values())
    .map((category) => ({ ...category, columns: Array.from(category.columns) }))
    .sort((left, right) => left.label.localeCompare(right.label));
  const pointInTime = Array.from(root.children).find((child) => child.localName === 'pointInTime');
  const clearingOrg = Array.from(pointInTime?.children || []).find((child) => child.localName === 'clearingOrg');
  const metadata = {
    format: Array.from(root.children).find((child) => child.localName === 'fileFormat')?.textContent.trim() || 'Unknown',
    created: Array.from(root.children).find((child) => child.localName === 'created')?.textContent.trim() || 'Unknown',
    dataDate: Array.from(pointInTime?.children || []).find((child) => child.localName === 'date')?.textContent.trim() || 'Unknown',
    organization: Array.from(clearingOrg?.children || []).find((child) => child.localName === 'name')?.textContent.trim() || 'Unknown',
    recordCount: new Set(categories.flatMap((category) => category.records)).size,
  };
  return { categories, metadata };
}

function formatSpnDate(value) {
  if (/^\d{12}$/.test(value)) return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)} ${value.slice(8, 10)}:${value.slice(10, 12)}`;
  if (/^\d{8}$/.test(value)) return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`;
  return value;
}

function elementToValue(element) {
  const children = Array.from(element.children);
  if (children.length === 0) return element.textContent.trim();

  return children.reduce((record, child) => {
    const key = child.localName;
    const value = elementToValue(child);
    if (Object.hasOwn(record, key)) {
      record[key] = Array.isArray(record[key]) ? [...record[key], value] : [record[key], value];
    } else {
      record[key] = value;
    }
    return record;
  }, {});
}

function compactValue(value) {
  if (value === null || value === undefined || value === '') return '—';
  const values = [];
  function collect(current, path) {
    if (Array.isArray(current)) {
      current.forEach((item) => collect(item, path));
    } else if (current && typeof current === 'object') {
      Object.entries(current).forEach(([key, child]) => collect(child, [...path, key]));
    } else {
      values.push(path.length ? `${path.join('.')}: ${current}` : String(current));
    }
  }
  collect(value, []);
  return values.slice(0, 4).join(' · ') || '—';
}

function RecordBrowser({ uploadId, categories, selectedKey, onSelect }) {
  const [categoryQuery, setCategoryQuery] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [selectedRecord, setSelectedRecord] = useState(null);
  const [records, setRecords] = useState([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [loadingRecords, setLoadingRecords] = useState(false);
  const [recordError, setRecordError] = useState('');
  const category = categories.find((item) => item.key === selectedKey) || categories[0];
  const matchingCategories = categories.filter((item) => item.label.toLocaleLowerCase().includes(categoryQuery.toLocaleLowerCase()));
  const pageCount = Math.max(1, Math.ceil(totalRecords / RECORDS_PER_PAGE));

  useEffect(() => {
    if (!uploadId || !category) return undefined;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      const params = new URLSearchParams({ categoryKey: category.key, page: String(page) });
      if (query.trim()) params.set('query', query.trim());
      setLoadingRecords(true);
      setRecordError('');
      // Cancel an older page/search request when the user changes the selection quickly.
      request(`/api/spn/uploads/${encodeURIComponent(uploadId)}/records?${params}`, { signal: controller.signal })
        .then((result) => {
          setRecords(result.items);
          setTotalRecords(result.totalRecords);
          setSelectedRecord(null);
        })
        .catch((error) => {
          if (error.name !== 'AbortError') setRecordError(error.message);
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoadingRecords(false);
        });
    }, 180);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [uploadId, category, page, query]);

  return (
    <div className="record-browser">
      <aside className="category-sidebar">
        <label htmlFor="category-filter">Categories <span>{categories.length}</span></label>
        <input id="category-filter" type="search" value={categoryQuery} placeholder="Find a category" onChange={(event) => setCategoryQuery(event.target.value)} />
        <div className="category-options">
          {matchingCategories.map((item) => <button
            type="button"
            key={item.key}
            className={item.key === category.key ? 'category-option selected' : 'category-option'}
            onClick={() => { onSelect(item.key); setQuery(''); setPage(0); setSelectedRecord(null); }}
          ><span>{item.label}</span><small>{item.recordCount.toLocaleString()}</small></button>)}
          {matchingCategories.length === 0 && <p className="empty-results">No categories match.</p>}
        </div>
      </aside>
      <section className="records-panel">
        <header className="records-header">
          <div><p className="eyebrow">{category.key.split('/').slice(0, -1).join(' / ')}</p><h3>{category.key.split('/').at(-1)}</h3></div>
            <span>{totalRecords.toLocaleString()} records</span>
        </header>
        <div className="records-controls">
          <input type="search" value={query} placeholder={`Search ${category.recordCount.toLocaleString()} records`} onChange={(event) => { setQuery(event.target.value); setPage(0); setSelectedRecord(null); }} />
          <span>{category.columns.length} fields</span>
        </div>
        {recordError && <p className="file-error" role="alert">{recordError}</p>}
        {loadingRecords ? <p className="records-loading">Loading records…</p> : records.length > 0 ? <div className="records-table-scroll"><table className="records-table">
          <thead><tr><th>#</th>{category.columns.map((column) => <th key={column}>{column}</th>)}<th>Details</th></tr></thead>
          <tbody>{records.map((record, index) => <tr key={`${page}-${index}`}>
            <td>{(page * RECORDS_PER_PAGE + index + 1).toLocaleString()}</td>
            {category.columns.map((column) => {
              const value = compactValue(record[column]);
              return <td key={column} title={value}>{value}</td>;
            })}
            <td><button className="inspect-record" type="button" onClick={() => setSelectedRecord({ record, number: page * RECORDS_PER_PAGE + index + 1 })}>Inspect</button></td>
          </tr>)}</tbody>
        </table></div> : <p className="empty-results">No records match that search.</p>}
        {selectedRecord && <section className="record-detail">
          <header><strong>Record {selectedRecord.number.toLocaleString()}</strong><button type="button" onClick={() => setSelectedRecord(null)}>Close</button></header>
          <pre>{JSON.stringify(selectedRecord.record, null, 2)}</pre>
        </section>}
        <footer className="record-pagination">
          <span>{totalRecords ? `${(page * RECORDS_PER_PAGE + 1).toLocaleString()}–${Math.min((page + 1) * RECORDS_PER_PAGE, totalRecords).toLocaleString()} of ${totalRecords.toLocaleString()}` : '0 records'}</span>
          <div><button type="button" disabled={page === 0 || loadingRecords} onClick={() => { setPage(page - 1); setSelectedRecord(null); }}>Previous</button><button type="button" disabled={page + 1 >= pageCount || loadingRecords} onClick={() => { setPage(page + 1); setSelectedRecord(null); }}>Next</button></div>
        </footer>
      </section>
    </div>
  );
}

async function request(path, options = {}) {
  const url = path.startsWith('/api/') ? path : `${API}${path}`;
  const response = await fetch(url, {
    credentials: 'include',
    ...options,
    headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new Error(payload.error || 'Something went wrong. Please try again.');
  }
  return response.status === 204 ? null : response.json();
}

function routeFor(path) {
  return path === '/signup' ? 'signup' : path === '/dashboard' ? 'dashboard' : 'login';
}

function App() {
  const [page, setPage] = useState(() => routeFor(window.location.pathname));
  const [username, setUsername] = useState('');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [captchaVersion, setCaptchaVersion] = useState(Date.now());
  const [busy, setBusy] = useState(false);
  const [timeLeft, setTimeLeft] = useState(900);
  const [renewalPrompt, setRenewalPrompt] = useState(false);
  const [fileName, setFileName] = useState('');
  const [fileSize, setFileSize] = useState(0);
  const [fileCategories, setFileCategories] = useState([]);
  const [fileMetadata, setFileMetadata] = useState(null);
  const [uploadId, setUploadId] = useState('');
  const [selectedCategoryKey, setSelectedCategoryKey] = useState('');
  const [fileError, setFileError] = useState('');
  const [isParsingFile, setIsParsingFile] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(null);

  function navigate(next) {
    const path = next === 'signup' ? '/signup' : next === 'dashboard' ? '/dashboard' : '/';
    window.history.pushState({}, '', path);
    setPage(next);
    setError('');
  }

  useEffect(() => {
    const onPopState = () => setPage(routeFor(window.location.pathname));
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => {
    if (page !== 'dashboard') return undefined;
    request('/me').then((session) => setUsername(session.username)).catch(() => navigate('login'));
    request('/api/spn/uploads/latest').then((upload) => {
      setUploadId(upload.id);
      setFileName(upload.fileName);
      setFileSize(upload.fileSize);
      setFileMetadata({
        format: upload.format,
        created: upload.created,
        dataDate: upload.dataDate,
        organization: upload.organization,
        recordCount: upload.recordCount,
      });
      setFileCategories(upload.categories);
      setSelectedCategoryKey([...upload.categories].sort((left, right) => right.recordCount - left.recordCount)[0]?.key || '');
    }).catch(() => {});
    return undefined;
  }, [page]);

  useEffect(() => {
    if (page !== 'dashboard') return undefined;
    const timer = window.setInterval(() => setTimeLeft((remaining) => Math.max(0, remaining - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [page]);

  useEffect(() => {
    if (page === 'dashboard' && timeLeft === 300) setRenewalPrompt(true);
    if (page === 'dashboard' && timeLeft === 0) {
      navigate('login');
      setNotice('Your session expired. Please sign in again.');
    }
  }, [page, timeLeft]);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    const payload = Object.fromEntries(new FormData(event.currentTarget).entries());
    try {
      if (page === 'signup') {
        const result = await request('/signup', { method: 'POST', body: JSON.stringify(payload) });
        setNotice(result.message);
        setCaptchaVersion(Date.now());
        navigate('login');
      } else {
        const session = await request('/login', { method: 'POST', body: JSON.stringify(payload) });
        setUsername(session.username);
        setTimeLeft(900);
        navigate('dashboard');
      }
    } catch (submitError) {
      setError(submitError.message);
      setCaptchaVersion(Date.now());
    } finally {
      setBusy(false);
    }
  }

  async function renewSession() {
    try {
      await request('/extend-session', { method: 'POST' });
      setTimeLeft(900);
      setRenewalPrompt(false);
    } catch {
      navigate('login');
      setNotice('Your session expired. Please sign in again.');
    }
  }

  async function logout() {
    await request('/logout', { method: 'POST' }).catch(() => null);
    setNotice('You have been signed out.');
    navigate('login');
  }

  async function uploadSpanFile(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    let createdUploadId = '';
    setFileError('');
    setUploadProgress(null);
    setIsParsingFile(true);
    try {
      const parsedFile = parseSpanFile(await file.text());
      const categories = parsedFile.categories;
      if (categories.length === 0) throw new Error('No record categories were found in this file.');
      const categoryCount = (category) => category.records.length;
      const totalRecords = categories.reduce((total, category) => total + categoryCount(category), 0);
      const createdUpload = await request('/api/spn/uploads', {
        method: 'POST',
        body: JSON.stringify({
          fileName: file.name,
          fileSize: file.size,
          ...parsedFile.metadata,
          categories: categories.map((category) => ({
            key: category.key,
            label: category.label,
            recordCount: categoryCount(category),
            columns: category.columns,
          })),
        }),
      });
      createdUploadId = createdUpload.id;
      setUploadProgress({ saved: 0, total: totalRecords });
      // Upload bounded batches with limited concurrency to avoid one giant JSON request.
      const batches = categories.flatMap((category) => {
        const categoryBatches = [];
        for (let startRow = 0; startRow < category.records.length; startRow += 1000) {
          categoryBatches.push({ category, startRow, records: category.records.slice(startRow, startRow + 1000) });
        }
        return categoryBatches;
      });
      let savedRecords = 0;
      for (let batchOffset = 0; batchOffset < batches.length; batchOffset += 4) {
        await Promise.all(batches.slice(batchOffset, batchOffset + 4).map(async (batch) => {
          const result = await request(`/api/spn/uploads/${encodeURIComponent(createdUploadId)}/records`, {
            method: 'POST',
            body: JSON.stringify({
              categoryKey: batch.category.key,
              startRow: batch.startRow,
              records: batch.records.map(elementToValue),
            }),
          });
          savedRecords += result.saved;
          setUploadProgress({ saved: savedRecords, total: totalRecords });
        }));
      }
      await request(`/api/spn/uploads/${encodeURIComponent(createdUploadId)}/complete`, { method: 'POST' });
      setFileName(file.name);
      setFileSize(file.size);
      setUploadId(createdUploadId);
      setFileCategories(categories.map(({ key, label, columns, records }) => ({ key, label, columns, recordCount: records.length })));
      setFileMetadata(parsedFile.metadata);
      setSelectedCategoryKey([...categories].sort((left, right) => right.records.length - left.records.length)[0].key);
      setUploadProgress(null);
    } catch (parseError) {
      if (createdUploadId) await request(`/api/spn/uploads/${encodeURIComponent(createdUploadId)}`, { method: 'DELETE' }).catch(() => null);
      setFileName('');
      setFileSize(0);
      setUploadId('');
      setFileCategories([]);
      setFileMetadata(null);
      setSelectedCategoryKey('');
      setFileError(parseError.message || 'Could not read this SPN file.');
      setUploadProgress(null);
    } finally {
      setIsParsingFile(false);
      event.target.value = '';
    }
  }

  const minutes = String(Math.floor(timeLeft / 60)).padStart(2, '0');
  const seconds = String(timeLeft % 60).padStart(2, '0');

  if (page === 'dashboard') {
    return (
      <main className="dashboard-shell">
        <header className="topbar"><a className="wordmark" href="/" onClick={(event) => { event.preventDefault(); navigate('dashboard'); }}>SESSION<span>/</span>AUTH</a><span className="status"><i /> SESSION ACTIVE</span></header>
        <section className="workspace">
          <div className="workspace-head"><div><p className="eyebrow">WORKSPACE</p><h1>{username || 'Your account'}</h1></div><button className="quiet-button" onClick={logout}>Sign out</button></div>
          <div className="timer-strip"><span>TIME LEFT IN THIS SESSION</span><strong>{minutes}:{seconds}</strong><button className="text-button" onClick={renewSession}>Extend session</button></div>
          <section className="file-explorer">
            <div className="file-explorer-head">
              <div><h2>SPN file</h2><p>Upload a SPN file to browse its categories and records.</p></div>
              <label className="file-picker">{isParsingFile ? uploadProgress ? `Saving ${uploadProgress.saved.toLocaleString()} / ${uploadProgress.total.toLocaleString()}` : 'Reading file…' : fileName ? 'Choose another file' : 'Choose file'}
                <input type="file" accept=".spn,.xml,application/xml,text/xml" onChange={uploadSpanFile} disabled={isParsingFile} />
              </label>
            </div>
            {fileError && <p className="file-error" role="alert">{fileError}</p>}
            {fileCategories.length > 0 && <>
              <div className="file-summary"><strong>{fileName}</strong><span>{(fileSize / (1024 * 1024)).toFixed(1)} MB</span></div>
              <div className="file-facts">
                <div><span>Data date</span><strong>{formatSpnDate(fileMetadata.dataDate)}</strong></div>
                <div><span>Created</span><strong>{formatSpnDate(fileMetadata.created)}</strong></div>
                <div><span>Organization</span><strong>{fileMetadata.organization}</strong></div>
                <div><span>Format</span><strong>{fileMetadata.format}</strong></div>
                <div><span>Categories</span><strong>{fileCategories.length}</strong></div>
                <div><span>Records</span><strong>{fileMetadata.recordCount.toLocaleString()}</strong></div>
              </div>
              <RecordBrowser uploadId={uploadId} categories={fileCategories} selectedKey={selectedCategoryKey} onSelect={setSelectedCategoryKey} />
            </>}
          </section>
          <label className="notes-label" htmlFor="notes">Notes <span>Saved in this browser</span></label>
          <textarea id="notes" className="notes" placeholder="Write a note…" defaultValue={localStorage.getItem('session-auth-notes') || ''} onChange={(event) => localStorage.setItem('session-auth-notes', event.target.value)} />
          <footer className="workspace-foot"><span>Session Auth</span><span>Session expires after 15 minutes of inactivity</span></footer>
        </section>
        {renewalPrompt && <div className="modal-backdrop"><section className="dialog"><p className="eyebrow">SESSION NOTICE</p><h2>Still working?</h2><p>Your session has five minutes remaining. Extend it to keep your workspace open.</p><div className="dialog-actions"><button className="primary-button" onClick={renewSession}>Extend session <span aria-hidden="true">↗</span></button><button className="quiet-button" onClick={() => setRenewalPrompt(false)}>Not now</button></div></section></div>}
      </main>
    );
  }

  return (
    <main className="auth-shell">
      <section className="form-panel"><div className="form-panel-inner">
        <a className="wordmark" href="/" onClick={(event) => { event.preventDefault(); navigate('login'); }}>Session Auth</a>
        <h2>{page === 'signup' ? 'Create an account' : 'Sign in'}</h2>
        <p className="form-intro">{page === 'signup' ? 'Enter a username and password to get started.' : 'Enter your account details to continue.'}</p>
        {notice && <div className="message success" role="status">{notice}<button aria-label="Dismiss message" onClick={() => setNotice('')}>×</button></div>}
        {error && <div className="message failure" role="alert">{error}</div>}
        <form onSubmit={submit} className="auth-form">
          <label htmlFor="username">USERNAME</label><input id="username" name="username" autoComplete="username" maxLength="50" required />
          <div className="password-label"><label htmlFor="password">PASSWORD</label>{page === 'signup' && <span>8 characters minimum</span>}</div><input id="password" name="password" type="password" autoComplete={page === 'signup' ? 'new-password' : 'current-password'} minLength={page === 'signup' ? 8 : undefined} maxLength="100" required />
          <label htmlFor="captcha">SECURITY CHECK</label>
          <div className="captcha-row"><img src={`${API}/captcha?v=${captchaVersion}`} alt="CAPTCHA challenge" /><button type="button" className="refresh-button" aria-label="Refresh CAPTCHA" onClick={() => setCaptchaVersion(Date.now())}>↻</button></div>
          <input id="captcha" name="captcha" placeholder="Enter the characters above" autoComplete="off" maxLength="10" required />
          <button className="primary-button submit-button" type="submit" disabled={busy}>{busy ? 'Please wait…' : page === 'signup' ? 'Create account' : 'Sign in'} <span aria-hidden="true">↗</span></button>
        </form>
        <p className="switch-link">{page === 'signup' ? 'Already have an account?' : 'New to Session Auth?'} <button onClick={() => navigate(page === 'signup' ? 'login' : 'signup')}>{page === 'signup' ? 'Sign in' : 'Create an account'}</button></p>
        <div className="form-footer"><span>SECURE SESSION</span><span>SESSION AUTH © 2026</span></div>
      </div></section>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);