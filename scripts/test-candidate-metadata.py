#!/usr/bin/env python3
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
base_version = ET.parse(ROOT / "pom.xml").getroot().findtext("{http://maven.apache.org/POM/4.0.0}version", "").removesuffix("-SNAPSHOT")
COORDS = [
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
]

for event, number in [("pull_request", 42), ("push", None)]:
    sha = "0123456789abcdef0123456789abcdef01234567"
    suffix = f"-pr.{number}.{sha[:12]}" if number else f"-main.{sha[:12]}"
    candidate = f"{base_version}{suffix}"
    with tempfile.TemporaryDirectory(prefix="tpf-candidate-metadata-") as temp:
        candidate_root = pathlib.Path(temp)
        repository = candidate_root / "repository"
        (candidate_root / "pom.xml").write_text(
            f'<project xmlns="http://maven.apache.org/POM/4.0.0"><version>{candidate}</version></project>\n'
        )
        (candidate_root / "candidate-manifest.json").write_text(
            json.dumps({"candidateVersion": candidate}, sort_keys=True) + "\n"
        )
        for group, artifact, packaging in COORDS:
            directory = repository / pathlib.Path(*group.split(".")) / artifact / candidate
            directory.mkdir(parents=True)
            ns = "http://maven.apache.org/POM/4.0.0"
            pom = f'<project xmlns="{ns}"><modelVersion>4.0.0</modelVersion><groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{candidate}</version><packaging>{packaging}</packaging></project>\n'
            (directory / f"{artifact}-{candidate}.pom").write_text(pom)
            if packaging == "jar":
                (directory / f"{artifact}-{candidate}.jar").write_bytes(b"deterministic candidate jar fixture")
        env = os.environ | {
            "GITHUB_REPOSITORY": "The-Pipeline-Framework/pipelineframework-contracts",
            "SOURCE_REPOSITORY": "Contributor/pipelineframework-contracts" if number else "The-Pipeline-Framework/pipelineframework-contracts",
            "SOURCE_SHA": sha,
            "GITHUB_EVENT_NAME": event,
            "PULL_REQUEST_NUMBER": str(number or ""),
            "GITHUB_RUN_ID": "1234",
            "GITHUB_RUN_ATTEMPT": "2",
        }
        subprocess.run(["python3", str(ROOT / "scripts/create-build-metadata.py"), str(candidate_root)],
                       check=True, cwd=ROOT, env=env)
        metadata = json.loads((candidate_root / "build-metadata.json").read_text())
        assert metadata["candidateVersion"] == candidate
        assert metadata["component"] == "contracts"
        assert metadata["pullRequestNumber"] == number
        assert len(metadata["mavenArtifacts"]) == len(COORDS)
        for artifact, (group, name, packaging) in zip(metadata["mavenArtifacts"], COORDS, strict=True):
            assert (artifact["groupId"], artifact["artifactId"], artifact["packaging"]) == (group, name, packaging)
            for item in artifact["files"]:
                path = repository / pathlib.Path(*artifact["groupId"].split(".")) / artifact["artifactId"] / candidate / item["name"]
                assert hashlib.sha256(path.read_bytes()).hexdigest() == item["sha256"]
        final_env = env | {"GITHUB_RUN_ID": "5678", "GITHUB_RUN_ATTEMPT": "1"}
        subprocess.run(["python3", str(ROOT / "scripts/finalize-candidate-manifest.py"), str(candidate_root)],
                       check=True, cwd=candidate_root, env=final_env)
        manifest_bytes = (candidate_root / "candidate-manifest/candidate-manifest.json").read_bytes()
        manifest = json.loads(manifest_bytes)
        assert set(manifest) == {"schemaVersion", "repository", "component", "sourceSha", "pullRequestNumber", "candidateVersion", "provenance", "mavenArtifacts", "images"}
        assert manifest["repository"] == "The-Pipeline-Framework/pipelineframework-contracts"
        assert manifest["component"] == "contracts"
        assert manifest["provenance"]["publication"]["event"] == "workflow_run"
        assert manifest["provenance"]["build"]["event"] == event
        assert manifest["images"] == []
        event_doc = json.loads((candidate_root / "candidate-event/event.json").read_text())
        assert set(event_doc) == {"schema_version", "source_repository", "source_sha", "pull_request_number", "component", "candidate_version", "publication_run_id", "manifest_sha256", "compatibility_set_id"}
        assert event_doc["manifest_sha256"] == hashlib.sha256(manifest_bytes).hexdigest()
        assert event_doc["publication_run_id"] == 5678
        assert event_doc["source_repository"] == "The-Pipeline-Framework/pipelineframework-contracts"
        if number:
            assert env["SOURCE_REPOSITORY"] != event_doc["source_repository"]
        pr_json = candidate_root / "current-pr.json"
        pr_json.write_text(json.dumps({
            "state": "open", "number": number or 42,
            "head": {"sha": sha, "ref": "candidate-branch", "repo": {"full_name": env["SOURCE_REPOSITORY"]}},
            "labels": [{"name": "safe-to-system-test"}],
        }))
        validate_env = env | {
            "BUILD_RUN_ID": "1234", "BUILD_RUN_ATTEMPT": "2", "BUILD_RUN_EVENT": event,
            "BUILD_RUN_HEAD_SHA": "fedcba9876543210fedcba9876543210fedcba98" if number else sha,
            "BUILD_RUN_HEAD_BRANCH": "candidate-branch" if number else "main",
            "BUILD_RUN_PATH": ".github/workflows/tpf-candidate-build.yml@refs/heads/main",
            "BUILD_RUN_REPOSITORY": "The-Pipeline-Framework/pipelineframework-contracts",
            "BUILD_ASSOCIATED_PR_NUMBERS": "[42]" if number else "[]",
        }
        subprocess.run(["bash", str(ROOT / "scripts/validate-candidate-files.sh"), str(candidate_root), str(pr_json)],
                       check=True, cwd=ROOT, env=validate_env)
        if number:
            missing_association_env = validate_env | {"BUILD_ASSOCIATED_PR_NUMBERS": "[]"}
            rejected = subprocess.run(
                ["bash", str(ROOT / "scripts/validate-candidate-files.sh"), str(candidate_root), str(pr_json)],
                cwd=ROOT, env=missing_association_env, capture_output=True,
            )
            assert rejected.returncode != 0, "empty workflow_run PR association must be rejected"
            pr_json.write_text(json.dumps({
                "state": "open", "number": 42,
                "head": {"sha": sha, "ref": "candidate-branch", "repo": {"full_name": env["SOURCE_REPOSITORY"]}},
                "labels": [],
            }))
            rejected = subprocess.run(
                ["bash", str(ROOT / "scripts/validate-candidate-files.sh"), str(candidate_root), str(pr_json)],
                cwd=ROOT, env=validate_env, capture_output=True,
            )
            assert rejected.returncode != 0, "fork PR without the safe label must be rejected"
            pr_json.write_text(json.dumps({
                "state": "open", "number": 42,
                "head": {"sha": "fedcba9876543210fedcba9876543210fedcba98", "ref": "candidate-branch", "repo": {"full_name": env["SOURCE_REPOSITORY"]}},
                "labels": [{"name": "safe-to-system-test"}],
            }))
            rejected = subprocess.run(
                ["bash", str(ROOT / "scripts/validate-candidate-files.sh"), str(candidate_root), str(pr_json)],
                cwd=ROOT, env=validate_env, capture_output=True,
            )
            assert rejected.returncode != 0, "a changed current PR head must be rejected"
print("synthetic PR and main candidate metadata passed")
