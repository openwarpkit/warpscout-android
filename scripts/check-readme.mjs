import { existsSync, readFileSync } from "node:fs";

const files = ["README.md", "README_RU.md"];
const bannedCharacters = ["\u2014", "\u2013"];
const emoji = /[\u{1F1E6}-\u{1F1FF}\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE00}-\u{FE0F}]/u;
const attribution = [
  "WARPSCOUT for Android is an independent OpenWarpKit project based on the WARPSCOUT CLI.",
  "Original project: https://github.com/vernette/warpscout",
  "Original author: Nikita S. (@vernette)",
  "This repository is not an official Android release maintained by the upstream author.",
];
const screenshots = ["scan.png", "progress.png", "results.png", "tools.png"];
const failures = [];

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
  for (const screenshot of screenshots) {
    if (!existsSync(`docs/screenshots/${screenshot}`)) {
      failures.push(`missing release screenshot: docs/screenshots/${screenshot}`);
    }
  }
}

if (failures.length > 0) {
  process.stderr.write(`${failures.join("\n")}\n`);
  process.exit(1);
}
