import React, { useState, useRef, useEffect } from 'react';
import { Type, Check, ChevronDown, Sparkles } from 'lucide-react';
import { GOOGLE_FONTS, FontOption, applyFontToDocument } from '../data/googleFonts';

interface FontSwitcherProps {
  currentFont?: string;
  onSelectFont: (fontName: string) => void;
  className?: string;
  compact?: boolean;
}

export const FontSwitcher: React.FC<FontSwitcherProps> = ({
  currentFont = 'Inter',
  onSelectFont,
  className = '',
  compact = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [activeCategory, setActiveCategory] = useState<string>('All');
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen]);

  const categories = ['All', 'Sans-Serif', 'Serif', 'Monospace', 'Display'];

  const filteredFonts = GOOGLE_FONTS.filter((f) => {
    const matchesCategory = activeCategory === 'All' || f.category === activeCategory;
    const matchesSearch = f.name.toLowerCase().includes(search.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  const selectedOption = GOOGLE_FONTS.find(
    (f) => f.name.toLowerCase() === currentFont.toLowerCase()
  ) || GOOGLE_FONTS[0];

  const handleSelect = (font: FontOption) => {
    applyFontToDocument(font.name);
    onSelectFont(font.name);
    setIsOpen(false);
  };

  return (
    <div className={`relative inline-block ${className}`} ref={dropdownRef}>
      {/* Trigger Button */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className={`flex items-center justify-between gap-2.5 rounded-xl border border-white/10 bg-white/5 
          hover:bg-white/10 hover:border-white/20 px-3 py-2 text-white/90 transition-all cursor-pointer select-none ${
          compact ? 'text-[12px] h-8' : 'text-[13px] h-10 min-w-[180px]'
        }`}
        title="Change Browser Font"
      >
        <div className="flex items-center gap-2 truncate">
          <Type className="w-3.5 h-3.5 text-[#d4ad66] shrink-0" />
          <span className="truncate font-medium">{selectedOption.name}</span>
          <span className="text-[10px] text-white/40 hidden sm:inline">
            ({selectedOption.category})
          </span>
        </div>
        <ChevronDown className={`w-3.5 h-3.5 text-white/40 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {/* Dropdown Menu */}
      {isOpen && (
        <div className="absolute right-0 top-full mt-2 w-72 sm:w-80 rounded-2xl bg-[#141414]/95 border border-white/15 shadow-2xl backdrop-blur-xl p-3 z-50 animate-in fade-in slide-in-from-top-1 duration-150">
          {/* Header & Search */}
          <div className="space-y-2 mb-2 pb-2 border-b border-white/10">
            <div className="flex items-center justify-between px-1">
              <span className="text-[12px] font-semibold text-white/80 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-[#d4ad66]" />
                Google Fonts Typography
              </span>
              <span className="text-[10px] text-white/40 font-mono">
                {filteredFonts.length} fonts
              </span>
            </div>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search font by name..."
              className="w-full px-2.5 py-1.5 rounded-lg bg-black/40 border border-white/10 text-[12px] text-white placeholder-white/30 outline-none focus:border-[#d4ad66]"
              autoFocus
            />

            {/* Category Pills */}
            <div className="flex items-center gap-1 overflow-x-auto py-1 no-scrollbar text-[10px]">
              {categories.map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setActiveCategory(cat)}
                  className={`px-2 py-0.5 rounded-md whitespace-nowrap transition-colors ${
                    activeCategory === cat
                      ? 'bg-[#d4ad66]/20 text-[#d4ad66] font-medium'
                      : 'text-white/50 hover:text-white/80 hover:bg-white/5'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          {/* Font List */}
          <div className="max-h-60 overflow-y-auto space-y-1 custom-scrollbar pr-1">
            {filteredFonts.map((font) => {
              const isSelected = font.name.toLowerCase() === currentFont.toLowerCase();
              return (
                <button
                  key={font.id}
                  type="button"
                  onClick={() => handleSelect(font)}
                  className={`w-full flex items-center justify-between px-2.5 py-2 rounded-xl text-left transition-colors ${
                    isSelected
                      ? 'bg-[#d4ad66]/15 text-[#d4ad66] font-semibold'
                      : 'hover:bg-white/5 text-white/85'
                  }`}
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-[13px] truncate">{font.name}</span>
                      <span className="text-[10px] text-white/35 font-mono">
                        {font.category}
                      </span>
                    </div>
                    <div
                      className="text-[11px] text-white/50 truncate mt-0.5"
                      style={{
                        fontFamily: font.isGoogleFont ? `"${font.name}", sans-serif` : undefined,
                      }}
                    >
                      The quick brown fox jumps over the lazy dog
                    </div>
                  </div>
                  {isSelected && <Check className="w-4 h-4 text-[#d4ad66] shrink-0 ml-2" />}
                </button>
              );
            })}
            {filteredFonts.length === 0 && (
              <div className="text-center py-4 text-white/40 text-[12px]">
                No fonts found matching "{search}"
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
