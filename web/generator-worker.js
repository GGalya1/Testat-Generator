// Laeuft als Web Worker, damit die Seite beim Erzeugen und beim Probelauf nicht stockt.
import { bearbeiten } from "./generator-kern.js";

self.onmessage = async (e) => {
  const { id, typ, auftrag } = e.data;
  try {
    const r = await bearbeiten(typ, auftrag);
    self.postMessage({ id, ...r }, r.ok && r.zip ? [r.zip.buffer] : []);
  } catch (err) {
    self.postMessage({ id, ok: false, fehler: String(err && err.message ? err.message : err) });
  }
};

// Erst jetzt ist onmessage gesetzt; vorher geschickte Nachrichten koennten verloren gehen.
self.postMessage({ bereit: true });
