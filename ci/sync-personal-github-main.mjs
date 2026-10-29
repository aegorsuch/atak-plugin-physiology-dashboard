import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PROJECT = '6557';
const PERSONAL_REPO = 'aegorsuch/atak-plugin-physiology-dashboard';
const PERSONAL_REMOTE = `https://github.com/${PERSONAL_REPO}.git`;
const nullDevice = process.platform === 'win32' ? 'NUL' : '/dev/null';
const requireThat = (condition, message) => {
  if (!condition) throw new Error(message);
};

export async function syncPersonalMain(config, { fetcher = fetch, run = execFileSync } = {}) {
  requireThat(config.host === 'git.tak.gov' && config.projectId === PROJECT &&
    config.projectPath === 'aegorsuch/atak-plugin-physiology-dashboard' &&
    config.branch === 'main' && config.protected === 'true' &&
    config.pipelineSource === 'push', 'Only canonical protected main push pipelines may sync');
  requireThat(!['true', '1', 'yes'].includes(String(config.debugTrace ?? '').toLowerCase()),
    'Disable CI_DEBUG_TRACE before syncing the personal mirror');
  requireThat(/^[a-f0-9]{40}$/.test(config.sha ?? ''), 'Invalid pipeline source SHA');
  requireThat(config.jobToken && config.personalToken, 'Personal sync credentials are missing');

  async function api(url, headers, label) {
    const target = new URL(url);
    requireThat(target.protocol === 'https:' &&
      ['git.tak.gov', 'api.github.com'].includes(target.hostname), 'Untrusted sync API destination');
    const response = await fetcher(url, {
      headers, redirect: 'error', signal: AbortSignal.timeout(30_000),
    });
    requireThat(response.ok, `${label} lookup failed (${response.status})`);
    return response.json();
  }

  async function currentCanonicalSha() {
    const commit = await api(
      `https://git.tak.gov/api/v4/projects/${PROJECT}/repository/commits/main`,
      { 'JOB-TOKEN': config.jobToken }, 'Canonical main');
    requireThat(/^[a-f0-9]{40}$/.test(commit.id ?? ''), 'Canonical main returned an invalid SHA');
    return commit.id;
  }

  async function currentPersonalSha() {
    const repository = await api(`https://api.github.com/repos/${PERSONAL_REPO}`, {
      Authorization: `Bearer ${config.personalToken}`,
      'X-GitHub-Api-Version': '2022-11-28',
      Accept: 'application/vnd.github+json',
    }, 'Personal GitHub repository');
    requireThat(repository.full_name === PERSONAL_REPO && repository.visibility === 'public' &&
      repository.archived === false && repository.default_branch === 'main',
    'Personal GitHub repository is not the expected active public main mirror');
    const branch = await api(`https://api.github.com/repos/${PERSONAL_REPO}/branches/main`, {
      Authorization: `Bearer ${config.personalToken}`,
      'X-GitHub-Api-Version': '2022-11-28',
      Accept: 'application/vnd.github+json',
    }, 'Personal GitHub main');
    requireThat(/^[a-f0-9]{40}$/.test(branch.commit?.sha ?? ''),
      'Personal GitHub main returned an invalid SHA');
    return branch.commit.sha;
  }

  requireThat(await currentCanonicalSha() === config.sha,
    'Pipeline SHA is no longer canonical main HEAD');
  const personalSha = await currentPersonalSha();
  if (personalSha !== config.sha) {
    const encodedCredential = Buffer.from(`x-access-token:${config.personalToken}`).toString('base64');
    const env = {
      PATH: process.env.PATH,
      HOME: process.env.HOME,
      TMPDIR: process.env.TMPDIR,
      SYSTEMROOT: process.env.SYSTEMROOT,
      GIT_TERMINAL_PROMPT: '0',
      GIT_CONFIG_NOSYSTEM: '1',
      GIT_CONFIG_GLOBAL: nullDevice,
      GIT_CONFIG_COUNT: '3',
      GIT_CONFIG_KEY_0: 'http.followRedirects',
      GIT_CONFIG_VALUE_0: 'false',
      GIT_CONFIG_KEY_1: 'core.hooksPath',
      GIT_CONFIG_VALUE_1: nullDevice,
      GIT_CONFIG_KEY_2: `http.${PERSONAL_REMOTE}.extraheader`,
      GIT_CONFIG_VALUE_2: `Authorization: Basic ${encodedCredential}`,
    };
    const gitConfig = ['-c', 'credential.helper=', '-c', 'http.extraheader='];
    const git = args => {
      try {
        return run('git', [...gitConfig, ...args], {
          env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
        }).trim();
      } catch (error) {
        const step = args[0];
        if (step === 'merge-base' && error.status === 1) {
          throw new Error('Personal main is not an ancestor of canonical main; refusing non-fast-forward sync');
        }
        throw new Error(`Personal main git ${step} failed (${error.status ?? 'unknown exit'})`);
      }
    };

    requireThat(git(['rev-parse', 'HEAD']) === config.sha,
      'Canonical checkout HEAD does not match the pipeline SHA');
    requireThat(git(['rev-parse', '--is-shallow-repository']) === 'false',
      'Personal mirror sync requires a full-depth canonical checkout');
    git(['fetch', '--no-tags', '--no-recurse-submodules', PERSONAL_REMOTE, 'refs/heads/main']);
    const fetchedSha = git(['rev-parse', 'FETCH_HEAD']);
    requireThat(fetchedSha === personalSha,
      'Personal GitHub main changed while it was being fetched; retry from a fresh pipeline');
    git(['merge-base', '--is-ancestor', fetchedSha, config.sha]);

    if (fetchedSha !== config.sha) {
      requireThat(await currentCanonicalSha() === config.sha,
        'Canonical main advanced before personal mirror push');
      git(['push', '--no-follow-tags', '--recurse-submodules=no',
        PERSONAL_REMOTE, `${config.sha}:refs/heads/main`]);
    }
  }

  requireThat(await currentCanonicalSha() === config.sha,
    'Canonical main advanced during personal mirror sync');
  requireThat(await currentPersonalSha() === config.sha,
    'Personal GitHub main did not fast-forward to the pipeline SHA');
  return { synchronized: personalSha !== config.sha, sha: config.sha };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  syncPersonalMain({
    host: process.env.CI_SERVER_HOST,
    projectId: process.env.CI_PROJECT_ID,
    projectPath: process.env.CI_PROJECT_PATH,
    branch: process.env.CI_COMMIT_BRANCH,
    protected: process.env.CI_COMMIT_REF_PROTECTED,
    pipelineSource: process.env.CI_PIPELINE_SOURCE,
    debugTrace: process.env.CI_DEBUG_TRACE,
    sha: process.env.CI_COMMIT_SHA,
    jobToken: process.env.CI_JOB_TOKEN,
    personalToken: process.env.GITHUB_PERSONAL_RELEASE_TOKEN,
  }).then(result => console.log(`Personal GitHub main verified at ${result.sha}`))
    .catch(error => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
