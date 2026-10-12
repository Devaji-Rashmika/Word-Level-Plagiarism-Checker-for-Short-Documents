'use strict';
const MODULES = [
  ['1 · TextHack as a System', 'part', 'Working: query console, command history, REST API, sample corpus, honest status. Missing: algorithm catalog UI, progress panel, full API docs page.'],
  ['2 · String Algorithms', 'part', 'Working: naive, KMP, Z, Rabin–Karp (double hash, collisions counted), Aho–Corasick, suffix array, Kasai LCP, longest common substring. Missing: SA-IS, suffix automaton/tree, step-through visualizer.'],
  ['3 · Advanced DP', 'part', 'Working: Levenshtein (table + traceback), Damerau-OSA. Missing: weighted edit, Needleman–Wunsch, Smith–Waterman, interval/bitmask/tree/SOS DP.'],
  ['4 · Network Flow', 'part', 'Working: Edmonds–Karp (augmenting paths shown), Dinic, min cut on the sample citation graph. Missing: graph editor, matching, min-cost flow, reductions.'],
  ['5 · NP-Completeness & Approximation', 'none', 'Not implemented. SCHEDULE returns a clear "not implemented" error.'],
  ['6 · Randomized & Parallel', 'part', 'Working: Miller–Rabin on arbitrary-size integers. Missing: randomized quicksort, universal hashing, reservoir sampling, all parallel demos.']
];
const EXAMPLES = ['SEARCH "भारत"', 'SEARCH "இந்தியா" ALGO ac', 'SEARCH "भारत" ALGO rk', 'FUZZY "algorithm" "algoritm"', 'FUZZY "भारत" "भारथ"',
  'SIMILAR hi-001 hi-002', 'FLOW citation-network', 'PRIME 1000000007', 'PRIME 170141183460469231731687303715884105727', 'SCHEDULE project-demo', 'HELP'];

const $ = id => document.getElementById(id);
const out = $('out'), cmd = $('cmd');
const history = []; let hi = 0;

$('cards').innerHTML = MODULES.map(([t, s, d]) =>
  `<article class="card"><h3>${t}</h3><span class="tag t-${s}">${s === 'part' ? 'partially implemented' : 'not implemented'}</span><p>${d}</p></article>`).join('');
const ex = $('examples');
EXAMPLES.forEach(e => { const b = document.createElement('button'); b.textContent = e; b.onclick = () => { cmd.value = e; run(); }; ex.appendChild(b); });

function esc(s) { return String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); }
function line(html, cls) { const d = document.createElement('div'); if (cls) d.className = cls; d.innerHTML = html; out.appendChild(d); out.scrollTop = out.scrollHeight; }
const meta = r => `<div class="meta">${r.algorithm ? esc(r.algorithm) + ' · ' : ''}${r.complexity ? esc(r.complexity) + ' · ' : ''}${r.timeMicros !== undefined ? r.timeMicros + ' µs' : ''}</div>`;

function render(r) {
  if (!r.ok) return line('Error: ' + esc(r.error), 'err');
  switch (r.type) {
    case 'help': return line('<pre>' + r.commands.map(esc).join('\n') + '</pre>');
    case 'search': {
      let h = meta(r) + `<div>${r.totalMatches} match(es) for "${esc(r.pattern)}"${r.hashCollisions !== undefined ? ' · hash collisions: ' + r.hashCollisions : ''}</div>`;
      r.documents.forEach(d => {
        h += `<div><b>${esc(d.id)}</b> ${esc(d.title)} [${esc(d.lang)}] — ${d.matches} match(es) <span class="meta">${esc(d.source)}</span></div>`;
        d.snippets.forEach(s => h += `<div>&nbsp;&nbsp;@${s.position}: …${esc(s.before)}<mark>${esc(s.match)}</mark>${esc(s.after)}…</div>`);
      });
      return line(h + `<div class="meta">${esc(r.note)}</div>`);
    }
    case 'fuzzy': {
      let h = meta(r) + `<div>Levenshtein = <b>${r.levenshtein}</b> · Damerau (OSA) = <b>${r.damerauOSA}</b> · edit script: ${esc(r.ops)} <span class="meta">(${esc(r.opsLegend)})</span></div>`;
      if (r.table) {
        // highlight the traceback path through the DP table
        const path = new Set(); let i = r.rows.length, j = r.cols.length; path.add(i + ',' + j);
        for (let k = r.ops.length - 1; k >= 0; k--) { const o = r.ops[k]; if (o === 'M' || o === 'S') { i--; j--; } else if (o === 'D') i--; else j--; path.add(i + ',' + j); }
        h += '<table><tr><th></th><th>∅</th>' + r.cols.map(c => `<th>${esc(c)}</th>`).join('') + '</tr>';
        r.table.forEach((row, a) => h += `<tr><th>${a ? esc(r.rows[a - 1]) : '∅'}</th>` + row.map((v, b) => `<td class="${path.has(a + ',' + b) ? 'path' : ''}">${v}</td>`).join('') + '</tr>');
        h += '</table><div class="meta">Recurrence: D[i][j] = min(D[i-1][j]+1, D[i][j-1]+1, D[i-1][j-1]+[a_i≠b_j]); base D[i][0]=i, D[0][j]=j. Highlighted cells = traceback.</div>';
      }
      return line(h);
    }
    case 'similar':
      return line(meta(r) + `<div>${esc(r.doc1)} vs ${esc(r.doc2)}: longest common substring = <b>${r.longestCommonSubstringLength}</b> code points → "<mark>${esc(r.sharedText)}</mark>" (doc1@${r.startInDoc1}, doc2@${r.startInDoc2})</div><div class="meta">${esc(r.note)}</div>`);
    case 'flow': {
      let h = `<div class="meta">${esc(r.dataLabel)}</div>` + meta({complexity: r.complexity}) +
        `<div>${esc(r.source)} → ${esc(r.sink)}: max flow = <b>${r.maxFlowEdmondsKarp}</b> (Edmonds–Karp, ${r.edmondsKarpMicros} µs) / <b>${r.maxFlowDinic}</b> (Dinic, ${r.dinicPhases} phases, ${r.dinicMicros} µs) · agree: ${r.agree}</div>`;
      r.augmentations.forEach((a, k) => h += `<div>&nbsp;&nbsp;augmentation ${k + 1}: ${a.path.map(esc).join(' → ')} (+${a.bottleneck})</div>`);
      h += `<div>Min cut: source side {${r.minCutSourceSide.map(esc).join(', ')}}, capacity <b>${r.minCutCapacity}</b></div>`;
      r.minCutEdges.forEach(e => h += `<div>&nbsp;&nbsp;cut edge ${esc(e.from)} → ${esc(e.to)}: flow ${e.flow}/${e.capacity} (saturated)</div>`);
      return line(h + `<div class="meta">${esc(r.note)}</div>`);
    }
    case 'prime':
      return line(meta(r) + `<div>${esc(r.n)} (${r.bits} bits) → <b>${esc(r.verdict.replace('_', ' '))}</b> · rounds run: ${r.roundsRun} · ${r.deterministic ? 'result is deterministic/proven' : 'probabilistic'}` +
        (r.witness ? ` · witness: ${esc(r.witness)}` : '') + (r.errorBound ? ` · error bound ${esc(r.errorBound)}` : '') + '</div>' + (r.note ? `<div class="meta">${esc(r.note)}</div>` : ''));
    default: return line('<pre>' + esc(JSON.stringify(r, null, 2)) + '</pre>');
  }
}

async function run() {
  const c = cmd.value.trim(); if (!c) return;
  history.push(c); hi = history.length; cmd.value = '';
  line('› ' + esc(c), 'cmdline');
  try {
    const res = await fetch('/api/query', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ command: c }) });
    render(await res.json());
  } catch (e) { line('Cannot reach the TextHack server: ' + esc(e.message), 'err'); }
}
$('run').onclick = run;
cmd.addEventListener('keydown', e => {
  if (e.key === 'Enter') run();
  else if (e.key === 'ArrowUp' && hi > 0) { cmd.value = history[--hi]; e.preventDefault(); }
  else if (e.key === 'ArrowDown') { hi = Math.min(hi + 1, history.length); cmd.value = history[hi] || ''; }
});
$('theme').onclick = () => { const r = document.documentElement; r.dataset.theme = r.dataset.theme === 'dark' ? '' : 'dark'; };
line('Type HELP, or click an example. All corpus documents are hand-written SAMPLE text, not Wikipedia.', 'meta');
