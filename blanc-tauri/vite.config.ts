import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

function webProxyPlugin() {
  return {
    name: 'web-proxy-plugin',
    configureServer(server: any) {
      server.middlewares.use('/api/proxy', async (req: any, res: any) => {
        try {
          const urlObj = new URL(req.url, 'http://localhost');
          const targetUrl = urlObj.searchParams.get('url');

          if (!targetUrl) {
            res.statusCode = 400;
            res.end('Missing url parameter');
            return;
          }

          let fetchUrl = targetUrl;
          // For Google searches or Google home, ensure igu=1 is set to enable official iframe embedding
          if (fetchUrl.includes('google.') && !fetchUrl.includes('igu=1')) {
            fetchUrl += (fetchUrl.includes('?') ? '&' : '?') + 'igu=1';
          } else if (fetchUrl.includes('duckduckgo.com/?q=')) {
            fetchUrl = fetchUrl.replace('duckduckgo.com/?q=', 'html.duckduckgo.com/html/?q=');
          }

          const response = await fetch(fetchUrl, {
            headers: {
              'User-Agent':
                'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
              Accept:
                'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
              'Accept-Language': 'en-US,en;q=0.9',
            },
            redirect: 'follow',
          });

          const finalUrl = response.url || fetchUrl;
          const html = await response.text();

          const baseTag = `<base href="${finalUrl}"><script>
            document.addEventListener('click', function(e) {
              var a = e.target.closest('a');
              if (a && a.href && !a.href.startsWith('javascript:') && !a.href.startsWith('#')) {
                e.preventDefault();
                var destUrl = a.href;
                // If DuckDuckGo redirect link, extract real destination URL
                if (destUrl.indexOf('uddg=') !== -1) {
                  try {
                    var match = destUrl.match(/uddg=([^&]+)/);
                    if (match && match[1]) {
                      destUrl = decodeURIComponent(match[1]);
                    }
                  } catch (err) {}
                }
                window.parent.postMessage({ type: 'BLANC_NAV', url: destUrl }, '*');
              }
            }, true);
            document.addEventListener('submit', function(e) {
              var form = e.target;
              if (form && form.action) {
                e.preventDefault();
                var fd = new FormData(form);
                var qs = new URLSearchParams(fd as any).toString();
                var dest = form.action + (form.action.indexOf('?') >= 0 ? '&' : '?') + qs;
                window.parent.postMessage({ type: 'BLANC_NAV', url: dest }, '*');
              }
            }, true);
          </script>`;

          let modified = html;
          if (html.includes('<head>')) {
            modified = html.replace('<head>', `<head>${baseTag}`);
          } else if (html.includes('<html>')) {
            modified = html.replace('<html>', `<html><head>${baseTag}</head>`);
          } else {
            modified = `<html><head>${baseTag}</head><body>${html}</body></html>`;
          }

          res.setHeader('Content-Type', 'text/html; charset=utf-8');
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.statusCode = 200;
          res.end(modified);
        } catch (err: any) {
          res.statusCode = 500;
          res.setHeader('Content-Type', 'text/plain');
          res.end(`Proxy error: ${err?.message || err}`);
        }
      });

      server.middlewares.use('/api/yt-search', async (req: any, res: any) => {
        try {
          const urlObj = new URL(req.url, 'http://localhost');
          const q = urlObj.searchParams.get('q') || '';
          if (!q) {
            res.statusCode = 400;
            res.end(JSON.stringify({ error: 'Missing q parameter', results: [] }));
            return;
          }

          const response = await fetch(
            `https://www.youtube.com/results?search_query=${encodeURIComponent(q)}`,
            {
              headers: {
                'User-Agent':
                  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
                Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.9',
              },
            }
          );

          const html = await response.text();
          const match = html.match(/ytInitialData\s*=\s*({.+?});<\/script>/);
          let results: any[] = [];
          if (match) {
            try {
              const data = JSON.parse(match[1]);
              const items =
                data.contents?.twoColumnSearchResultsRenderer?.primaryContents?.sectionListRenderer
                  ?.contents?.[0]?.itemSectionRenderer?.contents || [];
              results = items
                .filter((i: any) => i.videoRenderer && i.videoRenderer.videoId)
                .map((i: any) => ({
                  id: i.videoRenderer.videoId,
                  title:
                    i.videoRenderer.title?.runs?.[0]?.text ||
                    i.videoRenderer.title?.simpleText ||
                    'YouTube Video',
                  channel:
                    i.videoRenderer.ownerText?.runs?.[0]?.text ||
                    i.videoRenderer.shortBylineText?.runs?.[0]?.text ||
                    '',
                  views: i.videoRenderer.shortViewCountText?.simpleText || '',
                  time: i.videoRenderer.lengthText?.simpleText || '',
                  published: i.videoRenderer.publishedTimeText?.simpleText || '',
                  avatar:
                    i.videoRenderer.channelThumbnailSupportedRenderers
                      ?.channelThumbnailWithLinkRenderer?.thumbnail?.thumbnails?.[0]?.url || '',
                  desc:
                    i.videoRenderer.detailedMetadataSnippets?.[0]?.snippetText?.runs
                      ?.map((r: any) => r.text)
                      .join('') ||
                    i.videoRenderer.descriptionSnippet?.runs?.map((r: any) => r.text).join('') ||
                    '',
                  badges: (i.videoRenderer.badges || [])
                    .map((b: any) => b.metadataBadgeRenderer?.label)
                    .filter(Boolean),
                }))
                .slice(0, 20);
            } catch (parseErr) {
              console.warn('[yt-search] JSON parse error:', parseErr);
            }
          }

          res.setHeader('Content-Type', 'application/json; charset=utf-8');
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.statusCode = 200;
          res.end(JSON.stringify({ query: q, results }));
        } catch (err: any) {
          res.statusCode = 500;
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify({ error: err.message, results: [] }));
        }
      });

      server.middlewares.use('/api/suggest', async (req: any, res: any) => {
        try {
          const urlObj = new URL(req.url, 'http://localhost');
          const q = urlObj.searchParams.get('q') || '';
          if (!q) {
            res.setHeader('Content-Type', 'application/json; charset=utf-8');
            res.setHeader('Access-Control-Allow-Origin', '*');
            res.end(JSON.stringify({ query: '', navSites: [], suggestions: [] }));
            return;
          }

          const navSites: Array<{ url: string; title: string }> = [];
          const suggestions: string[] = [];

          try {
            const googleRes = await fetch(
              `https://suggestqueries.google.com/complete/search?client=chrome&q=${encodeURIComponent(q)}`,
              {
                headers: {
                  'User-Agent':
                    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
                  Accept: 'application/json',
                },
              }
            );
            if (googleRes.ok) {
              const data = await googleRes.json();
              const queries = Array.isArray(data[1]) ? data[1] : [];
              const descs = Array.isArray(data[2]) ? data[2] : [];
              const types =
                data[4] && Array.isArray(data[4]['google:suggesttype'])
                  ? data[4]['google:suggesttype']
                  : [];

              for (let i = 0; i < queries.length; i++) {
                const item = queries[i];
                const isNav =
                  types[i] === 'NAVIGATION' ||
                  item.startsWith('http://') ||
                  item.startsWith('https://');

                if (isNav) {
                  let cleanUrl = item;
                  if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
                    cleanUrl = `https://${cleanUrl}`;
                  }
                  let domain = '';
                  try {
                    domain = new URL(cleanUrl).hostname.replace(/^www\./, '');
                  } catch {
                    domain = cleanUrl;
                  }
                  let title = descs[i] || domain;
                  if (!title || title === domain) {
                    const name = domain.split('.')[0];
                    title = name.charAt(0).toUpperCase() + name.slice(1);
                  }
                  navSites.push({ url: cleanUrl, title });
                } else if (typeof item === 'string' && !item.startsWith('http')) {
                  suggestions.push(item);
                }
              }
            }
          } catch (e) {
            console.warn('[suggest] Google suggest error:', e);
          }

          // If no suggestions from Google, fallback to DuckDuckGo
          if (suggestions.length === 0) {
            try {
              const ddgRes = await fetch(
                `https://duckduckgo.com/ac/?q=${encodeURIComponent(q)}&type=list`,
                {
                  headers: {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
                    Accept: 'application/json',
                  },
                }
              );
              if (ddgRes.ok) {
                const ddgData = await ddgRes.json();
                if (Array.isArray(ddgData) && Array.isArray(ddgData[1])) {
                  suggestions.push(...ddgData[1]);
                }
              }
            } catch {}
          }

          res.setHeader('Content-Type', 'application/json; charset=utf-8');
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.statusCode = 200;
          res.end(JSON.stringify({ query: q, navSites, suggestions }));
        } catch (err: any) {
          res.statusCode = 200;
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify({ query: '', navSites: [], suggestions: [] }));
        }
      });
    },
  };
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), webProxyPlugin()],
  clearScreen: false,
  server: {
    port: 5173,
    strictPort: true,
    host: '0.0.0.0',
  },
  envPrefix: ['VITE_', 'TAURI_'],
  build: {
    target: ['es2021', 'chrome100', 'safari13'],
    minify: !process.env.TAURI_DEBUG ? 'esbuild' : false,
    sourcemap: !!process.env.TAURI_DEBUG,
  },
});
