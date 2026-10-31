import { useEffect, useState } from 'react';
import { messageErreur } from '@/commun/http';
import {
  Avis,
  Bouton,
  BoutonLien,
  Cadre,
  CarteEvenement,
  ChampRecherche,
  Chargement,
  EtatVide,
  EvenementALaUne,
  GrilleEvenements,
  Icone,
  type NomIcone,
  type ResumeEvenement,
} from '@/ui';
import { rechercherEvenements, type EvenementPublic, type PageEvenements } from '../api';
import s from './pages.module.css';

const DELAI_RECHERCHE_MS = 300;

function versResume(e: EvenementPublic): ResumeEvenement {
  return { id: e.id, titre: e.titre, debuteLe: e.debuteLe, lieu: e.venueNom };
}

const AVANTAGES: ReadonlyArray<{ icone: NomIcone; titre: string; texte: string }> = [
  { icone: 'mobile', titre: 'Sans compte', texte: 'Votre numéro de mobile suffit pour acheter.' },
  { icone: 'carte', titre: 'Wave ou Orange Money', texte: 'Paiement validé sur votre téléphone.' },
  { icone: 'bouclier', titre: 'Billet infalsifiable', texte: 'QR code signé, affichable sans réseau.' },
];

export function DecouvrirPage() {
  const [saisie, setSaisie] = useState('');
  const [recherche, setRecherche] = useState('');
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
    rechercherEvenements(recherche)
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
  }, [recherche, essai]);

  const voirPlus = () => {
    if (!page) return;
    setChargement(true);
    rechercherEvenements(recherche, page.page + 1)
      .then((suite) => {
        setPage(suite);
        setEvenements((deja) => [...deja, ...suite.contenu]);
      })
      .catch((e: unknown) => setErreur(messageErreur(e)))
      .finally(() => setChargement(false));
  };

  const [premier, ...autres] = evenements;
  const aLaUne = !recherche && premier ? premier : null;
  const grille = aLaUne ? autres : evenements;
  const resteDesPages = page !== null && page.page + 1 < page.pages;

  return (
    <Cadre>
      <div className={s.pile}>
        <div className={s.entete}>
          <div>
            <p className={s.surtitre}>Dakar et alentours</p>
            <h1 className={s.titrePage}>Découvrir</h1>
          </div>
          <div className={s.recherche}>
            <ChampRecherche
              valeur={saisie}
              onChange={setSaisie}
              libelle="Rechercher un événement"
              placeholder="Concert, match, conférence…"
            />
          </div>
        </div>

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
            titre={recherche ? 'Aucun événement trouvé' : 'Aucun événement pour le moment'}
            texte={
              recherche
                ? `Rien ne correspond à « ${recherche} ». Essayez un autre mot.`
                : 'Revenez bientôt : les organisateurs publient leurs événements ici.'
            }
          />
        )}

        {grille.length > 0 && (
          <section className={s.pile} aria-labelledby="titre-liste">
            <h2 id="titre-liste" className={s.titreSection}>
              {recherche ? `Résultats pour « ${recherche} »` : 'À venir'}
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
