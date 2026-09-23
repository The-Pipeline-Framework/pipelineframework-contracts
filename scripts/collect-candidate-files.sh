#!/usr/bin/env bash
set -euo pipefail

output_dir=${1:?usage: collect-candidate-files.sh OUTPUT_DIR}
[[ ! -e "$output_dir" ]] || { echo "output directory already exists: $output_dir" >&2; exit 2; }
repo_root=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$output_dir/repository"
version=$(python3 - "$repo_root/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
print(ET.parse(sys.argv[1]).getroot().findtext("{http://maven.apache.org/POM/4.0.0}version", ""))
PY
)
local_repository="$repo_root/.m2/repository/org/pipelineframework"

# This must stay in sync with .github/tpf-system-tests.json's component allowlist.
coordinates=(
  "pipelineframework-api:jar"
  "pipelineframework-contracts-parent:pom"
  "pipelineframework-dsl:jar"
  "pipelineframework-runtime-api:jar"
  "pipelineframework-runtime-core:jar"
  "pipelineframework-runtime-protocol:jar"
  "pipelineframework-runtime-serialization:jar"
  "pipelineframework-runtime-spi:jar"
  "pipelineframework-semantic-model:jar"
  "representation-provider-api:jar"
)

for coordinate in "${coordinates[@]}"; do
  IFS=: read -r artifact_id packaging <<< "$coordinate"
  source_dir="$local_repository/$artifact_id/$version"
  destination="$output_dir/repository/org/pipelineframework/$artifact_id/$version"
  mkdir -p "$destination"
  pom="$source_dir/$artifact_id-$version.pom"
  [[ -f "$pom" && ! -L "$pom" ]] || { echo "missing or unsafe candidate POM: $pom" >&2; exit 1; }
  cp "$pom" "$destination/"
  if [[ "$packaging" == jar ]]; then
    jar="$source_dir/$artifact_id-$version.jar"
    [[ -f "$jar" && ! -L "$jar" ]] || { echo "missing or unsafe candidate JAR: $jar" >&2; exit 1; }
    cp "$jar" "$destination/"
  fi
done
