import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSession } from '@/commun/auth';
import { Avis, Bouton, Cadre } from '@/ui';
import { totalEnAttente, viderAppareil } from '../fileHorsLigne';

/**
 * Cadre du contrôle d'entrée : pas de navigation (un seul geste à faire), déconnexion
 * visible. Se déconnecter efface la mémoire du contrôle sur l'appareil (QR = jetons
 * porteurs) ; s'il reste des entrées non synchronisées, l'agent est prévenu avant.
 */
export function CadreControle({ children }: { children: ReactNode }) {
  const { deconnexion } = useSession();
  const navigate = useNavigate();
  const [aConfirmer, setAConfirmer] = useState<number | null>(null);

  const quitter = async () => {
    await viderAppareil().catch(() => undefined);
    deconnexion();
    navigate('/scanner/connexion', { replace: true });
  };

  const demanderDeconnexion = async () => {
    const restant = await totalEnAttente().catch(() => 0);
    if (restant > 0) setAConfirmer(restant);
    else await quitter();
  };

  return (
    <Cadre
      sansNav
      fondGris
      bandeauHorsLigne={false}
      accueil="/scanner"
      actions={
        <Bouton variante="secondaire" onClick={() => void demanderDeconnexion()}>
          Se déconnecter
        </Bouton>
      }
    >
      {aConfirmer !== null && (
        <div style={{ marginBottom: 16 }}>
          <Avis ton="attention">
            <p>
              <strong>{aConfirmer} entrée(s) faite(s) sans réseau ne sont pas encore envoyées.</strong> Retrouvez du
              réseau pour les synchroniser : se déconnecter maintenant les efface de cet appareil.
            </p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
              <Bouton variante="secondaire" onClick={() => setAConfirmer(null)}>
                Rester connecté
              </Bouton>
              <Bouton onClick={() => void quitter()}>Me déconnecter quand même</Bouton>
            </div>
          </Avis>
        </div>
      )}
      {children}
    </Cadre>
  );
}
