import { useEffect, useState, type FormEvent } from 'react';
import { MOT_DE_PASSE_MAX, MOT_DE_PASSE_MIN, TEXTE_MAX } from '@/commun/auth';
import { messageErreur } from '@/commun/http';
import { Avis, Bouton, ChampTexte, Chargement, Panneau, Tableau, type ColonneTableau } from '@/ui';
import { creerAgent, mesAgents, modifierAgent, supprimerAgent, type Agent } from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import s from './organisateur.module.css';

/** Comptes d'agents de contrôle de l'organisateur (rôle AGENT fixé par le backend). */
export function EquipePage() {
  const [agents, setAgents] = useState<Agent[] | null>(null);
  const [erreurListe, setErreurListe] = useState<string | null>(null);
  const [nom, setNom] = useState('');
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [erreurMotDePasse, setErreurMotDePasse] = useState<string>();
  const [message, setMessage] = useState<{ ton: 'succes' | 'danger'; texte: string } | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const [enEdition, setEnEdition] = useState<Agent | null>(null);
  const [aRetirer, setARetirer] = useState<Agent | null>(null);
  const [retrait, setRetrait] = useState(false);
  const [retourListe, setRetourListe] = useState<{ ton: 'succes' | 'danger'; texte: string } | null>(null);

  useEffect(() => {
    mesAgents()
      .then(setAgents)
      .catch((e: unknown) => setErreurListe(messageErreur(e)));
  }, []);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setMessage(null);
    const octets = new TextEncoder().encode(motDePasse).length;
    if (motDePasse.length < MOT_DE_PASSE_MIN || octets > MOT_DE_PASSE_MAX) {
      setErreurMotDePasse(`Entre ${MOT_DE_PASSE_MIN} et ${MOT_DE_PASSE_MAX} caractères.`);
      return;
    }
    setErreurMotDePasse(undefined);
    setEnvoi(true);
    try {
      const agent = await creerAgent({ email: email.trim(), motDePasse, nomComplet: nom.trim() });
      setAgents((liste) => [...(liste ?? []), agent]);
      setNom('');
      setEmail('');
      setMotDePasse('');
      setMessage({
        ton: 'succes',
        texte: `Compte créé pour ${agent.nomComplet}. Transmettez-lui son e-mail et son mot de passe, puis affectez-le à un événement.`,
      });
    } catch (e2) {
      setMessage({ ton: 'danger', texte: messageErreur(e2) });
    } finally {
      setEnvoi(false);
    }
  };

  const retirer = async (agent: Agent) => {
    setRetourListe(null);
    setRetrait(true);
    try {
      await supprimerAgent(agent.id);
      setAgents((liste) => (liste ?? []).filter((a) => a.id !== agent.id));
      if (enEdition?.id === agent.id) setEnEdition(null);
      setRetourListe({ ton: 'succes', texte: `${agent.nomComplet} ne fait plus partie de l'équipe.` });
    } catch (e) {
      setRetourListe({ ton: 'danger', texte: messageErreur(e) });
    } finally {
      setRetrait(false);
      setARetirer(null);
    }
  };

  const colonnes: ReadonlyArray<ColonneTableau<Agent>> = [
    { titre: 'Nom', cellule: (a) => <strong>{a.nomComplet}</strong> },
    { titre: 'E-mail', cellule: (a) => a.email },
    {
      titre: 'Actions',
      cellule: (a) => (
        <div className={s.actions}>
          <Bouton
            variante="secondaire"
            aria-label={`Modifier ${a.nomComplet}`}
            onClick={() => {
              setRetourListe(null);
              setARetirer(null);
              setEnEdition(a);
            }}
          >
            Modifier
          </Bouton>
          <Bouton
            variante="secondaire"
            aria-label={`Retirer ${a.nomComplet}`}
            onClick={() => {
              setRetourListe(null);
              setARetirer(a);
            }}
          >
            Retirer
          </Bouton>
        </div>
      ),
    },
  ];

  return (
    <CadreOrganisateur>
      <div className={s.pile}>
        <div>
          <h1 className={s.titrePage}>Équipe</h1>
          <p className={s.sousTitre}>Contrôle d'entrée</p>
        </div>
        {retourListe && (
          <Avis ton={retourListe.ton}>
            <p>{retourListe.texte}</p>
          </Avis>
        )}
        {aRetirer && (
          <Avis ton="attention">
            <p>
              <strong>Retirer {aRetirer.nomComplet} de l'équipe ?</strong> Cet agent ne pourra plus se connecter et quitte
              tous vos événements. Cette action est définitive : son e-mail ne pourra pas servir à un nouveau compte.
            </p>
            <p>
              <strong>A-t-il contrôlé des entrées sans réseau ?</strong> Faites d'abord synchroniser son téléphone :
              après le retrait, les entrées encore en attente sur l'appareil seront refusées, et ces billets resteront
              valables pour une autre entrée.
            </p>
            <div className={s.actions}>
              <Bouton onClick={() => void retirer(aRetirer)} disabled={retrait}>
                {retrait ? 'Retrait…' : "Oui, retirer l'agent"}
              </Bouton>
              <Bouton variante="secondaire" onClick={() => setARetirer(null)} disabled={retrait}>
                Garder l'agent
              </Bouton>
            </div>
          </Avis>
        )}
        <div className={s.deuxColonnes}>
          <Panneau>
            {erreurListe && (
              <Avis ton="danger">
                <p>{erreurListe}</p>
              </Avis>
            )}
            {!erreurListe && agents === null && <Chargement />}
            {agents?.length === 0 && <p className={s.texte}>Aucun agent pour l'instant.</p>}
            {agents && agents.length > 0 && (
              <Tableau legende="Agents de contrôle" colonnes={colonnes} lignes={agents} cle={(a) => a.id} />
            )}
          </Panneau>
          <div className={s.pile}>
            {enEdition && (
              <ModificationAgent
                key={enEdition.id}
                agent={enEdition}
                onFermer={() => setEnEdition(null)}
                onModifie={(modifie) => {
                  setAgents((liste) => (liste ?? []).map((a) => (a.id === modifie.id ? modifie : a)));
                  setEnEdition(null);
                  setRetourListe({ ton: 'succes', texte: `Compte de ${modifie.nomComplet} modifié.` });
                }}
              />
            )}
            <Panneau titre="Nouvel agent" id="nouvel-agent">
              <form className={s.formulaire} onSubmit={valider}>
                <ChampTexte libelle="Nom complet" valeur={nom} onChange={setNom} requis maxLength={TEXTE_MAX} />
                <ChampTexte
                  libelle="E-mail"
                  type="email"
                  valeur={email}
                  onChange={setEmail}
                  requis
                  maxLength={TEXTE_MAX}
                  inputMode="email"
                  autoComplete="off"
                />
                <ChampTexte
                  libelle="Mot de passe provisoire"
                  type="password"
                  valeur={motDePasse}
                  onChange={setMotDePasse}
                  requis
                  minLength={MOT_DE_PASSE_MIN}
                  maxLength={MOT_DE_PASSE_MAX}
                  autoComplete="new-password"
                  aide={`De ${MOT_DE_PASSE_MIN} à ${MOT_DE_PASSE_MAX} caractères.`}
                  erreur={erreurMotDePasse}
                />
                {message && (
                  <Avis ton={message.ton}>
                    <p>{message.texte}</p>
                  </Avis>
                )}
                <Bouton type="submit" disabled={envoi}>
                  {envoi ? 'Création…' : "Créer le compte d'agent"}
                </Bouton>
              </form>
            </Panneau>
          </div>
        </div>
      </div>
    </CadreOrganisateur>
  );
}

function ModificationAgent({
  agent,
  onFermer,
  onModifie,
}: {
  agent: Agent;
  onFermer: () => void;
  onModifie: (agent: Agent) => void;
}) {
  const [nom, setNom] = useState(agent.nomComplet);
  const [email, setEmail] = useState(agent.email);
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    setEnvoi(true);
    try {
      onModifie(await modifierAgent(agent.id, { email: email.trim(), nomComplet: nom.trim() }));
    } catch (e2) {
      setErreur(messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <Panneau titre={`Modifier ${agent.nomComplet}`} id="modifier-agent">
      <form className={s.formulaire} onSubmit={valider}>
        <ChampTexte libelle="Nom de l'agent" valeur={nom} onChange={setNom} requis maxLength={TEXTE_MAX} />
        <ChampTexte
          libelle="E-mail de l'agent"
          type="email"
          valeur={email}
          onChange={setEmail}
          requis
          maxLength={TEXTE_MAX}
          inputMode="email"
          autoComplete="off"
          aide="C'est son identifiant de connexion : prévenez l'agent s'il change."
        />
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
          </Avis>
        )}
        <div className={s.actions}>
          <Bouton type="submit" disabled={envoi}>
            {envoi ? 'Enregistrement…' : 'Enregistrer'}
          </Bouton>
          <Bouton variante="secondaire" onClick={onFermer} disabled={envoi}>
            Fermer
          </Bouton>
        </div>
      </form>
    </Panneau>
  );
}
