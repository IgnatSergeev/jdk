import subprocess
import argparse
import sys
import csv
import numpy
import seaborn
import pandas
import matplotlib.pyplot as plt
from pathlib import Path

upstream = 'upstream'
latest = 'latest'


def get_stdout(index: int, version: str, bench: str):
    directory = f'{str(Path(__file__).parent)}/res/{index}/out/{version}'
    Path(directory).resolve().mkdir(parents=True, exist_ok=True)
    return f'{directory}/{bench}'

def get_stderr(index: int, version: str, bench: str):
    directory = f'{str(Path(__file__).parent)}/res/{index}/err/{version}'
    Path(directory).resolve().mkdir(parents=True, exist_ok=True)
    return f'{directory}/{bench}'

def get_csv(index: int, version: str, bench: str):
    directory = f'{str(Path(__file__).parent)}/res/{index}/csv/{version}'
    Path(directory).resolve().mkdir(parents=True, exist_ok=True)
    return f'{directory}/{bench}'


def get_java_options(version: str):
    min_heap = 8192
    max_heap = 8192
    mem_track = 'summary'
    extra_java_options = [ '-XX:+SpecializedMethodData' ] if version == latest else []
    return [ '-XX:+UnlockDiagnosticVMOptions', f'-Xms{min_heap}m', f'-Xmx{max_heap}m', f'-XX:NativeMemoryTracking={mem_track}', '-XX:+PrintNMTStatistics', '-XX:+PrintMethodData' ] + extra_java_options

def get_java_path(version: str):
    root_dir = Path(__file__).resolve().parent.parent
    return root_dir / 'build' / 'release' / version / 'jdk' / 'bin' / 'java'


def get_renaissance_path():
    return Path(__file__).resolve().parent / 'renaissance-gpl-0.16.1.jar'

def get_benches():
    bench_list_proc = subprocess.Popen([get_java_path(upstream), '-jar', get_renaissance_path(), '--raw-list'], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    bench_list_proc.wait()
    stdout: str = bench_list_proc.communicate()[0]
    bench_list = stdout.splitlines()
    bench_list.sort()
    return bench_list

def get_bench_repetitions(bench: str):
    return 100


def run(index: int, version: str, bench: str, force: bool):
    try:
        open(get_csv(index, version, bench))
        if not force:
            return
    except:
        pass

    print(f'{index}: running {bench} {version}...')
    java_cmd = [ get_java_path(version) ] + get_java_options(version)
    java_cmd += [ '-jar', get_renaissance_path(), bench ] + [ "-r", str(get_bench_repetitions(bench)) ]
    java_cmd += [ '--csv', get_csv(index, version, bench) ]

    with open(get_stdout(index, version, bench), 'w') as f_out:
        with open(get_stderr(index, version, bench), 'w') as f_err:
            bench_proc = subprocess.Popen(java_cmd, stdout=f_out, stderr=f_err, text=True)
            bench_proc.wait()
            print(f'{index}: {bench} {version} exited with code {bench_proc.returncode}')

def get_measurements(version: str, bench: str):
    print(f'Analyzing {bench} {version}')
    measurements = []
    for index in range(1, 10):
        try:
            open(get_csv(index, version, bench))
        except:
            continue

        with open(get_csv(index, version, bench), 'r') as csv_file:
            data = csv.DictReader(csv_file)
            csv_arr = []
            for row in data:
                csv_arr.append(row)
            iterations = [int(x["duration_ns"]) for x in csv_arr[-10:]]
            measurements.append(int(numpy.mean(numpy.array(iterations))))

    print(f'Got {len(measurements)} measurements')

    return measurements


def main():
    parser = argparse.ArgumentParser(prog='bench')
    parser.add_argument('mode', choices=['run', 'analyze'])
    parser.add_argument('-f', '--force', action='store_true')
    args = parser.parse_args()

    print(f'Running in {args.mode} mode {"with force rewrite" if args.force else ""}')
    if args.mode == 'run':
        for index in range(1, 11):
            for bench in get_benches():
                run(index, upstream, bench, args.force)
                run(index, latest, bench, args.force)
    elif args.mode == 'analyze':
        data = []
        for bench in get_benches():
            for version in [ latest, upstream ]:
                data += [[bench, "shared \nprofiles" if version == "upstream" else "separate \nprofiles", x] for x in get_measurements(version, bench)]

        data_frame = pandas.DataFrame(data, columns=["benchmark", "version", "duration"])
        data_frame["duration_ms"] = data_frame["duration"] / 1e6

        benches = get_benches()

        palette = {"shared \nprofiles": "tab:orange", "separate \nprofiles": "tab:blue"}

        n_cols = min(5, len(benches))
        n_rows = (len(benches) + n_cols - 1) // n_cols
        fig, axes = plt.subplots(n_rows, n_cols, figsize=(3.2 * n_cols, 2.8 * n_rows))
        axes = numpy.atleast_1d(axes).flatten()
        for ax, bench in zip(axes, benches):
            sub = data_frame[data_frame["benchmark"] == bench]
            seaborn.violinplot(data=sub, x="version", y="duration_ms", ax=ax,
                            order=["shared \nprofiles", "separate \nprofiles"], palette=palette)
            ax.set_title(bench, fontsize=10)
            ax.set_xlabel("")
            ax.set_ylabel("duration (ms)")
            ax.grid(axis="y", linestyle=":", alpha=0.4)
        for ax in axes[len(benches):]:
            ax.set_visible(False)


        fig.tight_layout()
        plt.savefig("benchmark-results.png")

main()
