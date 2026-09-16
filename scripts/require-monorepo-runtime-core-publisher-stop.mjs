#!/usr/bin/env node
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const artifactId = 'pipelineframework-runtime-core';
const repository = 'The-Pipeline-Framework/pipelineframework';
const apiRoot = `https://api.github.com/repos/${repository}`;

export function assertMonorepoPublisherStopped(manifest, mirrorPom, frameworkPom) {
  if (!Array.isArray(manifest?.publicArtifacts) || !Array.isArray(manifest?.externalArtifacts)) {
    throw new Error('Monorepo publication manifest is missing public or external artifacts');
  }
  if (manifest.publicArtifacts.some((entry) => entry?.artifactId === artifactId)) {
    throw new Error('Monorepo still declares runtime-core as a public artifact');
  }
  if (!manifest.externalArtifacts.some((entry) => entry?.artifactId === artifactId && entry?.ownership === 'external')) {
    throw new Error('Monorepo does not declare runtime-core as externally owned');
  }
  if (mirrorPom !== undefined) {
    const properties = mirrorPom.match(/<properties>([\s\S]*?)<\/properties>/)?.[1] ?? '';
    if (!/<maven\.deploy\.skip>\s*true\s*<\/maven\.deploy\.skip>/.test(properties)) {
      throw new Error('Monorepo runtime-core source mirror remains deployable');
    }
    const publishingProfile = [...(frameworkPom ?? '').matchAll(/<profile>([\s\S]*?)<\/profile>/g)]
      .map((match) => match[1])
      .find((profile) => /<id>\s*central-publishing\s*<\/id>/.test(profile));
    const centralPlugin = [...(publishingProfile ?? '').matchAll(/<plugin>([\s\S]*?)<\/plugin>/g)]
      .map((match) => match[1])
      .find((plugin) => /<artifactId>\s*central-publishing-maven-plugin\s*<\/artifactId>/.test(plugin));
    const excluded = [...(centralPlugin ?? '').matchAll(/<excludeArtifact>\s*([^<]+)\s*<\/excludeArtifact>/g)]
      .some((match) => match[1].trim() === artifactId);
    if (!excluded) throw new Error('Monorepo Central bundle does not exclude the runtime-core mirror');
  }
}

async function githubJson(url) {
  const headers = {
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
  };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  const response = await fetch(url, { headers });
  if (!response.ok) throw new Error(`GitHub publication preflight failed: HTTP ${response.status} for ${url}`);
  return response.json();
}

async function githubFile(filePath, commitSha, optional = false) {
  const url = `${apiRoot}/contents/${filePath}?ref=${commitSha}`;
  const headers = {
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
  };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  const response = await fetch(url, { headers });
  if (optional && response.status === 404) return undefined;
  if (!response.ok) throw new Error(`GitHub publication preflight failed: HTTP ${response.status} for ${filePath}`);
  const file = await response.json();
  if (file.type !== 'file' || file.encoding !== 'base64' || typeof file.content !== 'string') {
    throw new Error(`GitHub publication preflight returned invalid content for ${filePath}`);
  }
  return Buffer.from(file.content, 'base64').toString('utf8');
}

async function main() {
  const mainRef = await githubJson(`${apiRoot}/git/ref/heads/main`);
  const commitSha = mainRef.object?.sha;
  if (typeof commitSha !== 'string' || !/^[0-9a-f]{40}$/.test(commitSha)) {
    throw new Error('GitHub publication preflight returned no monorepo main commit');
  }
  const [manifestJson, mirrorPom, frameworkPom] = await Promise.all([
    githubFile('framework/public-artifacts.json', commitSha),
    githubFile('framework/runtime-core/pom.xml', commitSha, true),
    githubFile('framework/pom.xml', commitSha),
  ]);
  assertMonorepoPublisherStopped(JSON.parse(manifestJson), mirrorPom, frameworkPom);
  console.log(`Monorepo ${commitSha} no longer publishes runtime-core; contracts is the sole publisher`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    await main();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
