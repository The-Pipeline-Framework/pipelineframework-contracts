import assert from 'node:assert/strict';
import test from 'node:test';
import { assertMonorepoPublishersStopped } from './require-monorepo-artifact-publishers-stop.mjs';

const artifacts = [
  { artifactId: 'pipelineframework-runtime-core' },
  { artifactId: 'pipelineframework-runtime-protocol' },
];
const manifest = () => ({
  publicArtifacts: [],
  externalArtifacts: artifacts.map(({ artifactId }) => ({ artifactId, ownership: 'external' })),
});
const mirrors = (skip = 'true') => Object.fromEntries(artifacts.map(({ artifactId }) => [artifactId, `<project><properties><maven.deploy.skip>${skip}</maven.deploy.skip></properties></project>`]));
const excludedFramework = `<project><profile><id>central-publishing</id><plugin><artifactId>central-publishing-maven-plugin</artifactId><configuration><excludeArtifacts>${artifacts.map(({ artifactId }) => `<excludeArtifact>${artifactId}</excludeArtifact>`).join('')}</excludeArtifacts></configuration></plugin></profile></project>`;

test('accepts externally owned, non-deployable source mirrors', () => {
  assert.doesNotThrow(() => assertMonorepoPublishersStopped(manifest(), mirrors(), excludedFramework, artifacts));
});

test('accepts removal of either monorepo source mirror', () => {
  for (const removed of artifacts) {
    const sourceMirrors = mirrors();
    delete sourceMirrors[removed.artifactId];
    const framework = excludedFramework.replace(`<excludeArtifact>${removed.artifactId}</excludeArtifact>`, '');
    assert.doesNotThrow(() => assertMonorepoPublishersStopped(manifest(), sourceMirrors, framework, artifacts));
  }
});

test('rejects a public artifact', () => {
  const value = manifest();
  value.publicArtifacts.push({ artifactId: artifacts[0].artifactId });
  assert.throws(() => assertMonorepoPublishersStopped(value, mirrors(), excludedFramework, artifacts), /still declares pipelineframework-runtime-core as a public artifact/);
});

test('rejects missing external ownership', () => {
  assert.throws(() => assertMonorepoPublishersStopped({ publicArtifacts: [], externalArtifacts: [] }, mirrors(), excludedFramework, artifacts), /does not declare pipelineframework-runtime-core as externally owned/);
});

test('rejects a deployable mirror', () => {
  assert.throws(() => assertMonorepoPublishersStopped(manifest(), mirrors('false'), excludedFramework, artifacts), /source mirror remains deployable/);
});

test('rejects a mirror included in the Central bundle', () => {
  const framework = excludedFramework.replace('<excludeArtifact>pipelineframework-runtime-protocol</excludeArtifact>', '');
  assert.throws(() => assertMonorepoPublishersStopped(manifest(), mirrors(), framework, artifacts), /Central bundle does not exclude the pipelineframework-runtime-protocol mirror/);
});
