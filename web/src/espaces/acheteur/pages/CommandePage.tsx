import { useEffect, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import { formatDateCourte, formatFcfa } from '@/commun/format';
import { ApiError, messageErreur } from '@/commun/http';
import {
  Avis,
  Bouton,
  BoutonLien,
  Cadre,
  Chargement,
  CompteARebours,
  EtatVide,
  Panneau,
  PasseBillet,
  Recapitulatif,
} from '@/ui';
import {
  lireBillets,
  lireCategories,
  lireCommande,
  ouvrirPaiement,
  type Billet,
  type Commande,
  type MoyenPaiement,
} from '../api';
import { enregistrerBillets, lireCommandeLocale, type CommandeLocale } from '../commandesLocales';
import { BoutonBilletsPdf } from './BoutonBilletsPdf';
import { ChoixMoyen, Etapes } from './paiement';
import { allerPayer, nomDuMoyen } from './paiementOutils';
import s from './pages.module.css';

/** Le webhook de la passerelle met quelques secondes à confirmer le paiement. */
export const INTERVALLE_SUIVI_MS = 4000;

export function CommandePage() {
  const { id } = useParams();
  // Échec d'ouverture du paiement transmis par l'écran précédent : il ne concerne que la
  // commande EN ATTENTE (il survit dans l'historique au rechargement, d'où le filtre plus bas).
  const erreurPaiement = (useLocation().state as { erreurPaiement?: string } | null)?.erreurPaiement ?? null;

  const [locale, setLocale] = useState<CommandeLocale | null | undefined>(undefined);
  const [commande, setCommande] = useState<Commande | null>(null);
  const [billets, setBillets] = useState<Billet[] | null>(null);
  const [noms, setNoms] = useState<Record<string, string>>({});
  const [horsLigne, setHorsLigne] = useState(false);
  const [introuvable, setIntrouvable] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [moyen, setMoyen] = useState<MoyenPaiement>('WAVE');
  const [envoi, setEnvoi] = useState(false);
  const [tour, setTour] = useState(0);

  // 1. Le jeton n'existe que sur cet appareil.
  useEffect(() => {
    if (!id) return setLocale(null);
    let annule = false;
    lireCommandeLocale(id)
      .then((c) => {
        if (annule) return;
        setLocale(c ?? null);
        if (c?.billets) setBillets(c.billets);
      })
      .catch(() => !annule && setLocale(null));
    return () => {
      annule = true;
    };
  }, [id]);

  // 2. État à jour de la commande ; relu régulièrement tant que le paiement est attendu.
  useEffect(() => {
    if (!locale) return;
    let annule = false;
    lireCommande(locale.id, locale.jeton)
      .then((c) => {
        if (annule) return;
        setCommande(c);
        setHorsLigne(false);
      })
      .catch((e: unknown) => {
        if (annule) return;
        if (e instanceof ApiError && e.status === 404) setIntrouvable(true);
        else if (e instanceof ApiError) setErreur(messageErreur(e));
        else setHorsLigne(true);
      });
    return () => {
      annule = true;
    };
  }, [locale, tour]);

  const enAttente = commande?.status === 'EN_ATTENTE_PAIEMENT';
  const payee = commande?.status === 'PAYEE';
  // Entrée libre (ADR 0010) : rien n'a été payé, rien n'est à rembourser.
  const gratuite = commande?.montantTotalFcfa === 0;

  useEffect(() => {
    if (!enAttente && !(payee && !billets)) return;
    const minuterie = window.setTimeout(() => setTour((n) => n + 1), INTERVALLE_SUIVI_MS);
    return () => window.clearTimeout(minuterie);
  }, [enAttente, payee, billets, tour]);

  // 3. Billets d'une commande payée : 409 tant que l'émission n'est pas finie, on réessaie au tour suivant.
  useEffect(() => {
    if (!locale || !payee) return;
    let annule = false;
    lireBillets(locale.id, locale.jeton)
      .then(async (b) => {
        if (annule) return;
        setBillets(b);
        await enregistrerBillets(locale.id, b);
      })
      .catch((e: unknown) => {
        if (!annule && !(e instanceof ApiError && e.status === 409)) setErreur(messageErreur(e));
      });
    return () => {
      annule = true;
    };
  }, [locale, payee, tour]);

  // Noms des catégories pour le récapitulatif (la commande ne porte que leurs identifiants).
  const eventId = commande?.eventId;
  useEffect(() => {
    if (!eventId) return;
    lireCategories(eventId)
      .then((cs) => setNoms(Object.fromEntries(cs.map((c) => [c.id, c.nom]))))
      .catch(() => undefined);
  }, [eventId]);

  const reprendrePaiement = async () => {
    if (!locale) return;
    setErreur(null);
    setEnvoi(true);
    try {
      const lien = await ouvrirPaiement(locale.id, locale.jeton, moyen);
      allerPayer(lien.urlPaiement);
    } catch (e) {
      setErreur(messageErreur(e));
      setEnvoi(false);
    }
  };

  if (locale === undefined) {
    return (
      <Cadre>
        <Chargement />
      </Cadre>
    );
  }

  if (locale === null || introuvable) {
    return (
      <Cadre>
        <EtatVide
          niveau={1}
          icone="billet"
          titre="Commande introuvable sur cet appareil"
          texte="Les billets restent sur l'appareil qui a servi à l'achat. Ouvrez Billet+ depuis ce téléphone ou ce navigateur."
          action={<BoutonLien to="/billets">Mes billets</BoutonLien>}
        />
      </Cadre>
    );
  }

  const sousTitre = `${formatDateCourte(locale.debuteLe)} · ${locale.lieu}`;
  const billetsAffiches = billets ?? locale.billets ?? null;

  return (
    <Cadre fondGris etroit={enAttente}>
      <div className={s.pile}>
        {horsLigne && (
          <Avis ton="attention" icone="horsLigne">
            <p>Pas de connexion : affichage des billets enregistrés sur cet appareil.</p>
          </Avis>
        )}
        {(erreur ?? (enAttente ? erreurPaiement : null)) && (
          <Avis ton="danger">
            <p>{erreur ?? erreurPaiement}</p>
          </Avis>
        )}

        {!commande && !horsLigne && <Chargement libelle="Lecture de la commande…" />}

        {enAttente && commande && (
          <>
            <Etapes courante={2} />
            <CompteARebours echeance={commande.expireLe} />
            <div className={s.formulaireEtAside}>
              <Panneau>
                <h1 className={s.titrePage}>Finaliser le paiement</h1>
                <p className={s.infoDetail}>
                  Si vous avez déjà validé le paiement sur votre téléphone, la confirmation arrive dans quelques
                  secondes : cette page se met à jour toute seule.
                </p>
                <ChoixMoyen valeur={moyen} onChange={setMoyen} />
                <Bouton grand pleineLargeur disabled={envoi} onClick={reprendrePaiement}>
                  {envoi
                    ? 'Redirection vers le paiement…'
                    : `Payer ${formatFcfa(commande.montantTotalFcfa)} avec ${nomDuMoyen(moyen)}`}
                </Bouton>
              </Panneau>
              <aside aria-label="Récapitulatif de la commande">
                <RecapCommande commande={commande} titre={locale.titre} sousTitre={sousTitre} noms={noms} />
              </aside>
            </div>
          </>
        )}

        {(payee || (horsLigne && billetsAffiches)) && (
          <>
            {payee && <Etapes courante={3} gratuit={gratuite} />}
            <div>
              <p className={s.surtitre}>
                {payee ? (gratuite ? 'Billets gratuits confirmés' : 'Paiement confirmé') : 'Billets enregistrés'}
              </p>
              <h1 className={s.titrePage}>Vos billets</h1>
            </div>
            {billetsAffiches?.some((b) => b.statut === 'ANNULE') && (
              // Un billet n'est annulé que par l'annulation de son événement (CancelEventTicketsUseCase) :
              // la commande payée rejoint alors la liste des remboursements de l'administrateur.
              <Avis ton="danger" icone="croix">
                <p>
                  <strong>Événement annulé.</strong> Vos billets ne sont plus valables.
                  {gratuite ? (
                    ' Ces billets étaient gratuits : il n’y a rien à rembourser.'
                  ) : (
                    <>
                      {' '}
                      Votre paiement
                      {commande ? ` de ${formatFcfa(commande.montantTotalFcfa)}` : ''} est remboursé par l'équipe
                      Billet+, sur le numéro qui a payé, par le même moyen de paiement.
                    </>
                  )}
                </p>
              </Avis>
            )}
            {billetsAffiches ? (
              <>
                <Avis ton="succes" icone="cadenas">
                  <p>
                    Billets enregistrés sur cet appareil : ils s'affichent même sans réseau. Retrouvez-les à tout moment
                    dans « Mes billets ».
                  </p>
                </Avis>
                <BoutonBilletsPdf
                  billets={billetsAffiches}
                  titre={locale.titre}
                  debuteLe={locale.debuteLe}
                  lieu={locale.lieu}
                  commandeId={locale.id}
                />
                <div className={s.billets}>
                  {billetsAffiches.map((b) => (
                    <PasseBillet
                      key={b.id}
                      id={b.id}
                      categorie={b.categorie}
                      titre={locale.titre}
                      sousTitre={sousTitre}
                      qr={b.qr}
                      statut={b.statut}
                      mention={
                        b.statut === 'TRANSFERE'
                          ? `Transféré au ${b.transfereA ?? 'destinataire'} : ce billet n'est plus valable sur cet appareil.`
                          : undefined
                      }
                      action={
                        (b.statut === 'EMIS' && b.qr && !horsLigne) ||
                        (b.statut === 'TRANSFERE' && locale.liensTransfert?.[b.id]) ? (
                          <BoutonLien to={`/commandes/${locale.id}/billets/${b.id}/transfert`} variante="secondaire">
                            {b.statut === 'TRANSFERE' ? 'Renvoyer le lien' : 'Transférer ce billet'}
                          </BoutonLien>
                        ) : undefined
                      }
                    />
                  ))}
                </div>
              </>
            ) : (
              <Chargement libelle="Émission de vos billets…" />
            )}
          </>
        )}

        {commande?.status === 'PAYEE_SANS_BILLET' && (
          <>
            <h1 className={s.titrePage}>Paiement reçu, places indisponibles</h1>
            <Avis ton="attention">
              <p>
                Votre paiement de <strong>{formatFcfa(commande.montantTotalFcfa)}</strong> a bien été reçu, mais les
                places n'étaient plus disponibles au moment de la confirmation. Aucun billet n'a été émis : le
                remboursement est traité manuellement par l'équipe Billet+.
              </p>
            </Avis>
            <RecapCommande commande={commande} titre={locale.titre} sousTitre={sousTitre} noms={noms} />
          </>
        )}

        {(commande?.status === 'EXPIREE' || commande?.status === 'ANNULEE') && (
          <EtatVide
            niveau={1}
            ton="attention"
            icone="horloge"
            titre={commande.status === 'EXPIREE' ? 'Délai de paiement dépassé' : 'Commande annulée'}
            texte="Aucun montant n'a été prélevé et les places ont été remises en vente."
            action={<BoutonLien to={`/evenements/${commande.eventId}`}>Choisir à nouveau</BoutonLien>}
          />
        )}
      </div>
    </Cadre>
  );
}

function RecapCommande({
  commande,
  titre,
  sousTitre,
  noms,
}: {
  commande: Commande;
  titre: string;
  sousTitre: string;
  noms: Record<string, string>;
}) {
  return (
    <Panneau titre={titre} id="recap-commande">
      <p className={s.infoDetail}>{sousTitre}</p>
      <Recapitulatif
        lignes={commande.lignes.map((l) => ({
          libelle: `${l.quantite} × ${noms[l.categoryId] ?? 'Billets'}`,
          montantFcfa: l.sousTotalFcfa,
        }))}
        totalFcfa={commande.montantTotalFcfa}
      />
    </Panneau>
  );
}
