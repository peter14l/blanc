import React, { Component, ErrorInfo, ReactNode } from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles/globals.css';

if (typeof window !== 'undefined' && ('__TAURI_INTERNALS__' in window || '__TAURI__' in window)) {
  document.body.classList.add('is-tauri');
}

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

class RootErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[RootErrorBoundary] Caught unhandled React error:', error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div className="w-screen h-screen bg-[#0e0e0e] text-white flex flex-col items-center justify-center p-6 select-none font-ui">
          <div className="p-6 rounded-2xl bg-white/5 border border-white/10 max-w-md w-full text-center space-y-4">
            <h2 className="text-lg font-semibold text-[#d4ad66]">Something went wrong</h2>
            <p className="text-sm text-white/60">
              The browser UI encountered an unexpected error.
            </p>
            <p className="text-xs font-mono text-rose-400/80 bg-black/40 p-2.5 rounded-lg text-left overflow-auto max-h-32">
              {this.state.error?.message || 'Unknown error'}
            </p>
            <button
              onClick={() => {
                this.setState({ hasError: false, error: null });
                window.location.reload();
              }}
              className="px-4 py-2 rounded-xl bg-[#d4ad66] text-black font-semibold text-sm hover:opacity-90 transition-opacity"
            >
              Reload Blanc
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <RootErrorBoundary>
      <App />
    </RootErrorBoundary>
  </React.StrictMode>
);
