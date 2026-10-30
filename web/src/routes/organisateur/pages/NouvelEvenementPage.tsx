import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Notice, Screen, TextField, TopBar } from '@/design-system';
import m from '@/shared/ui/miseEnPage.module.css';
import { creerEvenement, creerLieu } from '../api';
import { NavigationOrganisateur } from '../composants';
import { heureDeDakarVersIso, messageErreur } from '../outils';
import s from './organisateur.module.css';

/**
 * Création d'un événement, en brouillon. Le backend n'a pas de liste de lieux : le
 * lieu est créé avec l'événement (POST /api/venues puis POST /api/events). Les heures
 * sont celles de Dakar, quel que soit le fuseau de l'ordinateur.
 */
export function NouvelEvenementPage() {
  const naviguer = useNavigate();
  const [titre, setTitre] = useState('');
  const [description, setDescription] = useState('');
  const [debut, setDebut] = useState('');
  const [fin, setFin] = useState('');
  const [lieu, setLieu] = useState('');
  const [adresse, setAdresse] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  const finAvantDebut = Boolean(debut && fin && fin <= debut);
  const complet = titre.trim() && debut && lieu.trim() && adresse.trim() && !finAvantDebut;

  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    if (!complet) return;
    setEnvoi(true);
    setErreur(null);
    try {
      const nouveauLieu = await creerLieu(lieu.trim(), adresse.trim());
      const evenement = await creerEvenement({
        titre: titre.trim(),
        description: description.trim() || null,
        debuteLe: heureDeDakarVersIso(debut),
        termineLe: fin ? heureDeDakarVersIso(fin) : null,
        venueId: nouveauLieu.id,
      });
      naviguer(`/organisateur/evenements/${evenement.id}`, { replace: true });
    } catch (err) {
      setErreur(messageErreur(err));
      setEnvoi(false);
    }
  };

  return (
    <Screen withNav>
      <NavigationOrganisateur />
      <TopBar title="Nouvel événement" backTo="/organisateur" backLabel="Retour à mes événements" />
      <div className={`${m.deuxColonnes} ${m.apresTitre}`}>
        <form className={m.formulaire} onSubmit={soumettre} noValidate>
          <TextField
            label="Titre"
            value={titre}
            onChange={(e) => setTitre(e.target.value)}
            required
            maxLength={255}
            placeholder="Ex. Dakar Sunset Sessions"
          />
          <TextField
            label="Description"
            hint="Facultative : programme, artistes, informations pratiques."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={5000}
          />
          <div className={s.paire}>
            <TextField
              label="Début (heure de Dakar)"
              type="datetime-local"
              value={debut}
              onChange={(e) => setDebut(e.target.value)}
              required
            />
            <TextField
              label="Fin (facultative)"
              type="datetime-local"
              value={fin}
              min={debut || undefined}
              onChange={(e) => setFin(e.target.value)}
              error={finAvantDebut ? 'La fin doit suivre le début.' : undefined}
            />
          </div>
          <div className={s.paire}>
            <TextField
              label="Lieu"
              value={lieu}
              onChange={(e) => setLieu(e.target.value)}
              required
              maxLength={255}
              placeholder="Ex. Grand Théâtre"
            />
            <TextField
              label="Adresse"
              value={adresse}
              onChange={(e) => setAdresse(e.target.value)}
              required
              maxLength={500}
              placeholder="Ex. Place de la Nation, Dakar"
            />
          </div>
          <Notice icon="calendar">
            L'événement est créé en brouillon, invisible du public. Ajoutez ensuite ses catégories de billets, puis
            publiez-le.
          </Notice>
          {erreur ? (
            <Notice tone="danger" role="alert" icon="warning">
              {erreur}
            </Notice>
          ) : null}
          <Button type="submit" fullWidth loading={envoi} disabled={!complet}>
            Créer le brouillon
          </Button>
        </form>
        <aside className={`${m.pile} ${m.lateral}`} aria-labelledby="titre-etapes">
          <h2 id="titre-etapes" className={m.titreSection}>
            Ouvrir la vente en trois étapes
          </h2>
          <ol className={m.etapes}>
            <li>Créez l'événement : il reste un brouillon, invisible du public.</li>
            <li>Ajoutez ses catégories de billets (prix et nombre de places).</li>
            <li>Publiez-le : la vente ouvre, et l'événement apparaît dans Découvrir.</li>
          </ol>
        </aside>
      </div>
    </Screen>
  );
}
