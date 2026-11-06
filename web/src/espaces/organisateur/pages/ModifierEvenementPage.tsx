import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { CATEGORIES, type CategorieEvenement } from '@/commun/categories';
import { formatDateLongue, formatHeure, heureDakarVersInstant, instantVersHeureDakar } from '@/commun/format';
import { ApiError, messageErreur } from '@/commun/http';
import { Avis, Bouton, BoutonLien, Chargement, ChampChoix, ChampTexte, EtatVide, Panneau } from '@/ui';
import {
  definirPosition,
  LIMITES,
  lireCoordonnees,
  lireEvenement,
  lireLieu,
  modifierEvenement,
  type EvenementOrganisateur,
  type Lieu,
} from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import { CartePosition } from './CartePosition';
import s from './organisateur.module.css';

interface Erreurs {
  debut?: string;
  fin?: string;
  coordonnees?: string;
}

type EtatPage =
  | { etat: 'chargement' }
  | { etat: 'erreur'; message: string; introuvable: boolean }
  | { etat: 'pret'; evenement: EvenementOrganisateur };

/**
 * Modification d'un événement (PUT /events/{id}). Brouillon : tout, sauf le nom et l'adresse
 * du lieu. Publié : titre et description seulement. Des billets ont pu être vendus pour ces
 * dates et les acheteurs ne seraient pas prévenus ; les dates sont donc affichées sans champ,
 * et renvoyées telles que reçues. La position GPS se modifie seulement en brouillon (route
 * distincte, appelée seulement si elle a changé) ; publiée, elle est figée comme les dates.
 * Annulé ou terminé : plus rien n'est modifiable.
 */
export function ModifierEvenementPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [page, setPage] = useState<EtatPage>({ etat: 'chargement' });
  const [titre, setTitre] = useState('');
  const [description, setDescription] = useState('');
  const [categorie, setCategorie] = useState<CategorieEvenement>('AUTRE');
  const [debut, setDebut] = useState('');
  const [fin, setFin] = useState('');
  const [lieu, setLieu] = useState<Lieu | null>(null);
  const [coordonnees, setCoordonnees] = useState('');
  const [erreurs, setErreurs] = useState<Erreurs>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  useEffect(() => {
    let annule = false;
    lireEvenement(id)
      .then(async (evenement) => {
        // Sans le lieu, l'événement reste modifiable : la carte s'ouvre seulement sans repère.
        const lieuLu = await lireLieu(evenement.venueId).catch(() => null);
        if (annule) return;
        setLieu(lieuLu);
        if (lieuLu?.latitude != null && lieuLu.longitude != null) {
          setCoordonnees(`${lieuLu.latitude}, ${lieuLu.longitude}`);
        }
        setTitre(evenement.titre);
        setDescription(evenement.description ?? '');
        setCategorie(evenement.categorie ?? 'AUTRE');
        setDebut(instantVersHeureDakar(evenement.debuteLe));
        setFin(evenement.termineLe ? instantVersHeureDakar(evenement.termineLe) : '');
        setPage({ etat: 'pret', evenement });
      })
      .catch((e: unknown) => {
        if (!annule) {
          setPage({ etat: 'erreur', message: messageErreur(e), introuvable: e instanceof ApiError && e.status === 404 });
        }
      });
    return () => {
      annule = true;
    };
  }, [id]);

  const retour = `/organisateur/evenements/${id}`;

  if (page.etat === 'chargement') {
    return (
      <CadreOrganisateur etroit>
        <Chargement libelle="Chargement de l'événement…" />
      </CadreOrganisateur>
    );
  }
  if (page.etat === 'erreur') {
    return (
      <CadreOrganisateur etroit>
        <EtatVide
          niveau={1}
          icone={page.introuvable ? 'recherche' : 'alerte'}
          titre={page.introuvable ? 'Événement introuvable' : 'Chargement impossible'}
          texte={page.message}
          action={<BoutonLien to="/organisateur">Mes événements</BoutonLien>}
        />
      </CadreOrganisateur>
    );
  }

  const { evenement } = page;
  const brouillon = evenement.status === 'BROUILLON';
  const modifiable = brouillon || evenement.status === 'PUBLIE';

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    let debuteLe = evenement.debuteLe;
    let termineLe = evenement.termineLe;
    const nouvelles: Erreurs = {};
    // Position modifiable seulement en brouillon (figée à la publication, comme les dates).
    const saisie = brouillon ? coordonnees.trim() : '';
    const gps = saisie ? lireCoordonnees(saisie) : null;
    if (saisie && !gps) nouvelles.coordonnees = 'Format attendu : latitude, longitude (par exemple 14.6928, -17.4467).';
    if (brouillon && !saisie && lieu?.latitude != null) nouvelles.coordonnees = 'La position peut être déplacée, mais pas retirée.';
    if (brouillon) {
      const d = heureDakarVersInstant(debut);
      const f = fin ? heureDakarVersInstant(fin) : null;
      if (!d) nouvelles.debut = 'Indiquez la date et l’heure de début.';
      if (fin && !f) nouvelles.fin = 'Date de fin invalide.';
      if (d && f && f < d) nouvelles.fin = 'La fin ne peut pas précéder le début.';
      if (d) {
        debuteLe = d;
        termineLe = f;
      }
    }
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0) return;
    const positionChangee = gps !== null && (gps.latitude !== lieu?.latitude || gps.longitude !== lieu?.longitude);
    setEnvoi(true);
    try {
      await modifierEvenement(evenement.id, {
        titre: titre.trim(),
        description: description.trim() || null,
        debuteLe,
        termineLe,
        categorie,
      });
      if (positionChangee) await definirPosition(evenement.id, gps);
      navigate(retour, { replace: true, state: { modifie: true } });
    } catch (e2) {
      setErreur(messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <CadreOrganisateur etroit>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>{evenement.venueNom}</p>
          <h1 className={s.titrePage}>Modifier l'événement</h1>
        </div>

        {!modifiable ? (
          <Avis ton="attention">
            <p>
              Cet événement est {evenement.status === 'ANNULE' ? 'annulé' : 'terminé'} : il ne peut plus être modifié.
            </p>
            <div>
              <BoutonLien to={retour} variante="secondaire">
                Retour à l'événement
              </BoutonLien>
            </div>
          </Avis>
        ) : (
          <form className={s.formulaire} onSubmit={valider}>
            <Panneau titre="L'événement" id="evenement">
              <ChampTexte libelle="Titre" valeur={titre} onChange={setTitre} requis maxLength={LIMITES.titre} />
              <ChampChoix libelle="Catégorie" options={CATEGORIES} valeur={categorie} onChange={setCategorie} />
              <ChampTexte
                libelle="Description"
                multiligne
                valeur={description}
                onChange={setDescription}
                maxLength={LIMITES.description}
                aide="Programme, artistes, consignes d'accès…"
              />
            </Panneau>

            <Panneau titre="Dates" id="dates">
              {brouillon ? (
                <div className={s.paire}>
                  <ChampTexte
                    libelle="Début"
                    type="datetime-local"
                    valeur={debut}
                    onChange={setDebut}
                    requis
                    aide="Heure de Dakar."
                    erreur={erreurs.debut}
                  />
                  <ChampTexte
                    libelle="Fin"
                    type="datetime-local"
                    valeur={fin}
                    onChange={setFin}
                    aide="Heure de Dakar."
                    erreur={erreurs.fin}
                  />
                </div>
              ) : (
                <>
                  <dl className={s.fiche}>
                    <div>
                      <dt>Début</dt>
                      <dd>
                        {formatDateLongue(evenement.debuteLe)} à {formatHeure(evenement.debuteLe)}
                      </dd>
                    </div>
                    {evenement.termineLe && (
                      <div>
                        <dt>Fin</dt>
                        <dd>
                          {formatDateLongue(evenement.termineLe)} à {formatHeure(evenement.termineLe)}
                        </dd>
                      </div>
                    )}
                  </dl>
                  <Avis>
                    <p>
                      L'événement est publié : ses dates ne changent plus, car des billets ont pu être vendus pour elles.
                      Pour un report, annulez l'événement (les commandes payées sont signalées pour remboursement) et créez-en un nouveau.
                    </p>
                  </Avis>
                </>
              )}
            </Panneau>

            <Panneau titre="Position du lieu" id="position">
              {brouillon ? (
                <>
                  <p className={s.texte}>
                    Le nom et l'adresse du lieu sont fixés à la création. Sa position peut changer jusqu'à la publication.
                  </p>
                  <CartePosition valeur={coordonnees} onChange={setCoordonnees} erreur={erreurs.coordonnees} />
                </>
              ) : coordonnees ? (
                <p className={s.texte}>
                  La position du lieu est fixée depuis la publication, comme les dates : des billets ont pu être vendus
                  pour ce lieu. Coordonnées : {coordonnees}.
                </p>
              ) : (
                <p className={s.texte}>
                  Aucune position n'a été définie avant la publication : elle ne peut plus être ajoutée.
                </p>
              )}
            </Panneau>

            {erreur && (
              <Avis ton="danger">
                <p>{erreur}</p>
              </Avis>
            )}
            <div className={s.actions}>
              <Bouton type="submit" grand disabled={envoi}>
                {envoi ? 'Enregistrement…' : 'Enregistrer'}
              </Bouton>
              <BoutonLien to={retour} variante="secondaire">
                Retour sans enregistrer
              </BoutonLien>
            </div>
          </form>
        )}
      </div>
    </CadreOrganisateur>
  );
}
