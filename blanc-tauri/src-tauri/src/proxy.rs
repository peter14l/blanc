use reqwest::header::{HeaderMap, HeaderValue, USER_AGENT, ACCEPT, ACCEPT_LANGUAGE};
use tauri::command;

/// Pure function for URL transformation logic - testable without network calls
pub fn transform_proxy_url(url: &str) -> String {
    let mut fetch_url = url.to_string();

    // For Google searches or Google home, ensure igu=1 is set to enable official iframe embedding
    if fetch_url.contains("google.") && !fetch_url.contains("igu=1") {
        fetch_url += if fetch_url.contains('?') { "&" } else { "?" };
        fetch_url += "igu=1";
    } else if fetch_url.contains("duckduckgo.com/?q=") {
        fetch_url = fetch_url.replace("duckduckgo.com/?q=", "html.duckduckgo.com/html/?q=");
    }

    fetch_url
}

/// Inject base tag and click/form interception script into HTML
pub fn inject_base_and_scripts(html: &str, final_url: &str) -> String {
    let base_tag = format!(
        r#"<base href="{}"><script>
            document.addEventListener('click', function(e) {{
              var a = e.target.closest('a');
              if (a && a.href && !a.href.startsWith('javascript:') && !a.href.startsWith('#')) {{
                e.preventDefault();
                var destUrl = a.href;
                // If DuckDuckGo redirect link, extract real destination URL
                if (destUrl.indexOf('uddg=') !== -1) {{
                  try {{
                    var match = destUrl.match(/uddg=([^&]+)/);
                    if (match && match[1]) {{
                      destUrl = decodeURIComponent(match[1]);
                    }}
                  }} catch (err) {{}}
                }}
                window.parent.postMessage({{ type: 'BLANC_NAV', url: destUrl }}, '*');
              }}
            }}, true);
            document.addEventListener('submit', function(e) {{
              var form = e.target;
              if (form && form.action) {{
                e.preventDefault();
                var fd = new FormData(form);
                var qs = new URLSearchParams(fd as any).toString();
                var dest = form.action + (form.action.indexOf('?') >= 0 ? '&' : '?') + qs;
                window.parent.postMessage({{ type: 'BLANC_NAV', url: dest }}, '*');
              }}
            }}, true);
          </script>"#,
        final_url
    );

    if html.contains("<head>") {
        html.replace("<head>", &format!("<head>{}", base_tag))
    } else if html.contains("<html>") {
        html.replace("<html>", &format!("<html><head>{}</head>", base_tag))
    } else {
        format!("<html><head>{}</head><body>{}</body></html>", base_tag, html)
    }
}

#[command]
pub async fn proxy_fetch(url: String) -> Result<String, String> {
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(10))
        .redirect(reqwest::redirect::Policy::limited(10))
        .build()
        .map_err(|e| format!("Failed to build HTTP client: {}", e))?;

    let mut headers = HeaderMap::new();
    headers.insert(
        USER_AGENT,
        HeaderValue::from_static(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        ),
    );
    headers.insert(
        ACCEPT,
        HeaderValue::from_static("text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"),
    );
    headers.insert(
        ACCEPT_LANGUAGE,
        HeaderValue::from_static("en-US,en;q=0.9"),
    );

    let fetch_url = transform_proxy_url(&url);

    let response = client
        .get(&fetch_url)
        .headers(headers)
        .send()
        .await
        .map_err(|e| format!("HTTP request failed: {}", e))?;

    let final_url = response.url().to_string();
    let html = response.text().await.map_err(|e| format!("Failed to read response body: {}", e))?;

    let modified = inject_base_and_scripts(&html, &final_url);

    Ok(modified)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_transform_proxy_url_google_adds_igu() {
        let url = "https://www.google.com/search?q=test";
        let transformed = transform_proxy_url(url);
        assert!(transformed.contains("igu=1"));
    }

    #[test]
    fn test_transform_proxy_url_google_already_has_igu() {
        let url = "https://www.google.com/search?q=test&igu=1";
        let transformed = transform_proxy_url(url);
        // Should not add duplicate igu=1
        assert_eq!(transformed.matches("igu=1").count(), 1);
    }

    #[test]
    fn test_transform_proxy_url_duckduckgo_to_html() {
        let url = "https://duckduckgo.com/?q=test";
        let transformed = transform_proxy_url(url);
        assert!(transformed.contains("html.duckduckgo.com/html/?q=test"));
        assert!(!transformed.contains("duckduckgo.com/?q="));
    }

    #[test]
    fn test_transform_proxy_url_other_unchanged() {
        let url = "https://example.com/path";
        let transformed = transform_proxy_url(url);
        assert_eq!(transformed, url);
    }

    #[test]
    fn test_inject_base_and_scripts_with_head() {
        let html = r#"<html><head><title>Test</title></head><body>Content</body></html>"#;
        let result = inject_base_and_scripts(html, "https://example.com");
        assert!(result.contains(r#"<base href="https://example.com">"#));
        assert!(result.contains("window.parent.postMessage"));
        assert!(result.contains("<head>"));
    }

    #[test]
    fn test_inject_base_and_scripts_without_head() {
        let html = r#"<html><body>Content</body></html>"#;
        let result = inject_base_and_scripts(html, "https://example.com");
        assert!(result.contains(r#"<base href="https://example.com">"#));
        assert!(result.contains("<head>"));
    }

    #[test]
    fn test_inject_base_and_scripts_no_html_tag() {
        let html = r#"Content only"#;
        let result = inject_base_and_scripts(html, "https://example.com");
        assert!(result.contains(r#"<base href="https://example.com">"#));
        assert!(result.contains("<html>"));
        assert!(result.contains("<body>"));
    }
}