// Prueft das mit TeaVM erzeugte generator.js in Node:
//   1. Jeder Fall aus der JVM-Referenz (WebReferenzTest) liefert Zeichen fuer Zeichen dasselbe.
//   2. Zeitmessung je Vorlage.
//   3. Ein ZIP wird gebaut (zum Pruefen mit "unzip -t").
// Aufruf (nach mvn -Pweb package):  node web/test/smoke.mjs target/site target/web-referenz.jsonl
import { readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const [site = "target/site", referenz = "target/web-referenz.jsonl"] = process.argv.slice(2);
const gen = await import(pathToFileURL(resolve(site, "generator.js")).href);
const { zipErstellen } = await import(pathToFileURL(resolve(site, "zip.js")).href);

const katalog = JSON.parse(gen.katalog());
for (const name of katalog.ressourcen) {
  gen.setzeRessource(name, readFileSync(join(site, "latex", name), "utf8"));
}
console.log(`Katalog: ${katalog.vorlagen.length} Vorlagen, ${katalog.typen.length} Aufgabentypen`);

/** Erste Stelle, an der sich zwei Ausgaben unterscheiden, lesbar machen. */
function unterschied(jvm, js) {
  let a, b;
  try {
    a = JSON.parse(jvm);
    b = JSON.parse(js);
  } catch {
    return `JS-Ausgabe ist kein JSON: ${js.slice(0, 300)}`;
  }
  if (!b.ok) return `JS meldet Fehler: ${b.fehler}`;
  for (const [datei, inhalt] of Object.entries(a.dateien)) {
    const anders = b.dateien[datei];
    if (anders === inhalt) continue;
    if (anders === undefined) return `Datei ${datei} fehlt in JS`;
    let i = 0;
    while (i < inhalt.length && inhalt[i] === anders[i]) i++;
    const zeile = inhalt.slice(0, i).split("\n").length;
    return `${datei}, Zeile ${zeile}:\n    JVM: ${JSON.stringify(inhalt.slice(Math.max(0, i - 60), i + 60))}\n    JS : ${JSON.stringify(anders.slice(Math.max(0, i - 60), i + 60))}`;
  }
  for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
    if (k !== "dateien" && JSON.stringify(a[k]) !== JSON.stringify(b[k])) {
      return `Feld "${k}":\n    JVM: ${JSON.stringify(a[k]).slice(0, 200)}\n    JS : ${JSON.stringify(b[k]).slice(0, 200)}`;
    }
  }
  return "Unterschied nur in der Reihenfolge der Dateien";
}

let ok = 0;
const fehler = [];
let ersterFehlerFall = null;
const zeiten = new Map();
for (const zeile of readFileSync(referenz, "utf8").split("\n")) {
  if (!zeile) continue;
  const f = JSON.parse(zeile);
  const t0 = performance.now();
  let aus;
  try {
    aus = gen.erzeugen(f.vorlage, f.kette, "", f.seed);
  } catch (e) {
    aus = JSON.stringify({ ok: false, fehler: "Ausnahme in JS: " + e });
  }
  const name = f.vorlage || f.kette;
  const z = zeiten.get(name) ?? [];
  z.push(performance.now() - t0);
  zeiten.set(name, z);
  if (aus === f.ausgabe) ok++;
  else {
    fehler.push({ name, text: `${name} (Seed ${f.seed}): ${unterschied(f.ausgabe, aus)}` });
    if (!ersterFehlerFall && aus.startsWith('{"ok":false')) ersterFehlerFall = f;
  }
}

console.log("\nZeit je Blatt in Node (erster Aufruf / Durchschnitt / Maximum):");
for (const [name, z] of zeiten) {
  const schnitt = z.reduce((s, x) => s + x, 0) / z.length;
  console.log(`  ${name.padEnd(20)} ${z[0].toFixed(0).padStart(5)} ms ${schnitt.toFixed(1).padStart(7)} ms ${Math.max(...z).toFixed(1).padStart(7)} ms`);
}

const beispiel = JSON.parse(gen.erzeugen("kodierung", "", "", "0123456789abcdef,fedcba9876543210"));
if (beispiel.ok) writeFileSync(join(site, "..", "smoke.zip"), zipErstellen(beispiel.dateien));

console.log(`\n${ok} von ${ok + fehler.length} Faellen gleich wie auf der JVM.`);
if (ersterFehlerFall) {
  // Denselben Fall noch einmal ohne Abfangen in Java: zeigt, wo es im Code passiert
  console.log(`\nStack fuer ${ersterFehlerFall.vorlage || ersterFehlerFall.kette} (Seed ${ersterFehlerFall.seed}):`);
  try {
    gen.erzeugenOhneSchutz(ersterFehlerFall.vorlage, ersterFehlerFall.kette, "", ersterFehlerFall.seed);
    console.log("  (ohne Abfangen kein Fehler)");
  } catch (e) {
    const stack = String((e && e.stack) || e).split("\n").slice(0, 30);
    console.log(stack.map((z) => "  " + z).join("\n"));
    const ursache = e && typeof e.getCause === "function" ? e.getCause() : null;
    if (ursache) console.log("  Ursache: " + String(ursache.stack || ursache));
  }
}
if (fehler.length) {
  // je Vorlage/Aufgabentyp nur die erste Abweichung, damit verschiedene Ursachen sichtbar werden
  const erste = new Map();
  for (const f of fehler) if (!erste.has(f.name)) erste.set(f.name, f.text);
  const betroffen = [...new Set(fehler.map((f) => f.name))];
  console.log(`\n${fehler.length} Abweichungen in ${betroffen.length} Vorlagen/Aufgabentypen: ${betroffen.join(", ")}`);
  console.log("Je eine davon:");
  for (const t of erste.values()) console.log("- " + t);
  process.exit(1);
}
if (!beispiel.ok) {
  console.log("Beispiel-ZIP fehlgeschlagen: " + beispiel.fehler);
  process.exit(1);
}
