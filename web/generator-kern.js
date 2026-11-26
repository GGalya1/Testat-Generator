// Kern: ruft den mit TeaVM nach JavaScript uebersetzten Java-Code auf
// (generator.js = de.ro.testat.web.WebApi): Uebungsblaetter als ZIP und den Probelauf.
// Wird im Web Worker benutzt, notfalls auch direkt auf der Seite.
import { katalog, setzeRessource, erzeugen, ausfuehren } from "./generator.js";
import { zipErstellen } from "./zip.js";

let bereit = null;

/** Laedt praeambel.tex usw. (liegen neben der Seite in latex/) und liefert den Katalog. */
export function katalogLaden() {
  bereit ??= (async () => {
    const k = JSON.parse(katalog());
    for (const name of k.ressourcen) {
      const antwort = await fetch(new URL("latex/" + name, import.meta.url));
      if (!antwort.ok) throw new Error("latex/" + name + " nicht gefunden (HTTP " + antwort.status + ")");
      setzeRessource(name, await antwort.text());
    }
    return k;
  })();
  return bereit;
}

function zufallsSeed() {
  const w = crypto.getRandomValues(new Uint32Array(2));
  return Array.from(w, (x) => x.toString(16).padStart(8, "0")).join("");
}

/**
 * @param {{vorlage?:string, kette?:string, titel?:string, anzahl:number, seeds?:string[]}} auftrag
 *        seeds: feste Seeds (Hex, je einer ein Blatt), sonst anzahl zufaellige
 * @returns {Promise<{ok:true,name,titel,blaetter,zip:Uint8Array,dauer}|{ok:false,fehler:string}>}
 */
export async function zipErzeugen(auftrag) {
  await katalogLaden();
  const seeds = (auftrag.seeds?.length ? auftrag.seeds : Array.from({ length: auftrag.anzahl }, zufallsSeed)).join(",");
  const start = performance.now();
  const r = JSON.parse(erzeugen(auftrag.vorlage ?? "", auftrag.kette ?? "", auftrag.titel ?? "", seeds));
  if (!r.ok) return { ok: false, fehler: r.fehler };
  const zip = zipErstellen(r.dateien);
  return { ok: true, name: r.name, titel: r.titel, blaetter: r.blaetter, zip,
           dauer: Math.round(performance.now() - start) };
}

/**
 * Probelauf (eigenen Code ausfuehren). Braucht keine LaTeX-Dateien.
 * @param {{quelle:string, sprache?:""|"asm"|"c", schritte?:boolean}} auftrag
 * @returns {{ok:true,sprache,erkannt,fehler:boolean,text,dauer}|{ok:false,fehler:string}}
 */
export function probelauf(auftrag) {
  const start = performance.now();
  let r;
  try {
    r = JSON.parse(ausfuehren(auftrag.quelle ?? "", auftrag.sprache ?? "", auftrag.schritte ? "1" : ""));
  } catch (err) {
    // z. B. RangeError, wenn der JavaScript-Stack trotz der Rekursionsgrenze nicht reicht
    const text = String(err && err.message ? err.message : err);
    return { ok: false, fehler: /stack|recursion/i.test(text)
      ? "Das Programm ist zu tief verschachtelt für den Browser (zu viele Aufrufe oder zu lange Ausdrücke)."
      : "Interner Fehler: " + text };
  }
  if (r.ok) r.dauer = Math.round(performance.now() - start);
  return r;
}

/** Eine Anfrage von der Seite: "katalog", "erzeugen" oder "ausfuehren". */
export async function bearbeiten(typ, auftrag) {
  if (typ === "katalog") return { ok: true, katalog: await katalogLaden() };
  if (typ === "erzeugen") return zipErzeugen(auftrag);
  if (typ === "ausfuehren") return probelauf(auftrag);
  return { ok: false, fehler: "Unbekannte Anfrage: " + typ };
}
