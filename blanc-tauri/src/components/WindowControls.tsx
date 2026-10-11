import React, { useState, useEffect } from 'react';
import { Minus, Square, X } from 'lucide-react';

export type WindowControlsPlatform = 'auto' | 'windows' | 'macos' | 'linux' | 'dots' | 'buttons';

interface WindowControlsProps {
  onMinimize: () => void;
  onMaximize: () => void;
  onClose: () => void;
  className?: string;
  variant?: WindowControlsPlatform;
}

function getPlatform(): 'windows' | 'macos' | 'linux' {
  if (typeof navigator === 'undefined') return 'windows';
  const ua = navigator.userAgent || '';
  if (/Macintosh|Mac OS X|macOS/i.test(ua)) return 'macos';
  if (/Linux/i.test(ua) && !/Android/i.test(ua)) return 'linux';
  return 'windows';
}

export const WindowControls: React.FC<WindowControlsProps> = ({
  onMinimize,
  onMaximize,
  onClose,
  className = '',
  variant = 'auto',
}) => {
  const [platform, setPlatform] = useState<'windows' | 'macos' | 'linux'>('windows');

  useEffect(() => {
    setPlatform(getPlatform());
  }, []);

  const activeVariant = variant === 'auto' ? platform : variant;

  // 1. WINDOWS 10 / 11 NATIVE CAPTION CONTROLS (Fitts's Law compliant, 46x32px hitboxes)
  if (activeVariant === 'windows') {
    return (
      <div
        className={`flex items-center h-8 no-drag select-none bg-transparent ${className}`}
        data-tauri-drag-region="false"
      >
        <button
          onClick={onMinimize}
          title="Minimize"
          aria-label="Minimize"
          className="w-[46px] h-8 flex items-center justify-center text-white/75 hover:text-white hover:bg-white/10 active:bg-white/20 transition-colors"
        >
          <svg width="10" height="1" viewBox="0 0 10 1" fill="currentColor">
            <rect width="10" height="1" />
          </svg>
        </button>
        <button
          onClick={onMaximize}
          title="Maximize"
          aria-label="Maximize"
          className="w-[46px] h-8 flex items-center justify-center text-white/75 hover:text-white hover:bg-white/10 active:bg-white/20 transition-colors"
        >
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1">
            <rect x="0.5" y="0.5" width="9" height="9" />
          </svg>
        </button>
        <button
          onClick={onClose}
          title="Close"
          aria-label="Close"
          className="w-[46px] h-8 flex items-center justify-center text-white/75 hover:text-white hover:bg-[#e81123] active:bg-[#c4101e] transition-colors"
        >
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.2">
            <path d="M1 1L9 9M9 1L1 9" />
          </svg>
        </button>
      </div>
    );
  }

  // 2. MACOS TRAFFIC LIGHTS (12px circular dots with hover glyphs)
  if (activeVariant === 'macos' || activeVariant === 'dots') {
    return (
      <div
        className={`flex items-center space-x-2 group no-drag select-none ${className}`}
        data-tauri-drag-region="false"
      >
        <button
          onClick={onClose}
          title="Close"
          aria-label="Close"
          className="w-3 h-3 rounded-full bg-[#ff5f56] border border-[#e0443e] flex items-center justify-center text-[#4c0000] hover:opacity-100 transition-opacity focus:outline-none"
        >
          <svg className="w-2 h-2 opacity-0 group-hover:opacity-100 transition-opacity" viewBox="0 0 8 8" stroke="currentColor" strokeWidth="1.2">
            <path d="M1 1L7 7M7 1L1 7" />
          </svg>
        </button>
        <button
          onClick={onMinimize}
          title="Minimize"
          aria-label="Minimize"
          className="w-3 h-3 rounded-full bg-[#ffbd2e] border border-[#dea123] flex items-center justify-center text-[#664400] hover:opacity-100 transition-opacity focus:outline-none"
        >
          <svg className="w-2 h-2 opacity-0 group-hover:opacity-100 transition-opacity" viewBox="0 0 8 2" fill="currentColor">
            <rect width="8" height="1.5" rx="0.5" />
          </svg>
        </button>
        <button
          onClick={onMaximize}
          title="Maximize"
          aria-label="Maximize"
          className="w-3 h-3 rounded-full bg-[#27c93f] border border-[#1aab29] flex items-center justify-center text-[#004d00] hover:opacity-100 transition-opacity focus:outline-none"
        >
          <svg className="w-2 h-2 opacity-0 group-hover:opacity-100 transition-opacity" viewBox="0 0 8 8" fill="currentColor">
            <path d="M1 4L4 1H1V4ZM7 4L4 7H7V4Z" />
          </svg>
        </button>
      </div>
    );
  }

  // 3. LINUX ADWAITA / GNOME / KDE CONTROLS
  if (activeVariant === 'linux') {
    return (
      <div
        className={`flex items-center space-x-1.5 no-drag select-none ${className}`}
        data-tauri-drag-region="false"
      >
        <button
          onClick={onMinimize}
          title="Minimize"
          aria-label="Minimize"
          className="w-6 h-6 rounded-full bg-white/10 hover:bg-white/20 text-white/70 hover:text-white transition-colors flex items-center justify-center"
        >
          <Minus className="w-3 h-3" />
        </button>
        <button
          onClick={onMaximize}
          title="Maximize"
          aria-label="Maximize"
          className="w-6 h-6 rounded-full bg-white/10 hover:bg-white/20 text-white/70 hover:text-white transition-colors flex items-center justify-center"
        >
          <Square className="w-2.5 h-2.5" />
        </button>
        <button
          onClick={onClose}
          title="Close"
          aria-label="Close"
          className="w-6 h-6 rounded-full bg-white/10 hover:bg-[#e81123] text-white/70 hover:text-white transition-colors flex items-center justify-center"
        >
          <X className="w-3 h-3" />
        </button>
      </div>
    );
  }

  // 4. FLOATING CAPSULE BUTTONS (Minimalist fallback)
  return (
    <div
      className={`flex items-center space-x-0.5 no-drag bg-black/40 backdrop-blur-md border border-white/10 rounded-xl px-1 py-0.5 shadow-sm ${className}`}
      data-tauri-drag-region="false"
    >
      <button
        onClick={onMinimize}
        title="Minimize"
        aria-label="Minimize"
        className="w-7 h-7 rounded-lg hover:bg-white/10 text-white/70 hover:text-white transition-colors flex items-center justify-center"
      >
        <Minus className="w-3.5 h-3.5" />
      </button>
      <button
        onClick={onMaximize}
        title="Maximize"
        aria-label="Maximize"
        className="w-7 h-7 rounded-lg hover:bg-white/10 text-white/70 hover:text-white transition-colors flex items-center justify-center"
      >
        <Square className="w-3 h-3" />
      </button>
      <button
        onClick={onClose}
        title="Close"
        aria-label="Close"
        className="w-7 h-7 rounded-lg hover:bg-[#e81123] hover:text-white text-white/70 transition-colors flex items-center justify-center"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
};
