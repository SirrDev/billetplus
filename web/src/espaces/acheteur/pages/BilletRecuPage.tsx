import { useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { estMobileSenegalais, formatDateCourte, versE164 } from '@/commun/format';
import { ApiError, messageErreur } from '@/commun/http';
import { Avis, Bouton, BoutonLien, Cadre, ChampTelephone, Chargement, EtatVide, Panneau, PasseBillet } from '@/ui';
import { consulterBilletRecu, lireEvenement } from '../api';
import { enregistrerBilletRecu, lireBilletRecu, type BilletRecuLocal } from '../commandesLocales';
import s from './pages.module.css';

/**
 * Billet reçu par transfert (ADR 0008), sans compte. Le lien porte le jeton dans son
 * fragment (#, jamais envoyé au serveur) ; le destinataire saisit son numéro, second
 * facteur. Le billet est ensuite gardé sur l'appareil (affichable sans réseau) et le
 * fragment retiré de l'adresse.
 */
export function BilletRecuPage() {
  const { ticketId = '' } = useParams();
  const { hash } = useLocation();
  const navigate = useNavigate();
  // Jeton Base64 URL : rien à décoder (un fragment malformé ferait planter decodeURIComponent).
  const jetonDuLien = hash.startsWith('#') ? hash.slice(1) : '';

  const [local, setLocal] = useState<BilletRecuLocal | null | undefined>(undefined);
  const [telephone, setTelephone] = useState('');
  const [erreurTelephone, setErreurTelephone] = useState<string | undefined>();
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  // Déjà ouvert sur cet appareil : affichage immédiat, puis statut rafraîchi si le réseau répond.
  useEffect(() => {
    let annule = false;
    lireBilletRecu(ticketId)
      .catch(() => undefined)
      .then((garde) => {
        if (annule) return;
        setLocal(garde ?? null);
        if (!garde) return;
        consulterBilletRecu(garde.id, garde.jeton, garde.telephone)
          .then(async (billet) => {
            const misAJour = { ...garde, billet };
            if (!annule) setLocal(misAJour);
            await enregistrerBilletRecu(misAJour);
          })
          .catch(() => undefined);
      });
    return () => {
      annule = true;
    };
  }, [ticketId]);

  // Le jeton ne reste pas dans l'adresse une fois le billet gardé.
  useEffect(() => {
    if (local && hash) navigate({ hash: '' }, { replace: true });
  }, [local, hash, navigate]);

  const ouvrir = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    if (!estMobileSenegalais(telephone)) {
      setErreurTelephone('Entrez un numéro de mobile sénégalais à 9 chiffres, par exemple 77 123 45 67.');
      return;
    }
    setErreurTelephone(undefined);
    setEnvoi(true);
    try {
      const e164 = versE164(telephone);
      const billet = await consulterBilletRecu(ticketId, jetonDuLien, e164);
      const evenement = await lireEvenement(billet.eventId).catch(() => null);
      const recu: BilletRecuLocal = {
        id: billet.id,
        jeton: jetonDuLien,
        telephone: e164,
        titre: evenement?.titre ?? 'Événement Billet+',
        debuteLe: evenement?.debuteLe ?? '',
        lieu: evenement?.venueNom ?? '',
        recuLe: new Date().toISOString(),
        billet,
      };
      await enregistrerBilletRecu(recu).catch(() => undefined);
      setLocal(recu);
    } catch (e2) {
      setErreur(
        e2 instanceof ApiError && e2.status === 404
          ? "Lien ou numéro incorrect. Vérifiez le numéro auquel le billet vous a été transféré, ou demandez à l'expéditeur de vous renvoyer le lien."
          : messageErreur(e2),
      );
    } finally {
      setEnvoi(false);
    }
  };

  if (local === undefined) {
    return (
      <Cadre etroit>
        <Chargement />
      </Cadre>
    );
  }

  if (local) {
    const sousTitre = [local.debuteLe && formatDateCourte(local.debuteLe), local.lieu].filter(Boolean).join(' · ');
    return (
      <Cadre etroit>
        <div className={s.pile}>
          <div>
            <h1 className={s.titrePage}>{local.titre}</h1>
            <p className={s.sousTitre}>Billet reçu</p>
          </div>
          <Avis ton="succes" icone="cadenas">
            <p>Billet enregistré sur cet appareil : il s'affiche même sans réseau, dans « Mes billets ».</p>
          </Avis>
          <div className={s.billets}>
            <PasseBillet
              id={local.billet.id}
              categorie={local.billet.categorie}
              titre={local.titre}
              sousTitre={sousTitre}
              qr={local.billet.qr}
              statut={local.billet.statut}
            />
          </div>
          <div>
            <BoutonLien to="/billets" variante="secondaire">
              Mes billets
            </BoutonLien>
          </div>
        </div>
      </Cadre>
    );
  }

  if (!jetonDuLien) {
    return (
      <Cadre etroit>
        <EtatVide
          niveau={1}
          icone="billet"
          titre="Lien incomplet"
          texte="Ce lien ne contient pas la clé du billet. Demandez à l'expéditeur de vous le renvoyer en entier."
          action={<BoutonLien to="/">Découvrir les événements</BoutonLien>}
        />
      </Cadre>
    );
  }

  return (
    <Cadre etroit>
      <div className={s.pile}>
        <div>
          <h1 className={s.titrePage}>Ouvrir mon billet</h1>
          <p className={s.sousTitre}>Billet transféré</p>
        </div>
        <form className={s.formulaire} onSubmit={ouvrir} noValidate>
          <Panneau titre="Votre numéro" id="numero">
            <ChampTelephone
              libelle="Votre numéro de mobile"
              valeur={telephone}
              onChange={setTelephone}
              aide="Le numéro auquel le billet vous a été transféré."
              erreur={erreurTelephone}
            />
          </Panneau>
          {erreur && (
            <Avis ton="danger">
              <p>{erreur}</p>
            </Avis>
          )}
          <div>
            <Bouton type="submit" grand disabled={envoi}>
              {envoi ? 'Ouverture…' : 'Ouvrir mon billet'}
            </Bouton>
          </div>
        </form>
      </div>
    </Cadre>
  );
}
