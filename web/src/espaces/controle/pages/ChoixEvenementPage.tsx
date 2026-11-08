import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useSession } from '@/commun/auth';
import { CLE_LISTE_CONTROLE } from '@/commun/session';
import { formatDateCourte } from '@/commun/format';
import { ApiError, messageErreur } from '@/commun/http';
import { Affiche, Avis, Chargement, EtatVide, Icone, LienRetour } from '@/ui';
import { evenementsAffectes, evenementsDeLOrganisateur, type EvenementControle } from '../api';
import { CadreControle } from './CadreControle';
import s from './controle.module.css';

/**
 * Dernière liste reçue, gardée sur l'appareil : le jour J, le scanner doit se rouvrir sans
 * réseau (la route est authentifiée, donc jamais mise en cache par le service worker).
 * Données publiques seulement (titre, date, lieu), propres au compte qui les a reçues.
 */
const CLE_LISTE = CLE_LISTE_CONTROLE;

function listeGardee(compte: string): EvenementControle[] | null {
  try {
    const brut = localStorage.getItem(CLE_LISTE);
    const garde = brut ? (JSON.parse(brut) as { compte?: string; evenements?: EvenementControle[] }) : null;
    return garde?.compte === compte && Array.isArray(garde.evenements) ? garde.evenements : null;
  } catch {
    return null;
  }
}

function garderListe(compte: string, evenements: EvenementControle[]) {
  try {
    localStorage.setItem(CLE_LISTE, JSON.stringify({ compte, evenements }));
  } catch {
    // Stockage indisponible : la liste reste affichée, seule la réouverture hors ligne est perdue.
  }
}

/**
 * Choix de l'événement à contrôler : pour un agent, ceux auxquels l'organisateur l'a
 * affecté ; pour un organisateur, ses événements publiés. Sans réseau : la dernière liste.
 */
export function ChoixEvenementPage() {
  const { session } = useSession();
  const utilisateur = session.etat === 'connecte' ? session.utilisateur : null;
  const organisateur = utilisateur?.role === 'ORGANISATEUR';
  const compte = utilisateur?.id ?? '';
  const [evenements, setEvenements] = useState<EvenementControle[] | null>(null);
  const [horsLigne, setHorsLigne] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    if (!compte) return;
    let annule = false;
    setErreur(null);
    const chargement = organisateur
      ? evenementsDeLOrganisateur().then((liste) => liste.filter((e) => e.status === 'PUBLIE'))
      : evenementsAffectes();
    chargement
      .then((liste) => {
        if (annule) return;
        setEvenements(liste);
        setHorsLigne(false);
        garderListe(compte, liste);
      })
      .catch((e: unknown) => {
        if (annule) return;
        const gardee = !(e instanceof ApiError) ? listeGardee(compte) : null;
        if (gardee) {
          setEvenements(gardee);
          setHorsLigne(true);
        } else setErreur(messageErreur(e));
      });
    return () => {
      annule = true;
    };
  }, [compte, organisateur]);

  return (
    <CadreControle>
      <div className={s.pile}>
        {organisateur && <LienRetour to="/organisateur">Retour à mon espace</LienRetour>}
        <div>
          <h1 className={s.titrePage}>Événement à contrôler</h1>
          <p className={s.sousTitre}>Connecté : {utilisateur?.nomComplet}</p>
          {!organisateur && (
            <p className={s.texte} style={{ marginTop: 8 }}>
              <Link to="/scanner/compte">Mon compte et mot de passe</Link>
            </p>
          )}
        </div>
        {horsLigne && (
          <Avis ton="attention" icone="horsLigne">
            <p>Sans réseau : dernière liste reçue sur cet appareil.</p>
          </Avis>
        )}
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
          </Avis>
        )}
        {!erreur && evenements === null && <Chargement />}
        {evenements?.length === 0 && (
          <EtatVide
            icone="calendrier"
            titre="Aucun événement à contrôler"
            texte={
              organisateur
                ? "Publiez un événement pour pouvoir en contrôler l'entrée."
                : "L'organisateur ne vous a affecté à aucun événement à venir. Demandez-lui de le faire depuis son espace."
            }
          />
        )}
        {evenements && evenements.length > 0 && (
          <ul className={s.liste} aria-label="Événements">
            {evenements.map((e) => (
              <li key={e.id}>
                <Link to={`/scanner/evenements/${e.id}`} className={s.ligne}>
                  <span style={{ width: 52, height: 52, borderRadius: 14, overflow: 'hidden', flex: 'none' }}>
                    <Affiche id={e.id} />
                  </span>
                  <span className={s.ligneCorps}>
                    <span className={s.ligneTitre} style={{ display: 'block' }}>
                      {e.titre}
                    </span>
                    <span className={s.ligneDetail}>
                      {formatDateCourte(e.debuteLe)} · {e.venueNom}
                    </span>
                  </span>
                  <Icone nom="suivant" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </CadreControle>
  );
}
