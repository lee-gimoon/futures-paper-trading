// 모의 선물거래 백엔드 도메인 규칙 PDF 생성 스크립트.
// 실행: node make_backend_domain_rules_pdf.js
// 출력: futures-paper-trading-backend-domain-rules.pdf (이 스크립트와 같은 폴더)
import { execFile } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { promisify } from "node:util";
import { fileURLToPath, pathToFileURL } from "node:url";

const run = promisify(execFile);
const dir = path.dirname(fileURLToPath(import.meta.url));
const input = path.join(dir, "futures-paper-trading-backend-domain-rules.html");
const output = path.join(dir, "futures-paper-trading-backend-domain-rules.pdf");

const edgeCandidates = [
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
];

const edge = edgeCandidates.find((candidate) => fs.existsSync(candidate));
if (!edge) {
  throw new Error("Microsoft Edge 실행 파일을 찾을 수 없습니다.");
}

function scrubPdfDates(file) {
  const pdf = fs.readFileSync(file, "latin1");
  const scrubbed = pdf.replace(/\/(CreationDate|ModDate) \(([^)]*)\)/g, (_match, key, value) =>
    `/${key} (${" ".repeat(value.length)})`
  );
  fs.writeFileSync(file, scrubbed, "latin1");
}

await run(edge, [
  "--headless",
  "--disable-gpu",
  "--no-sandbox",
  "--no-pdf-header-footer",
  "--print-to-pdf-no-header",
  `--print-to-pdf=${output}`,
  pathToFileURL(input).href,
]);

scrubPdfDates(output);

console.log(`OK: ${output}`);
