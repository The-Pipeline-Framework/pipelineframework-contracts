import assert from 'node:assert/strict';
import test from 'node:test';
import { assertMonorepoPublisherStopped } from './require-monorepo-runtime-core-publisher-stop.mjs';

const artifactId = 'pipelineframework-runtime-core';
const externalManifest = () => ({
  publicArtifacts: [],
  externalArtifacts: [{ artifactId, ownership: 'external' }],
});
const skippedMirror = '<project><properties><maven.deploy.skip>true</maven.deploy.skip></properties></project>';
const excludedFramework = '<project><profile><id>central-publishing</id><plugin><artifactId>central-publishing-maven-plugin</artifactId><configuration><excludeArtifacts><excludeArtifact>pipelineframework-runtime-core</excludeArtifact></excludeArtifacts></configuration></plugin></profile></project>';

test('accepts an externally owned, non-deployable source mirror', () => {
  assert.doesNotThrow(() => assertMonorepoPublisherStopped(externalManifest(), skippedMirror, excludedFramework));
});

test('accepts removal of the monorepo source mirror', () => {
  assert.doesNotThrow(() => assertMonorepoPublisherStopped(externalManifest(), undefined, '<project/>'));
});

test('rejects the monorepo while it still lists runtime-core as public', () => {
  const manifest = externalManifest();
  manifest.publicArtifacts.push({ artifactId });
  assert.throws(() => assertMonorepoPublisherStopped(manifest, skippedMirror, excludedFramework), /still declares runtime-core as a public artifact/);
});

test('rejects a missing external ownership declaration', () => {
  assert.throws(() => assertMonorepoPublisherStopped({ publicArtifacts: [], externalArtifacts: [] }, skippedMirror, excludedFramework), /does not declare runtime-core as externally owned/);
});

test('rejects a deployable monorepo mirror', () => {
  const pom = '<project><properties><maven.deploy.skip>false</maven.deploy.skip></properties></project>';
  assert.throws(() => assertMonorepoPublisherStopped(externalManifest(), pom, excludedFramework), /source mirror remains deployable/);
});

test('rejects a mirror that Maven skips but Sonatype still bundles', () => {
  assert.throws(() => assertMonorepoPublisherStopped(externalManifest(), skippedMirror, '<project/>'), /Central bundle does not exclude/);
});
