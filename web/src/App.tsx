import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { CommandePage, DecouvrirPage, EvenementPage, MesBilletsPage, PaiementPage } from '@/espaces/acheteur';

/** Chaque changement de page repart du haut, comme une navigation classique. */
function RemonterEnHaut() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  return null;
}

/**
 * Routes de la PWA (ADR 0004). Espace acheteur pour l'instant ; l'organisateur
 * (/organisateur/...) et le contrôle d'entrée (/scanner/...) suivront.
 * /commandes/:id est l'adresse de retour de la passerelle (UNITECHPAY_URL_RETOUR_SUCCES).
 */
export function Routage() {
  return (
    <>
      <RemonterEnHaut />
      <Routes>
        <Route path="/" element={<DecouvrirPage />} />
        <Route path="/evenements/:id" element={<EvenementPage />} />
        <Route path="/evenements/:id/paiement" element={<PaiementPage />} />
        <Route path="/commandes/:id" element={<CommandePage />} />
        <Route path="/billets" element={<MesBilletsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}

export default function App() {
  return (
    // Comportements de React Router 7 activés dès maintenant : la montée de version n'y changera rien.
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routage />
    </BrowserRouter>
  );
}
