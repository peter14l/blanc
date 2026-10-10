import { useEffect, useState, useRef, useCallback } from 'react';

export type GestureActionType =
  | 'back'
  | 'forward'
  | 'reload'
  | 'newTab'
  | 'closeTab'
  | 'reopenTab'
  | 'none';

export interface GestureHUDState {
  visible: boolean;
  action: GestureActionType;
  label: string;
  icon: string;
}

interface UseMouseGesturesOptions {
  enabled?: boolean;
  onGoBack: () => void;
  onGoForward: () => void;
  onReload: () => void;
  onNewTab: () => void;
  onCloseTab: () => void;
  onReopenClosedTab: () => void;
}

const STROKE_PX = 24;

export function useMouseGestures({
  enabled = true,
  onGoBack,
  onGoForward,
  onReload,
  onNewTab,
  onCloseTab,
  onReopenClosedTab,
}: UseMouseGesturesOptions) {
  const [hudState, setHudState] = useState<GestureHUDState>({
    visible: false,
    action: 'none',
    label: '',
    icon: '',
  });

  const gestureRef = useRef<{
    isHeld: boolean;
    startX: number;
    startY: number;
    lastX: number;
    lastY: number;
    pattern: string;
    moved: boolean;
    button: number;
    suppressContextMenu: boolean;
    leftHeld: boolean;
    rightHeld: boolean;
  }>({
    isHeld: false,
    startX: 0,
    startY: 0,
    lastX: 0,
    lastY: 0,
    pattern: '',
    moved: false,
    button: 2,
    suppressContextMenu: false,
    leftHeld: false,
    rightHeld: false,
  });

  const getActionForPattern = (pattern: string): { action: GestureActionType; label: string; icon: string } => {
    switch (pattern) {
      case 'L':
        return { action: 'back', label: 'Go Back', icon: '←' };
      case 'R':
        return { action: 'forward', label: 'Go Forward', icon: '→' };
      case 'U':
        return { action: 'newTab', label: 'New Tab', icon: '↑' };
      case 'D':
      case 'DR':
        return { action: 'closeTab', label: 'Close Tab', icon: '↓' };
      case 'UD':
      case 'DU':
        return { action: 'reload', label: 'Reload Page', icon: '↻' };
      case 'LU':
      case 'UL':
        return { action: 'reopenTab', label: 'Reopen Tab', icon: '⤶' };
      default:
        return { action: 'none', label: '', icon: '' };
    }
  };

  const dispatchAction = useCallback(
    (action: GestureActionType) => {
      switch (action) {
        case 'back':
          onGoBack();
          break;
        case 'forward':
          onGoForward();
          break;
        case 'reload':
          onReload();
          break;
        case 'newTab':
          onNewTab();
          break;
        case 'closeTab':
          onCloseTab();
          break;
        case 'reopenTab':
          onReopenClosedTab();
          break;
        default:
          break;
      }
    },
    [onGoBack, onGoForward, onReload, onNewTab, onCloseTab, onReopenClosedTab]
  );

  useEffect(() => {
    if (!enabled) return;

    const onMouseDown = (e: MouseEvent) => {
      // Track rocker gesture states
      if (e.button === 0) gestureRef.current.leftHeld = true;
      if (e.button === 2) gestureRef.current.rightHeld = true;

      // Rocker gestures: Right held + Left click -> Back
      if (gestureRef.current.rightHeld && e.button === 0) {
        e.preventDefault();
        e.stopPropagation();
        gestureRef.current.suppressContextMenu = true;
        setHudState({ visible: true, action: 'back', label: 'Go Back', icon: '←' });
        setTimeout(() => setHudState((h) => ({ ...h, visible: false })), 400);
        dispatchAction('back');
        return;
      }

      // Rocker gestures: Left held + Right click -> Forward
      if (gestureRef.current.leftHeld && e.button === 2) {
        e.preventDefault();
        e.stopPropagation();
        gestureRef.current.suppressContextMenu = true;
        setHudState({ visible: true, action: 'forward', label: 'Go Forward', icon: '→' });
        setTimeout(() => setHudState((h) => ({ ...h, visible: false })), 400);
        dispatchAction('forward');
        return;
      }

      // Mouse auxiliary buttons: 3 (Back) and 4 (Forward)
      if (e.button === 3) {
        e.preventDefault();
        dispatchAction('back');
        return;
      }
      if (e.button === 4) {
        e.preventDefault();
        dispatchAction('forward');
        return;
      }

      // Right mouse button starts gesture recognizer
      if (e.button === 2) {
        gestureRef.current.isHeld = true;
        gestureRef.current.startX = e.clientX;
        gestureRef.current.startY = e.clientY;
        gestureRef.current.lastX = e.clientX;
        gestureRef.current.lastY = e.clientY;
        gestureRef.current.pattern = '';
        gestureRef.current.moved = false;
        gestureRef.current.button = 2;
      }
    };

    const onMouseMove = (e: MouseEvent) => {
      const g = gestureRef.current;
      if (!g.isHeld || g.button !== 2) return;

      const dx = e.clientX - g.lastX;
      const dy = e.clientY - g.lastY;
      const totalDx = e.clientX - g.startX;
      const totalDy = e.clientY - g.startY;

      if (Math.max(Math.abs(totalDx), Math.abs(totalDy)) >= STROKE_PX) {
        g.moved = true;
        g.suppressContextMenu = true;

        const direction =
          Math.abs(dx) >= Math.abs(dy)
            ? dx > 0
              ? 'R'
              : 'L'
            : dy > 0
            ? 'D'
            : 'U';

        if (g.pattern[g.pattern.length - 1] !== direction && g.pattern.length < 3) {
          g.pattern += direction;
        }

        g.lastX = e.clientX;
        g.lastY = e.clientY;

        const { action, label, icon } = getActionForPattern(g.pattern);
        if (action !== 'none') {
          setHudState({ visible: true, action, label, icon });
        }
      }
    };

    const onMouseUp = (e: MouseEvent) => {
      if (e.button === 0) gestureRef.current.leftHeld = false;
      if (e.button === 2) gestureRef.current.rightHeld = false;

      const g = gestureRef.current;
      if (e.button === 2 && g.isHeld) {
        g.isHeld = false;
        if (g.moved) {
          e.preventDefault();
          e.stopPropagation();
          const { action } = getActionForPattern(g.pattern);
          if (action !== 'none') {
            dispatchAction(action);
          }
        }
        setTimeout(() => {
          setHudState((h) => ({ ...h, visible: false }));
        }, 150);
      }
    };

    const onContextMenu = (e: MouseEvent) => {
      if (gestureRef.current.suppressContextMenu) {
        e.preventDefault();
        e.stopPropagation();
        gestureRef.current.suppressContextMenu = false;
      }
    };

    window.addEventListener('mousedown', onMouseDown, true);
    window.addEventListener('mousemove', onMouseMove, true);
    window.addEventListener('mouseup', onMouseUp, true);
    window.addEventListener('contextmenu', onContextMenu, true);

    return () => {
      window.removeEventListener('mousedown', onMouseDown, true);
      window.removeEventListener('mousemove', onMouseMove, true);
      window.removeEventListener('mouseup', onMouseUp, true);
      window.removeEventListener('contextmenu', onContextMenu, true);
    };
  }, [enabled, dispatchAction]);

  return { hudState };
}
