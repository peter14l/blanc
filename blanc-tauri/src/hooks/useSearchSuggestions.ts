import { useState, useEffect, useRef } from 'react';
import { HistoryEntry, Bookmark } from '../types/browser';

export type SuggestionType = 'website' | 'bookmark' | 'history' | 'internal' | 'query';

export interface SuggestionItem {
  id: string;
  type: SuggestionType;
  title: string;
  subtitle?: string;
  url?: string;
  query?: string;
  badge?: string;
}

export interface TopSite {
  title: string;
  url: string;
  domain: string;
  keywords: string[];
}

export const TOP_SITES: TopSite[] = [
  { title: 'Apple', url: 'https://apple.com', domain: 'apple.com', keywords: ['apple', 'mac', 'iphone', 'ipad', 'ios', 'app'] },
  { title: 'YouTube', url: 'https://youtube.com', domain: 'youtube.com', keywords: ['youtube', 'yt', 'you', 'video', 'videos', 'music', 'stream', 'tub'] },
  { title: 'Google', url: 'https://google.com', domain: 'google.com', keywords: ['google', 'search', 'gmail', 'drive', 'goo'] },
  { title: 'GitHub', url: 'https://github.com', domain: 'github.com', keywords: ['github', 'git', 'repo', 'code', 'open source', 'gh'] },
  { title: 'Wikipedia', url: 'https://wikipedia.org', domain: 'wikipedia.org', keywords: ['wikipedia', 'wiki', 'encyclopedia', 'knowledge', 'wik'] },
  { title: 'Reddit', url: 'https://reddit.com', domain: 'reddit.com', keywords: ['reddit', 'subreddits', 'threads', 'red'] },
  { title: 'X (Twitter)', url: 'https://x.com', domain: 'x.com', keywords: ['x', 'twitter', 'tweet', 'twit', 'feed'] },
  { title: 'Hacker News', url: 'https://news.ycombinator.com', domain: 'news.ycombinator.com', keywords: ['hacker news', 'hn', 'ycombinator', 'tech news', 'hack'] },
  { title: 'ChatGPT', url: 'https://chatgpt.com', domain: 'chatgpt.com', keywords: ['chatgpt', 'openai', 'gpt', 'ai', 'chat', 'prompt'] },
  { title: 'Amazon', url: 'https://amazon.com', domain: 'amazon.com', keywords: ['amazon', 'shopping', 'store', 'prime', 'ama'] },
  { title: 'Netflix', url: 'https://netflix.com', domain: 'netflix.com', keywords: ['netflix', 'movies', 'series', 'stream', 'net'] },
  { title: 'Twitch', url: 'https://twitch.tv', domain: 'twitch.tv', keywords: ['twitch', 'stream', 'gaming', 'live', 'twi'] },
  { title: 'Discord', url: 'https://discord.com', domain: 'discord.com', keywords: ['discord', 'chat', 'voice', 'server', 'disc'] },
  { title: 'Spotify', url: 'https://spotify.com', domain: 'spotify.com', keywords: ['spotify', 'music', 'songs', 'podcast', 'spot'] },
  { title: 'Stack Overflow', url: 'https://stackoverflow.com', domain: 'stackoverflow.com', keywords: ['stackoverflow', 'stack', 'coding', 'errors', 'so'] },
  { title: 'LinkedIn', url: 'https://linkedin.com', domain: 'linkedin.com', keywords: ['linkedin', 'jobs', 'network', 'career', 'link'] },
  { title: 'DuckDuckGo', url: 'https://duckduckgo.com', domain: 'duckduckgo.com', keywords: ['duckduckgo', 'ddg', 'privacy search', 'duck'] },
  { title: 'Instagram', url: 'https://instagram.com', domain: 'instagram.com', keywords: ['instagram', 'insta', 'photos', 'reels', 'ig'] },
  { title: 'Blanc Browser', url: 'https://blancbrowser.com', domain: 'blancbrowser.com', keywords: ['blanc', 'browser', 'bnfy', 'island'] },
  { title: 'Tauri', url: 'https://v2.tauri.app', domain: 'v2.tauri.app', keywords: ['tauri', 'rust gui', 'desktop app'] },
  { title: 'MDN Web Docs', url: 'https://developer.mozilla.org', domain: 'developer.mozilla.org', keywords: ['mdn', 'mozilla', 'javascript docs', 'css docs'] },
];

export function useSearchSuggestions({
  query,
  history = [],
  bookmarks = [],
  enabled = true,
}: {
  query: string;
  history?: HistoryEntry[];
  bookmarks?: Bookmark[];
  enabled?: boolean;
}) {
  const [suggestions, setSuggestions] = useState<SuggestionItem[]>([]);
  const [loading, setLoading] = useState(false);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const raw = query.trim();
    if (!enabled || !raw) {
      setSuggestions([]);
      setLoading(false);
      return;
    }

    const clean = raw.toLowerCase();
    const siteMatches: SuggestionItem[] = [];
    const seenUrls = new Set<string>();

    // 1. Direct Internal Blanc Pages
    if (clean.startsWith('blanc:') || 'history'.startsWith(clean)) {
      siteMatches.push({
        id: 'internal-history',
        type: 'internal',
        title: 'Blanc History',
        subtitle: 'blanc://history',
        url: 'blanc://history',
        badge: 'Internal',
      });
      seenUrls.add('blanc://history');
    }
    if (clean.startsWith('blanc:') || 'bookmarks'.startsWith(clean)) {
      siteMatches.push({
        id: 'internal-bookmarks',
        type: 'internal',
        title: 'Blanc Bookmarks',
        subtitle: 'blanc://bookmarks',
        url: 'blanc://bookmarks',
        badge: 'Internal',
      });
      seenUrls.add('blanc://bookmarks');
    }
    if (clean.startsWith('blanc:') || 'settings'.startsWith(clean)) {
      siteMatches.push({
        id: 'internal-settings',
        type: 'internal',
        title: 'Blanc Settings',
        subtitle: 'blanc://settings',
        url: 'blanc://settings',
        badge: 'Internal',
      });
      seenUrls.add('blanc://settings');
    }
    if (clean.startsWith('blanc:') || 'downloads'.startsWith(clean)) {
      siteMatches.push({
        id: 'internal-downloads',
        type: 'internal',
        title: 'Blanc Downloads',
        subtitle: 'blanc://downloads',
        url: 'blanc://downloads',
        badge: 'Internal',
      });
      seenUrls.add('blanc://downloads');
    }
    if (clean.startsWith('blanc:') || 'shortcuts'.startsWith(clean)) {
      siteMatches.push({
        id: 'internal-shortcuts',
        type: 'internal',
        title: 'Blanc Shortcuts',
        subtitle: 'blanc://shortcuts',
        url: 'blanc://shortcuts',
        badge: 'Internal',
      });
      seenUrls.add('blanc://shortcuts');
    }
    if (clean.startsWith('blanc:') || 'diagnostics'.startsWith(clean)) {
      siteMatches.push({
        id: 'internal-diagnostics',
        type: 'internal',
        title: 'Blanc Diagnostics',
        subtitle: 'blanc://diagnostics',
        url: 'blanc://diagnostics',
        badge: 'Internal',
      });
      seenUrls.add('blanc://diagnostics');
    }

    // 2. Direct Website Matches from Curated Top Sites
    for (const site of TOP_SITES) {
      const titleMatch = site.title.toLowerCase().startsWith(clean);
      const domainMatch = site.domain.toLowerCase().includes(clean);
      const keywordMatch = site.keywords.some(
        (k) => k === clean || k.startsWith(clean) || (clean.length >= 3 && k.includes(clean))
      );

      if (titleMatch || domainMatch || keywordMatch) {
        if (!seenUrls.has(site.url)) {
          seenUrls.add(site.url);
          siteMatches.push({
            id: `top-${site.domain}`,
            type: 'website',
            title: site.title,
            subtitle: site.url,
            url: site.url,
            badge: 'Suggested Website',
          });
        }
      }
    }

    // 3. User Bookmarks matching query
    for (const bm of bookmarks) {
      if (
        bm.title.toLowerCase().includes(clean) ||
        bm.url.toLowerCase().includes(clean)
      ) {
        if (!seenUrls.has(bm.url)) {
          seenUrls.add(bm.url);
          siteMatches.push({
            id: `bm-${bm.id}`,
            type: 'bookmark',
            title: bm.title,
            subtitle: bm.url,
            url: bm.url,
            badge: 'Bookmark',
          });
        }
      }
    }

    // 4. User History matching query
    for (const h of history) {
      if (
        h.url.toLowerCase().includes(clean) ||
        h.title.toLowerCase().includes(clean)
      ) {
        if (!seenUrls.has(h.url) && !h.url.startsWith('blanc://newtab')) {
          seenUrls.add(h.url);
          siteMatches.push({
            id: `hist-${h.id}`,
            type: 'history',
            title: h.title || h.url,
            subtitle: h.url,
            url: h.url,
            badge: 'History',
          });
        }
      }
    }

    // 5. If query contains a valid domain structure (e.g. reddit.com or https://...)
    if (
      (clean.includes('.') && !clean.includes(' ') && clean.length > 3) ||
      clean.startsWith('http://') ||
      clean.startsWith('https://')
    ) {
      const directUrl = clean.startsWith('http') ? clean : `https://${clean}`;
      if (!seenUrls.has(directUrl)) {
        siteMatches.unshift({
          id: `direct-url-${clean}`,
          type: 'website',
          title: clean,
          subtitle: `Open ${directUrl}`,
          url: directUrl,
          badge: 'Direct URL',
        });
        seenUrls.add(directUrl);
      }
    }

    // Cap initial immediate matches
    const initialSiteMatches = siteMatches.slice(0, 3);
    setSuggestions(initialSiteMatches);
    setLoading(true);

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    // Debounce fetching live navigation suggestions & query completions
    debounceTimerRef.current = setTimeout(async () => {
      try {
        const liveNavMatches: SuggestionItem[] = [];
        let externalSuggestions: string[] = [];

        // Try local Vite proxy endpoint first
        try {
          const res = await fetch(`/api/suggest?q=${encodeURIComponent(raw)}`);
          if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data.navSites)) {
              for (const nav of data.navSites) {
                if (!seenUrls.has(nav.url)) {
                  seenUrls.add(nav.url);
                  liveNavMatches.push({
                    id: `live-nav-${nav.url}`,
                    type: 'website',
                    title: nav.title,
                    subtitle: nav.url,
                    url: nav.url,
                    badge: 'Suggested Website',
                  });
                }
              }
            }
            if (Array.isArray(data.suggestions)) {
              externalSuggestions = data.suggestions;
            }
          }
        } catch {
          // Direct fallback to Google Suggest API (works directly in native environment or fallback)
          try {
            const googleRes = await fetch(
              `https://suggestqueries.google.com/complete/search?client=chrome&q=${encodeURIComponent(raw)}`
            );
            if (googleRes.ok) {
              const data = await googleRes.json();
              const queries = Array.isArray(data[1]) ? data[1] : [];
              const descs = Array.isArray(data[2]) ? data[2] : [];
              const types = data[4]?.['google:suggesttype'] || [];

              for (let i = 0; i < queries.length; i++) {
                const item = queries[i];
                if (types[i] === 'NAVIGATION' || item.startsWith('http://') || item.startsWith('https://')) {
                  const url = item.startsWith('http') ? item : `https://${item}`;
                  if (!seenUrls.has(url)) {
                    seenUrls.add(url);
                    liveNavMatches.push({
                      id: `live-nav-${url}`,
                      type: 'website',
                      title: descs[i] || new URL(url).hostname.replace(/^www\./, ''),
                      subtitle: url,
                      url,
                      badge: 'Suggested Website',
                    });
                  }
                } else if (typeof item === 'string' && !item.startsWith('http')) {
                  externalSuggestions.push(item);
                }
              }
            }
          } catch {}
        }

        const queryMatches: SuggestionItem[] = externalSuggestions
          .filter(
            (s) =>
              s.toLowerCase() !== clean &&
              !siteMatches.some((site) => site.title.toLowerCase() === s.toLowerCase()) &&
              !liveNavMatches.some((site) => site.title.toLowerCase() === s.toLowerCase())
          )
          .slice(0, 6)
          .map((s, idx) => ({
            id: `query-${idx}-${s}`,
            type: 'query',
            title: s,
            query: s,
          }));

        // Always ensure the exact current search query is an option
        const exactQuery: SuggestionItem = {
          id: `exact-${raw}`,
          type: 'query',
          title: raw,
          query: raw,
        };

        // Combine: live suggested website navigation first, then local site matches, then query suggestions
        const combined = [
          ...liveNavMatches,
          ...siteMatches.filter((s) => !liveNavMatches.some((ln) => ln.url === s.url)),
          exactQuery,
          ...queryMatches,
        ];

        setSuggestions(combined.slice(0, 8));
      } catch (e) {
        console.warn('[useSearchSuggestions] Suggestion error:', e);
      } finally {
        setLoading(false);
      }
    }, 100);

    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [query, enabled, history, bookmarks]);

  return { suggestions, loading };
}
