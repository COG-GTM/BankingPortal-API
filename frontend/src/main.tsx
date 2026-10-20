import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './index.css';

async function enableMocking() {
  if (import.meta.env.VITE_API_MODE === 'live') return;
  const { startMockWorker } = await import('./mocks/browser');
  await startMockWorker();
}

void enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
