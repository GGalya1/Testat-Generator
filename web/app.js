// Bedienung der Seite "Uebungsblaetter". Die eigentliche Arbeit macht generator-kern.js (im Web Worker).
import { generatorStarten } from "./verbindung.js";
import { gemerkt, meldung, merken } from "./seite.js";

// Die vier Testate. "vorlage" ist die Vorlage aus Templates.java, die das ganze Testat erzeugt;
// "aufgaben" steht so auf der Karte (Reihenfolge wie auf dem Blatt).
// "typen" ordnet die Aufgabentypen fuer "Eigene Auswahl"; Unbekanntes landet unter "Weitere".
const TESTATE = [
  { nr: 1, titel: "Grundlagen Assembler", vorlage: "grundlagen",
    aufgaben: ["Assembler → Maschinencode", "Maschinencode → Assembler", "C → RISC-V", "RISC-V → C"],
    typen: ["asm2bin", "bin2asm", "c2asm", "asm2c"] },
  { nr: 2, titel: "Funktionen und Speicher", vorlage: "funktionen",
    aufgaben: ["Rekursive Funktion in RISC-V", "Funktionsaufruf", "Stackinhalt", "Schleife über ein Array"],
    typen: ["rekursion", "aufruf", "stack", "array", "shift"] },
  { nr: 3, titel: "Prozessor", vorlage: "prozessor",
    aufgaben: ["Eintakt-Prozessor: Timing", "Mehrtakt-Steuerwerk erweitern", "Pipeline-Konflikte"],
    typen: ["eintakt", "mehrtakt", "mehrtakt-neu", "hazards"] },
  { nr: 4, titel: "Speicherhierarchie", vorlage: "speicherhierarchie",
    aufgaben: ["Cache-Parameter", "Zugriffsfolge im Cache", "Miss-Rate eines Programms", "Mittlere Zugriffszeit (AMAT)",
               "Virtueller Speicher"],
    typen: ["cache-bits", "cache-sim", "cache-prog", "amat", "schreiben", "vm"] },
];

const MAX_AUFGABEN = 12;
const MAX_BLAETTER = 10;
const SPEICHER = "uebungsblaetter-ro";

const $ = (s) => document.querySelector(s);

const generator = generatorStarten();

// ---------------------------------------------------------------- Anzeige

/** Kopie eines <template> direkt im Dokument (nicht im inerten Template-Dokument). */
function vonVorlage(sel) {
  return document.importNode($(sel).content, true).firstElementChild;
}

function pfeile(text) {
  return text.replace(/->/g, "→");
}

/** Aufgabentypen nach Testat gruppiert (fuer die Auswahlliste bei "Eigene Auswahl"). */
function gruppiert(ids) {
  const gruppen = TESTATE.map((t) => ({ name: `Testat ${t.nr}: ${t.titel}`, ids: t.typen.filter((id) => ids.includes(id)) }));
  const bekannt = new Set(TESTATE.flatMap((t) => t.typen));
  const rest = ids.filter((id) => !bekannt.has(id));
  if (rest.length) gruppen.push({ name: "Weitere", ids: rest });
  return gruppen.filter((g) => g.ids.length);
}

/** Eine Karte je Testat, sofern der Generator die Vorlage kennt. */
function testateZeigen(katalog, gewaehlt) {
  const ziel = $("#vorlagen");
  ziel.replaceChildren();
  const vorhanden = new Set(katalog.vorlagen.map((v) => v.id));
  for (const t of TESTATE.filter((t) => vorhanden.has(t.vorlage))) {
    const karte = vonVorlage("#testat-karte");
    const input = karte.querySelector("input");
    input.value = t.vorlage;
    input.checked = t.vorlage === gewaehlt;
    karte.querySelector(".t-nr").textContent = `Testat ${t.nr}`;
    karte.querySelector(".t-titel").textContent = t.titel;
    const liste = karte.querySelector(".t-aufgaben");
    for (const a of t.aufgaben) {
      const s = document.createElement("span");
      s.textContent = a;
      liste.append(s);
    }
    ziel.append(karte);
  }
  const erste = ziel.querySelector("input");
  if (erste && !ziel.querySelector("input:checked")) erste.checked = true;
}

let typen = [];

function typAuswahl() {
  const select = document.createElement("select");
  for (const g of gruppiert(typen.map((t) => t.id))) {
    const og = document.createElement("optgroup");
    og.label = g.name;
    for (const id of g.ids) {
      const t = typen.find((x) => x.id === id);
      const o = document.createElement("option");
      o.value = id;
      const kurz = pfeile(t.hilfe.split(/[,;]\s*Parameter|\s+\(/)[0]);
      o.textContent = `${id}: ${kurz}`;
      og.append(o);
    }
    select.append(og);
  }
  return select;
}

function aufgabeHinzufuegen(typ = null, param = "") {
  const liste = $("#aufgaben");
  if (liste.children.length >= MAX_AUFGABEN) return;
  const zeile = vonVorlage("#aufgabe-zeile");
  const select = typAuswahl();
  zeile.querySelector(".a-typ").append(select);
  liste.append(zeile); // erst einfuegen, dann Werte setzen
  const input = zeile.querySelector(".a-param input");
  const hilfe = zeile.querySelector(".a-hilfe");
  select.value = typ && typen.some((t) => t.id === typ) ? typ : typen[0].id;
  input.value = param;

  const aktualisieren = () => {
    const t = typen.find((x) => x.id === select.value);
    hilfe.textContent = pfeile(t.hilfe);
    const ohne = /ohne Parameter/i.test(t.hilfe);
    input.disabled = ohne;
    input.placeholder = ohne ? "keine Parameter" : "Parameter (optional)";
    if (ohne) input.value = "";
  };
  select.addEventListener("change", aktualisieren);
  aktualisieren();

  zeile.querySelector(".a-weg").addEventListener("click", () => {
    zeile.remove();
    knoepfeAnpassen();
  });
  knoepfeAnpassen();
  return select;
}

function knoepfeAnpassen() {
  const n = $("#aufgaben").children.length;
  $("#aufgabe-dazu").hidden = n >= MAX_AUFGABEN;
  for (const b of document.querySelectorAll(".a-weg")) b.hidden = n <= 1;
}

function modus() {
  return document.querySelector('input[name="modus"]:checked').value;
}

function modusZeigen() {
  const eigen = modus() === "eigen";
  $("#bereich-vorlage").hidden = eigen;
  $("#bereich-eigen").hidden = !eigen;
}

/** "zufall" oder "eigen" (Seed eingeben: genau ein Blatt mit diesem Seed). */
function seedModus() {
  return document.querySelector('input[name="seedmodus"]:checked').value;
}

function seedModusZeigen() {
  const eigen = seedModus() === "eigen";
  $("#seed-feld").hidden = !eigen;
  $(".anzahl").hidden = eigen;
}

/** Eingegebener Seed als Hex (wie unter "Fertig" angezeigt), 0x und Leerzeichen erlaubt; sonst null. */
function seedLesen() {
  const s = $("#seed").value.replace(/\s+/g, "").replace(/^0x/i, "").toLowerCase();
  return /^[0-9a-f]{1,16}$/.test(s) ? s : null;
}

/** Wie man das Blatt weitergibt: Testat-Nummer bzw. "eigene Auswahl", dazu der Seed. */
function teilenText(auftrag) {
  if (auftrag.modus === "eigen") return "dieselbe eigene Auswahl";
  const t = TESTATE.find((x) => x.vorlage === auftrag.vorlage);
  return t ? `Testat ${t.nr}` : "dieselbe Vorlage";
}

// ---------------------------------------------------------------- Erzeugen

function auftragLesen() {
  const anzahl = Math.min(MAX_BLAETTER, Math.max(1, parseInt($("#anzahl").value, 10) || 1));
  $("#anzahl").value = anzahl;
  if (modus() === "vorlage") {
    const v = document.querySelector('input[name="vorlage"]:checked');
    return { modus: "vorlage", vorlage: v ? v.value : "", anzahl };
  }
  const zeilen = [...document.querySelectorAll(".aufgabe")].map((z) => ({
    typ: z.querySelector("select").value,
    param: z.querySelector(".a-param input").value.replace(/\s+/g, ""),
  }));
  const kette = zeilen.map((z) => (z.param ? `${z.typ}:${z.param}` : z.typ)).join(" ");
  return { modus: "eigen", kette, zeilen, titel: $("#titel").value.trim(), anzahl };
}

let letzteUrl = null;

async function erzeugenKlick(e) {
  e.preventDefault();
  const knopf = $("#erzeugen");
  const auftrag = auftragLesen();
  let seeds;
  if (seedModus() === "eigen") {
    const seed = seedLesen();
    if (!seed) {
      meldung("Ein Seed besteht aus 1 bis 16 Hex-Ziffern (0–9, a–f), z. B. 28f75d14275fd065.", true);
      $("#seed").focus();
      return;
    }
    seeds = [seed];
  }
  merken(SPEICHER, auftrag); // eigene Auswahl bleibt, wenn ein ganzes Testat erzeugt wird
  if (seeds) auftrag.anzahl = 1; // gemerkte Anzahl bleibt fuer "Zufaellig"
  knopf.disabled = true;
  meldung(auftrag.anzahl === 1 ? "Blatt wird erzeugt …" : `${auftrag.anzahl} Blätter werden erzeugt …`);

  const r = await generator("erzeugen", auftrag.modus === "vorlage"
    ? { vorlage: auftrag.vorlage, anzahl: auftrag.anzahl, seeds }
    : { kette: auftrag.kette, titel: auftrag.titel, anzahl: auftrag.anzahl, seeds });
  knopf.disabled = false;

  if (!r.ok) {
    meldung(r.fehler, true);
    return;
  }

  if (letzteUrl) URL.revokeObjectURL(letzteUrl);
  letzteUrl = URL.createObjectURL(new Blob([r.zip], { type: "application/zip" }));
  const dateiname = `${r.name}.zip`;
  const link = $("#download");
  link.href = letzteUrl;
  link.download = dateiname;
  link.textContent = `${dateiname} herunterladen`;

  const liste = $("#blaetter");
  liste.replaceChildren();
  r.blaetter.forEach((b, i) => {
    const li = document.createElement("li");
    li.append(`Blatt ${i + 1}: ${r.titel}, Seed `);
    const c = document.createElement("code");
    c.textContent = b.seed;
    li.append(c, " ", kopierKnopf(b.seed));
    liste.append(li);
  });
  $("#ergebnis-hinweis").textContent = seeds
    ? "Mit diesem Seed entsteht immer genau dieses Blatt."
    : `Jeder Klick auf „ZIP erzeugen“ würfelt neue Aufgaben aus. Zum Teilen: ${teilenText(auftrag)} wählen, `
      + "„Seed eingeben“ und den Seed eintragen – dann entsteht dasselbe Blatt.";
  $("#ergebnis-titel").textContent = r.blaetter.length === 1 ? "1 Blatt erzeugt" : `${r.blaetter.length} Blätter erzeugt`;
  $("#ergebnis").hidden = false;
  meldung(`Fertig in ${r.dauer} ms. Der Download startet automatisch.`);
  link.click();
}

/** Knopf, der den Seed in die Zwischenablage kopiert. */
function kopierKnopf(seed) {
  const k = document.createElement("button");
  k.type = "button";
  k.className = "leise kopieren";
  k.textContent = "Kopieren";
  k.setAttribute("aria-label", `Seed ${seed} kopieren`);
  k.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(seed);
      k.textContent = "Kopiert";
    } catch {
      // ohne Zwischenablage (z. B. http): Seed markieren, dann Strg+C
      getSelection().selectAllChildren(k.previousElementSibling);
      k.textContent = "Markiert – Strg+C";
    }
    setTimeout(() => { k.textContent = "Kopieren"; }, 2000);
  });
  return k;
}

// ---------------------------------------------------------------- Start

async function start() {
  const r = await generator("katalog");
  if (!r.ok) {
    $("#vorlagen").replaceChildren();
    meldung("Der Generator konnte nicht geladen werden: " + r.fehler, true);
    return;
  }
  const alt = gemerkt(SPEICHER);
  typen = r.katalog.typen;
  testateZeigen(r.katalog, alt.vorlage);

  const zeilen = Array.isArray(alt.zeilen) && alt.zeilen.length ? alt.zeilen : [{ typ: "asm2bin", param: "" }];
  for (const z of zeilen) aufgabeHinzufuegen(z.typ, z.param ?? "");
  if (typeof alt.titel === "string") $("#titel").value = alt.titel;
  if (alt.anzahl) $("#anzahl").value = alt.anzahl;
  // Immer mit "Ganzes Testat" starten (auch wenn zuletzt "Eigene Auswahl" benutzt wurde);
  // die eigene Auswahl selbst bleibt gemerkt und steht beim Umschalten wieder da.
  document.querySelector('input[name="modus"][value="vorlage"]').checked = true;
  modusZeigen();
  // Seed nur auf ausdruecklichen Wunsch: beim Laden immer "Zufaellig"
  document.querySelector('input[name="seedmodus"][value="zufall"]').checked = true;
  seedModusZeigen();
  for (const r of document.querySelectorAll('input[name="seedmodus"]')) {
    r.addEventListener("change", () => {
      seedModusZeigen();
      if (seedModus() === "eigen") $("#seed").focus();
    });
  }

  for (const r of document.querySelectorAll('input[name="modus"]')) r.addEventListener("change", modusZeigen);
  $("#aufgabe-dazu").addEventListener("click", () => aufgabeHinzufuegen()?.focus());
  $("#formular").addEventListener("submit", erzeugenKlick);
  $("#erzeugen").disabled = false;
}

start();
