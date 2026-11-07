import { lazy, Suspense, useEffect, type ReactNode } from 'react';
import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ExigerRole, FournisseurSession } from '@/commun/FournisseurSession';
import { PageIntrouvable } from '@/commun/PageIntrouvable';
import {
  AidePage,
  BilletRecuPage,
  CommandePage,
  DecouvrirPage,
  EvenementPage,
  MesBilletsPage,
  PaiementPage,
  TransfertBilletPage,
} from '@/espaces/acheteur';
import {
  ComptePage,
  ConnexionPage,
  CreditPage,
  EquipePage,
  EvenementOrganisateurPage,
  InscriptionPage,
  MesEvenementsPage,
  ModifierEvenementPage,
  NouvelEvenementPage,
  VerificationPage,
} from '@/espaces/organisateur';
import { Cadre, Chargement } from '@/ui';

/*
 * Contrôle d'entrée chargé à la demande : jsQR et la vérification Ed25519 ne servent qu'aux
 * agents, l'acheteur ne les télécharge pas. Le service worker précache quand même ce
 * morceau (globPatterns) : le scanner reste disponible hors ligne après une visite.
 */
const espaceControle = () => import('@/espaces/controle');
const ChoixEvenementPage = lazy(() => espaceControle().then((m) => ({ default: m.ChoixEvenementPage })));
const CompteControlePage = lazy(() => espaceControle().then((m) => ({ default: m.CompteControlePage })));
const ConnexionControlePage = lazy(() => espaceControle().then((m) => ({ default: m.ConnexionControlePage })));
const ControlePage = lazy(() => espaceControle().then((m) => ({ default: m.ControlePage })));

/* Administration chargée à la demande, comme le contrôle : aucun acheteur n'en a besoin. */
const espaceAdmin = () => import('@/espaces/admin');
const CompteAdminPage = lazy(() => espaceAdmin().then((m) => ({ default: m.CompteAdminPage })));
const ConnexionAdminPage = lazy(() => espaceAdmin().then((m) => ({ default: m.ConnexionAdminPage })));
const RemboursementsPage = lazy(() => espaceAdmin().then((m) => ({ default: m.RemboursementsPage })));
const ReversementsPage = lazy(() => espaceAdmin().then((m) => ({ default: m.ReversementsPage })));
const VerificationsPage = lazy(() => espaceAdmin().then((m) => ({ default: m.VerificationsPage })));

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

/** Administration : rôle ADMIN (compte créé hors API), écran chargé à la demande. */
const admin = (page: ReactNode) => (
  <ExigerRole roles={['ADMIN']} connexion="/admin/connexion">
    <Suspense fallback={enChargement}>{page}</Suspense>
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
 * contrôle d'entrée (/scanner/...), administration (/admin : remboursements manuels).
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
        <Route path="/commandes/:id/billets/:ticketId/transfert" element={<TransfertBilletPage />} />
        <Route path="/billets" element={<MesBilletsPage />} />
        <Route path="/billets/recu/:ticketId" element={<BilletRecuPage />} />
        <Route path="/aide" element={<AidePage />} />

        <Route path="/organisateur/connexion" element={<ConnexionPage />} />
        <Route path="/organisateur/inscription" element={<InscriptionPage />} />
        <Route path="/organisateur" element={organisateur(<MesEvenementsPage />)} />
        <Route path="/organisateur/evenements/nouveau" element={organisateur(<NouvelEvenementPage />)} />
        <Route path="/organisateur/evenements/:id" element={organisateur(<EvenementOrganisateurPage />)} />
        <Route path="/organisateur/evenements/:id/modifier" element={organisateur(<ModifierEvenementPage />)} />
        <Route path="/organisateur/equipe" element={organisateur(<EquipePage />)} />
        <Route path="/organisateur/compte" element={organisateur(<ComptePage />)} />
        <Route path="/organisateur/credit" element={organisateur(<CreditPage />)} />
        <Route path="/organisateur/verification" element={organisateur(<VerificationPage />)} />

        <Route
          path="/scanner/connexion"
          element={
            <Suspense fallback={enChargement}>
              <ConnexionControlePage />
            </Suspense>
          }
        />
        <Route path="/scanner" element={controle(<ChoixEvenementPage />)} />
        <Route path="/scanner/compte" element={controle(<CompteControlePage />)} />
        <Route path="/scanner/evenements/:id" element={controle(<ControlePage />)} />

        <Route
          path="/admin/connexion"
          element={
            <Suspense fallback={enChargement}>
              <ConnexionAdminPage />
            </Suspense>
          }
        />
        <Route path="/admin" element={admin(<RemboursementsPage />)} />
        <Route path="/admin/reversements" element={admin(<ReversementsPage />)} />
        <Route path="/admin/verifications" element={admin(<VerificationsPage />)} />
        <Route path="/admin/compte" element={admin(<CompteAdminPage />)} />

        <Route path="*" element={<PageIntrouvable />} />
      </Routes>
    </FournisseurSession>
  );
}

export default function App() {
  return (
    // React Router 7 : transitions et chemins relatifs des routes « * » sont le comportement par défaut.
    <BrowserRouter>
      <Routage />
    </BrowserRouter>
  );
}
