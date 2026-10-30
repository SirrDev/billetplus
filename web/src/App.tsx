import { lazy, Suspense } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import {
  ChoisirBilletsPage,
  CommandePage,
  ComptePage,
  DecouvrirPage,
  EvenementPage,
  MesBilletsPage,
} from './routes/acheteur';
import { AuthProvider } from './shared/auth/AuthContext';
import { ConnexionPage } from './shared/auth/ConnexionPage';
import { RequireRole } from './shared/auth/RequireRole';

/*
 * Espaces organisateur et contrôle chargés à la demande : un acheteur, souvent sur
 * réseau mobile, ne télécharge que son parcours (le scanner embarque jsQR et
 * @noble/ed25519, inutiles pour acheter).
 */
const organisateur = () => import('./routes/organisateur');
const controle = () => import('./routes/scanner');
const InscriptionPage = lazy(() => organisateur().then((m) => ({ default: m.InscriptionPage })));
const MesEvenementsPage = lazy(() => organisateur().then((m) => ({ default: m.MesEvenementsPage })));
const NouvelEvenementPage = lazy(() => organisateur().then((m) => ({ default: m.NouvelEvenementPage })));
const EvenementOrganisateurPage = lazy(() => organisateur().then((m) => ({ default: m.EvenementOrganisateurPage })));
const AgentsPage = lazy(() => organisateur().then((m) => ({ default: m.AgentsPage })));
const ChoixEvenementPage = lazy(() => controle().then((m) => ({ default: m.ChoixEvenementPage })));
const ControlePage = lazy(() => controle().then((m) => ({ default: m.ControlePage })));

// Galerie du design system : en développement UNIQUEMENT. L'import dynamique est
// derrière import.meta.env.DEV, que Vite remplace par false au build de production :
// la galerie (code et CSS) n'entre donc pas dans le bundle livré.
const ComponentGallery = import.meta.env.DEV
  ? lazy(() => import('@/design-system/gallery/ComponentGallery').then((m) => ({ default: m.ComponentGallery })))
  : null;

/** Trois espaces dans une seule PWA, séparés par leurs routes (ADR 0004). */
export default function App() {
  const espaceOrganisateur = (page: JSX.Element) => (
    <RequireRole roles={['ORGANISATEUR']} connexion="/organisateur/connexion">
      {page}
    </RequireRole>
  );
  const espaceControle = (page: JSX.Element) => (
    <RequireRole roles={['AGENT', 'ORGANISATEUR']} connexion="/scanner/connexion">
      {page}
    </RequireRole>
  );

  return (
    <AuthProvider>
      <Suspense fallback={null}>
        <Routes>
          {/* Acheteur : sans compte (ADR 0006) */}
          <Route path="/" element={<DecouvrirPage />} />
          <Route path="/evenements/:id" element={<EvenementPage />} />
          <Route path="/evenements/:id/billets" element={<ChoisirBilletsPage />} />
          <Route path="/commandes/:id" element={<CommandePage />} />
          <Route path="/billets" element={<MesBilletsPage />} />
          <Route path="/compte" element={<ComptePage />} />

          {/* Organisateur */}
          <Route
            path="/organisateur/connexion"
            element={
              <ConnexionPage
                titre="Espace organisateur"
                sousTitre="Gérez vos événements et vos ventes"
                accueil="/organisateur"
                roles={['ORGANISATEUR']}
                pied={
                  <p>
                    Pas encore de compte ? <Link to="/organisateur/inscription">Créer un compte organisateur</Link>
                  </p>
                }
              />
            }
          />
          <Route path="/organisateur/inscription" element={<InscriptionPage />} />
          <Route path="/organisateur" element={espaceOrganisateur(<MesEvenementsPage />)} />
          <Route path="/organisateur/evenements/nouveau" element={espaceOrganisateur(<NouvelEvenementPage />)} />
          <Route path="/organisateur/evenements/:id" element={espaceOrganisateur(<EvenementOrganisateurPage />)} />
          <Route path="/organisateur/agents" element={espaceOrganisateur(<AgentsPage />)} />

          {/* Contrôle d'entrée : agents, et organisateurs qui contrôlent eux-mêmes */}
          <Route
            path="/scanner/connexion"
            element={
              <ConnexionPage
                titre="Contrôle d'entrée"
                sousTitre="Connexion des agents"
                accueil="/scanner"
                roles={['AGENT', 'ORGANISATEUR']}
              />
            }
          />
          <Route path="/scanner" element={espaceControle(<ChoixEvenementPage />)} />
          <Route path="/scanner/evenements/:id" element={espaceControle(<ControlePage />)} />

          {ComponentGallery && (
            <Route
              path="/dev/composants"
              element={
                <Suspense fallback={null}>
                  <ComponentGallery />
                </Suspense>
              }
            />
          )}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </AuthProvider>
  );
}
