// FreeCard webapp: stesse funzioni dell'app Android, tutto salvato nel telefono (IndexedDB).
(function () {
  'use strict';

  const $app = document.getElementById('app');
  const $overlay = document.getElementById('overlay');
  const Codec = window.FreeCardCodec;

  // ---------- Icone (Material) ----------
  const ICONS = {
    back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
    search: 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
    close: 'M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
    more: 'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
    settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z',
    star: 'M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
    edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
    del: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
    share: 'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11A2.99 2.99 0 0 0 21 5a3 3 0 1 0-5.91.7L8.04 9.81A2.99 2.99 0 0 0 3 12a3 3 0 0 0 5.04 2.19l7.12 4.16c-.05.21-.08.43-.08.65A2.92 2.92 0 1 0 18 16.08z',
    add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z'
  };
  const icon = (name) => `<svg viewBox="0 0 24 24"><path d="${ICONS[name]}"/></svg>`;

  // ---------- Utilità ----------
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const intToHex = (col) => '#' + ((col >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase();
  const hexToInt = (hex) => (0xFF000000 | parseInt(hex.slice(1), 16)) | 0;
  function textOn(hexOrInt) {
    const hex = typeof hexOrInt === 'number' ? intToHex(hexOrInt) : hexOrInt;
    const n = parseInt(hex.slice(1), 16);
    const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
    return 0.299 * r + 0.587 * g + 0.114 * b > 0.62 ? '#1B1B1B' : '#FFFFFF';
  }
  const storeByKey = (k) => (window.STORES || []).find((s) => s.key === k) || null;
  const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const isStandalone = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

  function toast(msg) {
    const t = document.createElement('div');
    t.className = 'toast';
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 2600);
  }

  function dialog({ title = '', body = '', actions = [] }) {
    return new Promise((resolve) => {
      const back = document.createElement('div');
      back.className = 'dialog-back';
      back.innerHTML = `<div class="dialog">${title ? `<h3>${esc(title)}</h3>` : ''}<div class="dbody"></div><div class="actions"></div></div>`;
      const bodyEl = back.querySelector('.dbody');
      if (typeof body === 'string') bodyEl.innerHTML = body; else bodyEl.appendChild(body);
      const close = (v) => { back.remove(); resolve(v); };
      actions.forEach((a) => {
        const b = document.createElement('button');
        b.className = 'btn text' + (a.danger ? ' danger' : '');
        b.textContent = a.label;
        if (a.disabled) b.disabled = true;
        b.onclick = () => close(a.value);
        back.querySelector('.actions').appendChild(b);
      });
      back.addEventListener('click', (e) => { if (e.target === back) close(undefined); });
      document.body.appendChild(back);
    });
  }
  const alertBox = (text, title = '') => dialog({ title, body: `<p>${esc(text)}</p>`, actions: [{ label: 'OK', value: true }] });
  const confirmBox = (title, text, okLabel = 'OK', danger = false) =>
    dialog({ title, body: `<p>${esc(text)}</p>`, actions: [{ label: 'Annulla', value: false }, { label: okLabel, value: true, danger }] });

  function popupMenu(anchor, items) {
    const back = document.createElement('div');
    back.style.cssText = 'position:fixed;inset:0;z-index:69';
    const m = document.createElement('div');
    m.className = 'menu';
    items.forEach((it) => {
      if (it === '-') { m.appendChild(document.createElement('hr')); return; }
      const b = document.createElement('button');
      b.textContent = it.label;
      b.onclick = () => { back.remove(); m.remove(); it.action(); };
      m.appendChild(b);
    });
    back.onclick = () => { back.remove(); m.remove(); };
    document.body.appendChild(back);
    document.body.appendChild(m);
    const r = anchor.getBoundingClientRect();
    m.style.top = (r.bottom + 4) + 'px';
    m.style.right = Math.max(8, window.innerWidth - r.right) + 'px';
  }

  // ---------- Database (IndexedDB) ----------
  let dbPromise = null;
  function db() {
    if (!dbPromise) {
      dbPromise = new Promise((resolve, reject) => {
        const req = indexedDB.open('freecard', 1);
        req.onupgradeneeded = () => req.result.createObjectStore('cards', { keyPath: 'id', autoIncrement: true });
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });
    }
    return dbPromise;
  }
  async function tx(mode, fn) {
    const d = await db();
    return new Promise((resolve, reject) => {
      const t = d.transaction('cards', mode);
      const store = t.objectStore('cards');
      let out;
      const r = fn(store);
      if (r) r.onsuccess = () => { out = r.result; };
      t.oncomplete = () => resolve(out);
      t.onerror = () => reject(t.error);
      t.onabort = () => reject(t.error);
    });
  }
  const Cards = {
    all: () => tx('readonly', (s) => s.getAll()),
    get: (id) => tx('readonly', (s) => s.get(Number(id))),
    put: (c) => tx('readwrite', (s) => s.put(c)),
    add: (c) => { const copy = { ...c }; delete copy.id; return tx('readwrite', (s) => s.add(copy)); },
    del: (id) => tx('readwrite', (s) => s.delete(Number(id)))
  };
  function newCard(fields) {
    return Object.assign({
      name: '', number: '', format: 'CODE_128', color: hexToInt('#00897B'), storeKey: null, note: '',
      displayNumber: '', favorite: false, frontPhoto: null, backPhoto: null,
      createdAt: Date.now(), lastUsedAt: 0, useCount: 0
    }, fields);
  }
  // Chiede al browser di non cancellare i dati quando lo spazio scarseggia.
  if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});

  // ---------- Formati e disegno codici ----------
  const FORMATS = [
    ['EAN_13', 'EAN-13', 'ean13', false], ['EAN_8', 'EAN-8', 'ean8', false], ['UPC_A', 'UPC-A', 'upca', false],
    ['UPC_E', 'UPC-E', 'upce', false], ['CODE_128', 'Code 128', 'code128', false], ['CODE_39', 'Code 39', 'code39', false],
    ['CODE_93', 'Code 93', 'code93', false], ['ITF', 'ITF', 'interleaved2of5', false], ['CODABAR', 'Codabar', 'rationalizedCodabar', false],
    ['QR_CODE', 'QR Code', 'qrcode', true], ['PDF_417', 'PDF417', 'pdf417', true], ['AZTEC', 'Aztec', 'azteccode', true],
    ['DATA_MATRIX', 'Data Matrix', 'datamatrix', true]
  ].map(([id, label, bcid, twoD]) => ({ id, label, bcid, twoD }));
  const fmt = (id) => FORMATS.find((f) => f.id === id) || FORMATS[4];

  function eanCheckOk(d) {
    if (!/^\d+$/.test(d)) return false;
    const body = d.slice(0, -1).split('').reverse();
    let sum = 0;
    body.forEach((c, i) => { sum += Number(c) * (i % 2 === 0 ? 3 : 1); });
    return (10 - (sum % 10)) % 10 === Number(d[d.length - 1]);
  }
  function guessFormat(n) {
    n = n.trim();
    if (/^\d+$/.test(n)) {
      if (n.length === 13 && eanCheckOk(n)) return 'EAN_13';
      if (n.length === 8 && eanCheckOk(n)) return 'EAN_8';
      if (n.length === 12 && eanCheckOk('0' + n)) return 'UPC_A';
    }
    return 'CODE_128';
  }

  /** Disegna il codice nel canvas; se il numero non va bene per il formato ripiega su Code 128. */
  function drawCode(canvas, text, formatId) {
    if (!window.bwipjs) return { ok: false, error: 'Libreria codici non caricata' };
    const tryDraw = (f) => {
      const opts = { bcid: f.bcid, text, scale: 1, includetext: false };
      if (f.twoD) opts.padding = 2; else { opts.height = 18; opts.paddingwidth = 10; } // margine bianco per i lettori
      if (f.bcid === 'qrcode') opts.eclevel = 'M';
      // Prima prova a scala 1 per sapere quanti moduli ha il codice, poi scala intera che riempie lo spazio:
      // ogni barra resta di pixel interi, così le casse lo leggono meglio.
      window.bwipjs.toCanvas(canvas, opts);
      const dpr = window.devicePixelRatio || 1;
      let avail = ((canvas.parentElement && canvas.parentElement.clientWidth) || 0) - 40;
      if (avail <= 0) avail = 320;
      if (f.twoD) avail *= 0.75;
      opts.scale = Math.max(1, Math.floor((avail * dpr) / canvas.width));
      if (!f.twoD) opts.scaleY = opts.scale;
      window.bwipjs.toCanvas(canvas, opts);
      canvas.className = f.twoD ? 'twod' : 'oned';
      canvas.style.width = (canvas.width / dpr) + 'px';
    };
    const f = fmt(formatId);
    try { tryDraw(f); return { ok: true, format: f, fellBack: false }; } catch (e) { /* ripiego */ }
    if (f.id !== 'CODE_128') {
      try { tryDraw(fmt('CODE_128')); return { ok: true, format: fmt('CODE_128'), fellBack: true }; } catch (e) { /* niente */ }
    }
    return { ok: false, error: 'Codice non valido' };
  }

  // ---------- Foto ----------
  function pickImage() {
    return new Promise((resolve) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = 'image/*';
      input.onchange = () => resolve(input.files && input.files[0] ? input.files[0] : null);
      input.click();
    });
  }
  function loadImage(file) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => { resolve(img); URL.revokeObjectURL(url); };
      img.onerror = () => { reject(new Error('Immagine non leggibile')); URL.revokeObjectURL(url); };
      img.src = url;
    });
  }
  async function imageToDataUrl(file, maxSide = 1400, quality = 0.85) {
    const img = await loadImage(file);
    const scale = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight));
    const c = document.createElement('canvas');
    c.width = Math.round(img.naturalWidth * scale);
    c.height = Math.round(img.naturalHeight * scale);
    c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
    return c.toDataURL('image/jpeg', quality);
  }

  // ---------- Fotocamera e lettura codici ----------
  function makeReader() {
    if (!window.ZXing) throw new Error('Libreria scanner non caricata');
    const hints = new Map();
    hints.set(ZXing.DecodeHintType.TRY_HARDER, true);
    return new ZXing.BrowserMultiFormatReader(hints, 250);
  }
  const formatName = (result) => ZXing.BarcodeFormat[result.getBarcodeFormat()];

  /** Legge un codice da una foto (galleria o scatto). */
  async function scanFile(file) {
    const reader = makeReader();
    const url = await imageToDataUrl(file, 1600, 0.92);
    try {
      const r = await reader.decodeFromImageUrl(url);
      return { value: r.getText(), format: formatName(r) };
    } catch (e) {
      return null;
    } finally {
      try { reader.reset(); } catch (e) { /* */ }
    }
  }

  /**
   * Scanner a tutto schermo. onCode(text, formatName) riceve ogni codice letto:
   * se restituisce true lo scanner si chiude.
   */
  function openScanner({ title, frameHeight = 220, extraButtons = true, onCode, onManual, onClose }) {
    const el = document.createElement('div');
    el.className = 'scanner';
    el.innerHTML = `
      <video playsinline muted autoplay></video>
      <div class="frame" style="height:${frameHeight}px"></div>
      <div class="top"><button class="icon-btn" data-x>${icon('close')}</button><h2>${esc(title)}</h2></div>
      <div class="bottom">
        <div class="msg" hidden></div>
        ${extraButtons ? `<button class="btn tonal block" data-photo>Da una foto</button>
        <div style="height:12px"></div>
        <button class="btn tonal block" data-manual>Scrivi il numero a mano</button>` : ''}
      </div>`;
    $overlay.appendChild(el);
    const video = el.querySelector('video');
    const msg = el.querySelector('.msg');
    const showMsg = (html) => { msg.hidden = !html; msg.innerHTML = html || ''; };
    let reader = null;
    let closed = false;

    const close = (cb) => {
      if (closed) return;
      closed = true;
      try { reader && reader.reset(); } catch (e) { /* */ }
      el.remove();
      if (cb) cb();
    };
    el.querySelector('[data-x]').onclick = () => close(onClose);
    if (extraButtons) {
      el.querySelector('[data-manual]').onclick = () => close(onManual || onClose);
      el.querySelector('[data-photo]').onclick = async () => {
        const file = await pickImage();
        if (!file) return;
        showMsg('Lettura della foto…');
        const r = await scanFile(file);
        if (r && onCode(r.value, r.format)) close();
        else if (!closed) showMsg('Nessun codice trovato nella foto');
      };
    }

    try {
      reader = makeReader();
      reader.decodeFromConstraints({ audio: false, video: { facingMode: { ideal: 'environment' } } }, video, (result) => {
        if (closed || !result) return;
        if (onCode(result.getText(), formatName(result))) close();
      }).catch((e) => {
        showMsg('Fotocamera non disponibile: ' + esc(e && e.name === 'NotAllowedError' ? 'permesso negato. Puoi abilitarlo nelle impostazioni del browser.' : (e && e.message) || e));
      });
    } catch (e) {
      showMsg(esc(e.message));
    }
    return { close: () => close(), showMsg, el };
  }

  // ---------- Schermo acceso ----------
  let wakeLock = null;
  async function keepAwake(on) {
    try {
      if (on && 'wakeLock' in navigator) {
        wakeLock = await navigator.wakeLock.request('screen');
      } else if (!on && wakeLock) {
        const l = wakeLock;
        wakeLock = null;
        await l.release();
      }
    } catch (e) { /* non supportato */ }
  }
  let wantAwake = false;
  document.addEventListener('visibilitychange', () => { if (wantAwake && document.visibilityState === 'visible') keepAwake(true); });

  // ---------- Router ----------
  const routes = [];
  const route = (re, fn) => routes.push([re, fn]);
  function go(hash, replace = false) {
    if (replace) location.replace(hash); else location.hash = hash;
  }
  function back(fallback = '#/') {
    if (history.length > 1 && sessionStorage.getItem('fc-nav')) history.back(); else go(fallback, true);
  }
  async function render() {
    $overlay.innerHTML = '';
    document.querySelectorAll('.dialog-back,.menu').forEach((e) => e.remove());
    wantAwake = false;
    keepAwake(false);
    const hash = location.hash || '#/';
    const [path, query] = hash.slice(1).split('?');
    const params = new URLSearchParams(query || '');
    for (const [re, fn] of routes) {
      const m = path.match(re);
      if (m) { window.scrollTo(0, 0); await fn(m, params); return; }
    }
    go('#/', true);
  }
  window.addEventListener('hashchange', () => { sessionStorage.setItem('fc-nav', '1'); render(); });

  function bar(title, { backBtn = false, actions = '', color = null } = {}) {
    const style = color ? `style="background:${intToHex(color)};color:${textOn(color)}"` : '';
    return `<div class="bar ${color ? 'colored-bar' : ''}" ${style}>
      ${backBtn ? `<button class="icon-btn" data-back aria-label="Indietro">${icon('back')}</button>` : ''}
      <h1>${esc(title)}</h1>${actions}</div>`;
  }
  function wireBack(fallback) {
    const b = $app.querySelector('[data-back]');
    if (b) b.onclick = () => back(fallback);
  }

  function cardFace(c, big = false) {
    const hex = intToHex(c.color);
    const star = c.favorite ? '<span class="star">★</span>' : '';
    if (c.frontPhoto) {
      return `<div class="cardface ${big ? 'big' : ''}" style="background:${hex}">
        <img src="${c.frontPhoto}" alt=""><div class="shade"></div><div class="pname">${esc(c.name)}</div>${star}</div>`;
    }
    return `<div class="cardface ${big ? 'big' : ''}" style="background:${hex};color:${textOn(hex)}">
      <div class="name ${c.name.length > 12 ? 'long' : ''}">${esc(c.name || 'Nome carta')}</div>${star}</div>`;
  }

  // ---------- Home ----------
  const SORTS = { USAGE: 'Più usate', RECENT: 'Usate di recente', NAME: 'Nome A-Z', ADDED: 'Aggiunte di recente' };
  route(/^\/$/, async () => {
    const cards = await Cards.all();
    let query = '';
    let searching = false;
    let sort = localStorage.getItem('fc-sort') || 'USAGE';

    $app.innerHTML = `
      <div class="bar">
        <h1 data-title>Le mie carte</h1>
        <input class="search" data-q placeholder="Cerca una carta" hidden>
        <button class="icon-btn" data-search aria-label="Cerca">${icon('search')}</button>
        <button class="icon-btn" data-menu aria-label="Menu">${icon('more')}</button>
        <button class="icon-btn" data-settings aria-label="Impostazioni">${icon('settings')}</button>
      </div>
      <div class="page"><div data-banner></div><div data-list></div></div>
      <button class="fab" data-add>${icon('add').replace('<svg', '<svg width="22" height="22" fill="currentColor"')} Aggiungi carta</button>`;

    const banner = $app.querySelector('[data-banner]');
    if (isIOS && !isStandalone() && !localStorage.getItem('fc-hide-install')) {
      banner.innerHTML = `<div class="banner"><div><b>Installa FreeCard</b>: tocca il pulsante Condividi di Safari e poi «Aggiungi alla schermata Home». Così funziona offline e i dati non vengono cancellati.</div><button class="x" aria-label="Chiudi">✕</button></div>`;
      banner.querySelector('.x').onclick = () => { localStorage.setItem('fc-hide-install', '1'); banner.innerHTML = ''; };
    }

    const list = $app.querySelector('[data-list]');
    const draw = () => {
      if (!cards.length) {
        list.innerHTML = `<div class="empty"><h2>Nessuna carta ancora</h2>
          <p>Tocca «Aggiungi carta», scegli il negozio e inquadra il codice a barre della tessera.<br>Oppure ricevi le carte da un altro telefono con il QR (menu ⋮).</p></div>`;
        return;
      }
      const q = query.trim().toLowerCase();
      let items = cards.filter((c) => !q || c.name.toLowerCase().includes(q) || (c.note || '').toLowerCase().includes(q));
      const byName = (a, b) => a.name.localeCompare(b.name, 'it', { sensitivity: 'base' });
      const sorters = {
        USAGE: (a, b) => (b.useCount - a.useCount) || byName(a, b),
        RECENT: (a, b) => (b.lastUsedAt - a.lastUsedAt) || byName(a, b),
        NAME: byName,
        ADDED: (a, b) => b.createdAt - a.createdAt
      };
      items.sort(sorters[sort] || sorters.USAGE);
      const favs = items.filter((c) => c.favorite);
      const others = items.filter((c) => !c.favorite);
      const tiles = (arr) => `<div class="grid">${arr.map((c) => `<a href="#/card/${c.id}" style="text-decoration:none">${cardFace(c)}</a>`).join('')}</div>`;
      let html = '';
      if (favs.length) html += `<div class="section">Preferite</div>${tiles(favs)}${others.length ? '<div class="section">Tutte le carte</div>' : ''}`;
      html += tiles(others);
      if (!items.length) html = `<p class="center muted">Nessuna carta trovata per «${esc(query)}»</p>`;
      list.innerHTML = html;
    };
    draw();

    const qInput = $app.querySelector('[data-q]');
    const title = $app.querySelector('[data-title]');
    qInput.oninput = () => { query = qInput.value; draw(); };
    $app.querySelector('[data-search]').onclick = () => {
      searching = !searching;
      qInput.hidden = !searching;
      title.hidden = searching;
      $app.querySelector('[data-search]').innerHTML = icon(searching ? 'close' : 'search');
      if (searching) qInput.focus(); else { query = ''; qInput.value = ''; draw(); }
    };
    $app.querySelector('[data-menu]').onclick = (e) => {
      popupMenu(e.currentTarget, [
        ...Object.entries(SORTS).map(([k, label]) => ({
          label: (k === sort ? '✓ ' : '  ') + label,
          action: () => { sort = k; localStorage.setItem('fc-sort', k); draw(); }
        })),
        '-',
        { label: 'Condividi tutte (QR)', action: () => go('#/share/all') },
        { label: 'Ricevi carte (QR)', action: () => go('#/receive') }
      ]);
    };
    $app.querySelector('[data-settings]').onclick = () => go('#/settings');
    $app.querySelector('[data-add]').onclick = () => go('#/pick');
  });

  // ---------- Scelta negozio ----------
  route(/^\/pick$/, async () => {
    $app.innerHTML = `${bar('Scegli il negozio', { backBtn: true })}
      <div class="page" style="padding-bottom:40px">
        <input class="search" data-q placeholder="Cerca negozio">
        <div data-list style="margin-top:8px"></div>
      </div>`;
    wireBack('#/');
    const qInput = $app.querySelector('[data-q]');
    const list = $app.querySelector('[data-list]');
    const draw = () => {
      const q = qInput.value.trim();
      const lq = q.toLowerCase();
      const stores = window.STORES.filter((s) => !lq || s.name.toLowerCase().includes(lq));
      let html = `<button class="store" data-custom><span class="chip" style="background:var(--primary-c);color:var(--on-primary-c)">+</span>
        <b>${q ? `Crea «${esc(q)}»` : 'Altra carta (nome a scelta)'}</b></button><hr style="border:0;border-top:1px solid var(--surface-v)">`;
      let cat = null;
      stores.forEach((s) => {
        if (s.category !== cat) { cat = s.category; html += `<div class="section">${esc(cat)}</div>`; }
        html += `<button class="store" data-key="${esc(s.key)}"><span class="chip" style="background:${s.color};color:${textOn(s.color)}">${esc(s.name[0])}</span>${esc(s.name)}</button>`;
      });
      list.innerHTML = html;
      list.querySelector('[data-custom]').onclick = () => go(`#/edit/new?name=${encodeURIComponent(q)}`, true);
      list.querySelectorAll('[data-key]').forEach((b) => { b.onclick = () => go(`#/edit/new?store=${b.dataset.key}`, true); });
    };
    qInput.oninput = draw;
    draw();
  });

  // ---------- Nuova / modifica carta ----------
  route(/^\/edit\/(new|\d+)$/, async (m, params) => {
    const isNew = m[1] === 'new';
    const store = storeByKey(params.get('store'));
    let card;
    if (isNew) {
      card = newCard({ name: store ? store.name : (params.get('name') || ''), storeKey: store ? store.key : null, color: hexToInt(store ? store.color : '#00897B') });
    } else {
      card = await Cards.get(m[1]);
      if (!card) { go('#/', true); return; }
    }
    const draft = { ...card };
    let formatChosen = !isNew;

    $app.innerHTML = `${bar(isNew ? 'Nuova carta' : 'Modifica carta', { backBtn: true, actions: '<button class="btn text" data-save>Salva</button>' })}
      <div class="page stack" style="padding-bottom:60px">
        <div data-face></div>
        <label class="field"><span>Nome</span><input data-name></label>
        <label class="field"><span>Numero / contenuto del codice</span><input data-number autocomplete="off"></label>
        <button class="btn tonal block" data-scan>Scansiona il codice della tessera</button>
        <label class="field"><span>Tipo di codice</span><select data-format>
          <option value="">Automatico</option>${FORMATS.map((f) => `<option value="${f.id}">${f.label}</option>`).join('')}</select></label>
        <div data-preview></div>
        <label class="field"><span>Numero da mostrare (facoltativo)</span><input data-display>
          <div class="hint">Se il numero stampato sulla tessera è diverso dal contenuto del codice</div></label>
        <div class="section">Colore</div>
        <div class="colors" data-colors></div>
        <div class="section">Foto della tessera (facoltative)</div>
        <div class="photos"><div class="photo" data-ph="frontPhoto"></div><div class="photo" data-ph="backPhoto"></div></div>
        <label class="field"><span>Note</span><textarea rows="3" data-note></textarea></label>
        <button class="btn plain block" data-cancel>Annulla</button>
      </div>`;
    wireBack('#/');
    const $ = (s) => $app.querySelector(s);
    $('[data-name]').value = draft.name;
    $('[data-number]').value = draft.number;
    $('[data-display]').value = draft.displayNumber || '';
    $('[data-note]').value = draft.note || '';
    $('[data-format]').value = formatChosen ? draft.format : '';

    const effectiveFormat = () => (formatChosen ? draft.format : guessFormat(draft.number));
    const drawFace = () => { $('[data-face]').innerHTML = cardFace(draft, true); };
    const drawPreview = () => {
      const p = $('[data-preview]');
      const n = draft.number.trim();
      if (!n) { p.innerHTML = ''; return; }
      p.innerHTML = '<div class="codebox"><canvas></canvas></div><div class="error" data-warn></div>';
      const r = drawCode(p.querySelector('canvas'), n, effectiveFormat());
      if (!r.ok) p.querySelector('.codebox').innerHTML = `<span class="error">${esc(r.error)}</span>`;
      else if (r.fellBack) p.querySelector('[data-warn]').textContent = 'Il numero non è valido per il formato scelto: verrà mostrato come Code 128.';
      if (!formatChosen) {
        const opt = $('[data-format]').querySelector('option[value=""]');
        opt.textContent = 'Automatico (' + fmt(effectiveFormat()).label + ')';
      }
    };
    const drawColors = () => {
      const colors = [...new Set([...(store ? [store.color] : []), ...window.PALETTE, intToHex(draft.color)])];
      const cur = intToHex(draft.color);
      $('[data-colors]').innerHTML = colors.map((c) =>
        `<button class="swatch ${c === cur ? 'sel' : ''}" data-c="${c}" style="background:${c};color:${textOn(c)}">${c === cur ? '✓' : ''}</button>`).join('');
      $app.querySelectorAll('[data-c]').forEach((b) => { b.onclick = () => { draft.color = hexToInt(b.dataset.c); drawColors(); drawFace(); }; });
    };
    const drawPhotos = () => {
      $app.querySelectorAll('[data-ph]').forEach((el) => {
        const key = el.dataset.ph;
        const label = key === 'frontPhoto' ? 'Fronte' : 'Retro';
        el.innerHTML = `<button class="slot">${draft[key] ? `<img src="${draft[key]}" alt="">` : '+ ' + label}</button>
          ${draft[key] ? '<button class="btn text" data-rm>Rimuovi</button>' : ''}`;
        el.querySelector('.slot').onclick = async () => {
          const file = await pickImage();
          if (!file) return;
          try { draft[key] = await imageToDataUrl(file); } catch (e) { alertBox('Impossibile usare questa foto'); }
          drawPhotos(); drawFace();
        };
        const rm = el.querySelector('[data-rm]');
        if (rm) rm.onclick = () => { draft[key] = null; drawPhotos(); drawFace(); };
      });
    };
    drawFace(); drawPreview(); drawColors(); drawPhotos();

    $('[data-name]').oninput = (e) => { draft.name = e.target.value; drawFace(); };
    $('[data-number]').oninput = (e) => { draft.number = e.target.value; drawPreview(); };
    $('[data-display]').oninput = (e) => { draft.displayNumber = e.target.value; };
    $('[data-note]').oninput = (e) => { draft.note = e.target.value; };
    $('[data-format]').onchange = (e) => {
      formatChosen = !!e.target.value;
      if (formatChosen) draft.format = e.target.value;
      drawPreview();
    };
    const scan = () => openScanner({
      title: 'Inquadra il codice a barre',
      onCode: (text, format) => {
        if (text.startsWith(Codec.PREFIX)) return false; // QR di condivisione: non è una tessera
        draft.number = text;
        $('[data-number]').value = text;
        if (FORMATS.some((f) => f.id === format)) { draft.format = format; formatChosen = true; $('[data-format]').value = format; }
        drawPreview();
        return true;
      },
      onManual: () => setTimeout(() => $('[data-number]').focus(), 50)
    });
    $('[data-scan]').onclick = scan;
    $('[data-cancel]').onclick = () => back('#/');
    $('[data-save]').onclick = async () => {
      draft.name = draft.name.trim();
      draft.number = draft.number.trim();
      if (!draft.name) return alertBox('Scrivi il nome della carta');
      if (!draft.number) return alertBox('Inserisci o scansiona il numero della carta');
      draft.format = effectiveFormat();
      draft.note = (draft.note || '').trim();
      draft.displayNumber = (draft.displayNumber || '').trim();
      try {
        if (isNew) {
          const id = await Cards.add(draft);
          go(`#/card/${id}`, true);
        } else {
          await Cards.put(draft);
          back(`#/card/${draft.id}`);
        }
      } catch (e) {
        alertBox('Salvataggio non riuscito: ' + e.message);
      }
    };
    if (isNew) scan();
  });

  // ---------- Carta aperta alla cassa ----------
  route(/^\/card\/(\d+)$/, async (m) => {
    let c = await Cards.get(m[1]);
    if (!c) { go('#/', true); return; }
    c.useCount = (c.useCount || 0) + 1;
    c.lastUsedAt = Date.now();
    await Cards.put(c);
    wantAwake = true;
    keepAwake(true);

    const draw = () => {
      $app.innerHTML = `${bar(c.name, {
        backBtn: true, color: c.color,
        actions: `<button class="icon-btn" data-fav aria-label="Preferita" style="${c.favorite ? 'color:#FFD54F' : 'opacity:.55'}">${icon('star')}</button>
          <button class="icon-btn" data-share aria-label="Condividi">${icon('share')}</button>
          <button class="icon-btn" data-edit aria-label="Modifica">${icon('edit')}</button>
          <button class="icon-btn" data-del aria-label="Elimina">${icon('del')}</button>`
      })}
      <div class="page stack" style="padding-bottom:40px">
        <div class="codebox"><canvas></canvas>
          <div class="num">${esc(c.displayNumber || c.number)}</div><div class="fmt"></div></div>
        ${cardFace(c, true)}
        ${c.frontPhoto || c.backPhoto ? `<div class="photos">${[c.frontPhoto, c.backPhoto].filter(Boolean).map((p) =>
          `<div class="photo"><button class="slot" data-zoom><img src="${p}" alt=""></button></div>`).join('')}</div>` : ''}
        ${c.note ? `<div><div class="section">Note</div><div style="white-space:pre-wrap">${esc(c.note)}</div></div>` : ''}
        <p class="muted" style="font-size:13px">Usata ${c.useCount} ${c.useCount === 1 ? 'volta' : 'volte'}${'wakeLock' in navigator ? '' : ' · alza la luminosità per la cassa'}</p>
      </div>`;
      wireBack('#/');
      const r = drawCode($app.querySelector('.codebox canvas'), c.number, c.format);
      $app.querySelector('.fmt').textContent = r.ok ? r.format.label : 'Impossibile disegnare il codice';
      $app.querySelector('[data-fav]').onclick = async () => { c.favorite = !c.favorite; await Cards.put(c); draw(); };
      $app.querySelector('[data-edit]').onclick = () => go(`#/edit/${c.id}`);
      $app.querySelector('[data-del]').onclick = async () => {
        if (await confirmBox(`Eliminare «${c.name}»?`, 'La carta e le sue foto verranno cancellate dal telefono.', 'Elimina', true)) {
          await Cards.del(c.id);
          go('#/', true);
        }
      };
      $app.querySelector('[data-share]').onclick = (e) => popupMenu(e.currentTarget, [
        { label: 'QR per un altro telefono', action: () => go(`#/share/${c.id}`) },
        {
          label: 'Invia il numero come testo', action: async () => {
            const text = `${c.name}: ${c.displayNumber || c.number}`;
            if (navigator.share) { try { await navigator.share({ text }); } catch (err) { /* annullato */ } } else {
              try { await navigator.clipboard.writeText(text); toast('Numero copiato'); } catch (err) { alertBox(text); }
            }
          }
        }
      ]);
      $app.querySelectorAll('[data-zoom]').forEach((b) => {
        b.onclick = () => {
          const v = document.createElement('div');
          v.className = 'dialog-back';
          v.style.background = '#000';
          v.innerHTML = `<img src="${b.querySelector('img').src}" style="max-width:100%;max-height:100%">`;
          v.onclick = () => v.remove();
          document.body.appendChild(v);
        };
      });
    };
    draw();
  });

  // ---------- Condividi con QR ----------
  route(/^\/share\/(all|\d+)$/, async (m) => {
    const all = m[1] === 'all';
    let cards = all ? await Cards.all() : [await Cards.get(m[1])].filter(Boolean);
    cards.sort((a, b) => a.name.localeCompare(b.name, 'it'));
    $app.innerHTML = `${bar(all ? 'Condividi tutte le carte' : 'Condividi carta', { backBtn: true })}<div class="page stack center" data-body></div>`;
    wireBack('#/');
    const body = $app.querySelector('[data-body]');
    if (!cards.length) { body.innerHTML = '<p>Non ci sono carte da condividere.</p>'; return; }
    wantAwake = true;
    keepAwake(true);
    const qrs = await Codec.encode(cards);
    let i = 0;
    let playing = true;
    body.innerHTML = `<h2 style="margin:4px 0">${cards.length === 1 ? esc(cards[0].name) : cards.length + ' carte'}</h2>
      <div class="codebox"><canvas></canvas></div>
      ${qrs.length > 1 ? `<div><b data-count></b></div>
      <div class="row"><button class="btn tonal" data-prev>‹ Indietro</button><button class="btn tonal" data-play>Pausa</button><button class="btn tonal" data-next>Avanti ›</button></div>` : ''}
      <p class="muted">Sull'altro telefono apri FreeCard (app o webapp) → menu ⋮ → «Ricevi carte» e inquadra
      ${qrs.length > 1 ? 'questi codici finché non li ha letti tutti (scorrono da soli).' : 'questo codice.'}<br>
      Le foto delle tessere non vengono inviate: per quelle usa il backup.</p>`;
    const canvas = body.querySelector('canvas');
    const show = () => {
      drawCode(canvas, qrs[i], 'QR_CODE');
      const cnt = body.querySelector('[data-count]');
      if (cnt) cnt.textContent = `Codice ${i + 1} di ${qrs.length}`;
    };
    show();
    if (qrs.length > 1) {
      const timer = setInterval(() => {
        if (!document.body.contains(canvas)) { clearInterval(timer); return; }
        if (playing) { i = (i + 1) % qrs.length; show(); }
      }, 900);
      const playBtn = body.querySelector('[data-play]');
      body.querySelector('[data-prev]').onclick = () => { playing = false; playBtn.textContent = 'Scorri'; i = (i - 1 + qrs.length) % qrs.length; show(); };
      body.querySelector('[data-next]').onclick = () => { playing = false; playBtn.textContent = 'Scorri'; i = (i + 1) % qrs.length; show(); };
      playBtn.onclick = () => { playing = !playing; playBtn.textContent = playing ? 'Pausa' : 'Scorri'; };
    }
  });

  // ---------- Ricevi con QR ----------
  route(/^\/receive$/, async () => {
    $app.innerHTML = `${bar('Ricevi carte', { backBtn: true })}<div class="page"></div>`;
    wireBack('#/');
    const existing = new Set((await Cards.all()).map((c) => c.name + '\u0000' + c.number));
    const col = new Codec.Collector();
    let done = false;
    const sc = openScanner({
      title: 'Ricevi carte',
      frameHeight: 300,
      extraButtons: false,
      onCode: (text) => {
        if (done || !col.add(text)) return false;
        if (col.expected > 1) {
          sc.showMsg(`Letti ${col.received} codici su ${col.expected}<div class="progress"><div style="width:${Math.round(100 * col.received / col.expected)}%"></div></div>`);
        }
        if (!col.isComplete()) return false;
        done = true;
        col.result().then(async (cards) => {
          sc.close();
          const fresh = cards.filter((c) => !existing.has(c.name + '\u0000' + c.number));
          const listHtml = cards.map((c) => {
            const dup = existing.has(c.name + '\u0000' + c.number);
            return `<div style="display:flex;align-items:center;gap:12px;padding:4px 0">
              <span class="chip" style="width:28px;height:18px;background:${intToHex(c.color)}"></span>
              <div>${esc(c.name)}${dup ? '<div class="muted" style="font-size:12px">già presente</div>' : ''}</div></div>`;
          }).join('');
          const ok = await dialog({
            title: cards.length === 1 ? 'Carta ricevuta' : `${cards.length} carte ricevute`,
            body: listHtml,
            actions: [{ label: 'Annulla', value: false }, { label: fresh.length ? `Importa ${fresh.length}` : 'Nessuna nuova', value: true, disabled: !fresh.length }]
          });
          if (ok) {
            for (const c of fresh) await Cards.add(newCard(c));
            toast(`Importate ${fresh.length} carte`);
          }
          go('#/', true);
        }).catch(() => {
          sc.close();
          alertBox('Codice non leggibile, riprova.').then(() => go('#/', true));
        });
        return false;
      },
      onClose: () => back('#/')
    });
    sc.showMsg("Sull'altro telefono apri la carta (o il menu ⋮ → «Condividi tutte») e inquadra il QR che compare.");
  });

  // ---------- Impostazioni e backup ----------
  route(/^\/settings$/, async () => {
    $app.innerHTML = `${bar('Impostazioni', { backBtn: true })}
      <div class="page stack">
        <div class="section">Backup</div>
        <p class="muted">Le carte sono salvate solo su questo telefono. Il file di backup è compatibile con l'app Android FreeCard (foto comprese).</p>
        <button class="btn block" data-export>Esporta backup</button>
        <button class="btn plain block" data-import>Importa backup</button>
        <div class="section">Informazioni</div>
        <p>FreeCard webapp versione __BUILD__</p>
        <p class="muted">${isStandalone() ? 'Installata nella schermata Home.' : (isIOS ? 'Per installarla: in Safari tocca Condividi → «Aggiungi alla schermata Home».' : "Puoi installarla dal menu del browser («Installa app» o «Aggiungi a schermata Home»).")}</p>
      </div>`;
    wireBack('#/');
    $app.querySelector('[data-export]').onclick = async () => {
      const cards = await Cards.all();
      const strip = (p) => (p ? p.split(',')[1] : undefined);
      const out = {
        app: 'FreeCard', version: 1,
        cards: cards.map((c) => ({
          name: c.name, number: c.number, format: c.format, color: c.color, storeKey: c.storeKey || null,
          note: c.note || '', displayNumber: c.displayNumber || '', favorite: !!c.favorite,
          createdAt: c.createdAt, lastUsedAt: c.lastUsedAt || 0, useCount: c.useCount || 0,
          frontPhoto: strip(c.frontPhoto), backPhoto: strip(c.backPhoto)
        }))
      };
      const name = `freecard-${new Date().toISOString().slice(0, 10)}.json`;
      const file = new File([JSON.stringify(out)], name, { type: 'application/json' });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        try { await navigator.share({ files: [file], title: name }); return; } catch (e) { if (e.name === 'AbortError') return; }
      }
      const a = document.createElement('a');
      a.href = URL.createObjectURL(file);
      a.download = name;
      document.body.appendChild(a);
      a.click();
      setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
      toast(`Backup creato: ${cards.length} carte`);
    };
    $app.querySelector('[data-import]').onclick = () => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = '.json,application/json,text/plain';
      input.onchange = async () => {
        const f = input.files && input.files[0];
        if (!f) return;
        try {
          const root = JSON.parse(await f.text());
          const existing = new Set((await Cards.all()).map((c) => c.name + '\u0000' + c.number));
          let added = 0;
          for (const o of root.cards || []) {
            if (!o.name || !o.number || existing.has(o.name + '\u0000' + o.number)) continue;
            await Cards.add(newCard({
              name: String(o.name), number: String(o.number), format: o.format || 'CODE_128',
              color: typeof o.color === 'number' ? o.color | 0 : hexToInt('#00897B'), storeKey: o.storeKey || null,
              note: o.note || '', displayNumber: o.displayNumber || '', favorite: !!o.favorite,
              createdAt: o.createdAt || Date.now(), lastUsedAt: o.lastUsedAt || 0, useCount: o.useCount || 0,
              frontPhoto: o.frontPhoto ? 'data:image/jpeg;base64,' + o.frontPhoto : null,
              backPhoto: o.backPhoto ? 'data:image/jpeg;base64,' + o.backPhoto : null
            }));
            added++;
          }
          alertBox(`Importate ${added} carte.`);
        } catch (e) {
          alertBox('File non valido: ' + e.message);
        }
      };
      input.click();
    };
  });

  // ---------- Avvio ----------
  if ('serviceWorker' in navigator && location.protocol === 'https:') {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
  render();
})();
