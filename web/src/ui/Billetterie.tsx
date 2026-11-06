import type { ReactNode } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { formatCompteARebours, formatFcfa } from '@/commun/format';
import { Icone } from './Icone';
import { codeBillet, useCompteARebours } from './outils';
import { Avis } from './Retour';
import s from './Billetterie.module.css';

export function Panneau({
  titre,
  children,
  bordure,
  id,
}: {
  titre?: string;
  children: ReactNode;
  bordure?: boolean;
  id?: string;
}) {
  const idTitre = titre && id ? `${id}-titre` : undefined;
  return (
    <section className={[s.panneau, bordure && s.panneauBordure].filter(Boolean).join(' ')} aria-labelledby={idTitre}>
      {titre && (
        <h2 id={idTitre} className={s.panneauTitre}>
          {titre}
        </h2>
      )}
      {children}
    </section>
  );
}

/** Résumé et action principale, toujours à portée de pouce sur mobile. */
export function BarreAction({
  libelle,
  montant,
  sansNav,
  children,
}: {
  libelle?: string;
  montant?: string;
  sansNav?: boolean;
  children: ReactNode;
}) {
  return (
    <div className={[s.barre, sansNav && s.barreSansNav].filter(Boolean).join(' ')}>
      {(libelle || montant) && (
        <div className={s.barreResume} aria-live="polite">
          {libelle && <p className={s.barreLibelle}>{libelle}</p>}
          {montant && <p className={s.barreMontant}>{montant}</p>}
        </div>
      )}
      <div className={s.barreAction}>{children}</div>
    </div>
  );
}

export function SelecteurQuantite({
  nom,
  valeur,
  max,
  onChange,
}: {
  /** Nom de la catégorie, pour les libellés : « Ajouter un billet VIP ». */
  nom: string;
  valeur: number;
  max: number;
  onChange: (valeur: number) => void;
}) {
  return (
    <div className={s.quantite} role="group" aria-label={`Quantité ${nom}`}>
      <button
        type="button"
        className={s.pas}
        aria-label={`Retirer un billet ${nom}`}
        disabled={valeur <= 0}
        onClick={() => onChange(valeur - 1)}
      >
        <Icone nom="moins" />
      </button>
      <output className={s.valeur} aria-live="polite">
        {valeur}
      </output>
      <button
        type="button"
        className={`${s.pas} ${s.pasPlus}`}
        aria-label={`Ajouter un billet ${nom}`}
        disabled={valeur >= max}
        onClick={() => onChange(valeur + 1)}
      >
        <Icone nom="plus" />
      </button>
    </div>
  );
}

/** Une catégorie de billets. Le prix vient du backend ; le stock n'est jamais affiché (PR #20). */
export function LigneCategorie({
  nom,
  prixFcfa,
  disponible,
  quantite,
  max,
  onChange,
}: {
  nom: string;
  prixFcfa: number;
  disponible: boolean;
  quantite: number;
  max: number;
  onChange: (valeur: number) => void;
}) {
  return (
    <div className={s.ligne}>
      <div className={s.ligneInfo}>
        <p className={s.ligneNom}>{nom}</p>
        <p className={s.ligneDetail}>{prixFcfa === 0 ? 'Gratuit' : formatFcfa(prixFcfa)}</p>
      </div>
      {disponible ? (
        <SelecteurQuantite nom={nom} valeur={quantite} max={max} onChange={onChange} />
      ) : (
        <span className={s.epuise}>Épuisé</span>
      )}
    </div>
  );
}

export interface LigneRecap {
  libelle: string;
  montantFcfa: number;
}

/**
 * Affiche des montants, n'en calcule aucun : le total est fourni par l'appelant.
 * libelleTotal « Total estimé » avant la création de la commande (le montant ferme est
 * celui que renvoie le backend).
 */
export function Recapitulatif({
  lignes,
  totalFcfa,
  libelleTotal = 'Total',
}: {
  lignes: ReadonlyArray<LigneRecap>;
  totalFcfa: number;
  libelleTotal?: string;
}) {
  return (
    <dl className={s.recap}>
      {lignes.map((l) => (
        <div key={l.libelle} className={s.recapLigne}>
          <dt>{l.libelle}</dt>
          <dd>{formatFcfa(l.montantFcfa)}</dd>
        </div>
      ))}
      <div className={`${s.recapLigne} ${s.recapTotal}`}>
        <dt>{libelleTotal}</dt>
        <dd>{formatFcfa(totalFcfa)}</dd>
      </div>
    </dl>
  );
}

/**
 * Délai de garde des places. L'échéance vient du backend (expireLe de la commande).
 * Pas d'annonce à chaque seconde pour un lecteur d'écran (aria-live="off").
 */
export function CompteARebours({ echeance }: { echeance: string }) {
  const restant = useCompteARebours(echeance);
  if (restant <= 0) {
    return <Avis ton="danger" icone="horloge">Le délai de paiement est dépassé : les places ont été remises en vente.</Avis>;
  }
  return (
    <Avis ton="attention" icone="horloge">
      <p>
        Vos places sont gardées encore{' '}
        <strong role="timer" aria-live="off" style={{ fontSize: 17 }}>
          {formatCompteARebours(restant)}
        </strong>
        . Passé ce délai, elles sont remises en vente.
      </p>
    </Avis>
  );
}

/**
 * Le billet à présenter à l'entrée. `qr` est la chaîne signée Ed25519 par le backend,
 * dessinée telle quelle : le client ne signe ni ne modifie rien (ADR 0004).
 */
export function PasseBillet({
  id,
  categorie,
  titre,
  sousTitre,
  qr,
  statut,
  mention,
  action,
}: {
  id: string;
  categorie: string;
  titre: string;
  sousTitre: string;
  qr: string | null;
  statut: 'EMIS' | 'SCANNE' | 'ANNULE' | 'REMBOURSE' | 'TRANSFERE';
  /** Remplace la consigne sous le code (par exemple le destinataire d'un transfert). */
  mention?: string;
  /** Action propre au billet (transférer, renvoyer le lien), sous la consigne. */
  action?: ReactNode;
}) {
  const code = codeBillet(id);
  const valide = statut === 'EMIS' && qr !== null;
  const libelleStatut = {
    EMIS: 'Valide',
    SCANNE: 'Déjà utilisé',
    ANNULE: 'Annulé',
    REMBOURSE: 'Remboursé',
    TRANSFERE: 'Transféré',
  }[statut];
  return (
    <article className={s.passe} aria-label={`Billet ${categorie}, ${titre}, ${code}`}>
      <div className={s.passeHaut}>
        <div className={s.passeEntete}>
          <span className={s.categorie}>{categorie}</span>
          <span className={`${s.statut} ${valide ? s.statutValide : s.statutInactif}`}>
            <Icone nom={valide ? 'valide' : 'croix'} taille={16} />
            {libelleStatut}
          </span>
        </div>
        <h3 className={s.passeTitre}>{titre}</h3>
        <p className={s.passeSous}>{sousTitre}</p>
      </div>
      <div className={s.decoupe} aria-hidden="true">
        <span className={s.pointilles} />
      </div>
      <div className={s.passeBas}>
        {valide && qr ? (
          <QRCodeSVG
            value={qr}
            level="M"
            marginSize={2}
            className={s.qr}
            role="img"
            aria-label={`QR code du billet ${code}`}
          />
        ) : null}
        <p className={s.code}>{code}</p>
        <p className={s.consigne}>
          {mention ??
            (valide
              ? "Présentez ce code à l'entrée, luminosité de l'écran au maximum."
              : "Ce billet ne permet plus d'entrer.")}
        </p>
        {action}
      </div>
    </article>
  );
}
