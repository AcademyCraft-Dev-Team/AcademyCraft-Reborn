import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const workspace = path.resolve("../..");
if (process.argv.includes("--inspect-control")) {
  const inputPath = path.join(
    workspace,
    "outputs",
    "20260801-skill-control-matrix",
    "AcademyCraft技能统一调控表.xlsx",
  );
  const input = await FileBlob.load(inputPath);
  const controlWorkbook = await SpreadsheetFile.importXlsx(input);
  const skillSheet = controlWorkbook.worksheets.getItem("技能总表");
  const values = skillSheet.getRange("A1:S100").values;
  const changes = [];
  for (let index = 2; index < values.length; index++) {
    const row = values[index];
    if (!row?.[1]) continue;
    const changed = row[12] !== "是"
      || row[13] !== row[5]
      || row[14] !== row[6]
      || row[15] !== row[8]
      || Boolean(row[16]);
    if (changed && row[16] !== "63") {
      changes.push({
        row: index + 1,
        category: row[0], id: row[1], name: row[2], currentIf: row[5], currentCp: row[6],
        currentKey: row[8], priority: row[11], enabled: row[12], targetIf: row[13],
        targetCp: row[14], targetKey: row[15], note: row[16], implementationStatus: row[18],
      });
    }
  }
  const conflictSheet = controlWorkbook.worksheets.getItem("冲突与风险");
  const conflictValues = conflictSheet.getRange("A1:G20").values;
  const conflictUpdates = conflictValues.slice(2)
    .filter((row) => row?.[0] && (row[4] || (row[5] && row[5] !== "未开始") || row[6]))
    .map((row, index) => ({ row: index + 3, priority: row[0], issue: row[1], owner: row[4], status: row[5], note: row[6] }));
  const summary = await controlWorkbook.inspect({
    kind: "workbook,sheet,table", maxChars: 5000, tableMaxRows: 4, tableMaxCols: 8,
  });
  console.log(summary.ndjson);
  console.log(`CONTROL_CHANGES=${JSON.stringify(changes, null, 2)}`);
  console.log(`CONFLICT_UPDATES=${JSON.stringify(conflictUpdates, null, 2)}`);
  process.exit(0);
}

const sourcePath = path.join(workspace, "docs", "SKILL_IMPLEMENTATION_CONTROL_MATRIX.md");
const outputDir = path.join(workspace, "outputs", "20260801-skill-control-matrix");
const outputPath = path.join(outputDir, "AcademyCraft技能统一调控表-最终版.xlsx");
const source = await fs.readFile(sourcePath, "utf8");
const lines = source.split(/\r?\n/);

const categoryHeadings = new Map([
  ["## Level0 公共脑开发", "Level0"],
  ["## Aeromanip 气动操纵", "Aeromanip"],
  ["## Accelerator 矢量操纵", "Accelerator"],
  ["## Electromaster 电气操纵", "Electromaster"],
  ["## Meltdowner 原子崩坏", "Meltdowner"],
  ["## Teleport 空间移动", "Teleport"],
  ["## Darkmatter 未元物质", "Darkmatter"],
  ["## Mentalout 心理掌握", "Mentalout"],
]);

function parseParameter(parameter) {
  const parts = parameter.split("/").map((part) => part.trim());
  const level = Number((parts[0] ?? "").replace(/^L/, "")) || 0;
  const ifText = parts[1] ?? "0";
  const ifMatch = ifText.match(/([\d.]+)\s*k/i);
  const developmentIf = ifMatch ? Number(ifMatch[1]) * 1000 : Number(ifText.replace(/[^\d.]/g, "")) || 0;
  return { level, developmentIf, cp: parts.slice(2).join(" / ") || "—" };
}

const skills = [];
let currentCategory = null;
for (const line of lines) {
  if (categoryHeadings.has(line)) {
    currentCategory = categoryHeadings.get(line);
    continue;
  }
  if (line.startsWith("## ") && !categoryHeadings.has(line)) currentCategory = null;
  if (!currentCategory || !/^\| `[^`]+` /.test(line)) continue;
  const cells = line.split("|").slice(1, -1).map((cell) => cell.trim());
  if (cells.length !== 7) continue;
  const skillMatch = cells[0].match(/^`([^`]+)`\s*(.*)$/);
  if (!skillMatch) continue;
  const params = parseParameter(cells[2]);
  skills.push({
    category: currentCategory,
    id: skillMatch[1],
    name: skillMatch[2],
    status: cells[1],
    level: params.level,
    developmentIf: params.developmentIf,
    cp: params.cp,
    effect: cells[3],
    defaultKey: cells[4].replaceAll("`", ""),
    dependencies: cells[5],
    implementationClass: cells[6].replaceAll("`", ""),
  });
}

const conflicts = [];
let inConflicts = false;
for (const line of lines) {
  if (line === "## 默认按键冲突与调控优先级") {
    inConflicts = true;
    continue;
  }
  if (inConflicts && line.startsWith("## ")) break;
  if (!inConflicts || !/^\| P[01] \|/.test(line)) continue;
  const cells = line.split("|").slice(1, -1).map((cell) => cell.trim().replaceAll("`", ""));
  if (cells.length === 4) conflicts.push(cells);
}

if (skills.length !== 94) throw new Error(`Expected 94 skills, found ${skills.length}`);
if (conflicts.length !== 4) throw new Error(`Expected 4 conflict/risk rows, found ${conflicts.length}`);

const conflictSkillIds = new Set([
  "electrical_contact", "current_recharge", "ball_lightning", "current_symbiosis",
  "area_teleport_start", "spacial_excision", "mental_takeover", "precision_operation",
]);

function suggestedPriority(skill) {
  if (skill.status.includes("高风险") || conflictSkillIds.has(skill.id)) return "P0";
  if (skill.status === "P6" || skill.status.includes("待审计")) return "P1";
  if (skill.developmentIf === 0 && skill.category !== "Level0") return "P1";
  if (skill.status.startsWith("保留")) return "P2";
  return "P3";
}

const workbook = Workbook.create();
const skillSheet = workbook.worksheets.add("技能总表");
const conflictSheet = workbook.worksheets.add("冲突与风险");
const guideSheet = workbook.worksheets.add("调控说明");
skillSheet.showGridLines = false;
conflictSheet.showGridLines = false;
guideSheet.showGridLines = false;

const headers = [
  "分类", "技能 ID", "中文名", "实现状态", "等级", "当前 IF", "当前 CP/占用",
  "当前实现与效果", "源码默认按键", "依赖", "实现类", "调整优先级", "启用",
  "目标 IF", "目标 CP/占用", "目标默认按键", "调整说明", "差异", "实施状态",
];
const dataRows = skills.map((skill) => [
  skill.category, skill.id, skill.name, skill.status, skill.level, skill.developmentIf, skill.cp,
  skill.effect, skill.defaultKey, skill.dependencies, skill.implementationClass,
  suggestedPriority(skill), "是", skill.developmentIf, skill.cp, skill.defaultKey, "", null, "未开始",
]);
const lastSkillRow = dataRows.length + 2;

skillSheet.getRange("A1:S1").merge();
skillSheet.getRange("A1").values = [["AcademyCraft 技能统一调控表"]];
skillSheet.getRange("A1:S1").format = {
  fill: "#17365D",
  font: { bold: true, color: "#FFFFFF", size: 16 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
};
skillSheet.getRange("A1:S1").format.rowHeight = 32;
skillSheet.getRange("A2:S2").values = [headers];
skillSheet.getRange(`A3:S${lastSkillRow}`).values = dataRows;
skillSheet.getRange(`R3:R${lastSkillRow}`).formulas = dataRows.map((_, index) => {
  const row = index + 3;
  return [`=IF(OR(M${row}<>"是",N${row}<>F${row},O${row}<>G${row},P${row}<>I${row},Q${row}<>""),"有调整","未调整")`];
});

const skillTable = skillSheet.tables.add(`A2:S${lastSkillRow}`, true, "SkillControlTable");
skillTable.style = "TableStyleMedium2";
skillTable.showFilterButton = true;
skillTable.showBandedRows = true;
skillSheet.freezePanes.freezeRows(2);
skillSheet.freezePanes.freezeColumns(3);

skillSheet.getRange(`A2:S${lastSkillRow}`).format = {
  verticalAlignment: "center",
  borders: { preset: "insideHorizontal", style: "thin", color: "#D8E1EA" },
};
skillSheet.getRange(`H3:H${lastSkillRow}`).format.wrapText = true;
skillSheet.getRange(`I3:I${lastSkillRow}`).format.wrapText = true;
skillSheet.getRange(`J3:J${lastSkillRow}`).format.wrapText = true;
skillSheet.getRange(`Q3:Q${lastSkillRow}`).format.wrapText = true;
skillSheet.getRange(`E3:F${lastSkillRow}`).format.numberFormat = "#,##0";
skillSheet.getRange(`N3:N${lastSkillRow}`).format.numberFormat = "#,##0";
skillSheet.getRange(`A3:A${lastSkillRow}`).format.font = { bold: true, color: "#17365D" };
skillSheet.getRange(`B3:B${lastSkillRow}`).format.font = { color: "#1F4E78" };
skillSheet.getRange(`L3:Q${lastSkillRow}`).format.fill = "#FFF8E7";

const widths = [15, 31, 18, 16, 8, 12, 20, 62, 34, 34, 27, 12, 9, 12, 20, 34, 46, 12, 12];
widths.forEach((width, index) => {
  skillSheet.getRangeByIndexes(0, index, lastSkillRow, 1).format.columnWidth = width;
});
skillSheet.getRange(`3:${lastSkillRow}`).format.rowHeight = 42;

skillSheet.getRange(`L3:L${lastSkillRow}`).dataValidation = {
  rule: { type: "list", values: ["P0", "P1", "P2", "P3", "暂不调整"] },
};
skillSheet.getRange(`M3:M${lastSkillRow}`).dataValidation = {
  rule: { type: "list", values: ["是", "否"] },
};
skillSheet.getRange(`S3:S${lastSkillRow}`).dataValidation = {
  rule: { type: "list", values: ["未开始", "待实现", "已修改", "已验证", "暂缓"] },
};

skillSheet.getRange(`D3:D${lastSkillRow}`).conditionalFormats.add("containsText", {
  text: "高风险", format: { fill: "#FCE8E6", font: { color: "#B3261E", bold: true } },
});
skillSheet.getRange(`D3:D${lastSkillRow}`).conditionalFormats.add("containsText", {
  text: "P6", format: { fill: "#FFF1CC", font: { color: "#8A5700", bold: true } },
});
skillSheet.getRange(`D3:D${lastSkillRow}`).conditionalFormats.add("containsText", {
  text: "移植", format: { fill: "#E6F4EA", font: { color: "#137333" } },
});
skillSheet.getRange(`L3:L${lastSkillRow}`).conditionalFormats.add("containsText", {
  text: "P0", format: { fill: "#F4CCCC", font: { color: "#990000", bold: true } },
});
skillSheet.getRange(`R3:R${lastSkillRow}`).conditionalFormats.add("containsText", {
  text: "有调整", format: { fill: "#FFF2CC", font: { color: "#7F6000", bold: true } },
});
skillSheet.getRange(`S3:S${lastSkillRow}`).conditionalFormats.add("containsText", {
  text: "已验证", format: { fill: "#D9EAD3", font: { color: "#274E13", bold: true } },
});

const conflictHeaders = ["优先级", "冲突/问题", "影响", "建议", "负责人", "处理状态", "处理备注"];
const conflictRows = conflicts.map((row) => [...row, "", "未开始", ""]);
const lastConflictRow = conflictRows.length + 2;
conflictSheet.getRange("A1:G1").merge();
conflictSheet.getRange("A1").values = [["默认按键冲突与实现风险"]];
conflictSheet.getRange("A1:G1").format = {
  fill: "#7F1D1D", font: { bold: true, color: "#FFFFFF", size: 15 },
  horizontalAlignment: "center", verticalAlignment: "center",
};
conflictSheet.getRange("A1:G1").format.rowHeight = 32;
conflictSheet.getRange("A2:G2").values = [conflictHeaders];
conflictSheet.getRange(`A3:G${lastConflictRow}`).values = conflictRows;
const conflictTable = conflictSheet.tables.add(`A2:G${lastConflictRow}`, true, "ConflictRiskTable");
conflictTable.style = "TableStyleMedium10";
conflictTable.showFilterButton = true;
conflictSheet.freezePanes.freezeRows(2);
conflictSheet.getRange(`A3:G${lastConflictRow}`).format.wrapText = true;
conflictSheet.getRange(`A3:G${lastConflictRow}`).format.verticalAlignment = "top";
conflictSheet.getRange(`3:${lastConflictRow}`).format.rowHeight = 58;
[11, 47, 48, 48, 16, 14, 40].forEach((width, index) => {
  conflictSheet.getRangeByIndexes(0, index, lastConflictRow, 1).format.columnWidth = width;
});
conflictSheet.getRange(`F3:F${lastConflictRow}`).dataValidation = {
  rule: { type: "list", values: ["未开始", "处理中", "已解决", "暂缓"] },
};
conflictSheet.getRange(`A3:A${lastConflictRow}`).conditionalFormats.add("containsText", {
  text: "P0", format: { fill: "#F4CCCC", font: { color: "#990000", bold: true } },
});
conflictSheet.getRange(`F3:F${lastConflictRow}`).conditionalFormats.add("containsText", {
  text: "已解决", format: { fill: "#D9EAD3", font: { color: "#274E13", bold: true } },
});

guideSheet.getRange("A1:F1").merge();
guideSheet.getRange("A1").values = [["调控说明与进度概览"]];
guideSheet.getRange("A1:F1").format = {
  fill: "#1F4E78", font: { bold: true, color: "#FFFFFF", size: 15 },
  horizontalAlignment: "center", verticalAlignment: "center",
};
guideSheet.getRange("A1:F1").format.rowHeight = 32;
guideSheet.getRange("A3:A7").values = [["已注册技能"], ["已有调整"], ["高风险技能"], ["P6 技能"], ["当前 IF 为 0"]];
guideSheet.getRange("B3:B7").formulas = [
  [`=COUNTA('技能总表'!$B$3:$B$${lastSkillRow})`],
  [`=COUNTIF('技能总表'!$R$3:$R$${lastSkillRow},"有调整")`],
  [`=COUNTIF('技能总表'!$D$3:$D$${lastSkillRow},"保留/高风险")`],
  [`=COUNTIF('技能总表'!$D$3:$D$${lastSkillRow},"P6")`],
  [`=COUNTIF('技能总表'!$F$3:$F$${lastSkillRow},0)`],
];
guideSheet.getRange("A3:B7").format = {
  fill: "#DCE6F1", borders: { preset: "outside", style: "thin", color: "#9FBAD0" },
};
guideSheet.getRange("A3:A7").format.font = { bold: true, color: "#17365D" };
guideSheet.getRange("B3:B7").format.font = { bold: true, color: "#17365D", size: 13 };
guideSheet.getRange("B3:B7").format.numberFormat = "#,##0";

const categories = ["Level0", "Aeromanip", "Accelerator", "Electromaster", "Meltdowner", "Teleport", "Darkmatter"];
guideSheet.getRange("A10:B10").values = [["分类", "技能数"]];
guideSheet.getRange("A11:A17").values = categories.map((category) => [category]);
guideSheet.getRange("B11:B17").formulas = categories.map((_, index) => [
  `=COUNTIF('技能总表'!$A$3:$A$${lastSkillRow},A${index + 11})`,
]);
const categoryTable = guideSheet.tables.add("A10:B17", true, "CategorySummaryTable");
categoryTable.style = "TableStyleMedium2";

guideSheet.getRange("D3:F3").merge();
guideSheet.getRange("D3").values = [["填写方法"]];
guideSheet.getRange("D4:F10").merge();
guideSheet.getRange("D4").values = [[
  "1. 在“技能总表”的黄色列填写目标 IF、目标 CP、目标默认按键和调整说明。\n" +
  "2. “差异”列会自动标记有调整的技能。\n" +
  "3. 使用调整优先级和实施状态筛选开发批次。\n" +
  "4. 当前值来自 2026-08-01 的源码盘点；默认键不代表玩家已有覆盖值。",
]];
guideSheet.getRange("D3:F3").format = {
  fill: "#5B9BD5", font: { bold: true, color: "#FFFFFF" }, horizontalAlignment: "center",
};
guideSheet.getRange("D4:F10").format = {
  fill: "#EAF2F8", wrapText: true, verticalAlignment: "top",
  borders: { preset: "outside", style: "thin", color: "#9FBAD0" },
};
guideSheet.getRange("D4:F10").format.rowHeight = 24;

guideSheet.getRange("D12:F12").merge();
guideSheet.getRange("D12").values = [["统一修改入口"]];
guideSheet.getRange("D13:F18").merge();
guideSheet.getRange("D13").values = [[
  "源码 Builder：等级、IF、CP、维持占用、依赖。\n" +
  "InputSystem.combo：源码默认按键。\n" +
  "技能类常量/方法：伤害、范围、持续时间、扫描间隔。\n" +
  "建议后续实现按技能 ID 索引的 SkillBalanceConfig。",
]];
guideSheet.getRange("D12:F12").format = {
  fill: "#70AD47", font: { bold: true, color: "#FFFFFF" }, horizontalAlignment: "center",
};
guideSheet.getRange("D13:F18").format = {
  fill: "#E2F0D9", wrapText: true, verticalAlignment: "top",
  borders: { preset: "outside", style: "thin", color: "#A9D18E" },
};

[22, 14, 5, 24, 24, 24].forEach((width, index) => {
  guideSheet.getRangeByIndexes(0, index, 18, 1).format.columnWidth = width;
});
guideSheet.freezePanes.freezeRows(1);

await fs.mkdir(outputDir, { recursive: true });

const skillInspect = await workbook.inspect({
  kind: "table", sheetId: "技能总表", range: `A1:S12`, include: "values,formulas",
  tableMaxRows: 12, tableMaxCols: 19, maxChars: 8000,
});
console.log(skillInspect.ndjson);
const conflictInspect = await workbook.inspect({
  kind: "table", sheetId: "冲突与风险", range: `A1:G${lastConflictRow}`, include: "values,formulas",
  tableMaxRows: 12, tableMaxCols: 7, maxChars: 5000,
});
console.log(conflictInspect.ndjson);
const errorInspect = await workbook.inspect({
  kind: "match", searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 }, summary: "final formula error scan",
});
console.log(errorInspect.ndjson);

for (const [sheetName, range, fileName] of [
  ["技能总表", "A1:S18", "技能总表预览.png"],
  ["冲突与风险", `A1:G${lastConflictRow}`, "冲突与风险预览.png"],
  ["调控说明", "A1:F18", "调控说明预览.png"],
]) {
  const preview = await workbook.render({ sheetName, range, scale: 1.4, format: "png" });
  await fs.writeFile(path.join(outputDir, fileName), new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(`OUTPUT=${outputPath}`);
