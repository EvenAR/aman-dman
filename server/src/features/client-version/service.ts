interface VersionCache {
  version: string | null;
  timestamp: number;
}

const cache: VersionCache = {
  version: null,
  timestamp: 0,
};

export function compareVersions(a: string, b: string): number {
  const partsA = a.split('.').map(Number);
  const partsB = b.split('.').map(Number);

  for (let index = 0; index < Math.max(partsA.length, partsB.length); index += 1) {
    const valueA = partsA[index] || 0;
    const valueB = partsB[index] || 0;

    if (valueA > valueB) {
      return 1;
    }

    if (valueA < valueB) {
      return -1;
    }
  }

  return 0;
}

async function fetchLatestClientVersion(githubToken: string | null): Promise<string | null> {
  const response = await fetch('https://api.github.com/repos/EvenAR/aman-dman/releases/latest', {
    headers: {
      Accept: 'application/vnd.github+json',
      ...(githubToken ? { Authorization: `Bearer ${githubToken}` } : {}),
      'X-GitHub-Api-Version': '2022-11-28',
    },
  });

  if (!response.ok) {
    return null;
  }

  const json = (await response.json()) as { tag_name?: string };
  const tagName = json.tag_name ?? null;

  if (!tagName) {
    return null;
  }

  return tagName.startsWith('v') ? tagName.slice(1) : tagName;
}

export async function getLatestVersionCached(githubToken: string | null): Promise<string | null> {
  const now = Date.now();
  const ttlMs = 10 * 60 * 1000;

  if (cache.version && now - cache.timestamp < ttlMs) {
    return cache.version;
  }

  const latest = await fetchLatestClientVersion(githubToken);
  if (latest) {
    cache.version = latest;
    cache.timestamp = now;
  }

  return cache.version;
}
