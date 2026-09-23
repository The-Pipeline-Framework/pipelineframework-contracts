#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  candidate-version)
    mode=${2:?usage: system-tests.sh candidate-version pull_request|push PR_NUMBER SHA}
    pr_number=${3:--}
    sha=${4:?}
    case "$mode" in
      pull_request) mode=pr ;;
      push) mode=main ;;
      *) echo "event must be pull_request or push" >&2; exit 2 ;;
    esac
    current_version=$(python3 - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse("pom.xml").getroot()
print(root.findtext("{http://maven.apache.org/POM/4.0.0}version", ""))
PY
)
    if [[ "$current_version" == *-SNAPSHOT ]]; then
      base_version=${current_version%-SNAPSHOT}
    else
      base_version=${current_version%%-pr.*}
      base_version=${base_version%%-main.*}
    fi
    actual=$(bash scripts/candidate-version.sh "$mode" "$base_version" "$pr_number" "$sha")
    if [[ "$mode" == pr ]]; then
      expected="${base_version}-pr.${pr_number}.${sha:0:12}"
    else
      expected="${base_version}-main.${sha:0:12}"
    fi
    [[ "$actual" == "$expected" ]] || exit 1
    ;;
  reactor-coordinates)
    python3 - <<'PY'
import pathlib
import xml.etree.ElementTree as ET

ns = "{http://maven.apache.org/POM/4.0.0}"
expected = {
    ("org.pipelineframework", "pipelineframework-api", "jar"),
    ("org.pipelineframework", "pipelineframework-contracts-parent", "pom"),
    ("org.pipelineframework", "pipelineframework-dsl", "jar"),
    ("org.pipelineframework", "pipelineframework-runtime-api", "jar"),
    ("org.pipelineframework", "pipelineframework-runtime-core", "jar"),
    ("org.pipelineframework", "pipelineframework-runtime-protocol", "jar"),
    ("org.pipelineframework", "pipelineframework-runtime-serialization", "jar"),
    ("org.pipelineframework", "pipelineframework-runtime-spi", "jar"),
    ("org.pipelineframework", "pipelineframework-semantic-model", "jar"),
    ("org.pipelineframework", "representation-provider-api", "jar"),
}
root_pom = pathlib.Path("pom.xml")
root = ET.parse(root_pom).getroot()
version = root.findtext(ns + "version")
assert version
actual = set()
for pom in [root_pom, *sorted(pathlib.Path(".").glob("*/pom.xml"))]:
    project = ET.parse(pom).getroot()
    parent = project.find(ns + "parent")
    group = project.findtext(ns + "groupId") or (parent.findtext(ns + "groupId") if parent is not None else None)
    artifact = project.findtext(ns + "artifactId")
    packaging = project.findtext(ns + "packaging", "jar")
    actual.add((group, artifact, packaging))
    if pom != root_pom:
        assert parent is not None and parent.findtext(ns + "artifactId") == "pipelineframework-contracts-parent", pom
        assert parent.findtext(ns + "version") == version, pom
        assert project.findtext(ns + "version") in (None, version), pom
assert actual == expected, f"reactor coordinate drift: missing={expected - actual}, unexpected={actual - expected}"
PY
    ;;
  reactor-dependencies)
    python3 - <<'PY'
import pathlib
import subprocess
import tempfile
import xml.etree.ElementTree as ET

candidate = "26.9.4-pr.42.0123456789ab"
with tempfile.TemporaryDirectory(prefix="tpf-reactor-dependency-test-") as temporary:
    root = pathlib.Path(temporary)
    (root / "pom.xml").write_text("""<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.pipelineframework</groupId><artifactId>pipelineframework-contracts-parent</artifactId><version>26.9.4-SNAPSHOT</version></project>""")
    (root / "runtime-core").mkdir()
    (root / "runtime-protocol").mkdir()
    (root / "runtime-core/pom.xml").write_text("""<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.pipelineframework</groupId><artifactId>pipelineframework-runtime-core</artifactId><parent><groupId>org.pipelineframework</groupId><artifactId>pipelineframework-contracts-parent</artifactId><version>26.9.4-SNAPSHOT</version></parent></project>""")
    (root / "runtime-protocol/pom.xml").write_text("""<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.pipelineframework</groupId><artifactId>pipelineframework-runtime-protocol</artifactId><parent><groupId>org.pipelineframework</groupId><artifactId>pipelineframework-contracts-parent</artifactId><version>26.9.4-SNAPSHOT</version></parent><dependencies><dependency><groupId>org.pipelineframework</groupId><artifactId>pipelineframework-runtime-core</artifactId><version>26.9.4-SNAPSHOT</version></dependency><dependency><groupId>io.smallrye.reactive</groupId><artifactId>mutiny</artifactId><version>2.9.4</version></dependency></dependencies></project>""")
    subprocess.run(["python3", str(pathlib.Path("scripts/rewrite-reactor-dependencies.py").resolve()), str(root), candidate], check=True)
    pom = ET.parse(root / "runtime-protocol/pom.xml").getroot()
    ns = "{http://maven.apache.org/POM/4.0.0}"
    dependencies = pom.find(ns + "dependencies")
    assert dependencies[0].findtext(ns + "version") == candidate, ET.tostring(dependencies[0], encoding="unicode")
    assert dependencies[1].findtext(ns + "version") == "2.9.4", ET.tostring(dependencies[1], encoding="unicode")
PY
    ;;
  *) echo "usage: system-tests.sh candidate-version pull_request|push PR_NUMBER SHA | reactor-coordinates | reactor-dependencies" >&2; exit 2 ;;
esac
