import { Routes, Route, Navigate } from 'react-router-dom';

// Les pages réelles arrivent avec les branches feature/web-organisateur-*,
// feature/web-search-events, feature/web-scanner-* (voir roadmap Dev A/Dev B).
// Ce squelette pose uniquement le découpage par rôle, cohérent avec
// docs/architecture/adr/0004-pwa-vs-react-native.md.

function AcheteurHome() {
  return <div>Accueil acheteur — à implémenter (feature/web-search-events)</div>;
}

function OrganisateurDashboard() {
  return <div>Dashboard organisateur — à implémenter (feature/web-organisateur-dashboard)</div>;
}

function ScannerHome() {
  return <div>Scanner — à implémenter (feature/web-scanner-camera)</div>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AcheteurHome />} />
      <Route path="/organisateur/*" element={<OrganisateurDashboard />} />
      <Route path="/scanner/*" element={<ScannerHome />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
