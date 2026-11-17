// Controlli in CI: le librerie del browser si caricano e i formati usati da app.js sono validi.
const fs = require('fs');
const vm = require('vm');
const path = require('path');
const site = process.argv[2];

function loadBrowser(file, globalName) {
  const ctx = { window: {}, self: {}, console, setTimeout, clearTimeout, atob, btoa, TextEncoder, TextDecoder, navigator: { userAgent: 'node' } };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(site, file), 'utf8'), ctx);
  const g = ctx[globalName] || ctx.window[globalName] || ctx.self[globalName];
  if (!g) throw new Error(`${file} non definisce ${globalName}`);
  return g;
}

const ZX = loadBrowser('vendor/zxing.min.js', 'ZXing');
for (const k of ['BrowserMultiFormatReader', 'DecodeHintType', 'BarcodeFormat']) {
  if (!ZX[k]) throw new Error('ZXing.' + k + ' mancante');
}
if (typeof ZX.BrowserMultiFormatReader.prototype.decodeFromConstraints !== 'function') throw new Error('decodeFromConstraints mancante');
if (typeof ZX.BrowserMultiFormatReader.prototype.decodeFromImageUrl !== 'function') throw new Error('decodeFromImageUrl mancante');
for (const f of ['EAN_13', 'EAN_8', 'UPC_A', 'UPC_E', 'CODE_128', 'CODE_39', 'CODE_93', 'ITF', 'CODABAR', 'QR_CODE', 'PDF_417', 'AZTEC', 'DATA_MATRIX']) {
  if (ZX.BarcodeFormat[ZX.BarcodeFormat[f]] !== f) throw new Error('BarcodeFormat ' + f);
}
const B = loadBrowser('vendor/bwip-js-min.js', 'bwipjs');
if (typeof B.toCanvas !== 'function') throw new Error('bwipjs.toCanvas mancante');

// Formati e opzioni come in app.js, provati con la versione Node di bwip-js
const bwip = require('bwip-js');
const samples = {
  ean13: '4006381333931', ean8: '96385074', upca: '036000291452', upce: '0123456', code128: 'ABC-123 àè',
  code39: 'CARD123', code93: 'CARD123', interleaved2of5: '12345678', rationalizedCodabar: 'A123456A',
  qrcode: 'FREECARD1|z|abcd|1|1|' + 'x'.repeat(600), pdf417: '1234567890', azteccode: '1234567890', datamatrix: '1234567890'
};
(async () => {
  for (const [bcid, text] of Object.entries(samples)) {
    const twoD = ['qrcode', 'pdf417', 'azteccode', 'datamatrix'].includes(bcid);
    const opts = { bcid, text, scale: twoD ? 4 : 3, includetext: false };
    if (!twoD) opts.height = 18;
    if (bcid === 'qrcode') opts.eclevel = 'M';
    await bwip.toBuffer(opts);
  }
  let bad = false;
  try { await bwip.toBuffer({ bcid: 'ean13', text: '12345', scale: 3, height: 18 }); } catch (e) { bad = true; }
  if (!bad) throw new Error('ean13 non valido dovrebbe dare errore');
  console.log('Librerie e formati OK');
})().catch((e) => { console.error(e); process.exit(1); });
