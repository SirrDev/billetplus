import { useEffect, useState } from 'react';
import { CATEGORIES, libelleCategorie, type CategorieEvenement } from '@/commun/categories';
import { messageErreur } from '@/commun/http';
import { libelleVille, optionsVilles, useVilles } from '@/commun/villes';
import {
  Avis,
  Bouton,
  BoutonLien,
  Cadre,
  CarteEvenement,
  ChampChoix,
  ChampRecherche,
  Chargement,
  EtatVide,
  EvenementALaUne,
  Filtres,
  GrilleEvenements,
  Icone,
  type NomIcone,
  type ResumeEvenement,
} from '@/ui';
import { rechercherEvenements, type EvenementPublic, type PageEvenements } from '../api';
import s from './pages.module.css';

const DELAI_RECHERCHE_MS = 300;

function versResume(e: EvenementPublic): ResumeEvenement {
  return {
    id: e.id,
    titre: e.titre,
    debuteLe: e.debuteLe,
    lieu: `${e.venueNom} · ${libelleVille(e)}`,
    categorie: libelleCategorie(e.categorie),
  };
}

type Filtre = CategorieEvenement | 'TOUT';
const FILTRES: ReadonlyArray<{ valeur: Filtre; libelle: string }> = [{ valeur: 'TOUT', libelle: 'Tout' }, ...CATEGORIES];

const AVANTAGES: ReadonlyArray<{ icone: NomIcone; titre: string; texte: string }> = [
  { icone: 'mobile', titre: 'Sans compte', texte: 'Votre numéro de mobile suffit pour acheter.' },
  { icone: 'carte', titre: 'Wave ou Orange Money', texte: 'Paiement validé sur votre téléphone.' },
  { icone: 'bouclier', titre: 'Billet infalsifiable', texte: 'QR code signé, affichable sans réseau.' },
];

export function DecouvrirPage() {
  const [saisie, setSaisie] = useState('');
  const [recherche, setRecherche] = useState('');
  const [filtre, setFiltre] = useState<Filtre>('TOUT');
  const categorie = filtre === 'TOUT' ? null : filtre;
  const [ville, setVille] = useState('');
  const { villes } = useVilles();
  const nomVille = villes?.flatMap((p) => p.villes).find((v) => v.code === ville)?.nom;
  const [page, setPage] = useState<PageEvenements | null>(null);
  const [evenements, setEvenements] = useState<EvenementPublic[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [essai, setEssai] = useState(0);

  // Attendre une pause dans la frappe : une requête par recherche, pas par lettre.
  useEffect(() => {
    const minuterie = window.setTimeout(() => setRecherche(saisie.trim()), DELAI_RECHERCHE_MS);
    return () => window.clearTimeout(minuterie);
  }, [saisie]);

  useEffect(() => {
    let annule = false;
    setChargement(true);
    setErreur(null);
    rechercherEvenements(recherche, 0, 12, categorie, ville || null)
      .then((resultat) => {
        if (annule) return;
        setPage(resultat);
        setEvenements(resultat.contenu);
      })
      .catch((e: unknown) => !annule && setErreur(messageErreur(e)))
      .finally(() => !annule && setChargement(false));
    return () => {
      annule = true;
    };
  }, [recherche, categorie, ville, essai]);

  const voirPlus = () => {
    if (!page) return;
    setChargement(true);
    rechercherEvenements(recherche, page.page + 1, 12, categorie, ville || null)
      .then((suite) => {
        setPage(suite);
        setEvenements((deja) => [...deja, ...suite.contenu]);
      })
      .catch((e: unknown) => setErreur(messageErreur(e)))
      .finally(() => setChargement(false));
  };

  const [premier, ...autres] = evenements;
  // À l'affiche : seulement sur le catalogue complet, pas dans un résultat filtré.
  const aLaUne = !recherche && !categorie && !ville && premier ? premier : null;
  const grille = aLaUne ? autres : evenements;
  const resteDesPages = page !== null && page.page + 1 < page.pages;

  return (
    <Cadre>
      <div className={s.pile}>
        <div className={s.entete}>
          <div>
            <h1 className={s.titrePage}>Découvrir</h1>
            <p className={s.sousTitre}>{nomVille ?? "Afrique de l'Ouest"}</p>
          </div>
          <div className={s.recherche}>
            <ChampRecherche
              valeur={saisie}
              onChange={setSaisie}
              libelle="Rechercher un événement"
              placeholder="Concert, match, conférence…"
            />
            {villes !== null && villes.length > 0 && (
              <ChampChoix
                libelle="Ville"
                options={optionsVilles(villes, 'Toutes les villes')}
                valeur={ville}
                onChange={setVille}
              />
            )}
          </div>
        </div>

        <Filtres libelle="Catégories d'événements" options={FILTRES} valeur={filtre} onChange={setFiltre} />

        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
            <div>
              <Bouton variante="secondaire" onClick={() => setEssai((n) => n + 1)}>
                Réessayer
              </Bouton>
            </div>
          </Avis>
        )}

        {aLaUne && <EvenementALaUne evenement={versResume(aLaUne)} vers={`/evenements/${aLaUne.id}`} />}

        {!erreur && !chargement && evenements.length === 0 && (
          <EtatVide
            icone="recherche"
            titre={recherche || categorie ? 'Aucun événement trouvé' : 'Aucun événement pour le moment'}
            texte={
              recherche
                ? `Rien ne correspond à « ${recherche} »${categorie ? ` en ${libelleCategorie(categorie).toLowerCase()}` : ''}. Essayez un autre mot.`
                : categorie
                  ? `Aucun événement à venir en ${libelleCategorie(categorie).toLowerCase()} pour le moment.`
                  : 'Revenez bientôt : les organisateurs publient leurs événements ici.'
            }
            action={
              categorie ? (
                <Bouton variante="secondaire" onClick={() => setFiltre('TOUT')}>
                  Voir tous les événements
                </Bouton>
              ) : undefined
            }
          />
        )}

        {grille.length > 0 && (
          <section className={s.pile} aria-labelledby="titre-liste">
            <h2 id="titre-liste" className={s.titreSection}>
              {recherche ? `Résultats pour « ${recherche} »` : categorie ? libelleCategorie(categorie) : 'À venir'}
            </h2>
            <GrilleEvenements libelle={recherche ? 'Résultats de recherche' : 'Événements à venir'}>
              {grille.map((e) => (
                <CarteEvenement key={e.id} evenement={versResume(e)} vers={`/evenements/${e.id}`} />
              ))}
            </GrilleEvenements>
          </section>
        )}

        {chargement && <Chargement libelle="Chargement des événements…" />}

        {!chargement && resteDesPages && (
          <div className={s.suite}>
            <Bouton variante="secondaire" onClick={voirPlus}>
              Voir plus d'événements
            </Bouton>
          </div>
        )}

        <section className={s.organisateurs} aria-labelledby="titre-organisateurs">
          <div>
            <h2 id="titre-organisateurs" className={s.titreSection}>
              Vous organisez un événement ?
            </h2>
            <p className={s.infoDetail}>
              Vendez vos billets avec Billet+ : paiement Wave et Orange Money, billets à QR code infalsifiable, tableau de
              bord des ventes et contrôle d'entrée, même sans réseau.
            </p>
          </div>
          <div className={s.organisateursActions}>
            <BoutonLien to="/organisateur/inscription">Créer mon compte organisateur</BoutonLien>
            <BoutonLien to="/organisateur/connexion" variante="secondaire">
              Se connecter
            </BoutonLien>
          </div>
        </section>

        <section className={s.avantages} aria-label="Pourquoi Billet+">
          {AVANTAGES.map((a) => (
            <div key={a.titre} className={s.avantage}>
              <span className={s.infoIcone}>
                <Icone nom={a.icone} />
              </span>
              <div>
                <p className={s.infoTitre}>{a.titre}</p>
                <p className={s.infoDetail}>{a.texte}</p>
              </div>
            </div>
          ))}
        </section>
      </div>
    </Cadre>
  );
}
