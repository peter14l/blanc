import { useState, useEffect } from 'react';

export interface WebPageResult {
  content: string | null;
  loading: boolean;
  error: string | null;
  isDirect: boolean;
  directSrc: string;
  finalUrl: string;
}

/**
 * Loads external web content without being blocked by X-Frame-Options or frame-ancestors.
 * - For Google Search: Uses Google's official igu=1 parameter which strips X-Frame-Options
 *   and allows direct interactive embedding with full scripts and results.
 * - For sites like Wikipedia that allow framing: Uses direct iframe loading.
 * - For other sites with X-Frame-Options: Routes through the local /api/proxy middleware.
 */
export function useWebPageLoader(url?: string): WebPageResult {
  const [content, setContent] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isDirect, setIsDirect] = useState<boolean>(false);
  const [directSrc, setDirectSrc] = useState<string>(url || '');
  const [finalUrl, setFinalUrl] = useState<string>(url || '');

  useEffect(() => {
    if (!url || url.startsWith('blanc://') || url.startsWith('about:')) {
      setContent(null);
      setLoading(false);
      setError(null);
      setIsDirect(false);
      setDirectSrc(url || '');
      setFinalUrl(url || '');
      return;
    }

    setFinalUrl(url);

    // 1. Google with igu=1 allows direct iframe embedding
    if (/google\.[a-z.]+(\/|$)/i.test(url)) {
      let googleUrl = url;
      if (!googleUrl.includes('igu=1')) {
        googleUrl += (googleUrl.includes('?') ? '&' : '?') + 'igu=1';
      }
      setIsDirect(true);
      setDirectSrc(googleUrl);
      setContent(null);
      setLoading(false);
      setError(null);
      return;
    }

    // 2. Google with igu=1 allows direct iframe embedding
    if (/google\.[a-z.]+(\/|$)/i.test(url)) {
      let googleUrl = url;
      if (!googleUrl.includes('igu=1')) {
        googleUrl += (googleUrl.includes('?') ? '&' : '?') + 'igu=1';
      }
      setIsDirect(true);
      setDirectSrc(googleUrl);
      setContent(null);
      setLoading(false);
      setError(null);
      return;
    }

    // 3. YouTube Special Handling:
    // YouTube blocks direct site framing (X-Frame-Options: SAMEORIGIN & TrustedTypes CORS),
    // but official embeds (youtube-nocookie.com/embed) are 100% frameable and playable.
    if (/youtube\.com|youtu\.be/i.test(url)) {
      let videoId = '';
      let searchQuery = '';

      if (url.includes('watch?v=')) {
        videoId = url.split('watch?v=')[1]?.split('&')[0]?.split('#')[0] || '';
      } else if (url.includes('youtu.be/')) {
        videoId = url.split('youtu.be/')[1]?.split('?')[0]?.split('#')[0] || '';
      } else if (url.includes('/shorts/')) {
        videoId = url.split('/shorts/')[1]?.split('?')[0]?.split('#')[0] || '';
      } else if (url.includes('/embed/')) {
        videoId = url.split('/embed/')[1]?.split('?')[0]?.split('#')[0] || '';
      } else if (url.includes('search_query=')) {
        try {
          searchQuery = new URL(url).searchParams.get('search_query') || '';
        } catch {
          const match = url.match(/search_query=([^&]+)/);
          if (match && match[1]) searchQuery = decodeURIComponent(match[1]);
        }
      }

      // Single Video -> Direct Playable Embed
      if (videoId) {
        setIsDirect(true);
        setDirectSrc(`https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&rel=0`);
        setContent(null);
        setLoading(false);
        setError(null);
        return;
      }

      // Search Query -> Fetch real video results and render interactive YouTube Search Results Page
      if (searchQuery) {
        let isCancelled = false;
        setIsDirect(false);
        setLoading(true);
        setError(null);

        fetch(`/api/yt-search?q=${encodeURIComponent(searchQuery)}`)
          .then((r) => r.json())
          .then((data) => {
            if (isCancelled) return;
            setContent(renderYouTubeSearchResults(searchQuery, data.results || []));
            setLoading(false);
          })
          .catch((err) => {
            if (isCancelled) return;
            console.warn('[useWebPageLoader] yt-search error, using fallback:', err);
            setContent(renderYouTubeSearchResults(searchQuery, []));
            setLoading(false);
          });

        return () => {
          isCancelled = true;
        };
      }

      // YouTube Home or Channel -> Interactive Blanc YouTube Hub
      setContent(renderYouTubeHub(url));
      setIsDirect(false);
      setLoading(false);
      setError(null);
      return;
    }

    // 4. Direct framing allowed by the site
    const allowsDirect = /wikipedia\.org|archive\.org|w3schools\.com|example\.com/i.test(url);
    if (allowsDirect) {
      setIsDirect(true);
      setDirectSrc(url);
      setContent(null);
      setLoading(false);
      setError(null);
      return;
    }

    // 5. All other sites (including search engines) -> Try proxy chain
    // The proxy (Tauri in prod, Vite in dev) handles iframe-friendly rewrites

    // 3. YouTube Special Handling:
    // YouTube blocks direct site framing (X-Frame-Options: SAMEORIGIN & TrustedTypes CORS),
    // but official embeds (youtube-nocookie.com/embed) are 100% frameable and playable.
    if (/youtube\.com|youtu\.be/i.test(url)) {
      let videoId = '';
      let searchQuery = '';

      if (url.includes('watch?v=')) {
        videoId = url.split('watch?v=')[1]?.split('&')[0]?.split('#')[0] || '';
      } else if (url.includes('youtu.be/')) {
        videoId = url.split('youtu.be/')[1]?.split('?')[0]?.split('#')[0] || '';
      } else if (url.includes('/shorts/')) {
        videoId = url.split('/shorts/')[1]?.split('?')[0]?.split('#')[0] || '';
      } else if (url.includes('/embed/')) {
        videoId = url.split('/embed/')[1]?.split('?')[0]?.split('#')[0] || '';
      } else if (url.includes('search_query=')) {
        try {
          searchQuery = new URL(url).searchParams.get('search_query') || '';
        } catch {
          const match = url.match(/search_query=([^&]+)/);
          if (match && match[1]) searchQuery = decodeURIComponent(match[1]);
        }
      }

      // Single Video -> Direct Playable Embed
      if (videoId) {
        setIsDirect(true);
        setDirectSrc(`https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&rel=0`);
        setContent(null);
        setLoading(false);
        setError(null);
        return;
      }

      // Search Query -> Fetch real video results and render interactive YouTube Search Results Page
      if (searchQuery) {
        let isCancelled = false;
        setIsDirect(false);
        setLoading(true);
        setError(null);

        fetch(`/api/yt-search?q=${encodeURIComponent(searchQuery)}`)
          .then((r) => r.json())
          .then((data) => {
            if (isCancelled) return;
            setContent(renderYouTubeSearchResults(searchQuery, data.results || []));
            setLoading(false);
          })
          .catch((err) => {
            if (isCancelled) return;
            console.warn('[useWebPageLoader] yt-search error, using fallback:', err);
            setContent(renderYouTubeSearchResults(searchQuery, []));
            setLoading(false);
          });

        return () => {
          isCancelled = true;
        };
      }

      // YouTube Home or Channel -> Interactive Blanc YouTube Hub
      setContent(renderYouTubeHub(url));
      setIsDirect(false);
      setLoading(false);
      setError(null);
      return;
    }

    let isCancelled = false;
    setLoading(true);
    setError(null);
    setIsDirect(false);

    // 3. Try Tauri proxy command first (works in production builds)
    // Then fall back to local /api/proxy endpoint (Vite dev server)
    const tryTauriProxy = async (): Promise<string | null> => {
      if (typeof window !== 'undefined' && ('__TAURI_INTERNALS__' in window || '__TAURI__' in window)) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          const html = await invoke<string>('proxy_fetch', { url });
          return html;
        } catch (e) {
          console.warn('[useWebPageLoader] Tauri proxy_fetch failed:', e);
        }
      }
      return null;
    };

    const tryLocalProxy = async (): Promise<string | null> => {
      const localProxyUrl = `/api/proxy?url=${encodeURIComponent(url)}`;
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 10000);
      try {
        const res = await fetch(localProxyUrl, { signal: controller.signal });
        clearTimeout(timeoutId);
        if (!res.ok) throw new Error(`HTTP error ${res.status}`);
        return await res.text();
      } catch (err) {
        clearTimeout(timeoutId);
        throw err;
      }
    };

    const loadViaProxy = async () => {
      // Try Tauri first (production), then local proxy (dev)
      let html = await tryTauriProxy();
      if (!html) {
        try {
          html = await tryLocalProxy();
        } catch (err) {
          console.warn('[useWebPageLoader] Local proxy fetch failed:', err);
        }
      }

      if (isCancelled) return;

      if (html) {
        setContent(html);
        setLoading(false);
      } else {
        console.warn('[useWebPageLoader] All proxy methods failed, falling back to direct');
        setIsDirect(true);
        setDirectSrc(url);
        setContent(null);
        setLoading(false);
      }
    };

    loadViaProxy();

    return () => {
      isCancelled = true;
    };
  }, [url]);

  return { content, loading, error, isDirect, directSrc, finalUrl };
}

function renderYouTubeHub(targetUrl: string): string {
  return `<!DOCTYPE html>
<html lang="en" class="dark">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>YouTube — Blanc</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: #0f0f0f;
      color: #f1f1f1;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      padding: 24px;
      overflow-y: auto;
      min-height: 100vh;
    }
    .container {
      max-width: 1100px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 16px;
      padding-bottom: 16px;
      border-bottom: 1px solid rgba(255,255,255,0.08);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 20px;
      font-weight: 700;
      letter-spacing: -0.5px;
    }
    .yt-icon {
      width: 38px;
      height: 26px;
      background: #ff0000;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 14px rgba(255,0,0,0.35);
    }
    .yt-icon svg {
      width: 14px;
      height: 14px;
      fill: #ffffff;
      margin-left: 2px;
    }
    .search-box {
      flex: 1;
      max-width: 520px;
      min-width: 260px;
      position: relative;
    }
    .search-form {
      display: flex;
      background: #181818;
      border: 1px solid rgba(255,255,255,0.15);
      border-radius: 999px;
      overflow: hidden;
      transition: all 0.2s;
    }
    .search-form:focus-within {
      border-color: #d4ad66;
      box-shadow: 0 0 0 2px rgba(212,173,102,0.25);
    }
    .search-input {
      flex: 1;
      background: transparent;
      border: none;
      outline: none;
      padding: 10px 18px;
      color: #fff;
      font-size: 14px;
    }
    .search-btn {
      background: rgba(255,255,255,0.05);
      border: none;
      border-left: 1px solid rgba(255,255,255,0.1);
      padding: 0 18px;
      cursor: pointer;
      color: rgba(255,255,255,0.7);
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s;
    }
    .search-btn:hover {
      background: rgba(255,255,255,0.12);
      color: #fff;
    }
    .external-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      border-radius: 999px;
      background: rgba(255,255,255,0.08);
      color: #fff;
      text-decoration: none;
      font-size: 13px;
      font-weight: 500;
      border: 1px solid rgba(255,255,255,0.1);
      transition: background 0.15s;
      cursor: pointer;
    }
    .external-btn:hover {
      background: rgba(255,255,255,0.16);
    }
    .featured {
      position: relative;
      width: 100%;
      aspect-ratio: 16 / 9;
      max-height: 480px;
      border-radius: 18px;
      overflow: hidden;
      box-shadow: 0 16px 40px rgba(0,0,0,0.6);
      border: 1px solid rgba(255,255,255,0.1);
      background: #000;
    }
    .featured iframe {
      width: 100%;
      height: 100%;
      border: none;
    }
    .section-title {
      font-size: 16px;
      font-weight: 600;
      margin-top: 8px;
      margin-bottom: 4px;
      color: rgba(255,255,255,0.9);
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .chips {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding-bottom: 6px;
    }
    .chip {
      padding: 6px 14px;
      border-radius: 999px;
      background: rgba(255,255,255,0.06);
      border: 1px solid rgba(255,255,255,0.08);
      color: rgba(255,255,255,0.8);
      font-size: 12px;
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.15s;
    }
    .chip:hover {
      background: #d4ad66;
      color: #000;
      border-color: #d4ad66;
      font-weight: 600;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      gap: 18px;
    }
    .card {
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 14px;
      overflow: hidden;
      cursor: pointer;
      transition: transform 0.2s, background 0.2s, border-color 0.2s;
      display: flex;
      flex-direction: column;
    }
    .card:hover {
      transform: translateY(-3px);
      background: rgba(255,255,255,0.07);
      border-color: rgba(212,173,102,0.4);
    }
    .thumb {
      width: 100%;
      aspect-ratio: 16 / 9;
      background: #1c1c1c;
      position: relative;
      overflow: hidden;
    }
    .thumb img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .card-info {
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      flex: 1;
    }
    .card-title {
      font-size: 13px;
      font-weight: 600;
      line-height: 1.35;
      color: #fff;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .card-channel {
      font-size: 11px;
      color: rgba(255,255,255,0.5);
      margin-top: auto;
    }
    .play-badge {
      position: absolute;
      bottom: 8px;
      right: 8px;
      padding: 2px 6px;
      background: rgba(0,0,0,0.8);
      border-radius: 4px;
      font-size: 10px;
      font-weight: 600;
      color: #fff;
    }
  </style>
</head>
<body>
  <div class="container">
    <header class="header">
      <div class="brand">
        <div class="yt-icon">
          <svg viewBox="0 0 24 24"><polygon points="6 4 20 12 6 20 6 4"/></svg>
        </div>
        <span>YouTube</span>
      </div>

      <div class="search-box">
        <form class="search-form" id="searchForm">
          <input type="text" class="search-input" id="searchInput" placeholder="Search YouTube or paste video URL..." autocomplete="off">
          <button type="submit" class="search-btn" title="Search">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          </button>
        </form>
      </div>

      <button type="button" class="external-btn" id="openExternalBtn">
        <span>Open in App / Tab</span>
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
      </button>
    </header>

    <div class="chips">
      <button class="chip" onclick="searchTopic('trending')">🔥 Trending</button>
      <button class="chip" onclick="searchTopic('lofi hip hop')">☕ Lo-Fi Beats</button>
      <button class="chip" onclick="searchTopic('programming coding')">💻 Code & Tech</button>
      <button class="chip" onclick="searchTopic('ambient synthwave')">🌌 Synthwave</button>
      <button class="chip" onclick="searchTopic('documentary nature')">🌿 Documentaries</button>
      <button class="chip" onclick="searchTopic('spacex launches')">🚀 Space</button>
    </div>

    <!-- Featured Livestream / Video Player -->
    <div class="featured">
      <iframe
        src="https://www.youtube-nocookie.com/embed/jfKfPfyJRdk?autoplay=1&mute=1&rel=0"
        title="Featured Video"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
        allowfullscreen>
      </iframe>
    </div>

    <div class="section-title">
      <span>Recommended Videos</span>
    </div>

    <div class="grid">
      <div class="card" onclick="playVideo('jfKfPfyJRdk')">
        <div class="thumb">
          <img src="https://img.youtube.com/vi/jfKfPfyJRdk/mqdefault.jpg" alt="" loading="lazy">
          <span class="play-badge">LIVE</span>
        </div>
        <div class="card-info">
          <div class="card-title">lofi hip hop radio - beats to relax/study to</div>
          <div class="card-channel">Lofi Girl</div>
        </div>
      </div>

      <div class="card" onclick="playVideo('l6n5h1GZZWk')">
        <div class="thumb">
          <img src="https://img.youtube.com/vi/l6n5h1GZZWk/mqdefault.jpg" alt="" loading="lazy">
          <span class="play-badge">10:48</span>
        </div>
        <div class="card-info">
          <div class="card-title">What If Earth Stopped Spinning for 5 Seconds?</div>
          <div class="card-channel">Kurzgesagt – In a Nutshell</div>
        </div>
      </div>

      <div class="card" onclick="playVideo('sOpMrVnjYeY')">
        <div class="thumb">
          <img src="https://img.youtube.com/vi/sOpMrVnjYeY/mqdefault.jpg" alt="" loading="lazy">
          <span class="play-badge">14:22</span>
        </div>
        <div class="card-info">
          <div class="card-title">Starship Flight 5: The Tower Catch Breakdown</div>
          <div class="card-channel">SpaceX & Rocketry</div>
        </div>
      </div>

      <div class="card" onclick="playVideo('fJ9rUzIMcZQ')">
        <div class="thumb">
          <img src="https://img.youtube.com/vi/fJ9rUzIMcZQ/mqdefault.jpg" alt="" loading="lazy">
          <span class="play-badge">12:05</span>
        </div>
        <div class="card-info">
          <div class="card-title">How Web Browsers Actually Work Under the Hood</div>
          <div class="card-channel">Computerphile</div>
        </div>
      </div>

      <div class="card" onclick="playVideo('iHzzSao6ypE')">
        <div class="thumb">
          <img src="https://img.youtube.com/vi/iHzzSao6ypE/mqdefault.jpg" alt="" loading="lazy">
          <span class="play-badge">05:14</span>
        </div>
        <div class="card-info">
          <div class="card-title">The Simple Solution to Traffic</div>
          <div class="card-channel">CGP Grey</div>
        </div>
      </div>

      <div class="card" onclick="playVideo('dQw4w9WgXcQ')">
        <div class="thumb">
          <img src="https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg" alt="" loading="lazy">
          <span class="play-badge">03:33</span>
        </div>
        <div class="card-info">
          <div class="card-title">Never Gonna Give You Up (Official Music Video)</div>
          <div class="card-channel">Rick Astley</div>
        </div>
      </div>
    </div>
  </div>

  <script>
    function playVideo(id) {
      window.parent.postMessage({ type: 'BLANC_NAV', url: 'https://www.youtube.com/watch?v=' + id }, '*');
    }

    function searchTopic(query) {
      window.parent.postMessage({ type: 'BLANC_NAV', url: 'https://www.youtube.com/results?search_query=' + encodeURIComponent(query) }, '*');
    }

    document.getElementById('searchForm').addEventListener('submit', function(e) {
      e.preventDefault();
      var q = document.getElementById('searchInput').value.trim();
      if (!q) return;

      if (q.includes('youtube.com/watch') || q.includes('youtu.be/')) {
        window.parent.postMessage({ type: 'BLANC_NAV', url: q }, '*');
      } else {
        searchTopic(q);
      }
    });

    document.getElementById('openExternalBtn').addEventListener('click', function() {
      window.open('${targetUrl}', '_blank');
    });
  </script>
</body>
</html>`;
}

interface YouTubeSearchItem {
  id: string;
  title: string;
  channel: string;
  views: string;
  time: string;
  published?: string;
  avatar?: string;
  desc?: string;
  badges?: string[];
}

function escapeHtml(value: string): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Renders results the way youtube.com does: one video per row, wide thumbnail on the left. */
function renderYouTubeSearchResults(query: string, results: YouTubeSearchItem[]): string {
  const q = escapeHtml(query);
  const rows = results
    .map((item) => {
      const meta = [item.views ? `${item.views} views` : '', item.published || '']
        .filter(Boolean)
        .join(' • ');
      const badges = (item.badges || [])
        .map((b) => `<span class="badge">${escapeHtml(b)}</span>`)
        .join('');
      const initial = escapeHtml((item.channel || 'Y').charAt(0).toUpperCase());
      const avatar = item.avatar
        ? `<img class="avatar" src="${escapeHtml(item.avatar)}" alt="" referrerpolicy="no-referrer">`
        : `<span class="avatar avatar-fallback">${initial}</span>`;
      return `
      <a class="row" data-id="${escapeHtml(item.id)}" href="#">
        <div class="thumb">
          <img src="https://i.ytimg.com/vi/${escapeHtml(item.id)}/hqdefault.jpg" alt="" loading="lazy" referrerpolicy="no-referrer">
          ${item.time ? `<span class="duration">${escapeHtml(item.time)}</span>` : ''}
        </div>
        <div class="info">
          <h3 class="title">${escapeHtml(item.title)}</h3>
          <div class="meta">${escapeHtml(meta)}</div>
          <div class="channel">${avatar}<span>${escapeHtml(item.channel)}</span></div>
          ${item.desc ? `<p class="desc">${escapeHtml(item.desc)}</p>` : ''}
          ${badges ? `<div class="badges">${badges}</div>` : ''}
        </div>
      </a>`;
    })
    .join('');

  const body = results.length
    ? `<div class="list">${rows}</div>`
    : `<div class="empty">No results found for &ldquo;${q}&rdquo;.</div>`;

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${q} - YouTube</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #0f0f0f; color: #f1f1f1; font-family: Roboto, Arial, -apple-system, "Segoe UI", sans-serif; min-height: 100vh; }
    .topbar { position: sticky; top: 0; z-index: 5; display: flex; align-items: center; gap: 16px; padding: 10px 24px; background: #0f0f0f; border-bottom: 1px solid rgba(255,255,255,.08); }
    .brand { display: flex; align-items: center; gap: 6px; font-size: 18px; font-weight: 700; letter-spacing: -.5px; cursor: pointer; flex-shrink: 0; }
    .logo { width: 30px; height: 21px; background: #f00; border-radius: 6px; display: flex; align-items: center; justify-content: center; }
    .logo svg { width: 10px; height: 10px; fill: #fff; margin-left: 1px; }
    form { flex: 1; max-width: 640px; margin: 0 auto; display: flex; }
    input { flex: 1; min-width: 0; background: #121212; border: 1px solid #303030; border-right: none; border-radius: 40px 0 0 40px; padding: 0 16px; height: 40px; color: #fff; font-size: 15px; outline: none; }
    input:focus { border-color: #3ea6ff; }
    button.go { width: 64px; height: 40px; background: #222; border: 1px solid #303030; border-radius: 0 40px 40px 0; color: #fff; cursor: pointer; }
    button.go:hover { background: #2c2c2c; }
    .list { max-width: 1096px; margin: 0 auto; padding: 20px 24px 48px; display: flex; flex-direction: column; gap: 16px; }
    .row { display: flex; gap: 16px; color: inherit; text-decoration: none; border-radius: 12px; }
    .thumb { position: relative; flex: 0 0 min(500px, 42%); aspect-ratio: 16 / 9; background: #272727; border-radius: 12px; overflow: hidden; }
    .thumb img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform .2s; }
    .row:hover .thumb img { transform: scale(1.02); }
    .duration { position: absolute; right: 6px; bottom: 6px; background: rgba(0,0,0,.8); color: #fff; font-size: 12px; font-weight: 500; padding: 1px 4px; border-radius: 4px; }
    .info { flex: 1; min-width: 0; padding-top: 2px; }
    .title { font-size: 18px; font-weight: 400; line-height: 26px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .meta { font-size: 12px; color: #aaa; margin-top: 4px; }
    .channel { display: flex; align-items: center; gap: 8px; margin: 12px 0; font-size: 12px; color: #aaa; }
    .avatar { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; background: #3a3a3a; flex-shrink: 0; }
    .avatar-fallback { display: inline-flex; align-items: center; justify-content: center; color: #fff; font-size: 11px; font-weight: 600; }
    .desc { font-size: 12px; line-height: 18px; color: #aaa; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .badges { display: flex; gap: 4px; margin-top: 8px; flex-wrap: wrap; }
    .badge { background: rgba(255,255,255,.1); color: #aaa; font-size: 10px; font-weight: 500; padding: 2px 4px; border-radius: 2px; }
    .empty { text-align: center; padding: 80px 20px; color: #aaa; }
    @media (max-width: 640px) {
      .topbar { padding: 8px 12px; }
      .row { flex-direction: column; gap: 10px; }
      .thumb { flex: none; width: 100%; border-radius: 0; }
      .list { padding: 0 0 32px; }
      .info { padding: 0 12px; }
    }
  </style>
</head>
<body>
  <div class="topbar">
    <div class="brand" id="home"><span class="logo"><svg viewBox="0 0 24 24"><polygon points="6 4 20 12 6 20"/></svg></span><span>YouTube</span></div>
    <form id="searchForm">
      <input id="searchInput" type="text" value="${q}" placeholder="Search" autocomplete="off">
      <button class="go" type="submit" aria-label="Search">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
      </button>
    </form>
  </div>
  ${body}
  <script>
    function nav(url) { window.parent.postMessage({ type: 'BLANC_NAV', url: url }, '*'); }
    document.getElementById('home').addEventListener('click', function () { nav('https://www.youtube.com'); });
    document.getElementById('searchForm').addEventListener('submit', function (e) {
      e.preventDefault();
      var v = document.getElementById('searchInput').value.trim();
      if (v) nav('https://www.youtube.com/results?search_query=' + encodeURIComponent(v));
    });
    document.querySelectorAll('.row').forEach(function (row) {
      row.addEventListener('click', function (e) {
        e.preventDefault();
        nav('https://www.youtube.com/watch?v=' + row.getAttribute('data-id'));
      });
    });
  </script>
</body>
</html>`;
}
