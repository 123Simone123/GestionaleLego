// Formato dei QR di condivisione FreeCard, identico all'app Android:
//   FREECARD1|z|<id>|<parte>|<totale>|<dati>
// <dati> = pezzo del base64 di un JSON compresso deflate-raw (flag z) o non compresso (flag j).
(function (root) {
  'use strict';
  const PREFIX = 'FREECARD1|';
  const CHUNK = 600;

  function toB64(bytes) {
    let s = '';
    for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    return btoa(s);
  }
  function fromB64(b64) {
    const s = atob(b64);
    const out = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
    return out;
  }
  async function pipe(bytes, transform) {
    const stream = new Blob([bytes]).stream().pipeThrough(transform);
    return new Uint8Array(await new Response(stream).arrayBuffer());
  }
  const canZip = typeof CompressionStream !== 'undefined' && typeof DecompressionStream !== 'undefined';

  // Carta app -> forma compatta
  function compact(c) {
    const o = { n: c.name, k: c.number, f: c.format, col: c.color | 0 };
    if (c.storeKey) o.s = c.storeKey;
    if (c.note) o.o = c.note;
    if (c.displayNumber) o.d = c.displayNumber;
    if (c.favorite) o.p = 1;
    return o;
  }
  function expand(o) {
    return {
      name: String(o.n), number: String(o.k), format: o.f || 'CODE_128',
      color: typeof o.col === 'number' ? o.col | 0 : 0xFF00897B | 0,
      storeKey: o.s || null, note: o.o || '', displayNumber: o.d || '', favorite: o.p === 1
    };
  }

  async function encode(cards) {
    const json = JSON.stringify({ v: 1, c: cards.map(compact) });
    const raw = new TextEncoder().encode(json);
    let flag = 'j', bytes = raw;
    if (canZip) { flag = 'z'; bytes = await pipe(raw, new CompressionStream('deflate-raw')); }
    const data = toB64(bytes);
    const abc = 'abcdefghijkmnpqrstuvwxyz23456789';
    let id = '';
    for (let i = 0; i < 4; i++) id += abc[Math.floor(Math.random() * abc.length)];
    const parts = [];
    for (let i = 0; i < data.length; i += CHUNK) parts.push(data.slice(i, i + CHUNK));
    if (!parts.length) parts.push('');
    return parts.map((p, i) => `${PREFIX}${flag}|${id}|${i + 1}|${parts.length}|${p}`);
  }

  class Collector {
    constructor() { this.id = null; this.flag = 'z'; this.total = 0; this.parts = new Map(); }
    get received() { return this.parts.size; }
    get expected() { return this.total; }
    add(text) {
      if (typeof text !== 'string' || !text.startsWith(PREFIX)) return false;
      const f = text.split('|');
      if (f.length < 6) return false;
      const data = f.slice(5).join('|');
      const idx = parseInt(f[3], 10), tot = parseInt(f[4], 10);
      if (!(tot >= 1 && idx >= 1 && idx <= tot)) return false;
      if (f[2] !== this.id) { this.id = f[2]; this.flag = f[1]; this.total = tot; this.parts.clear(); }
      this.parts.set(idx, data);
      return true;
    }
    isComplete() { return this.total > 0 && this.parts.size === this.total; }
    async result() {
      let data = '';
      for (let i = 1; i <= this.total; i++) data += this.parts.get(i) || '';
      let bytes = fromB64(data);
      if (this.flag === 'z') {
        if (!canZip) throw new Error('Questo browser non sa decomprimere: aggiornalo.');
        bytes = await pipe(bytes, new DecompressionStream('deflate-raw'));
      }
      const obj = JSON.parse(new TextDecoder().decode(bytes));
      return (obj.c || []).map(expand);
    }
  }

  root.FreeCardCodec = { PREFIX, encode, Collector, toB64, fromB64 };
})(typeof window !== 'undefined' ? window : globalThis);
