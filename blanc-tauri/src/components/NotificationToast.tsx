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

export const NotificationToast: React.FC<NotificationToastProps> = ({
  notification,
  onDismiss,
  onClick,
}) => {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    if (notification) {
      setIsVisible(true);

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
              {(() => {
                if (!notification?.origin) return 'Website';
                try {
                  if (notification.origin.startsWith('http://') || notification.origin.startsWith('https://')) {
                    return new URL(notification.origin).hostname;
                  }
                  return notification.origin;
                } catch {
                  return notification.origin || 'Website';
                }
              })()}
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
