import { useCallback, useEffect, useId, useState, type FormEvent } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useSession } from '@/commun/auth';
import { formatDateCourte, formatFcfa } from '@/commun/format';
import { ApiError, messageErreur } from '@/commun/http';
import {
  Avis,
  Bouton,
  BoutonLien,
  ChampTexte,
  Chargement,
  EtatVide,
  Indicateur,
  Indicateurs,
  Panneau,
  Pastille,
  Tableau,
  type ColonneTableau,
} from '@/ui';
import {
  affecterAgent,
  annuler,
  creerCategorie,
  LIMITES,
  lireAffectations,
  lireCategories,
  lireEvenement,
  lireRecettes,
  lireTableauDeBord,
  mesAgents,
  publier,
  retirerAgent,
  type Agent,
  type CategorieOrganisateur,
  type EvenementOrganisateur,
  type LigneTableauDeBord,
  type Recettes,
  type TableauDeBord,
} from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import { STATUTS } from './statut';
import s from './organisateur.module.css';

const COLONNES: ReadonlyArray<ColonneTableau<LigneTableauDeBord>> = [
  { titre: 'Catégorie', cellule: (l) => <strong>{l.nom}</strong> },
  { titre: 'Prix', cellule: (l) => formatFcfa(l.prixFcfa), nombre: true },
  { titre: 'Vendus', cellule: (l) => `${l.vendus} / ${l.quotaTotal}`, nombre: true },
  { titre: 'En paiement', cellule: (l) => l.enCoursDePaiement, nombre: true },
  { titre: 'Disponibles', cellule: (l) => l.disponibles, nombre: true },
  { titre: 'Entrés', cellule: (l) => l.scannes, nombre: true },
  { titre: 'Annulés', cellule: (l) => l.annules, nombre: true },
  { titre: 'Recettes', cellule: (l) => formatFcfa(l.chiffreAffairesFcfa), nombre: true },
];

type EtatPage =
  | { etat: 'chargement' }
  | { etat: 'introuvable' }
  | { etat: 'erreur'; message: string }
  | { etat: 'pret'; evenement: EvenementOrganisateur; categories: CategorieOrganisateur[] };

export function EvenementOrganisateurPage() {
  const { id } = useParams();
  const etatNavigation = useLocation().state as { cree?: boolean; modifie?: boolean } | null;
  const vientDEtreCree = etatNavigation?.cree === true;
  const vientDEtreModifie = etatNavigation?.modifie === true;
  const [donnees, setDonnees] = useState<EtatPage>({ etat: 'chargement' });
  const [tableau, setTableau] = useState<TableauDeBord | null>(null);
  const [recettes, setRecettes] = useState<Recettes | null>(null);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [message, setMessage] = useState<{ ton: 'succes' | 'danger'; texte: string } | null>(null);
  const [confirmerAnnulation, setConfirmerAnnulation] = useState(false);
  const [action, setAction] = useState(false);

  const { session } = useSession();
  const moi = session.etat === 'connecte' ? session.utilisateur.id : null;

  const charger = useCallback(async () => {
    if (!id) return setDonnees({ etat: 'introuvable' });
    try {
      const [evenement, categories] = await Promise.all([lireEvenement(id), lireCategories(id)]);
      // GET /api/events/{id} est public : un événement PUBLIÉ d'un autre organisateur
      // répond 200. Le même écran « introuvable » que pour un identifiant inconnu (le
      // backend refuserait de toute façon chaque action : ceci n'évite qu'un écran trompeur).
      if (evenement.organizerId !== moi) return setDonnees({ etat: 'introuvable' });
      setDonnees({ etat: 'pret', evenement, categories });
    } catch (e) {
      // Brouillon d'un autre organisateur ou identifiant inconnu : 404 indiscernable.
      if (e instanceof ApiError && e.status === 404) setDonnees({ etat: 'introuvable' });
      else setDonnees({ etat: 'erreur', message: messageErreur(e) });
      return;
    }
    // Compléments : leur échec n'empêche pas de gérer l'événement.
    lireTableauDeBord(id).then(setTableau).catch(() => setTableau(null));
    lireRecettes(id).then(setRecettes).catch(() => setRecettes(null));
    mesAgents().then(setAgents).catch(() => setAgents([]));
  }, [id, moi]);

  useEffect(() => {
    void charger();
  }, [charger]);

  if (donnees.etat === 'chargement') {
    return (
      <CadreOrganisateur>
        <Chargement libelle="Chargement de l'événement…" />
      </CadreOrganisateur>
    );
  }
  if (donnees.etat === 'introuvable') {
    return (
      <CadreOrganisateur>
        <EtatVide
          niveau={1}
          icone="recherche"
          titre="Événement introuvable"
          texte="Cet événement n'existe pas ou n'appartient pas à votre compte."
          action={<BoutonLien to="/organisateur">Mes événements</BoutonLien>}
        />
      </CadreOrganisateur>
    );
  }
  if (donnees.etat === 'erreur') {
    return (
      <CadreOrganisateur>
        <Avis ton="danger">
          <p>{donnees.message}</p>
          <div>
            <Bouton variante="secondaire" onClick={() => void charger()}>
              Réessayer
            </Bouton>
          </div>
        </Avis>
      </CadreOrganisateur>
    );
  }

  const { evenement, categories } = donnees;
  const statut = STATUTS[evenement.status];
  const quotaTotal = tableau?.categories.reduce((n, c) => n + c.quotaTotal, 0) ?? 0;

  const changerStatut = async (operation: 'publier' | 'annuler') => {
    setMessage(null);
    setAction(true);
    try {
      const misAJour = operation === 'publier' ? await publier(evenement.id) : await annuler(evenement.id);
      setDonnees({ ...donnees, evenement: misAJour });
      setMessage({
        ton: 'succes',
        texte: operation === 'publier' ? 'Événement publié : il est en vente.' : 'Événement annulé.',
      });
    } catch (e) {
      setMessage({ ton: 'danger', texte: messageErreur(e) });
    } finally {
      setAction(false);
      setConfirmerAnnulation(false);
    }
  };

  return (
    <CadreOrganisateur>
      <div className={s.pile}>
        <div className={s.entete}>
          <div>
            <p className={s.surtitre}>
              {formatDateCourte(evenement.debuteLe)} · {evenement.venueNom}
            </p>
            <h1 className={s.titrePage}>{evenement.titre}</h1>
            <div style={{ marginTop: 8 }}>
              <Pastille ton={statut.ton} icone={statut.icone}>
                {statut.libelle}
              </Pastille>
            </div>
          </div>
          <div className={s.actions}>
            {evenement.status === 'PUBLIE' && (
              <>
                <BoutonLien to={`/evenements/${evenement.id}`} variante="secondaire">
                  Voir la page publique
                </BoutonLien>
                <BoutonLien to={`/scanner/evenements/${evenement.id}`} variante="secondaire">
                  Contrôler les entrées
                </BoutonLien>
              </>
            )}
            {evenement.status === 'BROUILLON' && (
              <Bouton onClick={() => void changerStatut('publier')} disabled={action || categories.length === 0}>
                Publier
              </Bouton>
            )}
            {(evenement.status === 'BROUILLON' || evenement.status === 'PUBLIE') && (
              <BoutonLien to={`/organisateur/evenements/${evenement.id}/modifier`} variante="secondaire">
                Modifier
              </BoutonLien>
            )}
            {(evenement.status === 'BROUILLON' || evenement.status === 'PUBLIE') && !confirmerAnnulation && (
              <Bouton variante="secondaire" onClick={() => setConfirmerAnnulation(true)} disabled={action}>
                Annuler l'événement
              </Bouton>
            )}
          </div>
        </div>

        {vientDEtreCree && !message && evenement.status === 'BROUILLON' && (
          <Avis ton="succes">
            <p>Brouillon créé. Ajoutez au moins une catégorie de billets, puis publiez.</p>
          </Avis>
        )}
        {evenement.status === 'BROUILLON' && categories.length === 0 && !vientDEtreCree && (
          <Avis>
            <p>Ajoutez au moins une catégorie de billets pour pouvoir publier.</p>
          </Avis>
        )}
        {vientDEtreModifie && !message && (
          <Avis ton="succes">
            <p>Modifications enregistrées.</p>
          </Avis>
        )}
        {message && (
          <Avis ton={message.ton}>
            <p>{message.texte}</p>
          </Avis>
        )}
        {confirmerAnnulation && (
          <Avis ton="attention">
            <p>
              <strong>Annuler « {evenement.titre} » ?</strong> La vente s'arrête et les billets émis sont annulés. Les
              remboursements sont traités manuellement. Cette action est définitive.
              {categories.some((c) => c.prixFcfa === 0) &&
                " Le crédit débité pour les billets gratuits déjà émis n'est pas rendu."}
            </p>
            <div className={s.actions}>
              <Bouton onClick={() => void changerStatut('annuler')} disabled={action}>
                Oui, annuler l'événement
              </Bouton>
              <Bouton variante="secondaire" onClick={() => setConfirmerAnnulation(false)} disabled={action}>
                Garder l'événement
              </Bouton>
            </div>
          </Avis>
        )}

        {tableau && (
          <Indicateurs libelle="Ventes de l'événement">
            <Indicateur ton="sombre" libelle="Recettes" valeur={formatFcfa(tableau.chiffreAffairesFcfa)} />
            <Indicateur
              libelle="Billets vendus"
              valeur={`${tableau.vendus}`}
              complement={quotaTotal > 0 ? `sur ${quotaTotal} places` : undefined}
              progression={quotaTotal > 0 ? (tableau.vendus / quotaTotal) * 100 : undefined}
            />
            <Indicateur libelle="En cours de paiement" valeur={`${tableau.enCoursDePaiement}`} complement="places gardées" />
            <Indicateur libelle="Entrées contrôlées" valeur={`${tableau.scannes}`} />
          </Indicateurs>
        )}

        {recettes && (recettes.commandes > 0 || recettes.reverseFcfa !== 0) && (
          <VosRecettes recettes={recettes} annule={evenement.status === 'ANNULE'} />
        )}

        <div className={s.deuxColonnes}>
          <div className={s.pile}>
            <Panneau>
              {tableau && tableau.categories.length > 0 ? (
                <Tableau legende="Par catégorie" colonnes={COLONNES} lignes={tableau.categories} cle={(l) => l.categoryId} />
              ) : (
                <>
                  <h2 className={s.titreSection}>Catégories de billets</h2>
                  {categories.length === 0 ? (
                    <p className={s.texte}>Aucune catégorie pour l'instant.</p>
                  ) : (
                    <ul className={s.liste}>
                      {categories.map((c) => (
                        <li key={c.id} className={s.ligneDetail}>
                          <strong>{c.nom}</strong> · {formatFcfa(c.prixFcfa)}
                          {c.quotaTotal !== undefined && ` · ${c.quotaVendu ?? 0} / ${c.quotaTotal} vendus`}
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )}
            </Panneau>
            {evenement.description && (
              <Panneau titre="Description" id="description">
                <p className={s.texte} style={{ whiteSpace: 'pre-line' }}>
                  {evenement.description}
                </p>
              </Panneau>
            )}
          </div>

          <div className={s.pile}>
            {(evenement.status === 'BROUILLON' || evenement.status === 'PUBLIE') && (
              <AjoutCategorie eventId={evenement.id} onAjout={() => void charger()} />
            )}
            <ControleEntree eventId={evenement.id} agents={agents} />
          </div>
        </div>
      </div>
    </CadreOrganisateur>
  );
}

/** Part de l'organisateur (ADR 0009) : la commission de Billet+ est déduite de chaque commande. */
function VosRecettes({ recettes, annule }: { recettes: Recettes; annule: boolean }) {
  const reste = recettes.resteAReverserFcfa;
  return (
    <Panneau titre="Vos recettes" id="recettes">
      {annule && (
        <Avis ton="attention">
          <p>Événement annulé : les acheteurs sont remboursés, il n'y a plus de recette à vous reverser.</p>
        </Avis>
      )}
      <dl className={s.recettes}>
        <dt>
          Ventes encaissées ({recettes.commandes} commande{recettes.commandes > 1 ? 's' : ''})
        </dt>
        <dd>{formatFcfa(recettes.brutFcfa)}</dd>
        <dt>Commission Billet+</dt>
        <dd>− {formatFcfa(recettes.commissionFcfa)}</dd>
        <dt>
          <strong>Votre part</strong>
        </dt>
        <dd>
          <strong>{formatFcfa(recettes.netFcfa)}</strong>
        </dd>
        <dt>Déjà reversé</dt>
        <dd>{formatFcfa(recettes.reverseFcfa)}</dd>
        <dt>
          <strong>{reste < 0 ? 'Trop perçu' : 'Reste à recevoir'}</strong>
        </dt>
        <dd>
          <strong>{formatFcfa(Math.abs(reste))}</strong>
        </dd>
      </dl>
      <p className={s.ligneDetail}>
        La commission de Billet+ est déduite de chaque commande, au taux en vigueur le jour de la vente. Billet+ vous
        reverse votre part par virement.
      </p>
    </Panneau>
  );
}

/** Entier positif saisi en texte, ou null. */
function entier(valeur: string): number | null {
  const n = Number(valeur.replace(/\s/g, ''));
  return Number.isInteger(n) ? n : null;
}

function AjoutCategorie({ eventId, onAjout }: { eventId: string; onAjout: () => void }) {
  const [nom, setNom] = useState('');
  const [prix, setPrix] = useState('');
  const [quota, setQuota] = useState('');
  const [erreurs, setErreurs] = useState<{ prix?: string; quota?: string }>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    const prixFcfa = entier(prix);
    const quotaTotal = entier(quota);
    const nouvelles: { prix?: string; quota?: string } = {};
    if (prixFcfa === null || prixFcfa < 0 || prixFcfa > LIMITES.prixMax) nouvelles.prix = 'Prix entier en FCFA, 0 pour une entrée libre.';
    if (quotaTotal === null || quotaTotal < 1 || quotaTotal > LIMITES.quotaMax) nouvelles.quota = 'Au moins 1 place.';
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0 || prixFcfa === null || quotaTotal === null) return;
    setEnvoi(true);
    try {
      await creerCategorie(eventId, { nom: nom.trim(), prixFcfa, quotaTotal });
      setNom('');
      setPrix('');
      setQuota('');
      onAjout();
    } catch (e2) {
      setErreur(messageErreur(e2));
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <Panneau titre="Ajouter une catégorie" id="ajout-categorie">
      <form className={s.formulaire} onSubmit={valider}>
        <ChampTexte libelle="Nom" valeur={nom} onChange={setNom} requis maxLength={LIMITES.categorieNom} aide="Standard, VIP, Carré or…" />
        <div className={s.paire}>
          <ChampTexte libelle="Prix (FCFA)" valeur={prix} onChange={setPrix} requis inputMode="numeric" erreur={erreurs.prix} />
          <ChampTexte libelle="Places" valeur={quota} onChange={setQuota} requis inputMode="numeric" erreur={erreurs.quota} />
        </div>
        {entier(prix) === 0 && (
          <Avis>
            <p>
              Entrée libre : chaque billet gratuit émis est payé sur votre{' '}
              <Link to="/organisateur/credit">crédit entrée libre</Link>. Sans crédit suffisant, les participants ne
              peuvent pas obtenir leur billet.
            </p>
          </Avis>
        )}
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
          </Avis>
        )}
        <Bouton type="submit" disabled={envoi}>
          {envoi ? 'Ajout…' : 'Ajouter la catégorie'}
        </Bouton>
      </form>
    </Panneau>
  );
}

/** Agents affectés au contrôle d'entrée de l'événement, avec ajout et retrait en un clic. */
function ControleEntree({ eventId, agents }: { eventId: string; agents: Agent[] }) {
  const idSelect = useId();
  const [affectes, setAffectes] = useState<string[] | null>(null);
  const [erreurListe, setErreurListe] = useState<string | null>(null);
  const [agentId, setAgentId] = useState('');
  const [message, setMessage] = useState<{ ton: 'succes' | 'danger'; texte: string } | null>(null);
  const [envoi, setEnvoi] = useState(false);

  useEffect(() => {
    let annule = false;
    lireAffectations(eventId)
      .then((ids) => {
        if (!annule) setAffectes(ids);
      })
      .catch((e: unknown) => {
        if (!annule) setErreurListe(messageErreur(e));
      });
    return () => {
      annule = true;
    };
  }, [eventId]);

  const enPoste = agents.filter((a) => affectes?.includes(a.id));
  const disponibles = agents.filter((a) => !affectes?.includes(a.id));
  const choisi = disponibles.find((a) => a.id === agentId);

  const affecter = async (agent: Agent) => {
    setMessage(null);
    setEnvoi(true);
    try {
      await affecterAgent(eventId, agent.id);
      setAffectes((ids) => [...(ids ?? []), agent.id]);
      setAgentId('');
      setMessage({ ton: 'succes', texte: `${agent.nomComplet} peut contrôler les entrées de cet événement.` });
    } catch (e) {
      setMessage({ ton: 'danger', texte: messageErreur(e) });
    } finally {
      setEnvoi(false);
    }
  };

  const retirer = async (agent: Agent) => {
    setMessage(null);
    setEnvoi(true);
    try {
      await retirerAgent(eventId, agent.id);
      setAffectes((ids) => (ids ?? []).filter((id) => id !== agent.id));
      setMessage({ ton: 'succes', texte: `${agent.nomComplet} ne contrôle plus cet événement.` });
    } catch (e) {
      setMessage({ ton: 'danger', texte: messageErreur(e) });
    } finally {
      setEnvoi(false);
    }
  };

  if (agents.length === 0) {
    return (
      <Panneau titre="Contrôle d'entrée" id="controle">
        <p className={s.texte}>
          Aucun agent pour l'instant.{' '}
          <Link to="/organisateur/equipe" className={s.lienDiscret}>
            Créer un compte d'agent
          </Link>
        </p>
      </Panneau>
    );
  }

  return (
    <Panneau titre="Contrôle d'entrée" id="controle">
      {erreurListe && (
        <Avis ton="danger">
          <p>{erreurListe}</p>
        </Avis>
      )}
      {!erreurListe && affectes === null && <Chargement />}
      {affectes !== null && (
        <div className={s.formulaire}>
          {enPoste.length === 0 ? (
            <p className={s.texte}>Aucun agent affecté à cet événement.</p>
          ) : (
            <ul className={s.liste} aria-label="Agents affectés">
              {enPoste.map((a) => (
                <li key={a.id} className={s.ligneAgent}>
                  <span>
                    <strong>{a.nomComplet}</strong>
                    <br />
                    <span className={s.ligneDetail}>{a.email}</span>
                  </span>
                  <Bouton
                    variante="secondaire"
                    aria-label={`Retirer ${a.nomComplet} de cet événement`}
                    onClick={() => void retirer(a)}
                    disabled={envoi}
                  >
                    Retirer
                  </Bouton>
                </li>
              ))}
            </ul>
          )}
          {disponibles.length > 0 && (
            <>
              <div className={s.formulaire} style={{ gap: 8 }}>
                <label htmlFor={idSelect} style={{ fontWeight: 700 }}>
                  Agent
                </label>
                <select
                  id={idSelect}
                  value={agentId}
                  onChange={(e) => setAgentId(e.target.value)}
                  style={{
                    minHeight: 52,
                    borderRadius: 16,
                    border: '1px solid var(--bp-line-strong)',
                    padding: '0 14px',
                    fontSize: 16,
                    background: 'var(--bp-white)',
                  }}
                >
                  <option value="">Choisir un agent</option>
                  {disponibles.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.nomComplet} ({a.email})
                    </option>
                  ))}
                </select>
              </div>
              <div className={s.actions}>
                <Bouton onClick={() => choisi && void affecter(choisi)} disabled={!choisi || envoi}>
                  Affecter
                </Bouton>
              </div>
            </>
          )}
          {message && (
            <Avis ton={message.ton}>
              <p>{message.texte}</p>
            </Avis>
          )}
        </div>
      )}
    </Panneau>
  );
}
