import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { formatDateCourte } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import { Affiche, Avis, Bouton, BoutonLien, Chargement, EtatVide, Pastille } from '@/ui';
import { mesEvenements, type EvenementOrganisateur } from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import { STATUTS } from './statut';
import s from './organisateur.module.css';

export function MesEvenementsPage() {
  const [evenements, setEvenements] = useState<EvenementOrganisateur[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [essai, setEssai] = useState(0);

  useEffect(() => {
    let annule = false;
    setErreur(null);
    mesEvenements()
      .then((liste) => {
        if (annule) return;
        // À venir d'abord, puis du plus proche au plus lointain.
        setEvenements([...liste].sort((a, b) => a.debuteLe.localeCompare(b.debuteLe)));
      })
      .catch((e: unknown) => !annule && setErreur(messageErreur(e)));
    return () => {
      annule = true;
    };
  }, [essai]);

  return (
    <CadreOrganisateur>
      <div className={s.pile}>
        <div className={s.entete}>
          <div>
            <h1 className={s.titrePage}>Mes événements</h1>
            <p className={s.sousTitre}>Espace organisateur</p>
          </div>
          <BoutonLien to="/organisateur/evenements/nouveau">Nouvel événement</BoutonLien>
        </div>

        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
            <div>
              <Bouton variante="secondaire" onClick={() => setEssai((n) => n + 1)}>
                Réessayer
              </Bouton>
            </div>
          </Avis>
        )}

        {!erreur && evenements === null && <Chargement libelle="Chargement de vos événements…" />}

        {evenements?.length === 0 && (
          <EtatVide
            icone="calendrier"
            titre="Aucun événement pour l'instant"
            texte="Créez votre premier événement : il reste en brouillon, invisible du public, jusqu'à sa publication."
            action={<BoutonLien to="/organisateur/evenements/nouveau">Créer un événement</BoutonLien>}
          />
        )}

        {evenements && evenements.length > 0 && (
          <ul className={s.liste} aria-label="Mes événements">
            {evenements.map((e) => {
              const statut = STATUTS[e.status];
              return (
                <li key={e.id}>
                  <Link to={`/organisateur/evenements/${e.id}`} className={s.ligne}>
                    <span className={s.vignette}>
                      <Affiche id={e.id} />
                    </span>
                    <span className={s.ligneCorps}>
                      <span className={s.ligneTitre}>{e.titre}</span>
                      <span className={s.ligneDetail}>
                        {formatDateCourte(e.debuteLe)} · {e.venueNom}
                      </span>
                    </span>
                    <Pastille ton={statut.ton} icone={statut.icone}>
                      {statut.libelle}
                    </Pastille>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </CadreOrganisateur>
  );
}
