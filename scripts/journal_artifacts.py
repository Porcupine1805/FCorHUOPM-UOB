#!/usr/bin/env python3
import argparse
import csv
import math
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt

PRINCIPAL = [
    "HUOPM",
    "CORHUOPM_AC",
    "CORHUOPM_BOND",
    "COUPM_KULC_ADAPTED",
    "UOB_POSTFILTER",
    "UOB_FULL",
]

ABLATION = [
    "UOB_FULL",
    "UOB_NO_EDA",
    "UOB_NO_UO_BOUND",
    "UOB_EAGER_DUO",
    "UOB_RECOMPUTE_DUO",
]

LABELS = {
    "HUOPM": "HUOPM",
    "CORHUOPM_AC": "HUOP+AC",
    "CORHUOPM_BOND": "HUOP+Bond",
    "COUPM_KULC_ADAPTED": "HUOP+Kulc",
    "UOB_POSTFILTER": "UOB-Post",
    "UOB_FULL": "FCorHUOPM-UOB",
    "UOB_NO_EDA": "No EDA",
    "UOB_NO_UO_BOUND": "No UO bound",
    "UOB_EAGER_DUO": "Eager DUO",
    "UOB_RECOMPUTE_DUO": "Recompute DUO",
}

DATASET_ORDER = [
    "BMSPOS2",
    "Chess",
    "Foodmart",
    "Kosarak",
    "Mushroom",
    "Retail",
    "Pumsb",
    "T10I4N4KD100K",
    "T10I4N4KD500K",
]


def read_csv(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def fnum(x, digits=1):
    x = float(x)
    if abs(x) >= 100:
        return f"{x:,.0f}"
    return f"{x:,.{digits}f}"


def tex_escape(s):
    return s.replace("_", r"\_")


def metric_map(summary):
    d = {}
    for r in summary:
        d[(r["dataset"], r["mode"])] = r
    return d


def grouped_bar(summary, out, metric, ylabel, modes, log=False, symlog=False):
    by = metric_map(summary)
    datasets = [d for d in DATASET_ORDER if any((d, m) in by for m in modes)]
    x = list(range(len(datasets)))
    width = 0.82 / len(modes)
    fig, ax = plt.subplots(figsize=(13.5, 5.4))
    for k, mode in enumerate(modes):
        vals = []
        for d in datasets:
            r = by.get((d, mode))
            vals.append(float(r[metric]) if r else float("nan"))
        pos = [i - 0.41 + width / 2 + k * width for i in x]
        ax.bar(pos, vals, width=width, label=LABELS.get(mode, mode))
    ax.set_xticks(x)
    ax.set_xticklabels(datasets, rotation=35, ha="right")
    ax.set_ylabel(ylabel)
    if log:
        ax.set_yscale("log")
    if symlog:
        ax.set_yscale("symlog", linthresh=1)
    ax.grid(axis="y", alpha=0.25)
    ax.legend(fontsize=8, ncol=3)
    fig.tight_layout()
    fig.savefig(out.with_suffix(".png"), dpi=300)
    fig.savefig(out.with_suffix(".pdf"))
    plt.close(fig)


def ratio_plot(summary, out):
    by = metric_map(summary)
    datasets, ratios = [], []
    for d in DATASET_ORDER:
        a = by.get((d, "UOB_POSTFILTER"))
        b = by.get((d, "UOB_FULL"))
        if not a or not b:
            continue
        full = float(b["runtime_median_ms"])
        ratios.append(float(a["runtime_median_ms"]) / full if full > 0 else float("nan"))
        datasets.append(d)
    fig, ax = plt.subplots(figsize=(12.5, 4.8))
    ax.bar(range(len(datasets)), ratios, color="#4477AA")
    ax.axhline(1.0, color="#222222", linewidth=1.0)
    ax.set_xticks(range(len(datasets)))
    ax.set_xticklabels(datasets, rotation=35, ha="right")
    ax.set_ylabel("Median runtime ratio: UOB-Post / FCorHUOPM-UOB")
    ax.set_yscale("log")
    ax.grid(axis="y", alpha=0.25)
    fig.tight_layout()
    fig.savefig(out.with_suffix(".png"), dpi=300)
    fig.savefig(out.with_suffix(".pdf"))
    plt.close(fig)


def scaling_plot(summary, out):
    by = metric_map(summary)
    all_sizes = [100, 200, 300, 400, 500]
    pairs = [(s, f"T10I4N4KD{s}K") for s in all_sizes if (f"T10I4N4KD{s}K", "HUOPM") in by]
    if len(pairs) < 2:
        return
    sizes = [s for s, _ in pairs]
    datasets = [d for _, d in pairs]
    modes = ["HUOPM", "UOB_POSTFILTER", "UOB_FULL", "UOB_EAGER_DUO"]
    fig, ax = plt.subplots(figsize=(7.8, 4.8))
    for mode in modes:
        vals = [float(by[(d, mode)]["runtime_median_ms"]) for d in datasets]
        ax.plot(sizes, vals, marker="o", label=LABELS[mode])
    ax.set_xlabel("Transactions in T10I4N4KD family (thousands)")
    ax.set_ylabel("Median runtime (ms)")
    ax.grid(alpha=0.25)
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(out.with_suffix(".png"), dpi=300)
    fig.savefig(out.with_suffix(".pdf"))
    plt.close(fig)


def write_dataset_table(manifest, config, out):
    cfg = {r["dataset"].lower(): r for r in config}
    valid = [r for r in manifest if r.get("status") == "VALID"]
    valid.sort(key=lambda r: DATASET_ORDER.index(display_name(r["dataset"])) if display_name(r["dataset"]) in DATASET_ORDER else 999)
    lines = [
        r"\begin{table*}[t]",
        r"\caption{Selected real datasets and journal benchmark thresholds}\label{tab:datasets2}",
        r"\centering\scriptsize",
        r"\resizebox{\textwidth}{!}{%",
        r"\begin{tabular}{lrrrrrrccc}",
        r"\toprule",
        r"Dataset & Tx. & Items & Occ. & Avg. len. & Density (\%) & Zero-U & $\alpha$ & $\beta$ & $\theta_{\mathrm{UOB}}$\\",
        r"\midrule",
    ]
    for r in valid:
        name = display_name(r["dataset"])
        c = cfg[name.lower()]
        tx = int(r["valid_transactions"])
        items = int(r["distinct_items"])
        occ = int(r["item_occurrences"])
        avg = occ / tx
        density = 100.0 * occ / (tx * items)
        lines.append(
            f"{tex_escape(name)} & {tx:,} & {items:,} & {occ:,} & {avg:.3f} & {density:.4f} & "
            f"{int(r['zero_utility_items'])} & {c['minsup']} & {c['minuo']} & {c['minuob']}\\\\"
        )
    lines += [r"\bottomrule", r"\end{tabular}}", r"\end{table*}"]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def display_name(name):
    return {"kosarak": "Kosarak", "chess": "Chess", "foodmart": "Foodmart", "mushroom": "Mushroom", "retail": "Retail", "pumsb": "Pumsb"}.get(name, name)


def write_uob_table(summary, out):
    by = metric_map(summary)
    lines = [
        r"\begin{table*}[t]",
        r"\caption{Integrated FCorHUOPM-UOB results on the selected real datasets ($n=10$)}\label{tab:uobfull}",
        r"\centering\scriptsize",
        r"\resizebox{\textwidth}{!}{%",
        r"\begin{tabular}{lrrrrr}",
        r"\toprule",
        r"Dataset & Patterns & Runtime median (ms) & IQR (ms) & Peak heap median (MB) & Nodes\\",
        r"\midrule",
    ]
    for d in DATASET_ORDER:
        r = by[(d, "UOB_FULL")]
        lines.append(
            f"{tex_escape(d)} & {int(r['patterns']):,} & {fnum(r['runtime_median_ms'], 1)} & "
            f"{fnum(r['runtime_iqr_ms'], 1)} & {fnum(r['heap_median_mb'], 1)} & {int(float(r['nodes_median'])):,}\\\\"
        )
    lines += [r"\bottomrule", r"\end{tabular}}", r"\end{table*}"]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_ablation_table(summary, out):
    by = metric_map(summary)
    candidates = ["BMSPOS2", "Chess", "Mushroom", "Pumsb", "T10I4N4KD500K", "T40I10D100K"]
    datasets = [d for d in candidates if all((d, mode) in by for mode in ABLATION)]
    lines = [
        r"\begin{table*}[t]",
        r"\caption{Ablation summary on representative datasets (medians over ten observations)}\label{tab:ablationnew}",
        r"\centering\scriptsize",
        r"\resizebox{\textwidth}{!}{%",
        r"\begin{tabular}{llrrrrrr}",
        r"\toprule",
        r"Dataset & Variant & Pat. & Runtime (ms) & Nodes & EDA aborts & DUO processed & Lazy skips\\",
        r"\midrule",
    ]
    for d in datasets:
        for mode in ABLATION:
            r = by[(d, mode)]
            lines.append(
                f"{tex_escape(d)} & {LABELS[mode]} & {int(r['patterns']):,} & {fnum(r['runtime_median_ms'])} & "
                f"{int(float(r['nodes_median'])):,} & {int(float(r['eda_aborts_median'])):,} & "
                f"{int(float(r['duo_entries_median'])):,} & {int(float(r['lazy_duo_skips_median'])):,}\\\\"
            )
    lines += [r"\bottomrule", r"\end{tabular}}", r"\end{table*}"]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_speed_table(summary, out):
    by = metric_map(summary)
    lines = [
        r"\begin{table*}[t]",
        r"\caption{Post-filtering versus integrated UO-Bond pruning on the selected datasets}\label{tab:postfilter}",
        r"\centering\scriptsize",
        r"\resizebox{\textwidth}{!}{%",
        r"\begin{tabular}{lrrrrr}",
        r"\toprule",
        r"Dataset & UOB-Post (ms) & FCorHUOPM-UOB (ms) & Ratio & Post nodes & Integrated nodes\\",
        r"\midrule",
    ]
    for d in DATASET_ORDER:
        post = by[(d, "UOB_POSTFILTER")]
        full = by[(d, "UOB_FULL")]
        ratio = float(post["runtime_median_ms"]) / float(full["runtime_median_ms"])
        lines.append(
            f"{tex_escape(d)} & {fnum(post['runtime_median_ms'])} & {fnum(full['runtime_median_ms'])} & "
            f"{ratio:.2f} & {int(float(post['nodes_median'])):,} & {int(float(full['nodes_median'])):,}\\\\"
        )
    lines += [r"\bottomrule", r"\end{tabular}}", r"\end{table*}"]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--summary", default="results/summary/jiis_selected9_summary.csv")
    ap.add_argument("--manifest", default="datasets/real/dataset_cleaning_manifest.csv")
    ap.add_argument("--config", default="config/jiis_selected9_grid.csv")
    ap.add_argument("--figures", default="figures")
    ap.add_argument("--tables", default="results/tables")
    ap.add_argument("--datasets", default="")
    args = ap.parse_args()

    summary = read_csv(args.summary)
    manifest = read_csv(args.manifest)
    config = read_csv(args.config)
    figdir = Path(args.figures)
    tabdir = Path(args.tables)
    figdir.mkdir(parents=True, exist_ok=True)
    tabdir.mkdir(parents=True, exist_ok=True)

    global DATASET_ORDER
    if args.datasets:
        requested = [d.strip() for d in args.datasets.split(",") if d.strip()]
        DATASET_ORDER = requested
        wanted = set(requested)
        summary = [r for r in summary if r["dataset"] in wanted]
        manifest = [r for r in manifest if display_name(r["dataset"]) in wanted]
        config = [r for r in config if r["dataset"] in wanted]

    grouped_bar(summary, figdir / "Fig1", "runtime_median_ms", "Median runtime (ms)", PRINCIPAL, log=True)
    grouped_bar(summary, figdir / "Fig2", "patterns", "Output patterns", PRINCIPAL, symlog=True)
    grouped_bar(summary, figdir / "Fig3", "nodes_median", "Visited search nodes", PRINCIPAL, log=True)
    grouped_bar(summary, figdir / "Fig4", "heap_median_mb", "Median peak heap (MB)", PRINCIPAL, log=True)
    ratio_plot(summary, figdir / "Fig5")
    scaling_plot(summary, figdir / "Fig6")

    write_dataset_table(manifest, config, tabdir / "table_datasets_journal.tex")
    write_uob_table(summary, tabdir / "table_uobfull_journal.tex")
    write_speed_table(summary, tabdir / "table_postfilter_journal.tex")
    write_ablation_table(summary, tabdir / "table_ablation_journal.tex")


if __name__ == "__main__":
    main()
