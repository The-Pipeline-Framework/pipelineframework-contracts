import assert from 'node:assert/strict';
import test from 'node:test';
import { assertMonorepoPublishersStopped } from './require-monorepo-artifact-publishers-stop.mjs';

const artifacts = [
  { artifactId: 'pipelineframework-runtime-core' },
  { artifactId: 'pipelineframework-runtime-protocol' },
  { artifactId: 'pipelineframework-runtime-spi' },
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

test('accepts removal of any monorepo source mirror', () => {
  for (const removed of artifacts) {
    const sourceMirrors = mirrors();
    delete sourceMirrors[removed.artifactId];
    const framework = excludedFramework.replace(`<excludeArtifact>${removed.artifactId}</excludeArtifact>`, '');
    assert.doesNotThrow(() => assertMonorepoPublishersStopped(manifest(), sourceMirrors, framework, artifacts));
  }
});

test('rejects a public artifact', () => {
  for (const { artifactId } of artifacts) {
    const value = manifest();
    value.publicArtifacts.push({ artifactId });
    assert.throws(() => assertMonorepoPublishersStopped(value, mirrors(), excludedFramework, artifacts), new RegExp(`still declares ${artifactId} as a public artifact`));
  }
});

test('rejects missing external ownership', () => {
  for (const { artifactId } of artifacts) {
    const value = manifest();
    value.externalArtifacts = value.externalArtifacts.filter((entry) => entry.artifactId !== artifactId);
    assert.throws(() => assertMonorepoPublishersStopped(value, mirrors(), excludedFramework, artifacts), new RegExp(`does not declare ${artifactId} as externally owned`));
  }
});

test('rejects a deployable mirror', () => {
  for (const { artifactId } of artifacts) {
    const sourceMirrors = mirrors();
    sourceMirrors[artifactId] = sourceMirrors[artifactId].replace('>true<', '>false<');
    assert.throws(() => assertMonorepoPublishersStopped(manifest(), sourceMirrors, excludedFramework, artifacts), new RegExp(`${artifactId} source mirror remains deployable`));
  }
});

test('rejects a mirror included in the Central bundle', () => {
  for (const { artifactId } of artifacts) {
    const framework = excludedFramework.replace(`<excludeArtifact>${artifactId}</excludeArtifact>`, '');
    assert.throws(() => assertMonorepoPublishersStopped(manifest(), mirrors(), framework, artifacts), new RegExp(`Central bundle does not exclude the ${artifactId} mirror`));
  }
});
