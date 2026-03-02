/* ═══════════════════════════════════════════════════════════════
   Outline Playground  —  Frontend Application
   ═══════════════════════════════════════════════════════════════ */

// ── State ──────────────────────────────────────────────────────
const state = {
  editor           : null,
  examples         : [],
  snippets         : JSON.parse(localStorage.getItem('outline_snippets') || '[]'),
  lastResult       : null,
  inferDebounce    : null,
  hoverProvider    : null,
  typeDecos        : [],
  diagDecos        : [],
  showInline       : true,   // inline type hints always on (toggle removed)
  isDark           : JSON.parse(localStorage.getItem('outline_dark') ?? 'true'),
  // panel resize
  resultsWidth     : parseInt(localStorage.getItem('outline_results_w') || '360', 10),
  // active snippet tracking for re-save
  activeSnippetIdx : -1,
  _progChange      : false,  // suppress activeSnippetIdx reset during programmatic setValue
};

// ── Monaco bootstrap ───────────────────────────────────────────
require.config({ paths: { vs: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.46.0/min/vs' } });
require(['vs/editor/editor.main'], () => {

  registerOutlineLanguage();

  state.editor = monaco.editor.create(document.getElementById('monaco-editor'), {
    value           : loadInitialCode(),
    language        : 'outline',
    theme           : state.isDark ? 'outline-dark' : 'outline-light',
    fontSize        : 14,
    fontFamily      : "'JetBrains Mono', 'Fira Code', monospace",
    fontLigatures   : true,
    lineNumbers     : 'on',
    minimap         : { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap        : 'on',
    padding         : { top: 16, bottom: 16 },
    smoothScrolling : true,
    cursorSmoothCaretAnimation: 'on',
    roundedSelection: true,
    renderLineHighlight: 'all',
    bracketPairColorization: { enabled: true },
    suggest         : { showKeywords: true },
    automaticLayout : true,
  });

  applyTheme(state.isDark);
  applyResultsWidth(state.resultsWidth);

  // Shortcuts
  state.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => runCode('run'));
  state.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyK, () => {
    state.editor.setValue('');
    clearResults();
  });

  // Auto-infer on change (debounced); user edits clear the active snippet lock
  state.editor.onDidChangeModelContent(() => {
    if (!state._progChange) {
      state.activeSnippetIdx = -1;
      updateSaveBtn();
    }
    clearTimeout(state.inferDebounce);
    updateLineMeta();
    state.inferDebounce = setTimeout(() => runCode('infer'), 700);
  });

  updateLineMeta();
  loadExamples();
  renderSnippets();
  bindEvents();
  bindResizeHandle();
  loadFromUrl();
});

// ── Language definition ────────────────────────────────────────
function registerOutlineLanguage() {
  monaco.languages.register({ id: 'outline' });

  monaco.languages.setMonarchTokensProvider('outline', {
    keywords: ['let', 'var', 'if', 'else', 'match', 'outline', 'module',
               'import', 'export', 'from', 'as', 'return', 'this', 'with',
               'fx', 'baseNode', 'sync', 'macro', 'true', 'false'],
    typeKeywords: ['String', 'Int', 'Long', 'Float', 'Double', 'Bool', 'Number', 'Unit'],
    tokenizer: {
      root: [
        [/\/\/.*/, 'comment'],
        [/\/\*[\s\S]*?\*\//, 'comment'],
        [/#"([^"\\]|\\.)*"/, 'string.literal-type'],   // #"value" — literal type
        [/"([^"\\]|\\.)*"/, 'string'],
        [/\b[A-Z]\w*\b/, 'type.identifier'],
        [/\b(let|var|if|else|match|outline|module|import|export|from|as|return|this|with|fx|baseNode|sync|macro)\b/, 'keyword'],
        [/\b(true|false)\b/, 'constant.language'],
        [/\b(String|Int|Long|Float|Double|Bool|Number|Unit)\b/, 'type.primitive'],
        [/\b\d+[lL]?\b/, 'number'],
        [/\b\d+\.\d+[fFdD]?\b/, 'number.float'],
        [/->|=>|\.\.\./, 'operator.special'],
        [/[=!<>]=?|[+\-*\/%&|^~]/, 'operator'],
        [/[{}\[\](),.;:]/, 'delimiter'],
        [/\b[a-z_]\w*\b/, 'identifier'],
      ],
    },
  });

  // Auto-close pairs
  monaco.languages.setLanguageConfiguration('outline', {
    brackets: [['(', ')'], ['{', '}'], ['[', ']']],
    autoClosingPairs: [
      { open: '(', close: ')' }, { open: '{', close: '}' },
      { open: '[', close: ']' }, { open: '"', close: '"' },
    ],
    surroundingPairs: [
      { open: '(', close: ')' }, { open: '{', close: '}' },
      { open: '[', close: ']' }, { open: '"', close: '"' },
    ],
    comments: { lineComment: '//', blockComment: ['/*', '*/'] },
  });

  defineThemes();
}

function defineThemes() {
  monaco.editor.defineTheme('outline-dark', {
    base: 'vs-dark', inherit: true,
    rules: [
      { token: 'keyword',              foreground: '4facfe', fontStyle: 'bold' },
      { token: 'type.identifier',      foreground: 'c084fc', fontStyle: 'bold' },
      { token: 'type.primitive',       foreground: '67e8f9' },
      { token: 'string',               foreground: 'a3e635' },
      { token: 'string.literal-type',  foreground: 'f9a825', fontStyle: 'bold' },
      { token: 'number',               foreground: 'fbbf24' },
      { token: 'number.float',         foreground: 'fb923c' },
      { token: 'comment',              foreground: '4a5a7a', fontStyle: 'italic' },
      { token: 'operator.special',     foreground: '4facfe', fontStyle: 'bold' },
      { token: 'operator',             foreground: '94a3b8' },
      { token: 'constant.language',    foreground: 'f472b6', fontStyle: 'bold' },
      { token: 'identifier',           foreground: 'e2e8f0' },
      { token: 'delimiter',            foreground: '64748b' },
    ],
    colors: {
      'editor.background'             : '#13172b',
      'editor.foreground'             : '#e2e8f0',
      'editor.lineHighlightBackground': '#1d2340',
      'editor.selectionBackground'    : '#3a4a7a',
      'editorLineNumber.foreground'   : '#2d3a5a',
      'editorLineNumber.activeForeground': '#5a7aaa',
      'editorCursor.foreground'       : '#4facfe',
      'editor.selectionHighlightBackground': '#2a355a',
      'editorError.foreground'        : '#f87171',
      'editorWarning.foreground'      : '#fbbf24',
    },
  });

  monaco.editor.defineTheme('outline-light', {
    base: 'vs', inherit: true,
    rules: [
      { token: 'keyword',              foreground: '1d4ed8', fontStyle: 'bold' },
      { token: 'type.identifier',      foreground: '7c3aed', fontStyle: 'bold' },
      { token: 'type.primitive',       foreground: '0891b2' },
      { token: 'string',               foreground: '15803d' },
      { token: 'string.literal-type',  foreground: 'd97706', fontStyle: 'bold' },
      { token: 'number',               foreground: 'b45309' },
      { token: 'number.float',         foreground: 'c2410c' },
      { token: 'comment',              foreground: '9ca3af', fontStyle: 'italic' },
      { token: 'operator.special',     foreground: '1d4ed8', fontStyle: 'bold' },
      { token: 'constant.language',    foreground: 'db2777', fontStyle: 'bold' },
    ],
    colors: {
      'editor.background'             : '#f8fafc',
      'editor.lineHighlightBackground': '#f1f5f9',
      'editorError.foreground'        : '#dc2626',
      'editorWarning.foreground'      : '#d97706',
    },
  });
}

// ── API ────────────────────────────────────────────────────────
async function runCode(mode = 'run') {
  const code = state.editor.getValue().trim();
  if (!code) { clearResults(); return; }

  setStatus('running', '⏳');

  try {
    const res = await fetch('/api/run', {
      method  : 'POST',
      headers : { 'Content-Type': 'application/json' },
      body    : JSON.stringify({ code }),
    });
    if (!res.ok) throw new Error(`Server error ${res.status}`);
    const data = await res.json();
    state.lastResult = data;
    renderResults(data, mode);
  } catch (err) {
    setStatus('error', '✗ Error');
    showToast(err.message, 'error');
  }
}

async function loadExamples() {
  try {
    const res = await fetch('/api/examples');
    state.examples = await res.json();
    renderExampleList(state.examples);
  } catch (_) {
    document.getElementById('example-list').innerHTML =
      '<div class="loading-msg" style="color:var(--red)">Failed to load examples</div>';
  }
}

// ── Render ─────────────────────────────────────────────────────
function renderResults(data, mode) {
  const diag    = data.diagnostics  || [];
  const errors  = diag.filter(d => d.severity === 'error');
  const warns   = diag.filter(d => d.severity === 'warning');
  const syms    = data.symbols      || [];
  const logs    = data.consoleLogs  || [];

  // Badges
  setBadge('badge-inference', syms.length);
  setBadge('badge-errors', errors.length + warns.length, true);
  setBadge('badge-console', logs.length);

  // Stats bar
  const statsBar = document.getElementById('stats-bar');
  statsBar.style.display = 'flex';
  el('stat-symbols').textContent  = syms.length;
  el('stat-infer-ms').textContent = data.inferenceMs + 'ms';
  el('stat-exec-ms').textContent  = data.executionMs + 'ms';

  renderInference(syms);
  updateInlineAnnotations(syms);
  registerHover(syms);
  renderOutput(data);
  renderConsole(logs);
  renderErrors(diag);
  updateDiagMarkers(diag);

  if (errors.length > 0) {
    setStatus('error', `✗ ${errors.length} error${errors.length > 1 ? 's' : ''}`);
    switchTab('errors');
  } else if (logs.length > 0 && mode === 'run') {
    setStatus('ok', `✓ ${logs.length} log${logs.length > 1 ? 's' : ''}`);
    switchTab('console');
  } else if (warns.length > 0) {
    setStatus('warn', `⚠ ${warns.length} warning${warns.length > 1 ? 's' : ''}`);
    switchTab('inference');
  } else {
    setStatus('ok', `✓ ${syms.length} inferred`);
    switchTab('inference');
  }
}

function renderInference(symbols) {
  const intro  = el('inference-intro');
  const result = el('inference-result');

  if (!symbols.length) {
    intro.style.display  = '';
    result.style.display = 'none';
    return;
  }

  intro.style.display  = 'none';
  result.style.display = '';

  const vars    = symbols.filter(s => s.kind !== 'outline');
  const types   = symbols.filter(s => s.kind === 'outline');

  const secTypes = el('section-types');
  const secVars  = el('section-vars');

  if (types.length) {
    secTypes.style.display = '';
    el('list-types').innerHTML = types.map(renderSymRow).join('');
  } else {
    secTypes.style.display = 'none';
  }

  el('list-vars').innerHTML = vars.length
    ? vars.map(renderSymRow).join('')
    : '<div class="sym-empty">No variable bindings found</div>';

  // Wire up expand-on-click for every sym-row
  document.querySelectorAll('.sym-row[data-sym]').forEach(row => {
    row.addEventListener('click', () => openTypeModal(JSON.parse(row.dataset.sym)));
  });
}

function renderSymRow(sym) {
  const locStr   = sym.line > 0 ? `${sym.line}:${sym.col}` : '';
  const typeHtml = formatTypeHtml(sym.type);
  const kindClass = `kind-${sym.kind}`;
  const symJson   = esc(JSON.stringify(sym));

  return `
    <div class="sym-row" data-sym="${symJson}" title="Click to see full type">
      <div class="sym-left">
        <span class="sym-name">${esc(sym.name)}</span>
        <span class="sym-kind-pill ${kindClass}">${sym.kind}</span>
      </div>
      <div class="sym-type sym-type-truncated">${typeHtml}<span class="sym-expand-hint">↗</span></div>
      ${locStr ? `<div class="sym-loc">${locStr}</div>` : ''}
    </div>`;
}

function renderOutput(data) {
  const box = el('output-box');
  if (data.output) {
    const isErr = data.output.startsWith('⚠');
    box.innerHTML = `<span class="${isErr ? 'out-error' : 'out-value'}">${esc(data.output)}</span>`;
  } else if (data.hasParseError) {
    box.innerHTML = '<span class="placeholder-text">Cannot execute: parse errors present</span>';
  } else {
    box.innerHTML = '<span class="placeholder-text">No output value</span>';
  }
}

function renderErrors(diag) {
  const list = el('errors-list');
  if (!diag.length) {
    list.innerHTML = `
      <div class="no-errors-state">
        <div class="no-errors-icon">✓</div>
        <div>No errors detected</div>
      </div>`;
    return;
  }
  list.innerHTML = diag.map(d => {
    const isWarn = d.severity === 'warning';
    const cls    = isWarn ? 'diag-warn' : 'diag-error';
    return `
      <div class="diag-item ${cls}">
        <div class="diag-kind">${isWarn ? '⚠ warning' : '✗ error'}</div>
        <pre class="diag-msg">${esc(d.message)}</pre>
        ${d.line > 0 ? `<div class="diag-loc" data-line="${d.line}">line ${d.line}:${d.col}</div>` : ''}
      </div>`;
  }).join('');

  // Click on loc → jump to line
  list.querySelectorAll('.diag-loc[data-line]').forEach(loc => {
    loc.addEventListener('click', () => {
      const line = +loc.dataset.line;
      state.editor.revealLineInCenter(line);
      state.editor.setPosition({ lineNumber: line, column: 1 });
      state.editor.focus();
    });
  });
}

function renderConsole(logs) {
  const list = el('console-log-list');
  if (!logs || !logs.length) {
    list.innerHTML = '<div class="console-empty">No console output — use <code>print(x)</code>, <code>Console.log(x)</code>, <code>Console.warn(x)</code> or <code>Console.error(x)</code></div>';
    return;
  }
  const ICONS = { log: '›', warn: '⚠', error: '✗' };
  list.innerHTML = logs.map((entry, i) => {
    const level = (entry.level || 'log').toLowerCase();
    const icon  = ICONS[level] || '›';
    const msg   = typeof entry === 'string' ? entry : (entry.message || '');
    return `<div class="console-line console-${level}">
      <span class="console-idx">${i + 1}</span>
      <span class="console-icon">${icon}</span>
      <span class="console-text">${esc(msg)}</span>
    </div>`;
  }).join('');
}

// ── Monaco diagnostic markers (squiggles) ──────────────────────

// Extract the best available position from a diagnostic entry.
// Returns { startLine, startCol, endLine, endCol } (all 1-based) or null.
function extractDiagPos(d, model) {
  const lineCount = model.getLineCount();

  // Pull token text from message patterns like:  – 'token text'
  // Take only content before the first display-newline (↵) and collapse runs of spaces.
  const tokenM = d.message.match(/[–-]\s+'([^']*)'/);
  const tokenFirstLine = tokenM
    ? tokenM[1].split('↵')[0].replace(/\s{2,}/g, ' ').trim()
    : '';
  const tokenLen = tokenFirstLine.length;

  // ── Case 1: server gave valid 1-based line, 0-based col  (@line N:M) ────────
  if (d.line > 0 && d.line <= lineCount) {
    const lineMax  = model.getLineMaxColumn(d.line);
    // GCP reports cols as 0-based → add 1 for Monaco's 1-based system
    const startCol = (d.col >= 0 && d.col + 1 < lineMax) ? d.col + 1 : 1;
    const endCol   = tokenLen > 0
      ? Math.min(startCol + tokenLen, lineMax)
      : lineMax;
    return { startLine: d.line, startCol, endLine: d.line, endCol };
  }

  // ── Case 2: @offset N  — GCP absolute 0-based char offset into source ───────
  const offsetM = d.message.match(/@offset\s+(\d+)/);
  if (offsetM) {
    const offset = parseInt(offsetM[1], 10);
    const pos = model.getPositionAt(offset);   // Monaco returns 1-based line/col
    if (pos && pos.lineNumber > 0 && pos.lineNumber <= lineCount) {
      const lineMax = model.getLineMaxColumn(pos.lineNumber);
      const endCol  = tokenLen > 0
        ? Math.min(pos.column + tokenLen, lineMax)
        : Math.min(pos.column + 15, lineMax);
      return { startLine: pos.lineNumber, startCol: pos.column, endLine: pos.lineNumber, endCol };
    }
  }

  // ── Case 3: parse errors  (GCP uses 0-based lines AND cols here) ─────────────
  // Two sub-formats:
  //   "at line: N, position from X to Y"   (aggregate errors)
  //   "at line:N, position: X - Y"         (single-token errors, EOF etc.)
  const parseM =
    d.message.match(/at line:\s*(\d+),\s*position from\s*(\d+)\s*to\s*(\d+)/) ||
    d.message.match(/at line:\s*(\d+),\s*position:\s*(\d+)\s*-\s*(\d+)/);
  if (parseM) {
    const line1 = parseInt(parseM[1], 10) + 1;   // 0-based → 1-based
    const sc    = parseInt(parseM[2], 10) + 1;   // 0-based col → 1-based
    const ec    = parseInt(parseM[3], 10) + 2;   // end 0-based → 1-based, inclusive
    if (line1 > 0 && line1 <= lineCount) {
      const lineMax = model.getLineMaxColumn(line1);
      // Clamp start to last valid column so we always get at least 1 visible char
      const sc2 = Math.min(sc, lineMax - 1);
      const ec2 = Math.min(Math.max(ec, sc2 + 1), lineMax);
      return { startLine: line1, startCol: sc2, endLine: line1, endCol: ec2 };
    }
  }

  return null;  // position unknown — diagnostic shown only in errors panel
}

function updateDiagMarkers(diagnostics) {
  if (!state.editor) return;
  const model   = state.editor.getModel();
  const markers = (diagnostics || []).flatMap(d => {
    const pos = extractDiagPos(d, model);
    if (!pos) return [];
    return [{
      severity        : d.severity === 'error'
        ? monaco.MarkerSeverity.Error
        : monaco.MarkerSeverity.Warning,
      startLineNumber : pos.startLine,
      startColumn     : pos.startCol,
      endLineNumber   : pos.endLine,
      endColumn       : pos.endCol,
      message         : d.message,
      source          : 'GCP',
    }];
  });
  monaco.editor.setModelMarkers(model, 'outline-server', markers);
}

// ── Examples ───────────────────────────────────────────────────
function renderExampleList(examples, filter = 'all') {
  const list   = el('example-list');
  const subset = filter === 'all' ? examples : examples.filter(e => e.category === filter);
  list.innerHTML = subset.map(ex => `
    <div class="example-item" data-id="${ex.id}">
      <div class="ex-meta">
        <span class="ex-cat">${ex.category}</span>
      </div>
      <div class="ex-title">${esc(ex.title)}</div>
      <div class="ex-desc">${esc(ex.description)}</div>
    </div>
  `).join('');
  list.querySelectorAll('.example-item').forEach(el_ => {
    el_.addEventListener('click', () => loadExample(el_.dataset.id));
  });
}

function loadExample(id) {
  const ex = state.examples.find(e => e.id === id);
  if (!ex) return;
  state.editor.setValue(ex.code.trim());
  document.querySelectorAll('.example-item').forEach(e_ =>
    e_.classList.toggle('active', e_.dataset.id === id));
  setTimeout(() => runCode('run'), 80);
}

// ── MySpace ────────────────────────────────────────────────────
function renderSnippets() {
  const list = el('snippet-list');
  if (!state.snippets.length) {
    list.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">📂</div>
        <div>No snippets yet</div>
        <div class="empty-hint">Press <kbd>＋ Save</kbd> to save your code</div>
      </div>`;
    return;
  }
  list.innerHTML = state.snippets.map((s, i) => `
    <div class="snippet-item${state.activeSnippetIdx === i ? ' snippet-active' : ''}" data-idx="${i}">
      <div class="snippet-left">
        <div class="snippet-name">${esc(s.name)}</div>
        <div class="snippet-date">${new Date(s.ts).toLocaleDateString()}</div>
      </div>
      <div class="snippet-actions">
        <button class="snippet-btn snippet-run" data-idx="${i}" title="Load & Run">▶</button>
        <button class="snippet-btn snippet-upd" data-idx="${i}" title="Overwrite with current editor code">💾</button>
        <button class="snippet-btn snippet-del" data-idx="${i}" title="Delete">✕</button>
      </div>
    </div>
  `).join('');

  list.querySelectorAll('.snippet-item').forEach(el_ => {
    el_.addEventListener('click', e => {
      if (e.target.closest('.snippet-btn')) return;
      loadSnippet(+el_.dataset.idx);
    });
  });
  list.querySelectorAll('.snippet-run').forEach(btn =>
    btn.addEventListener('click', e => { e.stopPropagation(); loadSnippetAndRun(+btn.dataset.idx); }));
  list.querySelectorAll('.snippet-upd').forEach(btn =>
    btn.addEventListener('click', e => { e.stopPropagation(); updateSnippet(+btn.dataset.idx); }));
  list.querySelectorAll('.snippet-del').forEach(btn =>
    btn.addEventListener('click', e => { e.stopPropagation(); deleteSnippet(+btn.dataset.idx); }));
}

function loadSnippet(idx) {
  if (!state.snippets[idx]) return;
  state._progChange = true;
  state.editor.setValue(state.snippets[idx].code);
  state._progChange = false;
  state.activeSnippetIdx = idx;
  updateSaveBtn();
}
function loadSnippetAndRun(idx) {
  loadSnippet(idx);
  setTimeout(() => runCode('run'), 80);
}
function deleteSnippet(idx) {
  if (state.activeSnippetIdx === idx) {
    state.activeSnippetIdx = -1;
    updateSaveBtn();
  } else if (state.activeSnippetIdx > idx) {
    state.activeSnippetIdx--;
  }
  state.snippets.splice(idx, 1);
  saveSnippets();
  renderSnippets();
}
function updateSnippet(idx) {
  if (!state.snippets[idx]) return;
  state.snippets[idx].code = state.editor.getValue();
  state.snippets[idx].ts   = Date.now();
  saveSnippets();
  renderSnippets();
  showToast(`"${state.snippets[idx].name}" updated`, 'ok');
}
function saveSnippets() {
  localStorage.setItem('outline_snippets', JSON.stringify(state.snippets));
}
function updateSaveBtn() {
  const btnSave   = el('btn-save');
  const btnSaveAs = el('btn-save-as');
  if (!btnSave) return;
  if (state.activeSnippetIdx >= 0 && state.snippets[state.activeSnippetIdx]) {
    const name = state.snippets[state.activeSnippetIdx].name;
    btnSave.textContent = '💾 Update';
    btnSave.title = `Overwrite "${name}" with current code`;
    if (btnSaveAs) btnSaveAs.style.display = '';
  } else {
    btnSave.textContent = '＋ Save';
    btnSave.title = 'Save current editor code';
    if (btnSaveAs) btnSaveAs.style.display = 'none';
  }
}

// ── Sharing ────────────────────────────────────────────────────
async function shareCode() {
  const code = state.editor.getValue();
  try {
    const res  = await fetch('/api/share', {
      method  : 'POST',
      headers : { 'Content-Type': 'application/json' },
      body    : JSON.stringify({ code }),
    });
    const { token } = await res.json();
    const url = `${location.origin}${location.pathname}?s=${token}`;
    await navigator.clipboard.writeText(url);
    showToast('Link copied to clipboard!', 'ok');
  } catch (_) {
    showToast('Could not create share link', 'error');
  }
}

async function loadFromUrl() {
  const params = new URLSearchParams(location.search);
  const token  = params.get('s');
  if (!token) return;
  try {
    const res  = await fetch(`/api/share/${token}`);
    const data = await res.json();
    if (data.code) {
      state.editor.setValue(data.code);
      setTimeout(() => runCode('run'), 200);
      showToast('Loaded shared snippet!', 'ok');
    }
  } catch (_) { /* ignore */ }
}

// ── Contribute Example ─────────────────────────────────────────
function openContribModal() {
  const code = state.editor.getValue();
  el('contrib-id').value    = '';
  el('contrib-title').value = '';
  el('contrib-desc').value  = '';
  el('contrib-cat').value   = 'types';
  el('contrib-code-preview').textContent = code.length > 300
    ? code.slice(0, 300) + '\n… (' + code.length + ' chars)'
    : code;
  el('contrib-modal-overlay').style.display = 'flex';
  setTimeout(() => el('contrib-id').focus(), 50);
}
function closeContribModal() {
  el('contrib-modal-overlay').style.display = 'none';
}
async function submitContrib() {
  const id    = el('contrib-id').value.trim().replace(/\s+/g, '-').toLowerCase();
  const title = el('contrib-title').value.trim();
  const desc  = el('contrib-desc').value.trim();
  const cat   = el('contrib-cat').value;
  const code  = state.editor.getValue();

  if (!id || !title || !code) {
    showToast('ID, title and code are required', 'error');
    return;
  }

  try {
    const res = await fetch('/api/examples', {
      method  : 'POST',
      headers : { 'Content-Type': 'application/json' },
      body    : JSON.stringify({ id, title, description: desc, category: cat, code }),
    });
    if (!res.ok) throw new Error(`Server error ${res.status}`);
    const saved = await res.json();
    // Merge into local examples list
    const existing = state.examples.findIndex(e => e.id === saved.id);
    if (existing >= 0) state.examples[existing] = saved;
    else state.examples.push(saved);
    renderExampleList(state.examples);
    closeContribModal();
    showToast(`Example "${title}" saved!`, 'ok');
  } catch (err) {
    showToast('Failed to save example: ' + err.message, 'error');
  }
}

// ── Type Detail Modal ──────────────────────────────────────────
function openTypeModal(sym) {
  const kindClass = `kind-${sym.kind}`;
  const locStr    = sym.line > 0 ? `line ${sym.line}:${sym.col}` : '';
  el('type-modal-title').textContent = sym.name;
  el('type-modal-meta').innerHTML = `
    <span class="sym-name">${esc(sym.name)}</span>
    <span class="sym-kind-pill ${kindClass}">${sym.kind}</span>
    ${locStr ? `<span class="sym-loc">${locStr}</span>` : ''}
  `;
  // Format the type nicely: break long entity/tuple types at punctuation
  el('type-modal-body').innerHTML = formatTypeHtml(sym.type);
  el('type-modal-overlay').style.display = 'flex';
}
function closeTypeModal() {
  el('type-modal-overlay').style.display = 'none';
}

// ── Panel Resize Handle ─────────────────────────────────────────
function bindResizeHandle() {
  const handle = el('panel-resize-handle');
  const panel  = el('results-panel');
  if (!handle || !panel) return;

  let startX = 0;
  let startW = 0;

  handle.addEventListener('mousedown', e => {
    startX = e.clientX;
    startW = panel.getBoundingClientRect().width;
    handle.classList.add('dragging');
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const onMove = e2 => {
      const delta = startX - e2.clientX;   // dragging left = bigger panel
      const newW  = Math.max(200, Math.min(window.innerWidth * 0.8, startW + delta));
      applyResultsWidth(newW);
    };

    const onUp = () => {
      handle.classList.remove('dragging');
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      localStorage.setItem('outline_results_w', String(Math.round(panel.getBoundingClientRect().width)));
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };

    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    e.preventDefault();
  });
}

function applyResultsWidth(w) {
  const panel = el('results-panel');
  if (panel) panel.style.flex = `0 0 ${w}px`;
}

// ── Events ─────────────────────────────────────────────────────
function bindEvents() {
  el('btn-run').addEventListener('click', () => runCode('run'));
  el('btn-infer').addEventListener('click', () => runCode('infer'));
  el('btn-clear').addEventListener('click', () => { state.editor.setValue(''); clearResults(); });
  el('btn-share').addEventListener('click', shareCode);
  el('btn-copy-output').addEventListener('click', () => {
    const text = el('output-box').innerText;
    navigator.clipboard.writeText(text).then(() => showToast('Copied!', 'ok'));
  });
  el('btn-clear-console').addEventListener('click', () => {
    el('console-log-list').innerHTML = '<div class="console-empty">No console output — use <code>print(x)</code>, <code>Console.log(x)</code>, <code>Console.warn(x)</code> or <code>Console.error(x)</code></div>';
    setBadge('badge-console', 0);
  });

  // Theme
  el('btn-theme').addEventListener('click', () => {
    state.isDark = !state.isDark;
    applyTheme(state.isDark);
    localStorage.setItem('outline_dark', JSON.stringify(state.isDark));
  });

  // Category filter
  document.querySelectorAll('.cat-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.cat-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      renderExampleList(state.examples, btn.dataset.cat);
    });
  });

  // Result tabs
  document.querySelectorAll('.result-tab').forEach(tab =>
    tab.addEventListener('click', () => switchTab(tab.dataset.tab)));

  // Sidebar tabs
  document.querySelectorAll('.sidebar-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.sidebar-tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.sidebar-panel').forEach(p => p.classList.remove('active'));
      tab.classList.add('active');
      el(`stab-${tab.dataset.stab}`).classList.add('active');
    });
  });

  // Sidebar collapse
  el('sidebar-toggle').addEventListener('click', () => {
    el('sidebar').classList.toggle('collapsed');
  });

  // Save / Update modal
  el('btn-save').addEventListener('click', () => {
    if (state.activeSnippetIdx >= 0) {
      updateSnippet(state.activeSnippetIdx);
    } else {
      openSaveModal();
    }
  });
  if (el('btn-save-as')) {
    el('btn-save-as').addEventListener('click', openSaveModal);
  }
  el('modal-cancel').addEventListener('click', closeSaveModal);
  el('modal-close').addEventListener('click', closeSaveModal);
  el('modal-confirm').addEventListener('click', confirmSave);
  el('modal-overlay').addEventListener('click', e => { if (e.target === e.currentTarget) closeSaveModal(); });
  el('snippet-name').addEventListener('keydown', e => {
    if (e.key === 'Enter') confirmSave();
    if (e.key === 'Escape') closeSaveModal();
  });

  // Contribute modal
  el('btn-contrib').addEventListener('click', openContribModal);
  el('contrib-cancel').addEventListener('click', closeContribModal);
  el('contrib-modal-close').addEventListener('click', closeContribModal);
  el('contrib-confirm').addEventListener('click', submitContrib);
  el('contrib-modal-overlay').addEventListener('click', e => { if (e.target === e.currentTarget) closeContribModal(); });

  // Type detail modal
  el('type-modal-close').addEventListener('click', closeTypeModal);
  el('type-modal-overlay').addEventListener('click', e => { if (e.target === e.currentTarget) closeTypeModal(); });

  // Global ESC closes any open modal
  document.addEventListener('keydown', e => {
    if (e.key !== 'Escape') return;
    closeSaveModal();
    closeContribModal();
    closeTypeModal();
  });
}

function openSaveModal() {
  el('snippet-name').value = '';
  el('modal-overlay').style.display = 'flex';
  setTimeout(() => el('snippet-name').focus(), 50);
}
function closeSaveModal() {
  el('modal-overlay').style.display = 'none';
}
function confirmSave() {
  const name = el('snippet-name').value.trim();
  if (!name) return;
  state.snippets.unshift({ name, code: state.editor.getValue(), ts: Date.now() });
  saveSnippets();
  renderSnippets();
  closeSaveModal();
  showToast(`Saved "${name}" to MySpace`, 'ok');
  // Switch sidebar to MySpace
  document.querySelector('[data-stab="myspace"]').click();
}

// ── Inline type annotations ────────────────────────────────────
function updateInlineAnnotations(symbols) {
  state.typeDecos = state.editor.deltaDecorations(state.typeDecos, []);
  if (!symbols.length || !state.showInline) return;

  const model = state.editor.getModel();
  const decos = [];

  symbols.forEach(sym => {
    if (sym.line <= 0 || sym.type === '?') return;
    try {
      const lineLen = model.getLineLength(sym.line);
      decos.push({
        range: new monaco.Range(sym.line, lineLen, sym.line, lineLen),
        options: {
          after: {
            content  : `  : ${sym.type}`,
            inlineClassName: 'inline-type-hint',
          },
          stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
        },
      });
    } catch (_) {}
  });

  state.typeDecos = state.editor.deltaDecorations([], decos);
}

function registerHover(symbols) {
  if (state.hoverProvider) { state.hoverProvider.dispose(); state.hoverProvider = null; }
  if (!symbols.length) return;
  const map = new Map(symbols.map(s => [s.name, s]));

  state.hoverProvider = monaco.languages.registerHoverProvider('outline', {
    provideHover(model, pos) {
      const word = model.getWordAtPosition(pos);
      if (!word) return null;
      const sym = map.get(word.word);
      if (!sym) return null;
      const kindLabel = sym.kind === 'outline' ? 'type' : sym.kind;
      return {
        contents: [
          { value: `**${esc(sym.name)}**` },
          { value: `\`\`\`\n${kindLabel} ${sym.name} : ${sym.type}\n\`\`\`` },
        ],
      };
    },
  });
}

// ── UI Helpers ─────────────────────────────────────────────────
function switchTab(name) {
  document.querySelectorAll('.result-tab')
    .forEach(t => t.classList.toggle('active', t.dataset.tab === name));
  document.querySelectorAll('.tab-content')
    .forEach(c => c.classList.toggle('active', c.id === `tab-${name}`));
}

function clearResults() {
  el('inference-intro').style.display  = '';
  el('inference-result').style.display = 'none';
  el('stats-bar').style.display        = 'none';
  el('output-box').innerHTML           = '<span class="placeholder-text">Press ▶ Run to execute your code</span>';
  el('errors-list').innerHTML          = `
    <div class="no-errors-state">
      <div class="no-errors-icon">✓</div>
      <div>No errors detected</div>
    </div>`;
  el('console-log-list').innerHTML     = '<div class="console-empty">No console output — use <code>print(x)</code>, <code>Console.log(x)</code>, <code>Console.warn(x)</code> or <code>Console.error(x)</code></div>';
  setBadge('badge-inference', 0);
  setBadge('badge-errors', 0, true);
  setBadge('badge-console', 0);
  setStatus('', 'Ready');
  state.typeDecos = state.editor.deltaDecorations(state.typeDecos, []);
  if (state.hoverProvider) { state.hoverProvider.dispose(); state.hoverProvider = null; }
  // Clear Monaco markers
  if (state.editor) {
    monaco.editor.setModelMarkers(state.editor.getModel(), 'outline-server', []);
  }
}

function setStatus(cls, msg) {
  const s = el('editor-status');
  s.className = `editor-status${cls ? ' status-' + cls : ''}`;
  s.textContent = msg;
}

function setBadge(id, count, isError = false) {
  const b = el(id);
  b.textContent    = count;
  b.style.display  = count > 0 ? '' : 'none';
  if (isError) b.classList.toggle('badge-has-error', count > 0);
  else         b.classList.toggle('badge-has-count', count > 0);
}

function updateLineMeta() {
  if (!state.editor) return;
  const lines = state.editor.getModel().getLineCount();
  el('meta-lines').textContent = `${lines} line${lines !== 1 ? 's' : ''}`;
}

function applyTheme(isDark) {
  document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
  if (state.editor) monaco.editor.setTheme(isDark ? 'outline-dark' : 'outline-light');
  el('btn-theme').textContent = isDark ? '☀' : '🌙';
}

function showToast(msg, type = 'ok') {
  const t = el('toast');
  t.textContent  = msg;
  t.className    = `toast toast-${type} toast-show`;
  clearTimeout(t._tid);
  t._tid = setTimeout(() => t.classList.remove('toast-show'), 2500);
}

function el(id) { return document.getElementById(id); }
function esc(s) {
  return String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Format a type string with colour spans for different categories. */
function formatTypeHtml(t) {
  if (!t) return '<span class="ty-unknown">?</span>';
  // Replace → arrows with styled versions
  const parts = t.split(' → ');
  if (parts.length > 1) {
    const styledParts = parts.map(p => formatSingleType(p));
    return styledParts.join(' <span class="ty-arrow">→</span> ');
  }
  return formatSingleType(t);
}

function formatSingleType(t) {
  if (!t || t === '?') return '<span class="ty-unknown">?</span>';
  if (/^[A-Z]/.test(t)) return `<span class="ty-adt">${esc(t)}</span>`;
  if (['Int','Long','Float','Double','Bool','String','Number','Unit'].includes(t))
    return `<span class="ty-prim">${esc(t)}</span>`;
  if (t.includes('α') || t.includes('β') || t.startsWith('`'))
    return `<span class="ty-generic">${esc(t)}</span>`;
  return `<span class="ty-other">${esc(t)}</span>`;
}

// ── Welcome code ───────────────────────────────────────────────
function loadInitialCode() {
  // Check for a share token first (handled by loadFromUrl after editor init)
  const params = new URLSearchParams(location.search);
  if (params.get('s')) return '// Loading shared snippet…';

  return `// ╔══════════════════════════════════════════════════════╗
// ║  Welcome to the Outline Playground!                  ║
// ║  GCP infers every type — zero annotations needed.   ║
// ║  Press  ▶ Run  or  Ctrl+Enter  to start.            ║
// ╚══════════════════════════════════════════════════════╝

// ── Primitives: GCP infers the narrowest type ─────────
let x    = 42;          // → Int
let name = "Outline";   // → String
let pi   = 3.14159;     // → Float

// ── Curried functions: full type chain inferred ───────
let add  = a -> b -> a + b;     // Int → Int → Int
let add5 = add(5);               // Int → Int  (partial app)

// ── Algebraic Data Types + Pattern Matching ───────────
outline Color = Red | Green | Blue;

let c = Green;
let color_name = match c {
    Red   -> "red",
    Green -> "green",
    Blue  -> "blue"
};

// ── Run it! ────────────────────────────────────────────
color_name;`.trim();
}
