import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { estMobileSenegalais, formatDateCourte, versE164 } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import { Avis, Bouton, Cadre, ChampTelephone, Chargement, Panneau, Recapitulatif } from '@/ui';
import { creerCommande, ouvrirPaiement, type MoyenPaiement } from '../api';
import { enregistrerCommande, verifierStockage } from '../commandesLocales';
import { ChoixMoyen, Etapes } from './paiement';
import { allerPayer, nomDuMoyen } from './paiementOutils';
import { apercuTotal, nombreDePlaces, useEvenement, type EtatNavigationPaiement } from './useEvenement';
import s from './pages.module.css';

export function PaiementPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const etatNavigation = useLocation().state as EtatNavigationPaiement | null;
  const chargement = useEvenement(id);
  const [moyen, setMoyen] = useState<MoyenPaiement>('WAVE');
  const [telephone, setTelephone] = useState('');
  const [erreurTelephone, setErreurTelephone] = useState<string>();
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  // Arrivée directe sur l'adresse (lien partagé, rechargement) : choisir d'abord ses billets.
  const quantites = etatNavigation?.quantites;
  if (!id || !quantites || nombreDePlaces(quantites) === 0) {
    return <Navigate to={id ? `/evenements/${id}` : '/'} replace />;
  }

  if (chargement.etat !== 'pret') {
    return (
      <Cadre sansNav etroit>
        {chargement.etat === 'chargement' ? (
          <Chargement />
        ) : (
          <Avis ton="danger">
            <p>{chargement.etat === 'erreur' ? chargement.message : 'Événement introuvable.'}</p>
          </Avis>
        )}
      </Cadre>
    );
  }

  const { evenement, categories } = chargement;
  // Annulé ou terminé entre le choix des places et le paiement : la page de l'événement l'explique.
  if (evenement.status !== 'PUBLIE') {
    return <Navigate to={`/evenements/${evenement.id}`} replace />;
  }
  const lignes = categories
    .filter((c) => (quantites[c.id] ?? 0) > 0)
    .map((c) => ({ categorie: c, quantite: quantites[c.id] ?? 0 }));
  const total = apercuTotal(categories, quantites);

  const payer = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    if (!estMobileSenegalais(telephone)) {
      setErreurTelephone('Entrez un numéro de mobile sénégalais à 9 chiffres, par exemple 77 123 45 67.');
      return;
    }
    setErreurTelephone(undefined);
    setEnvoi(true);

    // Sans stockage local, le jeton serait perdu : ne pas créer (ni bloquer de places).
    try {
      await verifierStockage();
    } catch {
      setErreur(
        "Ce navigateur ne peut pas enregistrer vos billets (mémoire pleine ou stockage bloqué). Libérez de l'espace ou ouvrez Billet+ dans une fenêtre normale pour acheter.",
      );
      setEnvoi(false);
      return;
    }

    let creee: Awaited<ReturnType<typeof creerCommande>>;
    try {
      creee = await creerCommande(
        evenement.id,
        versE164(telephone),
        lignes.map((l) => ({ categoryId: l.categorie.id, quantite: l.quantite })),
      );
    } catch (erreurCreation) {
      setErreur(messageErreur(erreurCreation));
      setEnvoi(false);
      return;
    }
    const commandeId = creee.commande.id;
    const jeton = creee.jetonAcces;

    // Le jeton n'est remis qu'ici, une seule fois : l'enregistrer AVANT toute autre étape.
    try {
      await enregistrerCommande({
        id: commandeId,
        jeton,
        eventId: evenement.id,
        titre: evenement.titre,
        debuteLe: evenement.debuteLe,
        lieu: evenement.venueNom,
        creeLe: creee.commande.creeLe,
      });
    } catch {
      // Cas résiduel (quota atteint entre la vérification et l'écriture) : le dire tel quel.
      setErreur(
        "Votre commande a été créée mais ce navigateur n'a pas pu l'enregistrer : aucun paiement n'a été demandé. Les places seront libérées automatiquement dans quelques minutes ; libérez de l'espace puis recommencez.",
      );
      setEnvoi(false);
      return;
    }

    try {
      const lien = await ouvrirPaiement(commandeId, jeton, moyen);
      allerPayer(lien.urlPaiement);
    } catch (erreurPaiement) {
      // La commande existe et garde les places : on la reprend depuis son écran.
      navigate(`/commandes/${commandeId}`, { replace: true, state: { erreurPaiement: messageErreur(erreurPaiement) } });
    }
  };

  return (
    <Cadre sansNav etroit fondGris>
      <div className={s.pile}>
        <Etapes courante={2} />
        <div className={s.formulaireEtAside}>
          <Panneau>
            <form className={s.formulaire} onSubmit={payer} noValidate>
              <h1 className={s.titrePage}>Paiement</h1>
              <ChoixMoyen valeur={moyen} onChange={setMoyen} />
              <ChampTelephone
                valeur={telephone}
                onChange={setTelephone}
                aide="Pas de compte à créer : ce numéro identifie votre commande."
                erreur={erreurTelephone}
              />
              <Avis icone="cadenas">
                <p>
                  Vos billets seront enregistrés <strong>sur cet appareil</strong>. Gardez ce navigateur (ou installez
                  Billet+) et ne videz pas ses données avant l'événement.
                </p>
              </Avis>
              {erreur && (
                <Avis ton="danger">
                  <p>{erreur}</p>
                </Avis>
              )}
              <Bouton type="submit" grand pleineLargeur disabled={envoi}>
                {envoi ? 'Redirection vers le paiement…' : `Payer avec ${nomDuMoyen(moyen)}`}
              </Bouton>
            </form>
          </Panneau>

          <aside aria-label="Récapitulatif de la commande">
            <Panneau titre={evenement.titre} id="recap">
              <p className={s.infoDetail}>
                {formatDateCourte(evenement.debuteLe)} · {evenement.venueNom}
              </p>
              <Recapitulatif
                lignes={lignes.map((l) => ({
                  libelle: `${l.quantite} × ${l.categorie.nom}`,
                  montantFcfa: l.categorie.prixFcfa * l.quantite,
                }))}
                totalFcfa={total}
                libelleTotal="Total estimé"
              />
              <p className={s.note}>Le montant définitif est confirmé à la création de la commande.</p>
            </Panneau>
          </aside>
        </div>
      </div>
    </Cadre>
  );
}
