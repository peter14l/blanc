export interface NatureWallpaper {
  id: string;
  title: string;
  location: string;
  photographer: string;
  url: string;
  thumbnailUrl: string;
  accentColor: string;
  tintColor: string;
  glowColor: string;
}

export const NATURE_WALLPAPERS: NatureWallpaper[] = [
  {
    id: 'emerald-lake',
    title: 'Emerald Alpine Lake',
    location: 'Banff National Park, Canada',
    photographer: 'Luca Bravo',
    url: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=2560&q=85',
    thumbnailUrl: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=480&q=70',
    accentColor: '#34d399', // Emerald green
    tintColor: 'rgba(10, 22, 18, 0.75)',
    glowColor: 'rgba(52, 211, 153, 0.18)',
  },
  {
    id: 'misty-forest',
    title: 'Misty Pine Forest',
    location: 'Bavarian Alps, Germany',
    photographer: 'Sebastian Unrau',
    url: 'https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&w=2560&q=85',
    thumbnailUrl: 'https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&w=480&q=70',
    accentColor: '#a3e635', // Lime/pine green
    tintColor: 'rgba(14, 20, 14, 0.78)',
    glowColor: 'rgba(163, 230, 53, 0.16)',
  },
  {
    id: 'golden-peaks',
    title: 'Sunrise Over Dolomites',
    location: 'Dolomites, Northern Italy',
    photographer: 'Ales Krivec',
    url: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=2560&q=85',
    thumbnailUrl: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=480&q=70',
    accentColor: '#d4ad66', // Blanc sunrise gold
    tintColor: 'rgba(23, 19, 15, 0.75)',
    glowColor: 'rgba(212, 173, 102, 0.22)',
  },
  {
    id: 'coastal-horizon',
    title: 'Sunset at Pacific Coast',
    location: 'Big Sur, California',
    photographer: 'Sean Oulashin',
    url: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=2560&q=85',
    thumbnailUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=480&q=70',
    accentColor: '#fb923c', // Warm amber / orange
    tintColor: 'rgba(24, 16, 12, 0.76)',
    glowColor: 'rgba(251, 146, 60, 0.20)',
  },
  {
    id: 'aurora-fjords',
    title: 'Northern Lights Over Fjords',
    location: 'Tromsø, Norway',
    photographer: 'Vincent Guth',
    url: 'https://images.unsplash.com/photo-1517411032315-54ef2cb783bb?auto=format&fit=crop&w=2560&q=85',
    thumbnailUrl: 'https://images.unsplash.com/photo-1517411032315-54ef2cb783bb?auto=format&fit=crop&w=480&q=70',
    accentColor: '#22d3ee', // Cyan / Glacier blue
    tintColor: 'rgba(8, 18, 24, 0.78)',
    glowColor: 'rgba(34, 211, 238, 0.22)',
  },
  {
    id: 'canyon-glow',
    title: 'Glowing Sandstone Canyon',
    location: 'Antelope Canyon, Arizona',
    photographer: 'Dino Reichmuth',
    url: 'https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?auto=format&fit=crop&w=2560&q=85',
    thumbnailUrl: 'https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?auto=format&fit=crop&w=480&q=70',
    accentColor: '#f97316', // Sunset terra cotta
    tintColor: 'rgba(26, 14, 10, 0.78)',
    glowColor: 'rgba(249, 115, 22, 0.22)',
  },
  {
    id: 'snowy-matterhorn',
    title: 'Alps Snow Summit',
    location: 'Zermatt, Switzerland',
    photographer: 'Fabrizio Conti',
    url: 'https://images.unsplash.com/photo-1486870591958-9b9d0d1dda99?auto=format&fit=crop&w=2560&q=85',
    thumbnailUrl: 'https://images.unsplash.com/photo-1486870591958-9b9d0d1dda99?auto=format&fit=crop&w=480&q=70',
    accentColor: '#93c5fd', // Ice blue
    tintColor: 'rgba(12, 16, 24, 0.76)',
    glowColor: 'rgba(147, 197, 253, 0.20)',
  },
  {
    id: 'misty-valley',
    title: 'Misty Alpine Valley',
    location: 'Lauterbrunnen, Switzerland',
    photographer: 'Kalep Tapp',
    url: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=2560&q=85',
    thumbnailUrl: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=480&q=70',
    accentColor: '#4ade80', // Spring emerald
    tintColor: 'rgba(10, 20, 14, 0.76)',
    glowColor: 'rgba(74, 222, 128, 0.20)',
  },
];

/**
 * Applies dynamic color adaptations to the browser frame based on the active wallpaper.
 */
export function applyWallpaperTheme(wallpaper: NatureWallpaper): void {
  const root = document.documentElement;
  root.style.setProperty('--wallpaper-accent', wallpaper.accentColor);
  root.style.setProperty('--wallpaper-tint', wallpaper.tintColor);
  root.style.setProperty('--wallpaper-glow', wallpaper.glowColor);
  root.style.setProperty('--accent', wallpaper.accentColor);
  root.style.setProperty('--island-border-adaptive', `${wallpaper.accentColor}40`);
  root.style.setProperty('--island-glow-adaptive', wallpaper.glowColor);
}
