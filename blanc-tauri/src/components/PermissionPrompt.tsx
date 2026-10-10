import React, { useState } from 'react';
import { Camera, Mic, Bell, MapPin, Check, X, ShieldAlert } from 'lucide-react';

export interface PermissionPromptData {
  id: string;
  origin: string;
  resource: 'camera' | 'microphone' | 'camera-microphone' | 'notifications' | 'geolocation' | string;
  tabId?: string;
}

interface PermissionPromptProps {
  prompt: PermissionPromptData | null;
  onRespond: (id: string, allow: boolean, remember: boolean) => void;
  onDismiss: () => void;
}

export const PermissionPrompt: React.FC<PermissionPromptProps> = ({
  prompt,
  onRespond,
  onDismiss,
}) => {
  const [remember, setRemember] = useState(true);

  if (!prompt) return null;

  const formattedOrigin = (() => {
    try {
      const url = new URL(prompt.origin);
      return url.hostname;
    } catch {
      return prompt.origin.replace(/^https?:\/\//, '').split('/')[0] || prompt.origin;
    }
  })();

  const getResourceDetails = () => {
    const res = prompt.resource.toLowerCase();
    if (res.includes('camera') && res.includes('microphone')) {
      return {
        title: 'use your Camera and Microphone',
        icon: (
          <div className="flex items-center -space-x-1">
            <Camera className="w-4 h-4 text-[#d4ad66]" />
            <Mic className="w-4 h-4 text-[#d4ad66]" />
          </div>
        ),
      };
    }
    if (res.includes('camera') || res.includes('video')) {
      return {
        title: 'use your Camera',
        icon: <Camera className="w-4.5 h-4.5 text-[#d4ad66]" />,
      };
    }
    if (res.includes('microphone') || res.includes('audio')) {
      return {
        title: 'use your Microphone',
        icon: <Mic className="w-4.5 h-4.5 text-[#d4ad66]" />,
      };
    }
    if (res.includes('notification')) {
      return {
        title: 'send you Notifications',
        icon: <Bell className="w-4.5 h-4.5 text-[#d4ad66]" />,
      };
    }
    if (res.includes('geo') || res.includes('location')) {
      return {
        title: 'access your Location',
        icon: <MapPin className="w-4.5 h-4.5 text-[#d4ad66]" />,
      };
    }
    return {
      title: `access ${prompt.resource}`,
      icon: <ShieldAlert className="w-4.5 h-4.5 text-[#d4ad66]" />,
    };
  };

  const { title, icon } = getResourceDetails();

  return (
    <div className="fixed top-20 left-1/2 -translate-x-1/2 z-50 pointer-events-auto animate-in fade-in slide-in-from-top-3 duration-200">
      <div className="flex flex-col w-[420px] max-w-[92vw] p-4 rounded-2xl bg-[#161616]/95 backdrop-blur-xl border border-[var(--island-border-adaptive,rgba(255,255,255,0.15))] shadow-[0_20px_50px_rgba(0,0,0,0.7)] text-white select-none">
        {/* Top Header */}
        <div className="flex items-start justify-between">
          <div className="flex items-center space-x-3">
            <div className="p-2.5 rounded-xl bg-white/[0.06] border border-white/10 flex items-center justify-center">
              {icon}
            </div>
            <div>
              <div className="flex items-center space-x-1.5">
                <span className="text-[14px] font-semibold text-white tracking-tight truncate max-w-[220px]">
                  {formattedOrigin}
                </span>
                <span className="text-[11px] px-1.5 py-0.5 rounded bg-white/10 text-white/60 font-mono">
                  Site Permission
                </span>
              </div>
              <p className="text-[13px] text-white/70 mt-0.5">
                wants to {title}
              </p>
            </div>
          </div>

          <button
            onClick={onDismiss}
            title="Dismiss prompt"
            className="p-1 rounded-lg hover:bg-white/10 text-white/40 hover:text-white transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Remember Toggle & Action Buttons */}
        <div className="mt-4 pt-3 border-t border-white/10 flex items-center justify-between">
          <label className="flex items-center space-x-2 text-[12px] text-white/60 cursor-pointer hover:text-white/80 transition-colors">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
              className="rounded bg-white/10 border-white/20 text-[#d4ad66] focus:ring-0 cursor-pointer accent-[#d4ad66]"
            />
            <span>Remember for this site</span>
          </label>

          <div className="flex items-center space-x-2">
            <button
              onClick={() => onRespond(prompt.id, false, remember)}
              className="px-3.5 py-1.5 rounded-xl text-[13px] font-medium text-white/70 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10 transition-colors"
            >
              Block
            </button>
            <button
              onClick={() => onRespond(prompt.id, true, remember)}
              className="px-4 py-1.5 rounded-xl text-[13px] font-semibold text-black bg-[#d4ad66] hover:bg-[#e0bc77] shadow-[0_4px_16px_rgba(212,173,102,0.3)] transition-colors flex items-center space-x-1.5"
            >
              <Check className="w-3.5 h-3.5 stroke-[2.5]" />
              <span>Allow</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
