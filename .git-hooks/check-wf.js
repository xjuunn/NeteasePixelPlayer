const fs = require("fs");
const path = require("path");

const wfPath = path.join(__dirname, "..", ".github", "workflows", "phone-release.yml");
const text = fs.readFileSync(wfPath, "utf8");

let yaml;
try {
  yaml = require("js-yaml");
} catch (e) {
  console.log("SKIP: js-yaml not resolvable from script location");
  process.exit(0);
}

const doc = yaml.load(text);
const b = doc.jobs["build-phone"];
const p = doc.jobs["publish-release"];

const uploads = b.steps
  .filter((s) => (s.uses || "").includes("upload-artifact"))
  .map((s) => s.with.name);

console.log("YAML parse: OK");
console.log("jobs:", Object.keys(doc.jobs).join(", "));
console.log("push.tags:", JSON.stringify(doc.on.push.tags));
console.log("push.branches:", JSON.stringify(doc.on.push.branches));
console.log("workflow_dispatch:", doc.on.workflow_dispatch === null || doc.on.workflow_dispatch !== undefined);
console.log("root permissions.contents:", doc.permissions.contents);
console.log("publish permissions.contents:", p.permissions.contents);
console.log("publish needs:", p.needs);
console.log("publish if:", p.if);
console.log("artifact names:", uploads.join(" | "));
console.log("download pattern:", p.steps[0].with.pattern);
console.log("keystore step:", b.steps.some((s) => s.name === "Restore release keystore"));
console.log("old cache/generate steps gone:", !b.steps.some((s) => /Generate keystore|Cache keystore/.test(s.name)));
console.log("hardcoded 994273 present:", text.includes("994273"));
console.log("build cmd:", b.steps.find((s) => s.name === "Build Phone release APK").run.trim());

const problems = [];
if (doc.permissions.contents !== "read") problems.push("root should stay read");
if (p.permissions.contents !== "write") problems.push("publish needs write");
if (p.if !== "github.ref_type == 'tag'") problems.push("publish if condition changed");
if (uploads.length !== 2) problems.push("expected 2 upload steps, got " + uploads.length);
if (text.includes("994273")) problems.push("hardcoded password still present");

console.log(problems.length ? "PROBLEMS: " + problems.join("; ") : "ALL CHECKS PASSED");
