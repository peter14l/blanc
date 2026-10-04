import React, { useState } from 'react';
import {
  Keyboard,
  Search,
  Command,
  Compass,
  Bookmark,
  Sparkles,
} from 'lucide-react';

interface ShortcutItem {
  id: string;
  category: 'tabs' | 'navigation' | 'palette' | 'utility';
  description: string;
  keys: string[];
}

const SHORTCUTS: ShortcutItem[] = [
  // Tabs & Windows
  { id: 'new-tab', category: 'tabs', description: 'Open new tab', keys: ['⌘', 'T'] },
  { id: 'close-tab', category: 'tabs', description: 'Close active tab', keys: ['⌘', 'W'] },
  { id: 'reopen-tab', category: 'tabs', description: 'Reopen recently closed tab', keys: ['⌘', '⇧', 'T'] },
  { id: 'switch-tab-next', category: 'tabs', description: 'Switch to next tab', keys: ['⌘', '⌥', '→'] },
  { id: 'switch-tab-prev', category: 'tabs', description: 'Switch to previous tab', keys: ['⌘', '⌥', '←'] },
  { id: 'tab-switcher', category: 'tabs', description: 'Toggle tab switcher cards', keys: ['⌘', '⇧', 'A'] },

  // Navigation
  { id: 'reload-tab', category: 'navigation', description: 'Reload active page', keys: ['⌘', 'R'] },
  { id: 'force-reload', category: 'navigation', description: 'Force reload without cache', keys: ['⌘', '⇧', 'R'] },
  { id: 'go-back', category: 'navigation', description: 'Navigate backward', keys: ['⌘', '['] },
  { id: 'go-forward', category: 'navigation', description: 'Navigate forward', keys: ['⌘', ']'] },
  { id: 'stop-loading', category: 'navigation', description: 'Stop page loading', keys: ['Esc'] },

  // Command Palette & Search
  { id: 'quick-switcher', category: 'palette', description: 'Open Quick Switcher / Omnibar', keys: ['⌘', 'K'] },
  { id: 'focus-address', category: 'palette', description: 'Focus Island command pill', keys: ['⌘', 'L'] },
  { id: 'find-page', category: 'palette', description: 'Find in current page', keys: ['⌘', 'F'] },

  // Utility Surfaces
  { id: 'open-bookmarks', category: 'utility', description: 'Open Bookmarks manager', keys: ['⌘', '⇧', 'B'] },
  { id: 'open-history', category: 'utility', description: 'Open History surface', keys: ['⌘', 'Y'] },
  { id: 'open-downloads', category: 'utility', description: 'Open Downloads surface', keys: ['⌘', 'J'] },
  { id: 'open-settings', category: 'utility', description: 'Open Settings surface', keys: ['⌘', ','] },
  { id: 'open-shortcuts', category: 'utility', description: 'Open Shortcuts cheat-sheet', keys: ['⌘', '/'] },
];

export const ShortcutsPage: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<'all' | 'tabs' | 'navigation' | 'palette' | 'utility'>('all');

  const categories = [
    { id: 'all', name: 'All Shortcuts', icon: Sparkles },
    { id: 'tabs', name: 'Tabs & Windows', icon: Compass },
    { id: 'navigation', name: 'Navigation', icon: Compass },
    { id: 'palette', name: 'Quick Switcher', icon: Command },
    { id: 'utility', name: 'Surfaces', icon: Bookmark },
  ];

  const filteredShortcuts = SHORTCUTS.filter((item) => {
    const matchesCategory = selectedCategory === 'all' || item.category === selectedCategory;
    const matchesSearch =
      item.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.keys.some((k) => k.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesCategory && matchesSearch;
  });

  return (
    <div className="w-full h-full overflow-y-auto bg-transparent text-white px-6 py-8 flex flex-col items-center select-none font-ui">
      <div className="w-full max-w-3xl space-y-6 pb-12">
        {/* Header */}
        <div className="flex items-center space-x-3 pb-4 border-b border-white/10">
          <div className="p-2 rounded-xl bg-white/5 border border-white/10 text-[#d4ad66]">
            <Keyboard className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl font-semibold tracking-tight text-white/95">Keyboard Shortcuts</h1>
            <p className="text-[13px] text-white/40">
              Complete command cheat-sheet for rapid browser navigation
            </p>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div className="space-y-3">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-white/30" />
            <input
              type="text"
              placeholder="Search shortcuts by action or key..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-white/[0.03] border border-white/10 focus:border-[#d4ad66]/50 focus:bg-white/[0.06] text-sm text-white placeholder-white/20 outline-none transition-all"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {categories.map((cat) => {
              const Icon = cat.icon;
              const isActive = selectedCategory === cat.id;
              return (
                <button
                  key={cat.id}
                  onClick={() => setSelectedCategory(cat.id as any)}
                  className={`px-3 py-1.5 rounded-xl text-[12px] font-medium border transition-all flex items-center space-x-1.5 ${
                    isActive
                      ? 'bg-[#d4ad66]/15 border-[#d4ad66]/40 text-[#d4ad66]'
                      : 'bg-white/[0.02] border-white/5 text-white/50 hover:bg-white/[0.06] hover:text-white/80'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  <span>{cat.name}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Shortcuts List */}
        {filteredShortcuts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-white/40 space-y-3">
            <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/5 text-white/20">
              <Keyboard className="w-8 h-8" />
            </div>
            <p className="text-[14px] font-medium text-white/60">No shortcuts matched your search</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-white/10 bg-white/[0.02] divide-y divide-white/5 backdrop-blur-md overflow-hidden">
            {filteredShortcuts.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between px-5 py-3 hover:bg-white/[0.03] transition-colors"
              >
                <span className="text-[13px] text-white/85 font-medium">{item.description}</span>
                <div className="flex items-center space-x-1.5">
                  {item.keys.map((k, idx) => (
                    <kbd
                      key={idx}
                      className="min-w-[26px] h-7 px-2 flex items-center justify-center rounded-lg bg-white/5 border border-white/15 text-white/90 font-mono text-[12px] font-medium shadow-sm"
                    >
                      {k}
                    </kbd>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Note on modifier keys */}
        <div className="p-4 rounded-2xl bg-white/[0.015] border border-white/5 text-[12px] text-white/40 flex items-center justify-between">
          <span>Note: On Windows and Linux, use <strong className="text-white/70">Ctrl</strong> in place of <strong className="text-white/70">⌘</strong> and <strong className="text-white/70">Alt</strong> in place of <strong className="text-white/70">⌥</strong>.</span>
          <span className="text-[#d4ad66] font-mono text-[11px]">Blanc v1.27.0 Parity</span>
        </div>
      </div>
    </div>
  );
};
