// Kern: ruft den mit TeaVM nach JavaScript uebersetzten Java-Generator auf
// (generator.js = de.ro.testat.web.WebApi) und packt das Ergebnis als ZIP.
// Wird im Web Worker benutzt, notfalls auch direkt auf der Seite.
import { katalog, setzeRessource, erzeugen } from "./generator.js";
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
 * @param {{vorlage?:string, kette?:string, titel?:string, anzahl:number}} auftrag
 * @returns {Promise<{ok:true,name,titel,blaetter,zip:Uint8Array,dauer}|{ok:false,fehler:string}>}
 */
export async function zipErzeugen(auftrag) {
  await katalogLaden();
  const seeds = Array.from({ length: auftrag.anzahl }, zufallsSeed).join(",");
  const start = performance.now();
  const r = JSON.parse(erzeugen(auftrag.vorlage ?? "", auftrag.kette ?? "", auftrag.titel ?? "", seeds));
  if (!r.ok) return { ok: false, fehler: r.fehler };
  const zip = zipErstellen(r.dateien);
  return { ok: true, name: r.name, titel: r.titel, blaetter: r.blaetter, zip,
           dauer: Math.round(performance.now() - start) };
}
