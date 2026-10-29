#!/usr/bin/env python3
"""Finite-alphabet correctness and performance acceptance (propagates failures)."""
import argparse
import os
import subprocess

GATE_SUITES = (
    "morkl.WordEncoding*Test",
    "morkl.FiniteAlphabetTest",
    "morkl.ByteTrieDeepTest",
)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", action="append", help="Override the suite globs (also useful for gate self-checks)")
    args = parser.parse_args()
    env = os.environ.copy()
    env.setdefault("_JAVA_OPTIONS", "-Xmx8g")
    command = ["scala-cli", "test", "src/main/scala", "src/test/scala", "--server=false", "--scala", "3.8.1",
               "--source", "3.3", "--dependency", "org.scalameta::munit:1.2.1", "--dependency",
               "org.scala-lang.modules::scala-collection-contrib:0.3.0", "--require-tests"]
    for suite in args.suite or GATE_SUITES:
        subprocess.run(command + ["--test-only", suite], env=env, check=True)

if __name__ == "__main__":
    main()
