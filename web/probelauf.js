// Bedienung der Seite "Code ausprobieren" (Probelauf). Ausgefuehrt wird im Web Worker
// (generator-kern.js -> WebApi.ausfuehren), wie java -jar testat-generator-probelauf.jar.
import { generatorStarten } from "./verbindung.js";
import { gemerkt, meldung, merken } from "./seite.js";

const SPEICHER = "probelauf-ro";

const BEISPIELE = {
  asm: `# Summe 1 + 2 + ... + n, Ergebnis in den Speicher
        addi a0, zero, 10      # n
        addi t0, zero, 0       # Summe
        addi t1, zero, 1       # i
schleife:
        blt  a0, t1, fertig    # i > n: fertig
        add  t0, t0, t1
        addi t1, t1, 1
        jal  zero, schleife
fertig:
        sw   t0, 0(gp)
`,
  c: `// Fakultaet rekursiv
int fak(int n) {
    if (n <= 1) return 1;
    return n * fak(n - 1);
}

int main() {
    int x = fak(5);
    printf("fak(5) = %d\\n", x);
    return 0;
}
`,
};

const $ = (s) => document.querySelector(s);
const code = $("#code");
const generator = generatorStarten();


function sprache() {
  return document.querySelector('input[name="sprache"]:checked').value;
}

function spracheAnpassen() {
  const nurC = sprache() === "c";
  for (const l of document.querySelectorAll(".nur-asm")) {
    l.querySelector("input").disabled = nurC;
    l.classList.toggle("aus", nurC);
  }
}

// ---------------------------------------------------------------- Merken (nur in diesem Browser)

function speichern() {
  clearTimeout(merkenTimer);
  merken(SPEICHER, { quelle: code.value, sprache: sprache(), schritte: $("#schritte").checked, streng: $("#streng").checked });
}

/** Beim Tippen nicht bei jedem Zeichen speichern; beim Ausfuehren und Verlassen sofort. */
let merkenTimer = 0;
function spaeterSpeichern() {
  clearTimeout(merkenTimer);
  merkenTimer = setTimeout(speichern, 300);
}
addEventListener("pagehide", speichern);

// ---------------------------------------------------------------- Bericht anzeigen

/**
 * Bericht als Text, Meldungen farbig: Eine Zeile mit "FEHLER"/"Hinweis" und die eingerueckten
 * Zeilen darunter (Quelltextzeile, Pfeil) gehoeren zusammen.
 */
function berichtZeigen(text) {
  const pre = $("#bericht-text");
  pre.replaceChildren();
  let art = null;
  for (const zeile of text.replace(/\n$/, "").split("\n")) {
    const kopf = /^(FEHLER|Hinweis)\b/.test(zeile);
    if (kopf) art = zeile.startsWith("FEHLER") ? "z-fehler" : "z-hinweis";
    else if (!zeile.startsWith("    ")) art = null;
    const span = document.createElement("span");
    span.textContent = zeile + "\n";
    // nur die Meldung selbst darf umbrechen; Quelltext, Pfeil und Tabellen bleiben spaltengenau
    if (art) span.className = kopf ? `${art} z-meldung` : art;
    else if (/^\S.*:$/.test(zeile)) span.className = "z-abschnitt";
    pre.append(span);
  }
}

async function ausfuehrenKlick(e) {
  e.preventDefault();
  const knopf = $("#ausfuehren");
  if (knopf.disabled) return;
  speichern();
  knopf.disabled = true;
  meldung("Wird ausgeführt …");
  const r = await generator("ausfuehren", {
    quelle: code.value, sprache: sprache(), schritte: $("#schritte").checked, streng: $("#streng").checked,
  });
  knopf.disabled = false;

  if (!r.ok) {
    $("#bericht").hidden = true;
    meldung(r.fehler, true);
    return;
  }
  meldung("");
  const bericht = $("#bericht");
  bericht.className = r.fehler ? "bericht mit-fehler" : "bericht ohne-fehler";
  $("#bericht-titel").textContent = r.fehler ? "Fehler gefunden" : "Ohne Fehler durchgelaufen";
  $("#bericht-info").textContent = `${r.sprache === "asm" ? "RISC-V-Assembler" : "C"}${r.erkannt ? " (erkannt)" : ""}, ${r.dauer} ms`;
  berichtZeigen(r.text);
  bericht.hidden = false;
  if (bericht.getBoundingClientRect().top > innerHeight * 0.6) bericht.scrollIntoView({ behavior: "smooth", block: "start" });
}

// ---------------------------------------------------------------- Eingabefeld

let escGedrueckt = false;

function einfuegen(text) {
  // execCommand haelt Rueckgaengig (Strg+Z) intakt; sonst setRangeText
  if (!document.execCommand || !document.execCommand("insertText", false, text)) {
    code.setRangeText(text, code.selectionStart, code.selectionEnd, "end");
  }
}

code.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
    e.preventDefault();
    $("#formular").requestSubmit();
    return;
  }
  if (e.key === "Escape") {
    escGedrueckt = true;
    return;
  }
  if (e.key === "Tab" && !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey && !escGedrueckt) {
    e.preventDefault();
    einfuegen("    ");
  }
  escGedrueckt = false;
});
code.addEventListener("input", spaeterSpeichern);

for (const b of document.querySelectorAll("[data-beispiel]")) {
  b.addEventListener("click", () => {
    const neu = BEISPIELE[b.dataset.beispiel];
    const leerOderBeispiel = !code.value.trim() || Object.values(BEISPIELE).includes(code.value);
    if (!leerOderBeispiel && !confirm("Den Code im Feld durch das Beispiel ersetzen?")) return;
    code.value = neu;
    document.querySelector(`input[name="sprache"][value="${b.dataset.beispiel}"]`).checked = true;
    spracheAnpassen();
    spaeterSpeichern();
    code.focus();
    code.setSelectionRange(0, 0);
    code.scrollTop = 0;
  });
}

// ---------------------------------------------------------------- Start

const alt = gemerkt(SPEICHER);
if (typeof alt.quelle === "string") code.value = alt.quelle;
if (["", "asm", "c"].includes(alt.sprache)) {
  document.querySelector(`input[name="sprache"][value="${alt.sprache}"]`).checked = true;
}
$("#schritte").checked = alt.schritte === true;
$("#streng").checked = alt.streng === true;
spracheAnpassen();

for (const r of document.querySelectorAll('input[name="sprache"]')) {
  r.addEventListener("change", () => { spracheAnpassen(); spaeterSpeichern(); });
}
$("#schritte").addEventListener("change", spaeterSpeichern);
$("#streng").addEventListener("change", spaeterSpeichern);
$("#formular").addEventListener("submit", ausfuehrenKlick);
$("#ausfuehren").disabled = false;
