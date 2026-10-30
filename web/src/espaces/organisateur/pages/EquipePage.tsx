import { useEffect, useState, type FormEvent } from 'react';
import { MOT_DE_PASSE_MAX, MOT_DE_PASSE_MIN, TEXTE_MAX } from '@/commun/auth';
import { messageErreur } from '@/commun/http';
import { Avis, Bouton, ChampTexte, Chargement, Panneau, Tableau, type ColonneTableau } from '@/ui';
import { creerAgent, mesAgents, type Agent } from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import s from './organisateur.module.css';

const COLONNES: ReadonlyArray<ColonneTableau<Agent>> = [
  { titre: 'Nom', cellule: (a) => <strong>{a.nomComplet}</strong> },
  { titre: 'E-mail', cellule: (a) => a.email },
];

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

  return (
    <CadreOrganisateur>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Contrôle d'entrée</p>
          <h1 className={s.titrePage}>Équipe</h1>
        </div>
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
              <Tableau legende="Agents de contrôle" colonnes={COLONNES} lignes={agents} cle={(a) => a.id} />
            )}
          </Panneau>
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
    </CadreOrganisateur>
  );
}
