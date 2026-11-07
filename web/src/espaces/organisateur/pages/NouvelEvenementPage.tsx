import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { CATEGORIES, type CategorieEvenement } from '@/commun/categories';
import { heureDakarVersInstant } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import { optionsVilles, paysDeLaVille, useVilles } from '@/commun/villes';
import { Avis, Bouton, ChampChoix, ChampTexte, Panneau } from '@/ui';
import { creerEvenement, creerLieu, LIMITES, lireCoordonnees } from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import { CartePosition } from './CartePosition';
import s from './organisateur.module.css';

interface Erreurs {
  debut?: string;
  fin?: string;
  coordonnees?: string;
  ville?: string;
}

/**
 * Création d'un événement en brouillon. Le backend n'a pas de liste des lieux : le lieu
 * est créé avec l'événement. L'organisateur vient du jeton, jamais du formulaire.
 */
export function NouvelEvenementPage() {
  const navigate = useNavigate();
  const [titre, setTitre] = useState('');
  const [description, setDescription] = useState('');
  const [categorie, setCategorie] = useState<CategorieEvenement>('MUSIQUE');
  const [debut, setDebut] = useState('');
  const [fin, setFin] = useState('');
  const [lieuNom, setLieuNom] = useState('');
  const [lieuAdresse, setLieuAdresse] = useState('');
  const [ville, setVille] = useState('');
  const { villes, erreur: erreurVilles } = useVilles();
  const [coordonnees, setCoordonnees] = useState('');
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
    const gps = coordonnees.trim() ? lireCoordonnees(coordonnees) : null;
    if (coordonnees.trim() && !gps) nouvelles.coordonnees = 'Format attendu : latitude, longitude (par exemple 14.6928, -17.4467).';
    if (!ville) nouvelles.ville = 'Choisissez la ville du lieu.';
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0 || !debuteLe) return;

    setEnvoi(true);
    try {
      const lieu = await creerLieu(lieuNom.trim(), lieuAdresse.trim(), ville, gps);
      const evenement = await creerEvenement({
        titre: titre.trim(),
        description: description.trim() || null,
        debuteLe,
        termineLe,
        venueId: lieu.id,
        categorie,
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
          <h1 className={s.titrePage}>Nouvel événement</h1>
          <p className={s.sousTitre}>Brouillon, invisible du public jusqu'à sa publication</p>
        </div>
        <form className={s.formulaire} onSubmit={valider}>
          <Panneau titre="L'événement" id="evenement">
            <ChampTexte libelle="Titre" valeur={titre} onChange={setTitre} requis maxLength={LIMITES.titre} />
            <ChampChoix
              libelle="Catégorie"
              options={CATEGORIES}
              valeur={categorie}
              onChange={setCategorie}
              aide="Les acheteurs filtrent le catalogue par catégorie."
            />
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
            <ChampChoix
              libelle="Ville"
              options={optionsVilles(villes ?? [], villes === null ? 'Chargement des villes…' : 'Choisissez une ville')}
              valeur={ville}
              onChange={setVille}
              aide="Les acheteurs cherchent les événements par ville. Ville absente : contactez Billet+."
            />
            {erreurs.ville && (
              <Avis ton="danger">
                <p>{erreurs.ville}</p>
              </Avis>
            )}
            {erreurVilles && (
              <Avis ton="danger">
                <p>{erreurVilles}</p>
              </Avis>
            )}
            {ville && paysDeLaVille(ville) !== 'SN' && (
              <Avis ton="attention">
                <p>
                  Hors du Sénégal, vous pouvez préparer l'événement, mais pas encore le publier : la vente n'y est pas
                  encore ouverte.
                </p>
              </Avis>
            )}
            <ChampTexte
              libelle="Adresse"
              valeur={lieuAdresse}
              onChange={setLieuAdresse}
              requis
              maxLength={LIMITES.lieuAdresse}
              autoComplete="street-address"
            />
            <CartePosition valeur={coordonnees} onChange={setCoordonnees} erreur={erreurs.coordonnees} />
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
