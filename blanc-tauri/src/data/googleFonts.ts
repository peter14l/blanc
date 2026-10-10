export interface FontOption {
  id: string;
  name: string;
  category: 'Sans-Serif' | 'Serif' | 'Monospace' | 'Display' | 'System';
  isGoogleFont: boolean;
  weights: number[];
  previewText?: string;
}

export const GOOGLE_FONTS: FontOption[] = [
  // System / Default
  { id: 'Inter', name: 'Inter', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 500, 600, 700] },
  { id: 'System UI', name: 'System UI', category: 'System', isGoogleFont: false, weights: [400, 500, 600, 700] },
  
  // Sans-Serif
  { id: 'Roboto', name: 'Roboto', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 500, 700] },
  { id: 'Open Sans', name: 'Open Sans', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 600, 700] },
  { id: 'Montserrat', name: 'Montserrat', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 500, 600, 700] },
  { id: 'Poppins', name: 'Poppins', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 500, 600, 700] },
  { id: 'Lato', name: 'Lato', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 700] },
  { id: 'Plus Jakarta Sans', name: 'Plus Jakarta Sans', category: 'Sans-Serif', isGoogleFont: true, weights: [400, 500, 600, 700] },
  { id: 'Outfit', name: 'Outfit', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 500, 600, 700] },
  { id: 'DM Sans', name: 'DM Sans', category: 'Sans-Serif', isGoogleFont: true, weights: [400, 500, 700] },
  { id: 'Work Sans', name: 'Work Sans', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 500, 600] },
  { id: 'Geist', name: 'Geist', category: 'Sans-Serif', isGoogleFont: true, weights: [300, 400, 500, 600, 700] },

  // Serif
  { id: 'Newsreader', name: 'Newsreader', category: 'Serif', isGoogleFont: true, weights: [400, 500, 600] },
  { id: 'Playfair Display', name: 'Playfair Display', category: 'Serif', isGoogleFont: true, weights: [400, 500, 600, 700] },
  { id: 'Merriweather', name: 'Merriweather', category: 'Serif', isGoogleFont: true, weights: [300, 400, 700] },
  { id: 'Lora', name: 'Lora', category: 'Serif', isGoogleFont: true, weights: [400, 500, 600, 700] },
  { id: 'Cormorant Garamond', name: 'Cormorant Garamond', category: 'Serif', isGoogleFont: true, weights: [400, 500, 600, 700] },
  { id: 'EB Garamond', name: 'EB Garamond', category: 'Serif', isGoogleFont: true, weights: [400, 500, 600] },
  { id: 'Spectral', name: 'Spectral', category: 'Serif', isGoogleFont: true, weights: [300, 400, 500, 600] },

  // Monospace
  { id: 'JetBrains Mono', name: 'JetBrains Mono', category: 'Monospace', isGoogleFont: true, weights: [400, 500, 600, 700] },
  { id: 'Fira Code', name: 'Fira Code', category: 'Monospace', isGoogleFont: true, weights: [400, 500, 600] },
  { id: 'Source Code Pro', name: 'Source Code Pro', category: 'Monospace', isGoogleFont: true, weights: [400, 500, 600] },
  { id: 'Space Mono', name: 'Space Mono', category: 'Monospace', isGoogleFont: true, weights: [400, 700] },
  { id: 'IBM Plex Mono', name: 'IBM Plex Mono', category: 'Monospace', isGoogleFont: true, weights: [400, 500, 600] },
  { id: 'Inconsolata', name: 'Inconsolata', category: 'Monospace', isGoogleFont: true, weights: [400, 500, 600] },

  // Display / Stylized
  { id: 'Syne', name: 'Syne', category: 'Display', isGoogleFont: true, weights: [400, 600, 700, 800] },
  { id: 'Space Grotesk', name: 'Space Grotesk', category: 'Display', isGoogleFont: true, weights: [400, 500, 600, 700] },
];

const loadedFonts = new Set<string>();

/**
 * Dynamically loads a font from Google Fonts and applies it to the document.
 */
export function applyFontToDocument(fontName: string): void {
  if (!fontName || fontName === 'System UI') {
    document.documentElement.style.removeProperty('--font-ui');
    return;
  }

  const option = GOOGLE_FONTS.find((f) => f.name.toLowerCase() === fontName.toLowerCase()) || {
    id: fontName,
    name: fontName,
    category: 'Sans-Serif',
    isGoogleFont: true,
    weights: [400, 500, 600],
  };

  if (option.isGoogleFont && !loadedFonts.has(option.name)) {
    const linkId = `google-font-${option.name.replace(/\s+/g, '-').toLowerCase()}`;
    if (!document.getElementById(linkId)) {
      const link = document.createElement('link');
      link.id = linkId;
      link.rel = 'stylesheet';
      const formattedName = option.name.replace(/\s+/g, '+');
      link.href = `https://fonts.googleapis.com/css2?family=${formattedName}:wght@300;400;500;600;700&display=swap`;
      document.head.appendChild(link);
      loadedFonts.add(option.name);
    }
  }

  const fallback = option.category === 'Serif'
    ? 'serif'
    : option.category === 'Monospace'
    ? 'monospace'
    : 'sans-serif';

  document.documentElement.style.setProperty('--font-ui', `"${option.name}", ${fallback}`);
}
