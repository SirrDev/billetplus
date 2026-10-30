import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
// Polices embarquées dans le bundle : elles s'affichent aussi hors ligne (PWA).
import '@fontsource-variable/inter';
import '@fontsource-variable/space-grotesk';
import './styles/global.css';
import App from './App';

const racine = document.getElementById('root');
if (!racine) throw new Error('Élément #root absent de index.html');

createRoot(racine).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
