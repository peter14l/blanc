import React, { useEffect, useState } from 'react';
import { Bell, X } from 'lucide-react';

export interface WebNotificationData {
  id: string;
  title: string;
  body: string;
  icon?: string;
  origin?: string;
  tabId?: string;
}

interface NotificationToastProps {
  notification: WebNotificationData | null;
  onDismiss: () => void;
  onClick: (tabId?: string) => void;
}

// Synthesizes a clean, pleasant notification chime using Web Audio API
function playNotificationChime() {
  try {
    const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    
    // Primary chime tone
    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(880, ctx.currentTime); // A5
    osc1.frequency.exponentialRampToValueAtTime(1320, ctx.currentTime + 0.12); // E6
    gain1.gain.setValueAtTime(0.12, ctx.currentTime);
    gain1.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35);

    osc1.connect(gain1);
    gain1.connect(ctx.destination);
    osc1.start();
    osc1.stop(ctx.currentTime + 0.35);

    // Harmonic bell overtone
    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();
    osc2.type = 'triangle';
    osc2.frequency.setValueAtTime(1760, ctx.currentTime + 0.05); // A6
    gain2.gain.setValueAtTime(0.06, ctx.currentTime + 0.05);
    gain2.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);

    osc2.connect(gain2);
    gain2.connect(ctx.destination);
    osc2.start(ctx.currentTime + 0.05);
    osc2.stop(ctx.currentTime + 0.4);
  } catch (err) {
    // Audio context may be restricted by autoplay policy before user gesture
  }
}

export const NotificationToast: React.FC<NotificationToastProps> = ({
  notification,
  onDismiss,
  onClick,
}) => {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    if (notification) {
      setIsVisible(true);
      playNotificationChime();

      // Auto-dismiss after 6.5 seconds
      const timer = setTimeout(() => {
        setIsVisible(false);
        setTimeout(onDismiss, 300);
      }, 6500);

      return () => clearTimeout(timer);
    } else {
      setIsVisible(false);
    }
  }, [notification, onDismiss]);

  if (!notification && !isVisible) return null;

  return (
    <div
      className={`fixed top-4 right-4 z-50 max-w-[360px] w-full transition-all duration-300 transform select-none ${
        isVisible ? 'translate-y-0 opacity-100 scale-100' : '-translate-y-3 opacity-0 scale-95 pointer-events-none'
      }`}
    >
      <div
        onClick={() => onClick(notification?.tabId)}
        className="group relative flex items-start gap-3 p-3.5 rounded-2xl bg-[#141414]/95 dark:bg-[#111111]/95 
          backdrop-blur-2xl border border-[var(--island-border-adaptive,rgba(255,255,255,0.15))] 
          shadow-[0_16px_48px_rgba(0,0,0,0.7)] text-white cursor-pointer hover:border-[#d4ad66]/40 transition-all"
      >
        {/* Site / Notification Icon */}
        <div className="relative shrink-0 mt-0.5">
          {notification?.icon ? (
            <img
              src={notification.icon}
              alt=""
              className="w-9 h-9 rounded-xl object-cover border border-white/10"
              onError={(e) => {
                e.currentTarget.style.display = 'none';
              }}
            />
          ) : (
            <div className="w-9 h-9 rounded-xl bg-[#d4ad66]/15 border border-[#d4ad66]/30 flex items-center justify-center text-[#d4ad66]">
              <Bell className="w-4 h-4" />
            </div>
          )}
          <div className="absolute -bottom-1 -right-1 p-0.5 rounded-full bg-[#111] border border-white/10 text-[#d4ad66]">
            <Bell className="w-2.5 h-2.5" />
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0 pr-6">
          <div className="flex items-center gap-1.5 mb-0.5">
            <span className="text-[11px] font-mono text-white/40 truncate max-w-[180px]">
              {notification?.origin ? new URL(notification.origin).hostname : 'Website'}
            </span>
            <span className="text-white/20 text-[10px]">• now</span>
          </div>

          <h4 className="text-[13px] font-semibold text-white/95 leading-snug line-clamp-1 group-hover:text-[#d4ad66] transition-colors">
            {notification?.title}
          </h4>

          {notification?.body && (
            <p className="text-[12px] text-white/60 leading-snug line-clamp-2 mt-0.5">
              {notification.body}
            </p>
          )}
        </div>

        {/* Dismiss Button */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            setIsVisible(false);
            setTimeout(onDismiss, 200);
          }}
          className="absolute top-2.5 right-2.5 p-1 rounded-lg text-white/40 hover:text-white hover:bg-white/10 transition-colors"
          title="Dismiss"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
};
