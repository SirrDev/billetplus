import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { formatDateLongue, formatHeure, heureDakarVersInstant, instantVersHeureDakar } from '@/commun/format';
import { ApiError, messageErreur } from '@/commun/http';
import { Avis, Bouton, BoutonLien, Chargement, ChampTexte, EtatVide, Panneau } from '@/ui';
import { LIMITES, lireEvenement, modifierEvenement, type EvenementOrganisateur } from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import s from './organisateur.module.css';

interface Erreurs {
  debut?: string;
  fin?: string;
}

type EtatPage =
  | { etat: 'chargement' }
  | { etat: 'erreur'; message: string; introuvable: boolean }
  | { etat: 'pret'; evenement: EvenementOrganisateur };

/**
 * Modification d'un événement (PUT /events/{id}). Brouillon : tout, sauf le lieu. Publié :
 * titre et description seulement. Des billets ont pu être vendus pour ces dates et les
 * acheteurs ne seraient pas prévenus ; les dates sont donc affichées sans champ, et
 * renvoyées telles que reçues. Annulé ou terminé : plus rien n'est modifiable.
 */
export function ModifierEvenementPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [page, setPage] = useState<EtatPage>({ etat: 'chargement' });
  const [titre, setTitre] = useState('');
  const [description, setDescription] = useState('');
  const [debut, setDebut] = useState('');
  const [fin, setFin] = useState('');
  const [erreurs, setErreurs] = useState<Erreurs>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  useEffect(() => {
    let annule = false;
    lireEvenement(id)
      .then((evenement) => {
        if (annule) return;
        setTitre(evenement.titre);
        setDescription(evenement.description ?? '');
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
    if (brouillon) {
      const nouvelles: Erreurs = {};
      const d = heureDakarVersInstant(debut);
      const f = fin ? heureDakarVersInstant(fin) : null;
      if (!d) nouvelles.debut = 'Indiquez la date et l’heure de début.';
      if (fin && !f) nouvelles.fin = 'Date de fin invalide.';
      if (d && f && f < d) nouvelles.fin = 'La fin ne peut pas précéder le début.';
      setErreurs(nouvelles);
      if (Object.keys(nouvelles).length > 0 || !d) return;
      debuteLe = d;
      termineLe = f;
    }
    setEnvoi(true);
    try {
      await modifierEvenement(evenement.id, {
        titre: titre.trim(),
        description: description.trim() || null,
        debuteLe,
        termineLe,
      });
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
              <p className={s.texte}>Le lieu ne se modifie pas : il est fixé à la création.</p>
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
