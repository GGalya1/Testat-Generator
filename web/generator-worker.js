// Laeuft als Web Worker, damit die Seite beim Erzeugen nicht stockt.
import { katalogLaden, zipErzeugen } from "./generator-kern.js";

self.onmessage = async (e) => {
  const { id, typ, auftrag } = e.data;
  try {
    if (typ === "katalog") {
      self.postMessage({ id, ok: true, katalog: await katalogLaden() });
    } else {
      const r = await zipErzeugen(auftrag);
      self.postMessage({ id, ...r }, r.ok ? [r.zip.buffer] : []);
    }
  } catch (err) {
    self.postMessage({ id, ok: false, fehler: String(err && err.message ? err.message : err) });
  }
};

// Erst jetzt ist onmessage gesetzt; vorher geschickte Nachrichten koennten verloren gehen.
self.postMessage({ bereit: true });
