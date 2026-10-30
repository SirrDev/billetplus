import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useSession } from '@/commun/auth';
import { formatDateCourte } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import { Affiche, Avis, ChampRecherche, Chargement, EtatVide, Icone } from '@/ui';
import { evenementsDeLOrganisateur, evenementsPublics, type EvenementControle } from '../api';
import { CadreControle } from './CadreControle';
import s from './controle.module.css';

/**
 * Choix de l'événement à contrôler. Le backend n'expose pas les affectations d'un agent :
 * un agent cherche parmi les événements publiés, et le serveur refuse le scan (403) s'il
 * n'y est pas affecté ; un organisateur voit directement ses événements publiés.
 */
export function ChoixEvenementPage() {
  const { session } = useSession();
  const organisateur = session.etat === 'connecte' && session.utilisateur.role === 'ORGANISATEUR';
  const nom = session.etat === 'connecte' ? session.utilisateur.nomComplet : '';
  const [saisie, setSaisie] = useState('');
  const [evenements, setEvenements] = useState<EvenementControle[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    let annule = false;
    const minuterie = window.setTimeout(() => {
      setErreur(null);
      const chargement = organisateur
        ? evenementsDeLOrganisateur().then((liste) => liste.filter((e) => e.status === 'PUBLIE'))
        : evenementsPublics(saisie);
      chargement
        .then((liste) => !annule && setEvenements(liste))
        .catch((e: unknown) => !annule && setErreur(messageErreur(e)));
    }, 300);
    return () => {
      annule = true;
      window.clearTimeout(minuterie);
    };
  }, [saisie, organisateur]);

  return (
    <CadreControle>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Connecté : {nom}</p>
          <h1 className={s.titrePage}>Événement à contrôler</h1>
        </div>
        {!organisateur && (
          <>
            <ChampRecherche valeur={saisie} onChange={setSaisie} libelle="Rechercher un événement" placeholder="Titre de l'événement" />
            <p className={s.texte}>Choisissez l'événement auquel l'organisateur vous a affecté.</p>
          </>
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
            texte={organisateur ? "Publiez un événement pour pouvoir en contrôler l'entrée." : 'Aucun événement publié ne correspond.'}
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
