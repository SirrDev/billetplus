import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { libelleCategorie } from '@/commun/categories';
import { formatDateLongue, formatFcfa, formatHeure, majusculeInitiale } from '@/commun/format';
import {
  Affiche,
  Avis,
  BarreAction,
  Bouton,
  BoutonLien,
  Cadre,
  Chargement,
  EtatVide,
  Icone,
  LigneCategorie,
  Panneau,
} from '@/ui';
import { lienCarte, QUANTITE_MAX_PAR_CATEGORIE } from '../api';
import { apercuTotal, nombreDePlaces, useEvenement, type EtatNavigationPaiement, type Quantites } from './useEvenement';
import s from './pages.module.css';

export function EvenementPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const chargement = useEvenement(id);
  const [quantites, setQuantites] = useState<Quantites>({});

  if (chargement.etat === 'chargement') {
    return (
      <Cadre>
        <Chargement libelle="Chargement de l'événement…" />
      </Cadre>
    );
  }

  if (chargement.etat === 'introuvable') {
    return (
      <Cadre>
        <EtatVide
          niveau={1}
          icone="recherche"
          titre="Événement introuvable"
          texte="Cet événement n'existe pas ou n'est plus en vente."
          action={<BoutonLien to="/">Voir les événements</BoutonLien>}
        />
      </Cadre>
    );
  }

  if (chargement.etat === 'erreur') {
    return (
      <Cadre>
        <Avis ton="danger">
          <p>{chargement.message}</p>
          <div>
            <Bouton variante="secondaire" onClick={chargement.reessayer}>
              Réessayer
            </Bouton>
          </div>
        </Avis>
      </Cadre>
    );
  }

  const { evenement, categories, lieu } = chargement;
  const annule = evenement.status === 'ANNULE';
  const termine = evenement.status === 'TERMINE';
  const places = nombreDePlaces(quantites);
  const total = apercuTotal(categories, quantites);
  const toutEpuise = categories.length > 0 && categories.every((c) => !c.disponible);

  const continuer = () => {
    const etat: EtatNavigationPaiement = { quantites };
    navigate(`/evenements/${evenement.id}/paiement`, { state: etat });
  };

  return (
    <Cadre fondGris>
      <div className={s.pile}>
        <div className={s.banniere}>
          <Affiche id={evenement.id} />
          <div className={s.banniereTexte}>
            <p className={s.banniereCategorie}>{libelleCategorie(evenement.categorie)}</p>
            <h1 className={s.banniereTitre}>{evenement.titre}</h1>
          </div>
        </div>

        {annule && (
          <Avis ton="danger" icone="croix">
            <p>
              <strong>Événement annulé.</strong> Les billets ne sont plus valables. Si vous en aviez acheté, votre
              paiement est remboursé par l'équipe Billet+, sur le numéro qui a payé. Votre commande, dans « Mes billets »,
              l'indique aussi.
            </p>
          </Avis>
        )}
        {termine && (
          <Avis ton="info">
            <p>Cet événement est terminé : la billetterie est fermée.</p>
          </Avis>
        )}

        <div className={s.colonnes}>
          <Panneau>
            <ul className={s.infos}>
              <li className={s.info}>
                <span className={s.infoIcone}>
                  <Icone nom="calendrier" />
                </span>
                <div>
                  <p className={s.infoTitre}>{majusculeInitiale(formatDateLongue(evenement.debuteLe))}</p>
                  <p className={s.infoDetail}>
                    {formatHeure(evenement.debuteLe)}
                    {evenement.termineLe ? ` – ${formatHeure(evenement.termineLe)}` : ''}, heure de Dakar
                  </p>
                </div>
              </li>
              <li className={s.info}>
                <span className={s.infoIcone}>
                  <Icone nom="lieu" />
                </span>
                <div>
                  <p className={s.infoTitre}>{evenement.venueNom}</p>
                  {lieu && <p className={s.infoDetail}>{lieu.adresse}</p>}
                  {lieu && (
                    <a className={s.lienAide} href={lienCarte(lieu)} target="_blank" rel="noopener noreferrer">
                      Voir sur la carte
                    </a>
                  )}
                </div>
              </li>
            </ul>
            {evenement.description && <p className={s.description}>{evenement.description}</p>}
          </Panneau>

          <Panneau titre="Vos billets" id="billets">
            {(annule || termine) && <p className={s.infoDetail}>La billetterie de cet événement est fermée.</p>}
            {!annule && !termine && categories.length === 0 && (
              <p className={s.infoDetail}>Les billets ne sont pas encore en vente.</p>
            )}
            {toutEpuise && <Avis ton="attention">Cet événement affiche complet.</Avis>}
            <div>
              {categories.map((c) => (
                <LigneCategorie
                  key={c.id}
                  nom={c.nom}
                  prixFcfa={c.prixFcfa}
                  disponible={c.disponible}
                  quantite={quantites[c.id] ?? 0}
                  max={QUANTITE_MAX_PAR_CATEGORIE}
                  onChange={(n) => setQuantites((q) => ({ ...q, [c.id]: n }))}
                />
              ))}
            </div>
            {categories.length > 0 && (
              <p className={s.note}>
                Places gardées pendant le paiement. Au plus {QUANTITE_MAX_PAR_CATEGORIE} billets par catégorie.
              </p>
            )}
          </Panneau>
        </div>

        {categories.length > 0 && !toutEpuise && (
          <BarreAction
            libelle={places === 0 ? 'Choisissez vos billets' : `${places} ${places > 1 ? 'billets' : 'billet'} · total`}
            montant={places === 0 ? undefined : formatFcfa(total)}
          >
            <Bouton grand disabled={places === 0} onClick={continuer}>
              Continuer vers le paiement
            </Bouton>
          </BarreAction>
        )}
      </div>
    </Cadre>
  );
}
