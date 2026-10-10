import React from 'react';
import { Minus, Square, X } from 'lucide-react';

interface WindowControlsProps {
  onMinimize: () => void;
  onMaximize: () => void;
  onClose: () => void;
  className?: string;
  variant?: 'dots' | 'buttons';
}

export const WindowControls: React.FC<WindowControlsProps> = ({
  onMinimize,
  onMaximize,
  onClose,
  className = '',
  variant = 'dots',
}) => {
  if (variant === 'buttons') {
    return (
      <div className={`flex items-center space-x-0.5 no-drag bg-black/40 backdrop-blur-md border border-white/10 rounded-xl px-1 py-0.5 shadow-sm ${className}`}>
        <button
          onClick={onMinimize}
          title="Minimize"
          className="w-7 h-7 rounded-lg hover:bg-white/10 text-white/70 hover:text-white transition-colors flex items-center justify-center"
        >
          <Minus className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={onMaximize}
          title="Maximize"
          className="w-7 h-7 rounded-lg hover:bg-white/10 text-white/70 hover:text-white transition-colors flex items-center justify-center"
        >
          <Square className="w-3 h-3" />
        </button>
        <button
          onClick={onClose}
          title="Close"
          className="w-7 h-7 rounded-lg hover:bg-[#e81123] hover:text-white text-white/70 transition-colors flex items-center justify-center"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    );
  }

  // Apple-style / minimal luxury dots
  return (
    <div className={`flex items-center space-x-2 group no-drag ${className}`}>
      <button
        onClick={onClose}
        title="Close"
        className="w-3 h-3 rounded-full bg-[#ff5f56] border border-[#e0443e] flex items-center justify-center text-black/70 hover:opacity-100 transition-opacity"
      >
        <X className="w-2 h-2 opacity-0 group-hover:opacity-100 transition-opacity" />
      </button>
      <button
        onClick={onMinimize}
        title="Minimize"
        className="w-3 h-3 rounded-full bg-[#ffbd2e] border border-[#dea123] flex items-center justify-center text-black/70 hover:opacity-100 transition-opacity"
      >
        <Minus className="w-2 h-2 opacity-0 group-hover:opacity-100 transition-opacity" />
      </button>
      <button
        onClick={onMaximize}
        title="Maximize"
        className="w-3 h-3 rounded-full bg-[#27c93f] border border-[#1aab29] flex items-center justify-center text-black/70 hover:opacity-100 transition-opacity"
      >
        <div className="w-1.5 h-1.5 border-[1px] border-black/70 rounded-xs opacity-0 group-hover:opacity-100 transition-opacity" />
      </button>
    </div>
  );
};
