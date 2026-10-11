//! Parses the universal Netscape Bookmark HTML format (DOCTYPE NETSCAPE-Bookmark-file-1).
//!
//! Exported by Firefox, Chrome, Safari, Edge, and every other browser via
//! Settings -> Bookmarks -> Export.

use crate::storage::Bookmark;

const MAX_BOOKMARKS: usize = 100_000;

/// Parse a Netscape Bookmark HTML string and return flat `Bookmark` records.
pub fn parse_html_bookmarks(html: &str) -> Vec<Bookmark> {
    let mut out = Vec::new();
    let mut folder_stack: Vec<String> = Vec::new();
    let mut id_counter = 0usize;

    for line in html.lines() {
        if out.len() >= MAX_BOOKMARKS {
            break;
        }
        let trimmed = line.trim();

        // Folder heading: <H3 ...>Folder Name</H3>
        if let Some(rest) = find_tag_content(trimmed, "H3") {
            let folder_name = decode_html_entities(rest);
            folder_stack.push(folder_name);
            continue;
        }

        // End of folder: </DL> pops stack.
        if trimmed.eq_ignore_ascii_case("</DL><p>") || trimmed.eq_ignore_ascii_case("</DL>") {
            folder_stack.pop();
            continue;
        }

        // Bookmark link: <DT><A HREF="..." ...>Title</A>
        if let Some(href_and_rest) = extract_href(trimmed) {
            let (url, title) = href_and_rest;
            if !url.starts_with("http://") && !url.starts_with("https://") {
                continue;
            }
            id_counter += 1;
            out.push(Bookmark {
                id: format!("html-import-{}", id_counter),
                url,
                title,
                favicon: None,
                created_at: std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as u64,
                folder: folder_stack.last().cloned(),
                tags: Vec::new(),
            });
        }
    }

    out
}

/// Extracts (href_value, inner_text) from a line containing `<A HREF="...">text</A>`.
fn extract_href(line: &str) -> Option<(String, String)> {
    let upper = line.to_uppercase();
    let href_start = upper.find("HREF=")?;
    let after_href = &line[href_start + 5..];
    let (url, rest) = if after_href.starts_with('"') {
        let end = after_href[1..].find('"')? + 1;
        (decode_html_entities(&after_href[1..end]), &after_href[end + 1..])
    } else {
        // Unquoted (rare).
        let end = after_href.find(|c: char| c == ' ' || c == '>')?;
        (decode_html_entities(&after_href[..end]), &after_href[end..])
    };

    // Extract inner text between > and </A>
    let gt_pos = rest.find('>')?;
    let after_gt = &rest[gt_pos + 1..];
    let end_pos = after_gt.to_uppercase().find("</A>").unwrap_or(after_gt.len());
    let title = decode_html_entities(after_gt[..end_pos].trim());
    let title = if title.is_empty() { url.clone() } else { title };

    Some((url, title))
}

/// Extracts inner text of a given tag from a line, case-insensitively.
fn find_tag_content<'a>(line: &'a str, tag: &str) -> Option<&'a str> {
    let open = format!("<{}", tag.to_uppercase());
    let close = format!("</{}>", tag.to_uppercase());
    let upper = line.to_uppercase();
    let start = upper.find(&open)?;
    let gt = upper[start..].find('>')? + start + 1;
    let end = upper[gt..].find(&close)? + gt;
    Some(&line[gt..end])
}

/// Basic HTML entity decoding for bookmark titles and URLs.
fn decode_html_entities(s: &str) -> String {
    s.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_HTML: &str = r#"<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<DL><p>
    <DT><H3>Work</H3>
    <DL><p>
        <DT><A HREF="https://example.com" ADD_DATE="1609459200">Example Site</A>
        <DT><A HREF="https://rust-lang.org">Rust Lang</A>
    </DL><p>
    <DT><A HREF="https://blancbrowser.com">Blanc</A>
</DL><p>"#;

    #[test]
    fn test_parse_netscape_html() {
        let bms = parse_html_bookmarks(SAMPLE_HTML);
        assert_eq!(bms.len(), 3, "expected 3 bookmarks");
        assert_eq!(bms[0].folder.as_deref(), Some("Work"));
        assert_eq!(bms[0].title, "Example Site");
        assert_eq!(bms[2].title, "Blanc");
        assert!(bms[2].folder.is_none());
    }
}
