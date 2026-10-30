import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { heureDakarVersInstant } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import { Avis, Bouton, ChampTexte, Panneau } from '@/ui';
import { creerEvenement, creerLieu, LIMITES } from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import s from './organisateur.module.css';

interface Erreurs {
  debut?: string;
  fin?: string;
}

/**
 * Création d'un événement en brouillon. Le backend n'a pas de liste des lieux : le lieu
 * est créé avec l'événement. L'organisateur vient du jeton, jamais du formulaire.
 */
export function NouvelEvenementPage() {
  const navigate = useNavigate();
  const [titre, setTitre] = useState('');
  const [description, setDescription] = useState('');
  const [debut, setDebut] = useState('');
  const [fin, setFin] = useState('');
  const [lieuNom, setLieuNom] = useState('');
  const [lieuAdresse, setLieuAdresse] = useState('');
  const [erreurs, setErreurs] = useState<Erreurs>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    const debuteLe = heureDakarVersInstant(debut);
    const termineLe = fin ? heureDakarVersInstant(fin) : null;
    const nouvelles: Erreurs = {};
    if (!debuteLe) nouvelles.debut = 'Indiquez la date et l’heure de début.';
    if (fin && !termineLe) nouvelles.fin = 'Date de fin invalide.';
    if (debuteLe && termineLe && termineLe < debuteLe) nouvelles.fin = 'La fin ne peut pas précéder le début.';
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0 || !debuteLe) return;

    setEnvoi(true);
    try {
      const lieu = await creerLieu(lieuNom.trim(), lieuAdresse.trim());
      const evenement = await creerEvenement({
        titre: titre.trim(),
        description: description.trim() || null,
        debuteLe,
        termineLe,
        venueId: lieu.id,
      });
      navigate(`/organisateur/evenements/${evenement.id}`, { replace: true, state: { cree: true } });
    } catch (e2) {
      setErreur(messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <CadreOrganisateur etroit>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Brouillon, invisible du public jusqu'à sa publication</p>
          <h1 className={s.titrePage}>Nouvel événement</h1>
        </div>
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
          </Panneau>

          <Panneau titre="Le lieu" id="lieu">
            <ChampTexte libelle="Nom du lieu" valeur={lieuNom} onChange={setLieuNom} requis maxLength={LIMITES.lieuNom} />
            <ChampTexte
              libelle="Adresse"
              valeur={lieuAdresse}
              onChange={setLieuAdresse}
              requis
              maxLength={LIMITES.lieuAdresse}
              autoComplete="street-address"
            />
          </Panneau>

          {erreur && (
            <Avis ton="danger">
              <p>{erreur}</p>
            </Avis>
          )}
          <Bouton type="submit" grand disabled={envoi}>
            {envoi ? 'Création…' : 'Créer le brouillon'}
          </Bouton>
        </form>
      </div>
    </CadreOrganisateur>
  );
}
