// Kleinigkeiten, die beide Seiten (app.js, probelauf.js) brauchen.

/** Statuszeile (#status) unter dem Knopf; fehler = rot. */
export function meldung(text, fehler = false) {
  const status = document.querySelector("#status");
  status.className = fehler ? "status fehler" : "status";
  status.textContent = text;
}

/** Gemerktes aus localStorage (nur in diesem Browser), sonst {}. */
export function gemerkt(schluessel) {
  try {
    const wert = JSON.parse(localStorage.getItem(schluessel));
    return wert && typeof wert === "object" ? wert : {};
  } catch {
    return {};
  }
}

/** Werte zum Gemerkten hinzufuegen (vorhandene Felder bleiben, wenn sie nicht mitkommen). */
export function merken(schluessel, werte) {
  try {
    localStorage.setItem(schluessel, JSON.stringify({ ...gemerkt(schluessel), ...werte }));
  } catch {
    // privater Modus, Speicher voll o. ae.: dann eben nicht
  }
}
