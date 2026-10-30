import { lazy, Suspense, useEffect, type ReactNode } from 'react';
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
import { Cadre, Chargement } from '@/ui';

/*
 * Contrôle d'entrée chargé à la demande : jsQR et la vérification Ed25519 ne servent qu'aux
 * agents, l'acheteur ne les télécharge pas. Le service worker précache quand même ce
 * morceau (globPatterns) : le scanner reste disponible hors ligne après une visite.
 */
const espaceControle = () => import('@/espaces/controle');
const ChoixEvenementPage = lazy(() => espaceControle().then((m) => ({ default: m.ChoixEvenementPage })));
const ConnexionControlePage = lazy(() => espaceControle().then((m) => ({ default: m.ConnexionControlePage })));
const ControlePage = lazy(() => espaceControle().then((m) => ({ default: m.ControlePage })));

const enChargement = (
  <Cadre sansNav>
    <Chargement />
  </Cadre>
);

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

/** Contrôle d'entrée : agent ou organisateur (ScanController : hasAnyRole AGENT, ORGANISATEUR). */
const controle = (page: ReactNode) => (
  <ExigerRole roles={['AGENT', 'ORGANISATEUR']} connexion="/scanner/connexion">
    <Suspense fallback={enChargement}>{page}</Suspense>
  </ExigerRole>
);

/**
 * Routes de la PWA (ADR 0004) : acheteur (sans compte), organisateur (/organisateur/...),
 * contrôle d'entrée (/scanner/...).
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

        <Route
          path="/scanner/connexion"
          element={
            <Suspense fallback={enChargement}>
              <ConnexionControlePage />
            </Suspense>
          }
        />
        <Route path="/scanner" element={controle(<ChoixEvenementPage />)} />
        <Route path="/scanner/evenements/:id" element={controle(<ControlePage />)} />

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
