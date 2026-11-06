import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { estMobileSenegalais, formatDateCourte, formatFcfa, versE164 } from '@/commun/format';
import { ErreurAffichable, messageErreur } from '@/commun/http';
import {
  Avis,
  Bouton,
  ChampTelephone,
  ChampTexte,
  Chargement,
  ChoixCartes,
  Panneau,
  Pastille,
  Tableau,
  type OptionCarte,
  type TonPastille,
} from '@/ui';
import { lireCredit, recharger, type Credit } from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import s from './organisateur.module.css';

type Moyen = 'WAVE' | 'ORANGE_MONEY';

const MOYENS: ReadonlyArray<OptionCarte<Moyen>> = [
  { valeur: 'WAVE', titre: 'Wave', detail: "Validation dans l'application Wave" },
  { valeur: 'ORANGE_MONEY', titre: 'Orange Money', detail: 'Confirmation sur votre téléphone' },
];

const STATUTS_RECHARGE: Record<Credit['recharges'][number]['status'], { libelle: string; ton: TonPastille }> = {
  EN_ATTENTE: { libelle: 'En attente', ton: 'attention' },
  PAYEE: { libelle: 'Créditée', ton: 'succes' },
  ECHOUEE: { libelle: 'Échouée', ton: 'neutre' },
  ANOMALIE: { libelle: 'À régulariser', ton: 'danger' },
};

/**
 * Redirection vers la passerelle : un lien https seulement (défense en profondeur, comme
 * pour le paiement d'une commande : un lien « javascript: » s'exécuterait dans l'origine).
 */
function allerPayer(url: string): void {
  let lien: URL;
  try {
    lien = new URL(url);
  } catch {
    throw new ErreurAffichable('Lien de paiement refusé par sécurité. Réessayez dans un instant.');
  }
  if (lien.protocol !== 'https:') {
    throw new ErreurAffichable('Lien de paiement refusé par sécurité. Réessayez dans un instant.');
  }
  window.location.assign(lien.href);
}

/**
 * Crédit prépayé de l'entrée libre (ADR 0010) : solde, tarifs lus dans la réponse (aucun
 * montant écrit en dur), recharge par Wave ou Orange Money, historique. Le solde n'augmente
 * qu'à la confirmation signée de la passerelle : au retour du paiement, l'écran se recharge.
 */
export function CreditPage() {
  const [credit, setCredit] = useState<Credit | null>(null);
  const [erreurChargement, setErreurChargement] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setErreurChargement(null);
    try {
      setCredit(await lireCredit());
    } catch (e) {
      setErreurChargement(messageErreur(e));
    }
  }, []);

  useEffect(() => {
    void charger();
  }, [charger]);

  return (
    <CadreOrganisateur>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Espace organisateur</p>
          <h1 className={s.titrePage}>Crédit entrée libre</h1>
        </div>
        {erreurChargement && (
          <Avis ton="danger">
            <p>{erreurChargement}</p>
            <div>
              <Bouton variante="secondaire" onClick={() => void charger()}>
                Réessayer
              </Bouton>
            </div>
          </Avis>
        )}
        {!credit && !erreurChargement && <Chargement />}
        {credit && <ContenuCredit credit={credit} onActualiser={() => void charger()} />}
      </div>
    </CadreOrganisateur>
  );
}

function ContenuCredit({ credit, onActualiser }: { credit: Credit; onActualiser: () => void }) {
  const { tarif } = credit;
  const enAttente = credit.recharges.some((r) => r.status === 'EN_ATTENTE');
  return (
    <>
      <Panneau titre="Solde" id="solde">
        <p className={s.titrePage}>{formatFcfa(credit.soldeFcfa)}</p>
        <p className={s.ligneDetail}>
          Chaque billet gratuit émis coûte {formatFcfa(tarif.tarifPetitFcfa)} si l'événement a {tarif.seuilPlaces} places
          ou moins (toutes catégories confondues), {formatFcfa(tarif.tarifGrandFcfa)} au-delà. Le montant est débité à
          l'émission du billet. Sans crédit suffisant, les entrées gratuites sont refusées aux participants.
        </p>
        <p className={s.ligneDetail}>
          Le crédit n'est pas remboursable et reste dû si un événement est annulé.
        </p>
        {enAttente && (
          <Avis>
            <p>Une recharge attend la confirmation du paiement. Le solde se met à jour dès sa réception.</p>
            <div>
              <Bouton variante="secondaire" onClick={onActualiser}>
                Actualiser
              </Bouton>
            </div>
          </Avis>
        )}
      </Panneau>

      <FormulaireRecharge min={credit.rechargeMinFcfa} max={credit.rechargeMaxFcfa} />

      <Panneau titre="Mouvements" id="mouvements">
        {credit.mouvements.length === 0 ? (
          <p className={s.ligneDetail}>Aucun mouvement pour l'instant.</p>
        ) : (
          <Tableau
            legende="Mouvements du crédit"
            cle={(m) => m.id}
            lignes={credit.mouvements}
            colonnes={[
              { titre: 'Date', cellule: (m) => formatDateCourte(m.creeLe) },
              { titre: 'Opération', cellule: (m) => (m.type === 'RECHARGE' ? 'Recharge' : 'Billets gratuits émis') },
              {
                titre: 'Montant',
                nombre: true,
                cellule: (m) => `${m.montantFcfa > 0 ? '+' : '−'} ${formatFcfa(Math.abs(m.montantFcfa))}`,
              },
            ]}
          />
        )}
      </Panneau>

      {credit.recharges.length > 0 && (
        <Panneau titre="Recharges" id="recharges">
          <Tableau
            legende="Recharges du crédit"
            cle={(r) => r.id}
            lignes={credit.recharges}
            colonnes={[
              { titre: 'Date', cellule: (r) => formatDateCourte(r.creeLe) },
              { titre: 'Montant', nombre: true, cellule: (r) => formatFcfa(r.montantFcfa) },
              {
                titre: 'État',
                cellule: (r) => <Pastille ton={STATUTS_RECHARGE[r.status].ton}>{STATUTS_RECHARGE[r.status].libelle}</Pastille>,
              },
            ]}
          />
          {credit.recharges.some((r) => r.status === 'ANOMALIE') && (
            <Avis ton="attention">
              <p>
                Une recharge « à régulariser » a été payée d'un autre montant que celui demandé : rien n'a été crédité.
                Contactez l'équipe Billet+.
              </p>
            </Avis>
          )}
        </Panneau>
      )}
    </>
  );
}

function FormulaireRecharge({ min, max }: { min: number; max: number }) {
  const [montant, setMontant] = useState('');
  const [moyen, setMoyen] = useState<Moyen>('WAVE');
  const [telephone, setTelephone] = useState('');
  const [erreurs, setErreurs] = useState<{ montant?: string; telephone?: string }>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    const valeur = Number(montant.replace(/\s/g, ''));
    const nouvelles: { montant?: string; telephone?: string } = {};
    if (!Number.isInteger(valeur) || valeur < min || valeur > max) {
      nouvelles.montant = `Montant entier entre ${formatFcfa(min)} et ${formatFcfa(max)}.`;
    }
    if (!estMobileSenegalais(telephone)) {
      nouvelles.telephone = 'Entrez un numéro de mobile sénégalais à 9 chiffres, par exemple 77 123 45 67.';
    }
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0) return;
    setEnvoi(true);
    try {
      const lien = await recharger({ montantFcfa: valeur, moyen, telephone: versE164(telephone) });
      allerPayer(lien.url);
    } catch (e2) {
      setErreur(messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <Panneau titre="Recharger" id="recharger">
      <form className={s.formulaire} onSubmit={valider} noValidate>
        <ChampTexte
          libelle="Montant (FCFA)"
          valeur={montant}
          onChange={setMontant}
          requis
          inputMode="numeric"
          erreur={erreurs.montant}
          aide={`Entre ${formatFcfa(min)} et ${formatFcfa(max)}.`}
        />
        <ChoixCartes legende="Moyen de paiement" nom="moyen-recharge" options={MOYENS} valeur={moyen} onChange={setMoyen} />
        <ChampTelephone
          valeur={telephone}
          onChange={setTelephone}
          aide="Le numéro Wave ou Orange Money qui paie la recharge."
          erreur={erreurs.telephone}
        />
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
          </Avis>
        )}
        <Bouton type="submit" disabled={envoi}>
          {envoi ? 'Redirection vers le paiement…' : 'Payer la recharge'}
        </Bouton>
      </form>
    </Panneau>
  );
}
