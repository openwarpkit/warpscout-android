import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync } from "node:fs";
import { join, relative } from "node:path";

const root = process.cwd();
const failures = [];

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

function git(...args) {
  return execFileSync("git", args, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
}

function gitSucceeds(...args) {
  try {
    execFileSync("git", args, { cwd: root, stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
}

function match(content, expression, label) {
  const value = content.match(expression)?.[1];
  if (!value) failures.push(`missing ${label}`);
  return value;
}

function goFiles(directory) {
  const entries = readdirSync(join(root, directory), { withFileTypes: true });
  return entries.flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return goFiles(path);
    return entry.name.endsWith(".go") ? [path] : [];
  });
}

const rootGoFiles = readdirSync(root, { withFileTypes: true })
  .filter((entry) => entry.isFile() && entry.name.endsWith(".go"))
  .map((entry) => entry.name)
  .sort();
if (rootGoFiles.join(",") !== "main.go") {
  failures.push(`unexpected root Go files: ${rootGoFiles.join(", ") || "none"}`);
}

const requiredCoreFiles = [
  "internal/warpscout/cli.go",
  "internal/warpscout/main.go",
  "internal/warpscout/register.go",
  "internal/warpscout/tunnel.go",
  "internal/warpscout/report.go",
  "internal/warpscout/warp.go",
  "internal/warpscout/wgconf.go",
  "internal/warpscout/masque.go",
  "internal/warpscout/mobile_backend.go",
];
for (const path of requiredCoreFiles) {
  try {
    read(path);
  } catch {
    failures.push(`missing core file: ${path}`);
  }
}

for (const path of goFiles("internal/warpscout")) {
  if (!/(^|\n)package warpscout\r?\n/.test(read(path))) {
    failures.push(`${relative(root, join(root, path))}: package must be warpscout`);
  }
}

for (const directory of ["core", "mobileapi"]) {
  for (const path of goFiles(directory)) {
    const content = read(path);
    if (/(^|\n)package main\r?\n/.test(content)) failures.push(`${path}: package main is not allowed`);
    if (/\bos\.Exit\s*\(/.test(content)) failures.push(`${path}: os.Exit is not allowed`);
  }
}

const main = read("main.go");
if (!main.includes('"github.com/vernette/warpscout/internal/warpscout"') || !main.includes("warpscout.Main()")) {
  failures.push("main.go must remain a thin CLI wrapper");
}

const bridge = read("mobileapi/bridge.go");
if (!bridge.includes("core.New(warpscout.NewMobileBackend())")) {
  failures.push("mobileapi must use the WARPSCOUT mobile backend");
}

const upstream = read("UPSTREAM.md");
const upstreamTag = match(upstream, /^- Upstream tag: `([^`]+)`/m, "UPSTREAM.md tag");
const upstreamCommit = match(upstream, /^- Upstream commit: `([0-9a-f]{40})`/m, "UPSTREAM.md commit");

if (upstreamCommit) {
  if (!gitSucceeds("cat-file", "-e", `${upstreamCommit}^{commit}`)) {
    failures.push(`upstream commit is unavailable: ${upstreamCommit}`);
  } else if (!gitSucceeds("merge-base", "--is-ancestor", upstreamCommit, "HEAD")) {
    failures.push(`upstream commit is not an ancestor of HEAD: ${upstreamCommit}`);
  }
}
if (upstreamTag && !gitSucceeds("rev-parse", "--verify", `refs/tags/${upstreamTag}`)) {
  failures.push(`upstream tag is unavailable: ${upstreamTag}`);
} else if (upstreamTag && upstreamCommit && !gitSucceeds("merge-base", "--is-ancestor", upstreamTag, upstreamCommit)) {
  failures.push(`${upstreamTag} is not an ancestor of ${upstreamCommit}`);
}

const gradle = read("android/app/build.gradle.kts");
const gradleTag = match(gradle, /val upstreamTag = .*\.orElse\("([^"]+)"\)/, "Android upstream tag");
const gradleCommit = match(gradle, /val upstreamCommit = .*\.orElse\("([0-9a-f]+)"\)/, "Android upstream commit");
const bridgeTag = match(bridge, /upstreamVersion\s*=\s*"([^"]+)"/, "mobileapi upstream tag");

if (upstreamTag && gradleTag !== upstreamTag) failures.push(`Android upstream tag is ${gradleTag}, expected ${upstreamTag}`);
if (upstreamTag && bridgeTag !== upstreamTag) failures.push(`mobileapi upstream tag is ${bridgeTag}, expected ${upstreamTag}`);
if (upstreamCommit && !upstreamCommit.startsWith(gradleCommit ?? "")) {
  failures.push(`Android upstream commit is ${gradleCommit}, expected a prefix of ${upstreamCommit}`);
}

for (const path of ["scripts/build-mobile.sh", "scripts/build-mobile.ps1"]) {
  if (upstreamTag && !read(path).includes(upstreamTag)) failures.push(`${path}: default upstream tag is stale`);
}

const requiredRefIndex = process.argv.indexOf("--require-ref");
if (requiredRefIndex >= 0) {
  const requiredRef = process.argv[requiredRefIndex + 1];
  if (!requiredRef) {
    failures.push("--require-ref needs a Git ref");
  } else {
    try {
      const requiredCommit = git("rev-parse", `${requiredRef}^{commit}`);
      if (upstreamCommit && requiredCommit !== upstreamCommit) {
        failures.push(`UPSTREAM.md records ${upstreamCommit}, but ${requiredRef} is ${requiredCommit}`);
      }
    } catch {
      failures.push(`cannot resolve required upstream ref: ${requiredRef}`);
    }
  }
}

if (failures.length > 0) {
  process.stderr.write(`${failures.join("\n")}\n`);
  process.exit(1);
}

process.stdout.write(`upstream sync contract OK: ${upstreamTag} ${upstreamCommit}\n`);
