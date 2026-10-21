/*
 * Objekt-Viewer – Skripte der Browser-Version.
 * Diese Datei kann angepasst werden (Kopie im Konfigurationsordner unter templates/app.js).
 * Änderungen wirken nach dem Speichern sofort beim nächsten Neuladen der Seite.
 */
document.addEventListener('toggle', async (ev) => {
  const d = ev.target;
  if (!(d instanceof HTMLDetailsElement) || !d.open || !d.classList.contains('lazy') || d.dataset.loaded) return;
  d.dataset.loaded = '1';
  const ul = d.querySelector(':scope > ul');
  ul.innerHTML = '<li class="note">lädt …</li>';
  const p = new URLSearchParams(location.search);
  const url = '/nav?id=' + d.dataset.id + '&sel=' + (p.get('id') || '') + '&layout=' + (p.get('layout') || 'v');
  try {
    const r = await fetch(url);
    if (!r.ok) throw new Error(r.status);
    ul.innerHTML = await r.text();
    d.classList.remove('lazy');
  } catch (e) {
    ul.innerHTML = '<li class="note err">Laden fehlgeschlagen</li>';
    delete d.dataset.loaded;
  }
}, true);

// ---------- Frames: verschiebbare Trennlinien ----------
(function () {
  const frames = document.querySelector('.frames');
  if (!frames) return;
  const key = 'objektviewer.frames.' + frames.dataset.view;
  const items = [...frames.querySelectorAll('.fcol, .fseg')];
  const wide = window.matchMedia('(min-width: 761px)');

  const fit = () => {
    if (!wide.matches) { frames.style.height = ''; return; }
    const top = frames.getBoundingClientRect().top + window.scrollY;
    frames.style.height = Math.max(document.documentElement.classList.contains('mode-table') ? 460 : 320, window.innerHeight - top - 24) + 'px';
  };
  fit();
  window.__fitFrames = fit;
  window.addEventListener('resize', fit);

  try {
    const saved = JSON.parse(localStorage.getItem(key) || 'null');
    if (Array.isArray(saved) && saved.length === items.length) {
      items.forEach((el, i) => { if (saved[i] > 0) el.style.flexGrow = saved[i]; });
    }
  } catch (e) { /* kein Speicher verfuegbar */ }

  const save = () => {
    try { localStorage.setItem(key, JSON.stringify(items.map(el => parseFloat(el.style.flexGrow) || 1))); }
    catch (e) { /* ignorieren */ }
  };

  // Verschiebt die Grenze zwischen a und b um delta Pixel.
  const resize = (sp, startA, startB, gA, gB, delta) => {
    const total = startA + startB, gt = gA + gB, min = Math.min(80, total / 2);
    const na = Math.max(min, Math.min(total - min, startA + delta));
    sp.previousElementSibling.style.flexGrow = (gt * na / total).toFixed(4);
    sp.nextElementSibling.style.flexGrow = (gt * (total - na) / total).toFixed(4);
  };
  const measure = (sp) => {
    const vert = sp.classList.contains('split-v');
    const a = sp.previousElementSibling, b = sp.nextElementSibling;
    const ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect();
    return {
      vert,
      sa: vert ? ra.width : ra.height, sb: vert ? rb.width : rb.height,
      ga: parseFloat(getComputedStyle(a).flexGrow) || 1, gb: parseFloat(getComputedStyle(b).flexGrow) || 1
    };
  };

  frames.addEventListener('pointerdown', (ev) => {
    const sp = ev.target.closest('.split-v, .split-h');
    if (!sp || ev.button !== 0) return;
    ev.preventDefault();
    const m = measure(sp);
    const start = m.vert ? ev.clientX : ev.clientY;
    sp.setPointerCapture(ev.pointerId);
    sp.classList.add('drag');
    document.body.classList.add(m.vert ? 'drag-v' : 'drag-h');
    const move = (e) => resize(sp, m.sa, m.sb, m.ga, m.gb, (m.vert ? e.clientX : e.clientY) - start);
    const up = () => {
      sp.removeEventListener('pointermove', move);
      sp.classList.remove('drag');
      document.body.classList.remove('drag-v', 'drag-h');
      save();
    };
    sp.addEventListener('pointermove', move);
    sp.addEventListener('pointerup', up, { once: true });
    sp.addEventListener('pointercancel', up, { once: true });
  });

  // Tastatur: Pfeiltasten verschieben in 20-px-Schritten
  frames.addEventListener('keydown', (ev) => {
    const sp = ev.target.closest('.split-v, .split-h');
    if (!sp) return;
    const m = measure(sp);
    const dir = { ArrowLeft: -1, ArrowUp: -1, ArrowRight: 1, ArrowDown: 1 }[ev.key];
    if (!dir || (m.vert !== (ev.key === 'ArrowLeft' || ev.key === 'ArrowRight'))) return;
    ev.preventDefault();
    resize(sp, m.sa, m.sb, m.ga, m.gb, dir * 20);
    save();
  });

  // Doppelklick auf eine Trennlinie: alle Groessen dieser Ansicht zuruecksetzen
  frames.addEventListener('dblclick', (ev) => {
    if (!ev.target.closest('.split-v, .split-h')) return;
    items.forEach(el => { el.style.flexGrow = el.dataset.grow; });
    try { localStorage.removeItem(key); } catch (e) { /* ignorieren */ }
  });
})();

// ---------- Umschalten Baum / Baumtabelle ----------
(function () {
  const root = document.documentElement;
  const sync = () => document.querySelectorAll('[data-mode]').forEach(b => {
    const on = (b.dataset.mode === 'table') === root.classList.contains('mode-table');
    b.classList.toggle('on', on);
    b.setAttribute('aria-pressed', on ? 'true' : 'false');
  });
  document.querySelectorAll('[data-mode]').forEach(b => b.addEventListener('click', () => {
    const table = b.dataset.mode === 'table';
    root.classList.toggle('mode-table', table);
    try { localStorage.setItem('objektviewer.mode', table ? 'table' : 'nav'); } catch (e) { /* ignorieren */ }
    sync();
    window.dispatchEvent(new Event('resize'));
    if (table && window.__ttReveal) window.__ttReveal();
  }));
  sync();
})();

// ---------- Baumtabelle ----------
(function () {
  const tt = document.querySelector('table.tt');
  if (!tt) return;
  const panel = tt.closest('.tt-panel');
  const scroller = panel.querySelector('.tt-scroll');
  const tbody = tt.tBodies[0];
  const filters = [...tt.querySelectorAll('.tt-filter input')];
  const count = panel.querySelector('.tt-count');
  const rows = () => [...tbody.rows];

  // Platzhalter * und ? ; ohne Platzhalter: "enthaelt"
  const glob = (p, t) => {
    let i = 0, j = 0, star = -1, mark = 0;
    while (j < t.length) {
      if (i < p.length && (p[i] === '?' || p[i] === t[j])) { i++; j++; }
      else if (i < p.length && p[i] === '*') { star = i++; mark = j; }
      else if (star >= 0) { i = star + 1; j = ++mark; }
      else return false;
    }
    while (i < p.length && p[i] === '*') i++;
    return i === p.length;
  };
  const isAll = (p) => { p = (p || '').trim(); return p === '' || p === '*'; };
  const match = (p, t) => {
    p = (p || '').trim().toLowerCase();
    if (isAll(p)) return true;
    t = (t || '').toLowerCase();
    return (p.includes('*') || p.includes('?')) ? glob(p, t) : t.includes(p);
  };

  const setTog = (r, open) => {
    r.dataset.open = open ? '1' : '0';
    const b = r.querySelector('.tt-tog');
    if (b) { b.textContent = open ? '−' : '+'; b.setAttribute('aria-label', open ? 'Zuklappen' : 'Aufklappen'); }
  };

  const update = () => {
    const pats = filters.map(f => f.value);
    const active = pats.some(p => !isAll(p));
    const all = rows();
    const byId = new Map(all.map(r => [r.dataset.id, r]));
    let keep = null;
    if (active) {
      keep = new Set();
      all.forEach(r => {
        const cells = [...r.cells].slice(1);
        if (pats.every((p, i) => match(p, cells[i] && cells[i].dataset.v))) {
          for (let x = r; x && !keep.has(x.dataset.id); x = byId.get(x.dataset.pid)) keep.add(x.dataset.id);
        }
      });
    }
    const vis = new Map();
    let n = 0;
    all.forEach(r => {
      const pid = r.dataset.pid, parent = byId.get(pid);
      let show;
      if (keep) show = keep.has(r.dataset.id);
      else show = !parent || (vis.get(pid) && parent.dataset.open === '1');
      vis.set(r.dataset.id, show);
      r.hidden = !show;
      if (show) n++;
    });
    count.textContent = keep ? (n + ' Zeilen passen zum Filter') : '';
  };

  const load = async (r) => {
    if (r.dataset.loading) return;
    r.dataset.loading = '1';
    const b = r.querySelector('.tt-tog');
    b.textContent = '…';
    try {
      const res = await fetch('/rows?id=' + r.dataset.id + '&sel=' + tt.dataset.sel + '&layout=' + tt.dataset.layout);
      if (!res.ok) throw new Error(await res.text());
      const html = await res.text();
      delete r.dataset.lazy;
      if (html.trim() === '') {
        b.replaceWith(Object.assign(document.createElement('span'), { className: 'tt-sp' }));
        delete r.dataset.open;
      } else {
        r.insertAdjacentHTML('afterend', html);
        setTog(r, true);
      }
      update();
    } catch (e) {
      b.textContent = '+';
      r.title = 'Laden fehlgeschlagen: ' + e.message;
      r.classList.add('err');
    } finally {
      delete r.dataset.loading;
    }
  };

  tbody.addEventListener('click', (ev) => {
    const b = ev.target.closest('.tt-tog');
    if (!b) return;
    const r = b.closest('tr');
    if (r.dataset.lazy) { load(r); return; }
    setTog(r, r.dataset.open !== '1');
    update();
  });

  panel.querySelector('[data-tt=expand]').addEventListener('click', () => {
    rows().forEach(r => { if (r.dataset.open !== undefined && !r.dataset.lazy) setTog(r, true); });
    update();
  });
  panel.querySelector('[data-tt=collapse]').addEventListener('click', () => {
    rows().forEach(r => { if (r.dataset.open !== undefined) setTog(r, false); });
    update();
  });
  panel.querySelector('[data-tt=clear]').addEventListener('click', () => {
    filters.forEach(f => { f.value = '*'; });
    update();
  });
  filters.forEach(f => f.addEventListener('input', update));

  // Sortieren: Geschwister innerhalb jedes Elternknotens, Klick wechselt auf-/absteigend
  let sortCol = -1, dir = 1;
  tt.querySelectorAll('.tt-sort').forEach(btn => btn.addEventListener('click', () => {
    const col = +btn.dataset.col;
    dir = sortCol === col ? -dir : 1;
    sortCol = col;
    tt.querySelectorAll('.tt-sort').forEach(x => { x.classList.remove('on'); x.textContent = '⇅'; });
    btn.classList.add('on');
    btn.textContent = dir > 0 ? '▲' : '▼';
    const all = rows();
    const kids = new Map();
    all.forEach(r => { const k = r.dataset.pid; if (!kids.has(k)) kids.set(k, []); kids.get(k).push(r); });
    const val = (r) => (r.cells[col + 1] && r.cells[col + 1].dataset.v) || '';
    const cmp = (a, b) => dir * val(a).localeCompare(val(b), 'de', { numeric: true, sensitivity: 'base' });
    const frag = document.createDocumentFragment();
    const walk = (pid) => (kids.get(pid) || []).sort(cmp).forEach(r => { frag.appendChild(r); walk(r.dataset.id); });
    walk(all[0].dataset.pid);
    tbody.appendChild(frag);
    update();
  }));

  // gewaehlte Zeile in den sichtbaren Bereich holen
  window.__ttReveal = () => {
    const sel = tbody.querySelector('tr.sel');
    if (sel && panel.offsetParent) scroller.scrollTop = Math.max(0, sel.offsetTop - scroller.clientHeight / 2);
  };
  window.__ttReveal();

  // Groesse des Panels geaendert: Frames darunter anpassen
  if (window.ResizeObserver) {
    new ResizeObserver(() => { if (window.__fitFrames) window.__fitFrames(); }).observe(panel);
  }
})();
