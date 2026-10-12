import { ThemeMode } from '../types/browser';

/**
 * Shared slash-command registry.
 *
 * Single source of truth for the `/`-prefixed commands surfaced in both the
 * island's address bar and the Quick Switcher (Ctrl+K). Keeping one registry
 * guarantees that a command listed in one surface behaves identically in the
 * other — typing `/history` in the address bar must do exactly what pressing
 * Enter on `/history` in the Quick Switcher does.
 */

export type SlashCommandIcon =
  | 'plus'
  | 'history'
  | 'settings'
  | 'bookmark'
  | 'key'
  | 'download'
  | 'keyboard'
  | 'activity'
  | 'panel-left'
  | 'trash'
  | 'moon'
  | 'sun'
  | 'sunrise'
  | 'sparkles';

export interface SlashCommandContext {
  onNavigate: (url: string) => void;
  onNewTab: (url?: string) => void;
  onToggleVerticalTabs?: () => void;
  onClearHistory?: () => void;
  onSetTheme?: (theme: ThemeMode) => void;
}

export interface SlashCommand {
  cmd: string;
  title: string;
  desc: string;
  icon: SlashCommandIcon;
  /** When set, the command simply opens this surface URL in the active tab. */
  url?: string;
  run: (ctx: SlashCommandContext) => void;
}

export const SLASH_COMMANDS: SlashCommand[] = [
  {
    cmd: '/newtab',
    title: 'New Tab',
    desc: 'Open a blank Blanc start page',
    icon: 'plus',
    url: 'blanc://newtab',
    run: (ctx) => ctx.onNewTab('blanc://newtab'),
  },
  {
    cmd: '/history',
    title: 'Browsing History',
    desc: 'Open the history viewer (blanc://history)',
    icon: 'history',
    url: 'blanc://history',
    run: (ctx) => ctx.onNavigate('blanc://history'),
  },
  {
    cmd: '/bookmarks',
    title: 'Bookmarks',
    desc: 'Open saved bookmarks (blanc://bookmarks)',
    icon: 'bookmark',
    url: 'blanc://bookmarks',
    run: (ctx) => ctx.onNavigate('blanc://bookmarks'),
  },
  {
    cmd: '/passwords',
    title: 'Saved Passwords',
    desc: 'View and manage saved passwords (blanc://passwords)',
    icon: 'key',
    url: 'blanc://passwords',
    run: (ctx) => ctx.onNavigate('blanc://passwords'),
  },
  {
    cmd: '/downloads',
    title: 'Downloads',
    desc: 'Open the downloads manager (blanc://downloads)',
    icon: 'download',
    url: 'blanc://downloads',
    run: (ctx) => ctx.onNavigate('blanc://downloads'),
  },
  {
    cmd: '/settings',
    title: 'Settings',
    desc: 'Open browser preferences (blanc://settings)',
    icon: 'settings',
    url: 'blanc://settings',
    run: (ctx) => ctx.onNavigate('blanc://settings'),
  },
  {
    cmd: '/shortcuts',
    title: 'Shortcuts',
    desc: 'Open the keyboard shortcuts guide (blanc://shortcuts)',
    icon: 'keyboard',
    url: 'blanc://shortcuts',
    run: (ctx) => ctx.onNavigate('blanc://shortcuts'),
  },
  {
    cmd: '/diagnostics',
    title: 'Diagnostics',
    desc: 'Inspect engine state and audit reports (blanc://diagnostics)',
    icon: 'activity',
    url: 'blanc://diagnostics',
    run: (ctx) => ctx.onNavigate('blanc://diagnostics'),
  },
  {
    cmd: '/vertical',
    title: 'Vertical Tabs',
    desc: 'Toggle the floating vertical tabs sidebar (Ctrl+Shift+V)',
    icon: 'panel-left',
    run: (ctx) => ctx.onToggleVerticalTabs?.(),
  },
  {
    cmd: '/clear',
    title: 'Clear History',
    desc: 'Clear all local browsing history records',
    icon: 'trash',
    run: (ctx) => ctx.onClearHistory?.(),
  },
  {
    cmd: '/dark',
    title: 'Theme: Dark',
    desc: 'Switch to the Obsidian dark palette',
    icon: 'moon',
    run: (ctx) => ctx.onSetTheme?.('dark'),
  },
  {
    cmd: '/light',
    title: 'Theme: Light',
    desc: 'Switch to the Porcelain light palette',
    icon: 'sun',
    run: (ctx) => ctx.onSetTheme?.('light'),
  },
  {
    cmd: '/sunrise',
    title: 'Theme: Sunrise',
    desc: 'Switch to the Warm Gold & Ivory palette',
    icon: 'sunrise',
    run: (ctx) => ctx.onSetTheme?.('sunrise'),
  },
  {
    cmd: '/patron',
    title: 'Theme: Patron',
    desc: 'Switch to Deep Espresso & Gilded Brass',
    icon: 'sparkles',
    run: (ctx) => ctx.onSetTheme?.('patron'),
  },
];

/**
 * Returns the commands matching a user-typed query.
 *
 * Only `/`-prefixed input is treated as a command search; anything else returns
 * an empty list so the caller falls back to normal address/search behavior.
 */
export function matchSlashCommands(query: string): SlashCommand[] {
  const q = query.trim().toLowerCase();
  if (!q.startsWith('/')) return [];
  return SLASH_COMMANDS.filter((c) => c.cmd.startsWith(q));
}