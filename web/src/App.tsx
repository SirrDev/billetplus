import { useEffect, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { ExigerRole, FournisseurSession } from '@/commun/FournisseurSession';
import { CommandePage, DecouvrirPage, EvenementPage, MesBilletsPage, PaiementPage } from '@/espaces/acheteur';
import {
  ConnexionPage,
  EquipePage,
  EvenementOrganisateurPage,
  InscriptionPage,
  MesEvenementsPage,
  NouvelEvenementPage,
} from '@/espaces/organisateur';

/** Chaque changement de page repart du haut, comme une navigation classique. */
function RemonterEnHaut() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  return null;
}

const organisateur = (page: ReactNode) => (
  <ExigerRole roles={['ORGANISATEUR']} connexion="/organisateur/connexion">
    {page}
  </ExigerRole>
);

/**
 * Routes de la PWA (ADR 0004) : acheteur (sans compte), organisateur (/organisateur/...).
 * Le contrôle d'entrée (/scanner/...) suivra.
 * /commandes/:id est l'adresse de retour de la passerelle (UNITECHPAY_URL_RETOUR_SUCCES).
 */
export function Routage() {
  return (
    <FournisseurSession>
      <RemonterEnHaut />
      <Routes>
        <Route path="/" element={<DecouvrirPage />} />
        <Route path="/evenements/:id" element={<EvenementPage />} />
        <Route path="/evenements/:id/paiement" element={<PaiementPage />} />
        <Route path="/commandes/:id" element={<CommandePage />} />
        <Route path="/billets" element={<MesBilletsPage />} />

        <Route path="/organisateur/connexion" element={<ConnexionPage />} />
        <Route path="/organisateur/inscription" element={<InscriptionPage />} />
        <Route path="/organisateur" element={organisateur(<MesEvenementsPage />)} />
        <Route path="/organisateur/evenements/nouveau" element={organisateur(<NouvelEvenementPage />)} />
        <Route path="/organisateur/evenements/:id" element={organisateur(<EvenementOrganisateurPage />)} />
        <Route path="/organisateur/equipe" element={organisateur(<EquipePage />)} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </FournisseurSession>
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
