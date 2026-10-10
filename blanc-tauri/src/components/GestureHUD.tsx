import React from 'react';
import { ArrowLeft, ArrowRight, ArrowUp, ArrowDown, RotateCw, Undo2 } from 'lucide-react';
import { GestureHUDState } from '../hooks/useMouseGestures';

interface GestureHUDProps {
  state: GestureHUDState;
}

export const GestureHUD: React.FC<GestureHUDProps> = ({ state }) => {
  if (!state.visible) return null;

  const renderIcon = () => {
    switch (state.action) {
      case 'back':
        return <ArrowLeft className="w-6 h-6 text-[#d4ad66]" />;
      case 'forward':
        return <ArrowRight className="w-6 h-6 text-[#d4ad66]" />;
      case 'newTab':
        return <ArrowUp className="w-6 h-6 text-emerald-400" />;
      case 'closeTab':
        return <ArrowDown className="w-6 h-6 text-rose-400" />;
      case 'reload':
        return <RotateCw className="w-6 h-6 text-sky-400" />;
      case 'reopenTab':
        return <Undo2 className="w-6 h-6 text-purple-400" />;
      default:
        return null;
    }
  };

  return (
    <div className="fixed inset-0 pointer-events-none z-50 flex items-center justify-center animate-in fade-in duration-100">
      <div className="flex items-center gap-3.5 px-6 py-4 rounded-3xl bg-[#141414]/90 border border-white/20 shadow-[0_20px_60px_rgba(0,0,0,0.7)] backdrop-blur-2xl text-white select-none scale-105 transition-transform">
        <div className="p-2 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center">
          {renderIcon()}
        </div>
        <div className="flex flex-col pr-1">
          <span className="text-[16px] font-semibold text-white tracking-tight">
            {state.label}
          </span>
          <span className="text-[11px] text-white/40 font-mono">
            Mouse Gesture
          </span>
        </div>
      </div>
    </div>
  );
};
