import { useEffect, useState, type FormEvent } from 'react';
import { Button, EmptyState, Notice, Screen, TextField, TopBar } from '@/design-system';
import m from '@/shared/ui/miseEnPage.module.css';
import { creerAgent, mesAgents, type Agent } from '../api';
import { NavigationOrganisateur } from '../composants';
import { messageErreur } from '../outils';
import s from './organisateur.module.css';

/** Agents de contrôle de l'organisateur (GET et POST /api/agents). */
export function AgentsPage() {
  const [agents, setAgents] = useState<Agent[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [nomComplet, setNomComplet] = useState('');
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreurCreation, setErreurCreation] = useState<string | null>(null);
  const [cree, setCree] = useState<Agent | null>(null);

  useEffect(() => {
    mesAgents()
      .then(setAgents)
      .catch((e: unknown) => setErreur(messageErreur(e)));
  }, []);

  const motDePasseValide = motDePasse.length >= 8 && motDePasse.length <= 72;

  const creer = async (e: FormEvent) => {
    e.preventDefault();
    if (!motDePasseValide) return;
    setEnvoi(true);
    setErreurCreation(null);
    setCree(null);
    try {
      const agent = await creerAgent(email.trim(), motDePasse, nomComplet.trim());
      setAgents((deja) => [...(deja ?? []), agent]);
      setCree(agent);
      setNomComplet('');
      setEmail('');
      setMotDePasse('');
    } catch (err) {
      setErreurCreation(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <Screen withNav>
      <TopBar title="Mes agents" large subtitle="Comptes du personnel de contrôle" />
      <div className={m.deuxColonnes}>
        <section className={m.pile} aria-labelledby="titre-liste">
          <h2 id="titre-liste" className={m.titreSection}>
            Agents
          </h2>
          {erreur ? (
            <Notice tone="danger" role="alert" icon="warning">
              {erreur}
            </Notice>
          ) : null}
          {agents === null && !erreur ? <Notice icon="clock">Chargement…</Notice> : null}
          {agents?.length === 0 ? (
            <EmptyState
              icon="user"
              title="Aucun agent"
              description="Créez un compte pour chaque personne qui contrôlera les entrées."
            />
          ) : null}
          <ul className={s.liste} aria-label="Agents">
            {agents?.map((agent) => (
              <li key={agent.id} className={s.ligneAgent}>
                <span className={s.identite}>
                  <strong>{agent.nomComplet}</strong>
                  <span className={m.muet}>{agent.email}</span>
                </span>
              </li>
            ))}
          </ul>
        </section>

        <form className={`${m.formulaire} ${m.lateral}`} onSubmit={creer} noValidate aria-labelledby="titre-creation">
          <h2 id="titre-creation" className={m.titreSection}>
            Créer un agent
          </h2>
          <TextField
            label="Nom complet"
            value={nomComplet}
            onChange={(e) => setNomComplet(e.target.value)}
            maxLength={255}
            autoComplete="off"
          />
          <TextField
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            maxLength={255}
            autoComplete="off"
          />
          <TextField
            label="Mot de passe provisoire"
            type="password"
            hint="8 caractères au minimum. Transmettez-le à l'agent en personne."
            error={motDePasse && !motDePasseValide ? 'Entre 8 et 72 caractères.' : undefined}
            value={motDePasse}
            onChange={(e) => setMotDePasse(e.target.value)}
            maxLength={72}
            autoComplete="new-password"
          />
          {cree ? (
            <Notice tone="success" icon="check">
              Compte créé pour {cree.nomComplet}. Affectez-le à un événement depuis la fiche de l'événement.
            </Notice>
          ) : null}
          {erreurCreation ? (
            <Notice tone="danger" role="alert" icon="warning">
              {erreurCreation}
            </Notice>
          ) : null}
          <Button
            type="submit"
            loading={envoi}
            disabled={!nomComplet.trim() || !email.trim() || !motDePasseValide}
            icon="plus"
          >
            Créer le compte
          </Button>
        </form>
      </div>
      <NavigationOrganisateur />
    </Screen>
  );
}
