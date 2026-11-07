import { useEffect, useState } from 'react';
import { formatDateCourte, formatFcfa } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import {
  Avis,
  Bouton,
  BoutonLien,
  Chargement,
  EtatVide,
  Icone,
  Indicateur,
  Indicateurs,
  Pastille,
  Tableau,
  type ColonneTableau,
} from '@/ui';
import { commandesARembourser, titreEvenement, type CommandeARembourser } from '../api';
import { libelleMotif, libelleMoyen, telephoneLisible, totalARembourser, versCsv } from '../remboursements';
import { CadreAdmin } from './CadreAdmin';
import s from './admin.module.css';

/**
 * Copie dans le presse-papiers, avec un retour bref. Le retour est dans une région live
 * HORS du bouton : un aria-label sur le bouton masquerait son contenu aux lecteurs d'écran.
 * Contexte non sécurisé (pas de HTTPS) ou refus du navigateur : « Copie impossible ».
 */
function BoutonCopier({ texte, libelle }: { texte: string; libelle: string }) {
  const [etat, setEtat] = useState<'repos' | 'copie' | 'echec'>('repos');
  const copier = async () => {
    try {
      await navigator.clipboard.writeText(texte);
      setEtat('copie');
    } catch {
      setEtat('echec');
    }
    window.setTimeout(() => setEtat('repos'), 2000);
  };
  return (
    <span className={s.copiable}>
      <button type="button" className={s.copier} onClick={() => void copier()} aria-label={`${libelle} : copier`}>
        <Icone nom={etat === 'copie' ? 'coche' : 'copier'} taille={16} />
      </button>
      <span className={s.retourCopie} aria-live="polite">
        {etat === 'copie' ? 'Copié' : etat === 'echec' ? 'Copie impossible' : ''}
      </span>
    </span>
  );
}

/**
 * Commandes payées à rembourser à la main (décision B) : payées sans billet, ou payées
 * pour un événement annulé. Données personnelles : affichées à l'administrateur seulement,
 * jamais mises en cache par le service worker (route authentifiée, réponses no-store).
 */
export function RemboursementsPage() {
  const [commandes, setCommandes] = useState<CommandeARembourser[] | null>(null);
  const [titres, setTitres] = useState<Record<string, string | null>>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [essai, setEssai] = useState(0);

  useEffect(() => {
    let annule = false;
    setErreur(null);
    commandesARembourser()
      .then(async (liste) => {
        if (annule) return;
        setCommandes(liste);
        const ids = [...new Set(liste.map((c) => c.eventId))];
        const lus = await Promise.all(ids.map(async (id) => [id, await titreEvenement(id)] as const));
        if (!annule) setTitres(Object.fromEntries(lus));
      })
      .catch((e: unknown) => !annule && setErreur(messageErreur(e)));
    return () => {
      annule = true;
    };
  }, [essai]);

  const exporter = () => {
    if (!commandes) return;
    const url = URL.createObjectURL(new Blob([versCsv(commandes, titres)], { type: 'text/csv;charset=utf-8' }));
    const lien = document.createElement('a');
    lien.href = url;
    lien.download = `remboursements-billetplus-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(lien);
    lien.click();
    lien.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
  };

  const colonnes: ReadonlyArray<ColonneTableau<CommandeARembourser>> = [
    {
      titre: 'Événement',
      cellule: (c) => (
        <span className={s.evenement}>
          <strong>{titres[c.eventId] ?? 'Événement non visible'}</strong>
          <span className={s.secondaire}>{c.payeeLe ? `Payée ${formatDateCourte(c.payeeLe)}` : 'Date de paiement inconnue'}</span>
        </span>
      ),
    },
    {
      titre: 'Motif',
      cellule: (c) => (
        <Pastille ton={c.motif === 'EVENEMENT_ANNULE' ? 'danger' : 'attention'} icone="alerte">
          {libelleMotif(c.motif)}
        </Pastille>
      ),
    },
    {
      titre: 'Téléphone',
      cellule: (c) => (
        <span className={s.copiable}>
          {telephoneLisible(c.telephone)}
          <BoutonCopier texte={c.telephone} libelle={`Téléphone ${telephoneLisible(c.telephone)}`} />
        </span>
      ),
    },
    { titre: 'Moyen', cellule: (c) => libelleMoyen(c.moyenPaiement) },
    {
      titre: 'Référence',
      cellule: (c) =>
        c.referencePaiement ? (
          <span className={s.copiable}>
            <code className={s.reference}>{c.referencePaiement}</code>
            <BoutonCopier texte={c.referencePaiement} libelle={`Référence ${c.referencePaiement}`} />
          </span>
        ) : (
          '—'
        ),
    },
    { titre: 'Montant', cellule: (c) => <strong>{formatFcfa(c.montantFcfa)}</strong>, nombre: true },
  ];

  return (
    <CadreAdmin>
      <div className={s.pile}>
        <div className={s.entete}>
          <div>
            <h1 className={s.titrePage}>Remboursements</h1>
            <p className={s.sousTitre}>Administration Billet+</p>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
            {commandes && commandes.length > 0 && (
              <Bouton variante="secondaire" onClick={exporter}>
                <Icone nom="telecharger" />
                Exporter en CSV
              </Bouton>
            )}
            <BoutonLien to="/admin/compte" variante="secondaire">
              <Icone nom="compte" />
              Mon compte
            </BoutonLien>
          </div>
        </div>

        <Avis>
          <p>
            Remboursement manuel (V1) : renvoyez le montant au numéro indiqué, par le même moyen de paiement, en citant la
            référence. Ces données sont personnelles : ne les partagez pas, supprimez l'export une fois traité, et videz
            l'historique du presse-papiers (Windows : touches Windows + V) sur un poste partagé.
          </p>
        </Avis>

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

        {!erreur && commandes === null && <Chargement libelle="Chargement des commandes à rembourser…" />}

        {commandes?.length === 0 && (
          <EtatVide icone="valide" titre="Aucune commande à rembourser" texte="Toutes les commandes payées ont reçu leurs billets." />
        )}

        {commandes && commandes.length > 0 && (
          <>
            <Indicateurs libelle="Synthèse des remboursements">
              <Indicateur ton="sombre" libelle="Total à rembourser" valeur={formatFcfa(totalARembourser(commandes))} />
              <Indicateur libelle="Commandes" valeur={`${commandes.length}`} />
            </Indicateurs>
            {/* Ordinateur et tablette : tableau. Téléphone (où se font les remboursements Wave et
                Orange Money) : une carte par commande, gros boutons de copie. display:none retire
                la version inutilisée des lecteurs d'écran : aucun doublon annoncé. */}
            <div className={`${s.carteTableau} ${s.surOrdinateur}`}>
              <Tableau legende="Commandes à rembourser" colonnes={colonnes} lignes={commandes} cle={(c) => c.id} />
            </div>
            <ul className={`${s.cartes} ${s.surTelephone}`} aria-label="Commandes à rembourser">
              {commandes.map((c) => (
                <li key={c.id} className={s.carte}>
                  <div className={s.carteHaut}>
                    <span className={s.evenement}>
                      <strong>{titres[c.eventId] ?? 'Événement non visible'}</strong>
                      <span className={s.secondaire}>
                        {c.payeeLe ? `Payée ${formatDateCourte(c.payeeLe)}` : 'Date de paiement inconnue'}
                      </span>
                    </span>
                    <strong className={s.montant}>{formatFcfa(c.montantFcfa)}</strong>
                  </div>
                  <Pastille ton={c.motif === 'EVENEMENT_ANNULE' ? 'danger' : 'attention'} icone="alerte">
                    {libelleMotif(c.motif)}
                  </Pastille>
                  <dl className={s.carteDetails}>
                    <div>
                      <dt>Téléphone</dt>
                      <dd className={s.copiable}>
                        {telephoneLisible(c.telephone)}
                        <BoutonCopier texte={c.telephone} libelle={`Téléphone ${telephoneLisible(c.telephone)}`} />
                      </dd>
                    </div>
                    <div>
                      <dt>Moyen</dt>
                      <dd>{libelleMoyen(c.moyenPaiement)}</dd>
                    </div>
                    <div>
                      <dt>Référence</dt>
                      <dd className={s.copiable}>
                        {c.referencePaiement ? (
                          <>
                            <code className={s.reference}>{c.referencePaiement}</code>
                            <BoutonCopier texte={c.referencePaiement} libelle={`Référence ${c.referencePaiement}`} />
                          </>
                        ) : (
                          '—'
                        )}
                      </dd>
                    </div>
                  </dl>
                </li>
              ))}
            </ul>
            {/* Plafond de ListerCommandesARembourserUseCase (500, sans pagination côté serveur). */}
            {commandes.length >= 500 && (
              <Avis ton="attention">
                <p>La liste est limitée à 500 commandes par le serveur : traitez celles-ci, puis rechargez.</p>
              </Avis>
            )}
          </>
        )}
      </div>
    </CadreAdmin>
  );
}
