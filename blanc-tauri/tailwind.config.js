/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        bg: 'var(--bg)',
        surface: 'var(--surface)',
        'surface-raised': 'var(--surface-raised)',
        'island-resting-surface': 'var(--island-resting-surface)',
        border: 'var(--border)',
        text: 'var(--text)',
        'text-dim': 'var(--text-dim)',
        accent: 'var(--accent)',
        'accent-dim': 'var(--accent-dim)',
        danger: 'var(--danger)',
        'on-danger': 'var(--on-danger)',
        sunrise: {
          bg: 'var(--sunrise-bg)',
          surface: 'var(--sunrise-surface)',
          'surface-raised': 'var(--sunrise-surface-raised)',
          border: 'var(--sunrise-border)',
          text: 'var(--sunrise-text)',
          'text-dim': 'var(--sunrise-text-dim)',
          accent: 'var(--sunrise-accent)',
          'accent-dim': 'var(--sunrise-accent-dim)',
        },
        patron: {
          gold: 'var(--patron-gold)',
          surface: 'var(--patron-surface)',
          label: 'var(--patron-label)',
          halo: 'var(--patron-halo)',
        },
        // Static fallbacks from Blanc tokens
        blanc: {
          bg: '#0e0e0e',
          surface: '#171717',
          surfaceRaised: '#1f1f1f',
          islandResting: 'rgba(31,31,31,0.94)',
          border: '#2e2e2e',
          text: '#f5f5f5',
          textDim: '#9c9c9c',
          accent: '#f5f5f5',
          gold: '#d4ad66',
        }
      },
      boxShadow: {
        'island-resting': 'inset 0 1px 0 rgba(255,255,255,.72), inset 0 -1px 0 rgba(14,14,14,.035), 0 5px 18px -12px rgba(14,14,14,.24)',
        'island-dark': 'inset 0 1px 0 rgba(255,255,255,.12), inset 0 -1px 0 rgba(0,0,0,.4), 0 8px 32px -4px rgba(0,0,0,.5)',
        'popover': 'inset 0 1px 0 rgba(255,255,255,0.55), inset 0 -6px 10px -8px rgba(14,14,14,0.12), inset 0 -1px 0 rgba(14,14,14,0.035), 0 10px 44px -4px rgba(14,14,14,0.12)',
        'pill': 'inset 0 1px 0 rgba(255,255,255,0.65), inset 1px 0 0.5px -0.5px rgba(255,255,255,0.28), inset -1px 0 0.5px -0.5px rgba(255,255,255,0.28), inset 0 -5px 7px -6px rgba(14,14,14,0.16), inset 0 -1px 0 rgba(14,14,14,0.045), 0 2px 16px -3px rgba(14,14,14,0.10)',
      },
      borderRadius: {
        'island-resting': '17px',
        'island-panel': '18px',
      },
      height: {
        'island-resting': '44px',
        'strip': '68px',
      },
      fontFamily: {
        ui: ['"Inter"', '-apple-system', '"Segoe UI Variable"', '"Segoe UI"', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
