import { existsSync, readFileSync } from "node:fs";

const files = ["README.md", "README_EN.md"];
const bannedCharacters = ["\u2014", "\u2013"];
const emoji = /[\u{1F1E6}-\u{1F1FF}\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE00}-\u{FE0F}]/u;
const attribution = [
  "WARPSCOUT for Android is an independent OpenWarpKit project based on the WARPSCOUT CLI.",
  "Original project: https://github.com/vernette/warpscout",
  "Original author: Nikita S. (@vernette)",
  "This repository is not an official Android release maintained by the upstream author.",
];
const screenshots = [
  "onboarding.png",
  "scan.png",
  "history.png",
  "tools.png",
  "settings.png",
  "expert.png",
  "progress.png",
  "results.png",
  "results-nodes.png",
  "best-endpoint.png",
];
const screenshotThemes = ["light", "dark"];
const failures = [];

function pngSize(path) {
  const png = readFileSync(path);
  if (png.length < 24 || png.toString("ascii", 1, 4) !== "PNG") {
    return null;
  }
  return {
    width: png.readUInt32BE(16),
    height: png.readUInt32BE(20),
  };
}

for (const file of files) {
  const content = readFileSync(file, "utf8");
  for (const character of bannedCharacters) {
    if (content.includes(character)) {
      failures.push(`${file}: banned dash U+${character.codePointAt(0).toString(16).toUpperCase()}`);
    }
  }
  if (emoji.test(content)) {
    failures.push(`${file}: emoji is not allowed`);
  }
  for (const line of attribution) {
    if (!content.includes(line)) {
      failures.push(`${file}: missing attribution line: ${line}`);
    }
  }
  if (/generated (by|with) (AI|Codex|ChatGPT)/i.test(content)) {
    failures.push(`${file}: generated-content disclosure is not allowed`);
  }
}
if (process.env.REQUIRE_SCREENSHOTS === "1") {
  for (const theme of screenshotThemes) {
    for (const screenshot of screenshots) {
      const path = `docs/screenshots/${theme}/${screenshot}`;
      if (!existsSync(path)) {
        failures.push(`missing release screenshot: ${path}`);
        continue;
      }
      const size = pngSize(path);
      if (size?.width !== 1080 || size?.height !== 2404) {
        failures.push(`${path}: expected 1080x2404, got ${size ? `${size.width}x${size.height}` : "invalid PNG"}`);
      }
    }
  }
}

if (failures.length > 0) {
  process.stderr.write(`${failures.join("\n")}\n`);
  process.exit(1);
}
