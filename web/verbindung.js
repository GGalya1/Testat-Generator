// Verbindung von einer Seite zum Generator (generator-kern.js). Normal laeuft der Kern in einem
// Web Worker, damit die Seite beim Rechnen nicht stockt; ohne Module-Worker direkt auf der Seite.
// Benutzt von app.js (Uebungsblaetter) und probelauf.js (Code ausprobieren).

/**
 * Startet den Worker und liefert eine Funktion (typ, auftrag) => Promise<Antwort>.
 * typ: "katalog", "erzeugen" oder "ausfuehren" (siehe generator-kern.js, bearbeiten).
 */
export function generatorStarten() {
  let direkt = null;
  const direktAusfuehren = async (typ, auftrag) => {
    direkt ??= import("./generator-kern.js");
    try {
      return await (await direkt).bearbeiten(typ, auftrag);
    } catch (err) {
      return { ok: false, fehler: String(err && err.message ? err.message : err) };
    }
  };

  let worker = null;
  try {
    worker = new Worker(new URL("generator-worker.js", import.meta.url), { type: "module" });
  } catch {
    worker = null;
  }
  if (!worker) return direktAusfuehren;

  let naechste = 1;
  const offen = new Map();
  let ohneWorker = false;
  // Anfragen erst schicken, wenn der Worker sein Modul geladen hat (meldet sich mit "bereit")
  let workerBereit;
  const bereit = new Promise((r) => { workerBereit = r; });
  worker.onmessage = (e) => {
    if (e.data.bereit) {
      workerBereit();
      return;
    }
    const eintrag = offen.get(e.data.id);
    offen.delete(e.data.id);
    eintrag?.fertig(e.data);
  };
  // Browser ohne Module-Worker: dasselbe direkt auf der Seite
  worker.onerror = (e) => {
    e.preventDefault();
    ohneWorker = true;
    worker.terminate();
    for (const [id, eintrag] of offen) {
      offen.delete(id);
      direktAusfuehren(eintrag.typ, eintrag.auftrag).then(eintrag.fertig);
    }
  };
  return (typ, auftrag) => {
    if (ohneWorker) return direktAusfuehren(typ, auftrag);
    return new Promise((fertig) => {
      const id = naechste++;
      offen.set(id, { typ, auftrag, fertig });
      bereit.then(() => {
        if (!ohneWorker && offen.has(id)) worker.postMessage({ id, typ, auftrag });
      });
    });
  };
}
