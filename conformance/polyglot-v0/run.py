#!/usr/bin/env python3
"""Run the same contract examples in three languages, then challenge their checks.

This is test tooling, not a pipeline executor. Normal runs must accept the shared
corpus. Each later run changes one expected answer and must fail at that specific
assertion; a crashed interpreter is never evidence of conformance.
"""

import argparse
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PROBE = ROOT / "conformance/polyglot-v0"


class NumberLexeme(str):
    """An already parsed JSON number's spelling, retained only for fixture copies."""


def fixture_json(value):
    """Serialize mutations without rounding untouched configuration numbers.

    The standard JSON decoder validates the input and supplies NumberLexeme for
    decimal/exponent tokens. Only those validated tokens bypass normal JSON string
    quoting. This is fixture-copy tooling, not a new TPF payload codec.
    """
    if isinstance(value, NumberLexeme):
        return str(value)
    if isinstance(value, dict):
        return "{" + ",".join(json.dumps(key) + ":" + fixture_json(item)
                              for key, item in value.items()) + "}"
    if isinstance(value, list):
        return "[" + ",".join(fixture_json(item) for item in value) + "]"
    return json.dumps(value, ensure_ascii=False)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--vectors", type=Path, default=PROBE / "vectors.json")
    args = parser.parse_args(argv)
    # Resolve once in the caller's directory. Child processes run from ROOT,
    # but must read exactly the file that the mutation runner reads.
    args.vectors = args.vectors.resolve()
    return args


def run(command):
    """Execute an explicit argument list; fixture data never becomes shell code."""
    subprocess.run([str(arg) for arg in command], cwd=ROOT, check=True)


def java_classpath():
    """Use existing build outputs and exact POM dependencies, not a jar search."""
    modules = ("runtime-core", "semantic-model", "runtime-serialization", "runtime-protocol")
    paths = [ROOT / module / "target/classes" for module in modules]
    wanted = {
        "jackson-annotations", "jackson-core", "jackson-databind",
        "protobuf-java", "protobuf-java-util",
    }
    found = set()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.parse(ROOT / "pom.xml").getroot()
    for dependency in pom.findall("m:dependencyManagement/m:dependencies/m:dependency", namespace):
        artifact = dependency.findtext("m:artifactId", namespaces=namespace)
        if artifact not in wanted:
            continue
        group = dependency.findtext("m:groupId", namespaces=namespace)
        version = dependency.findtext("m:version", namespaces=namespace)
        jar = (ROOT / ".m2/repository" / group.replace(".", "/") / artifact
               / version / f"{artifact}-{version}.jar")
        if not jar.is_file():
            raise SystemExit(f"Missing exact dependency {jar}; run without --skip-build")
        found.add(artifact)
        paths.append(jar)
    if found != wanted:
        raise SystemExit(f"Expected POM dependencies not found: {sorted(wanted - found)}")
    return os.pathsep.join(str(path) for path in paths)


def consumer_commands():
    """Compile the test-only Java consumer, then return the three launch commands."""
    classpath = java_classpath()
    output = ROOT / "target/polyglot-v0"
    output.mkdir(parents=True, exist_ok=True)
    run(["javac", "--release", "21", "-cp", classpath, "-d", output,
         PROBE / "java/PolyglotProbeConsumer.java"])
    return {
        "java": ["java", "-cp", os.pathsep.join([str(output), classpath]), "PolyglotProbeConsumer"],
        "node": ["node", str(PROBE / "consumer.mjs")],
        "python": ["python3", str(PROBE / "consumer.py")],
    }


def mutations(original):
    """Change one oracle at a time, retaining the actual input and other answers.

The schema cases instead corrupt the document header to exercise early rejection.
The expected check ID belongs to the consumer assertion, not the temporary filename.
"""
    changed = copy.deepcopy(original)
    row = next(row for row in changed["presence"] if row["state"] == "NULL")
    row["state"] = "ABSENT"
    yield "null", f"presence/{row['id']}/state", changed

    changed = copy.deepcopy(original)
    next(row for row in changed["numbers"] if row["id"] == "large-candidate")["exact"] = "9007199254740992"
    yield "precision", "numbers/large-candidate/exact", changed

    changed = copy.deepcopy(original)
    next(row for row in changed["executions"] if row["id"] == "retry")["expected"]["aAttempts"] = 2
    yield "retry", "executions/retry/outcome", changed

    changed = copy.deepcopy(original)
    changed["signature"]["bodySha256"] = "0" * 64
    yield "body-digest", "signature/bodySha256", changed

    for row_id, invalid in (("null-code", "UNKNOWN_FAILURE"), ("null-owner", "FAILURE_POLICY_CONFLICT")):
        changed = copy.deepcopy(original)
        next(row for row in changed["failures"] if row["id"] == row_id)["diagnostic"] = invalid
        yield row_id, f"failures/{row_id}", changed

    for row_id, invalid in (("conflicting-retry-owner", "COMPLETED"),
                            ("cancel-before-retry", "RETRY_EXHAUSTED"),
                            ("injected-terminal-failure", "MALFORMED_FAILURE")):
        changed = copy.deepcopy(original)
        next(row for row in changed["executions"] if row["id"] == row_id)["expected"]["outcome"] = invalid
        yield row_id, f"executions/{row_id}/outcome", changed

    changed = copy.deepcopy(original)
    row = next(row for row in changed["signaturePaths"] if row["id"] == "null")
    row["preimage"] = row["preimage"].replace("\n/\n", "\n/null\n")
    yield "null-signature-path", "signaturePaths/null/preimage", changed

    for name, invalid_version in (("boolean-version", True), ("fractional-version", 1.5)):
        changed = copy.deepcopy(original)
        changed["schemaVersion"] = invalid_version
        yield name, "corpus/schemaVersion", changed


def require_expected_rejection(result, check_id, description):
    """Only the named assertion's normal failure counts as a rejected mutation."""
    marker = f"CONFORMANCE_MISMATCH:{check_id}"
    markers = [line for line in result.stderr.splitlines()
               if line.startswith("CONFORMANCE_MISMATCH:")]
    if result.returncode != 1 or markers != [marker]:
        raise AssertionError(
            f"{description}: expected exit 1 and only {marker!r}; "
            f"got exit {result.returncode}, markers {markers!r}\n{result.stderr}"
        )


def check_mutations(commands, vectors):
    original = json.loads(vectors.read_text(encoding="utf-8"), parse_float=NumberLexeme)
    names = []
    checks = 0
    with tempfile.TemporaryDirectory(prefix="tpf-polyglot-mutations-") as temporary:
        for name, check_id, changed in mutations(original):
            names.append(name)
            fixture = Path(temporary) / f"{name}.json"
            fixture.write_text(fixture_json(changed), encoding="utf-8")
            for language, command in commands.items():
                # A timeout is a broken test run, not a successful rejection. This
                # harness guard does not define a TPF business-function deadline.
                result = subprocess.run(
                    [*command, str(fixture)], cwd=ROOT, capture_output=True,
                    text=True, encoding="utf-8", check=False, timeout=60,
                )
                require_expected_rejection(result, check_id, f"{language}/{name}")
                checks += 1
    print(json.dumps({"mutationChecks": checks, "status": "PASS", "mutations": names}))


def main():
    args = parse_args()
    if not args.skip_build:
        run(["./mvnw", "-B", "-pl", "runtime-serialization,runtime-protocol", "-am",
             "package", "-Dgpg.skip", f"-Dmaven.repo.local={ROOT}/.m2/repository"])
    commands = consumer_commands()
    for command in commands.values():
        run([*command, args.vectors])
    check_mutations(commands, args.vectors)


if __name__ == "__main__":
    main()
