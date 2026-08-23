import { execFileSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { extname, join, relative, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const ignored = new Set([".git", ".m2repo", "node_modules", "target", "dist", "playwright-report", "test-results"]);
const extensions = new Set([".java", ".ts", ".vue", ".js", ".mjs", ".cjs", ".json", ".yml", ".yaml", ".md", ".properties", ".xml", ".css", ".html"]);

function files(directory, result = []) {
  for (const name of readdirSync(directory)) {
    if (ignored.has(name)) continue;
    const path = join(directory, name);
    const entry = statSync(path);
    if (entry.isDirectory()) files(path, result);
    else if (extensions.has(extname(name))) result.push(path);
  }
  return result;
}

const failures = [];
const credentialPatterns = [
  ["GitHub classic token", new RegExp(["gh", "p_", "[A-Za-z0-9]{30,}"].join(""), "g")],
  ["GitHub fine-grained token", new RegExp(["github", "_pat_", "[A-Za-z0-9_]{40,}"].join(""), "g")],
  ["OpenAI-style secret", new RegExp(["s", "k-[A-Za-z0-9_-]{32,}"].join(""), "g")],
  ["AWS access key", new RegExp(["AK", "IA[0-9A-Z]{16}"].join(""), "g")],
  ["private key", new RegExp(["-----BEGIN ", "(?:RSA |EC |OPENSSH )?PRIVATE KEY-----"].join(""), "g")],
];

for (const path of files(root)) {
  const content = readFileSync(path, "utf8");
  for (const [label, pattern] of credentialPatterns) {
    pattern.lastIndex = 0;
    if (pattern.test(content)) failures.push(`${relative(root, path)}: ${label}`);
  }
  if (extname(path) !== ".vue") continue;
  const ids = [...content.matchAll(/(?:^|\s)id\s*=\s*"([^"]+)"/g)].map((match) => match[1]);
  const duplicates = ids.filter((id, index) => ids.indexOf(id) !== index);
  if (duplicates.length) failures.push(`${relative(root, path)}: duplicate ids ${[...new Set(duplicates)].join(", ")}`);
  for (const match of content.matchAll(/(?:for|aria-controls|aria-labelledby)\s*=\s*"([A-Za-z][\w:-]*)"/g)) {
    if (!ids.includes(match[1])) failures.push(`${relative(root, path)}: missing DOM target ${match[1]}`);
  }
}

const webCss = readFileSync(join(root, "dream_web/src/styles.css"), "utf8");
const adminCss = readFileSync(join(root, "manage_web/src/styles.css"), "utf8");
for (const token of ["#f7f8f9", "#ffffff", "#17191c", "#0e8f7c", "#0f1012", "#f3f5f6"]) {
  if (!webCss.toLowerCase().includes(token)) failures.push(`dream_web/src/styles.css: missing theme token ${token}`);
}
for (const token of ["#f4f6f7", "#ffffff", "#1b1f23", "#087766", "#bb3e46"]) {
  if (!adminCss.toLowerCase().includes(token)) failures.push(`manage_web/src/styles.css: missing theme token ${token}`);
}
const preferences = readFileSync(join(root, "dream_web/src/stores/preferences.ts"), "utf8");
for (const marker of ['"zh"', '"en"', '"system"', '"light"', '"dark"']) {
  if (!preferences.includes(marker)) failures.push(`dream_web/src/stores/preferences.ts: missing locale/theme marker ${marker}`);
}

const base = process.env.QUALITY_BASE_REF || "origin/feature/refactor-admin-application";
try {
  execFileSync("git", ["rev-parse", "--verify", base], { cwd: root, stdio: "ignore" });
  const changedBak = execFileSync("git", ["diff", "--name-only", `${base}...HEAD`, "--", "bak"], { cwd: root, encoding: "utf8" }).trim();
  if (changedBak) failures.push(`bak changed since ${base}: ${changedBak.replace(/\r?\n/g, ", ")}`);
} catch {
  failures.push(`cannot verify immutable bak baseline ${base}`);
}

if (failures.length) {
  console.error(`Quality gates failed:\n${failures.map((failure) => `- ${failure}`).join("\n")}`);
  process.exit(1);
}
console.log("Quality gates passed: credentials, DOM targets, theme/locale markers, and bak immutability.");
