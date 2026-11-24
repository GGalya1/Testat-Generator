// Minimaler ZIP-Schreiber (ohne Kompression, "stored"). Reicht fuer ein paar LaTeX-Dateien
// und braucht keine fremde Bibliothek. Dateinamen und Inhalte als UTF-8.

const CRC_TABELLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(bytes) {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) c = CRC_TABELLE[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function dosZeit(d) {
  const zeit = (d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1);
  const datum = ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
  return { zeit, datum };
}

/**
 * @param {Object<string,string>} dateien  Pfad im ZIP -> Textinhalt
 * @returns {Uint8Array} das fertige ZIP
 */
export function zipErstellen(dateien, jetzt = new Date()) {
  const enc = new TextEncoder();
  const { zeit, datum } = dosZeit(jetzt);
  const lokal = [];
  const zentral = [];
  let offset = 0;

  for (const [pfad, text] of Object.entries(dateien)) {
    const name = enc.encode(pfad);
    const daten = enc.encode(text);
    const crc = crc32(daten);

    const kopf = new DataView(new ArrayBuffer(30));
    kopf.setUint32(0, 0x04034b50, true); // local file header
    kopf.setUint16(4, 20, true);         // version needed
    kopf.setUint16(6, 0x0800, true);     // Bit 11: Namen in UTF-8
    kopf.setUint16(8, 0, true);          // stored
    kopf.setUint16(10, zeit, true);
    kopf.setUint16(12, datum, true);
    kopf.setUint32(14, crc, true);
    kopf.setUint32(18, daten.length, true);
    kopf.setUint32(22, daten.length, true);
    kopf.setUint16(26, name.length, true);
    kopf.setUint16(28, 0, true);
    lokal.push(new Uint8Array(kopf.buffer), name, daten);

    const z = new DataView(new ArrayBuffer(46));
    z.setUint32(0, 0x02014b50, true);    // central directory header
    z.setUint16(4, 20, true);            // version made by
    z.setUint16(6, 20, true);
    z.setUint16(8, 0x0800, true);
    z.setUint16(10, 0, true);
    z.setUint16(12, zeit, true);
    z.setUint16(14, datum, true);
    z.setUint32(16, crc, true);
    z.setUint32(20, daten.length, true);
    z.setUint32(24, daten.length, true);
    z.setUint16(28, name.length, true);
    z.setUint32(42, offset, true);       // Rest (extra, comment, disk, attrs) bleibt 0
    zentral.push(new Uint8Array(z.buffer), name);

    offset += 30 + name.length + daten.length;
  }

  const zentralGroesse = zentral.reduce((s, b) => s + b.length, 0);
  const ende = new DataView(new ArrayBuffer(22));
  ende.setUint32(0, 0x06054b50, true);   // end of central directory
  const anzahl = Object.keys(dateien).length;
  ende.setUint16(8, anzahl, true);
  ende.setUint16(10, anzahl, true);
  ende.setUint32(12, zentralGroesse, true);
  ende.setUint32(16, offset, true);

  const teile = [...lokal, ...zentral, new Uint8Array(ende.buffer)];
  const out = new Uint8Array(teile.reduce((s, b) => s + b.length, 0));
  let p = 0;
  for (const t of teile) {
    out.set(t, p);
    p += t.length;
  }
  return out;
}
