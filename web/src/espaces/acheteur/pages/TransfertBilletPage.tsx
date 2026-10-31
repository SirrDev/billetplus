import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { chiffresNationaux, estMobileSenegalais, formatDateCourte, versE164 } from '@/commun/format';
import { ApiError, messageErreur } from '@/commun/http';
import { Avis, Bouton, BoutonLien, Cadre, ChampTelephone, Chargement, EtatVide, Icone, Panneau } from '@/ui';
import { lienBilletRecu, transfererBillet } from '../api';
import { enregistrerTransfert, lireCommandeLocale, type CommandeLocale } from '../commandesLocales';
import s from './pages.module.css';

/** « 781234567 » -> « 78 ••• •• 67 », comme le masque du backend. */
function masquer(saisie: string): string {
  const c = chiffresNationaux(saisie);
  return `${c.slice(0, 2)} ••• •• ${c.slice(7, 9)}`;
}

/**
 * Transfert d'un billet à un autre mobile (ADR 0008). Définitif : l'ancien QR n'est plus
 * accepté au contrôle en ligne, et un billet ne se transfère qu'une fois. Billet+
 * n'envoie aucun SMS : l'expéditeur reçoit un lien à transmettre lui-même. Le
 * destinataire l'ouvre avec son numéro.
 */
export function TransfertBilletPage() {
  const { id = '', ticketId = '' } = useParams();
  const [locale, setLocale] = useState<CommandeLocale | null | undefined>(undefined);
  const [telephone, setTelephone] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [erreurs, setErreurs] = useState<{ telephone?: string; confirmation?: string }>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const [resultat, setResultat] = useState<{ lien: string; destinataire: string; nonGarde: boolean } | null>(null);

  useEffect(() => {
    let annule = false;
    lireCommandeLocale(id)
      .then((c) => !annule && setLocale(c ?? null))
      .catch(() => !annule && setLocale(null));
    return () => {
      annule = true;
    };
  }, [id]);

  if (locale === undefined) {
    return (
      <Cadre etroit>
        <Chargement />
      </Cadre>
    );
  }
  const billet = locale?.billets?.find((b) => b.id === ticketId);
  if (!locale || !billet) {
    return (
      <Cadre etroit>
        <EtatVide
          niveau={1}
          icone="billet"
          titre="Billet introuvable sur cet appareil"
          texte="Un billet se transfère depuis l'appareil qui a servi à l'achat."
          action={<BoutonLien to="/billets">Mes billets</BoutonLien>}
        />
      </Cadre>
    );
  }

  const retour = `/commandes/${locale.id}`;
  const lienGarde = locale.liensTransfert?.[ticketId];
  const affiche = resultat ?? (lienGarde ? { lien: lienGarde, destinataire: billet.transfereA ?? '', nonGarde: false } : null);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    const nouvelles: { telephone?: string; confirmation?: string } = {};
    if (!estMobileSenegalais(telephone)) nouvelles.telephone = 'Entrez un numéro de mobile sénégalais à 9 chiffres, par exemple 77 123 45 67.';
    else if (chiffresNationaux(confirmation) !== chiffresNationaux(telephone)) {
      nouvelles.confirmation = 'Les deux numéros ne correspondent pas.';
    }
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0) return;

    setEnvoi(true);
    try {
      const reponse = await transfererBillet(locale.id, locale.jeton, ticketId, versE164(telephone));
      const lien = lienBilletRecu(window.location.origin, ticketId, reponse.jeton);
      const destinataire = masquer(telephone);
      // Le jeton n'est remis qu'une fois : le garder avant tout affichage.
      let nonGarde = false;
      try {
        await enregistrerTransfert(locale.id, ticketId, lien, destinataire);
      } catch {
        nonGarde = true;
      }
      setResultat({ lien, destinataire, nonGarde });
    } catch (e2) {
      setErreur(
        e2 instanceof ApiError && e2.status === 404
          ? 'Commande introuvable : elle a peut-être été achetée depuis un autre appareil.'
          : messageErreur(e2),
      );
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <Cadre etroit>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>
            {billet.categorie} · {locale.titre}
          </p>
          <h1 className={s.titrePage}>{affiche ? 'Billet transféré' : 'Transférer ce billet'}</h1>
        </div>

        {affiche ? (
          <LienAEnvoyer
            lien={affiche.lien}
            destinataire={affiche.destinataire}
            nonGarde={affiche.nonGarde}
            titre={locale.titre}
            date={formatDateCourte(locale.debuteLe)}
          />
        ) : billet.statut !== 'EMIS' || !billet.qr ? (
          <Avis ton="attention">
            <p>Ce billet ne peut plus être transféré : il n'est plus valable ou a déjà été transféré.</p>
          </Avis>
        ) : (
          <form className={s.formulaire} onSubmit={valider} noValidate>
            <Avis ton="attention">
              <p>
                <strong>Le transfert est définitif.</strong> Le QR affiché sur cet appareil ne sera plus accepté à
                l'entrée : seul le destinataire pourra présenter le nouveau. Un billet ne se transfère qu'une fois.
              </p>
            </Avis>
            <Panneau titre="Destinataire" id="destinataire">
              <ChampTelephone
                libelle="Son numéro de mobile"
                valeur={telephone}
                onChange={setTelephone}
                aide="Il lui servira à ouvrir le billet."
                erreur={erreurs.telephone}
              />
              <ChampTelephone
                libelle="Confirmez son numéro"
                valeur={confirmation}
                onChange={setConfirmation}
                erreur={erreurs.confirmation}
              />
              <p className={s.note}>
                Billet+ n'envoie pas de SMS : vous recevrez un lien à lui envoyer vous-même (WhatsApp, SMS…).
              </p>
            </Panneau>
            {erreur && (
              <Avis ton="danger">
                <p>{erreur}</p>
              </Avis>
            )}
            <div className={s.entete}>
              <Bouton type="submit" grand disabled={envoi}>
                {envoi ? 'Transfert…' : 'Transférer définitivement'}
              </Bouton>
            </div>
          </form>
        )}

        <div>
          <BoutonLien to={retour} variante="secondaire">
            <Icone nom="retour" />
            Retour à la commande
          </BoutonLien>
        </div>
      </div>
    </Cadre>
  );
}

function LienAEnvoyer({
  lien,
  destinataire,
  nonGarde,
  titre,
  date,
}: {
  lien: string;
  destinataire: string;
  nonGarde: boolean;
  titre: string;
  date: string;
}) {
  const [copie, setCopie] = useState<'copie' | 'echec' | null>(null);
  const message = `Voici ton billet pour ${titre} (${date}) sur Billet+. Ouvre ce lien et saisis ton numéro : ${lien}`;
  const partage = typeof navigator.share === 'function';

  const copier = async () => {
    try {
      await navigator.clipboard.writeText(lien);
      setCopie('copie');
    } catch {
      setCopie('echec');
    }
  };

  return (
    <>
      <Avis ton="succes">
        <p>
          Billet transféré{destinataire ? ` au ${destinataire}` : ''}. Envoyez-lui maintenant ce lien : avec son numéro,
          il ouvre le billet.
        </p>
      </Avis>
      {nonGarde && (
        <Avis ton="attention">
          <p>Ce lien n'a pas pu être gardé sur cet appareil : copiez-le et envoyez-le avant de quitter cette page.</p>
        </Avis>
      )}
      <Panneau titre="Lien à envoyer" id="lien">
        <p className={s.note} style={{ overflowWrap: 'anywhere' }}>
          {lien}
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          <Bouton onClick={() => void copier()}>
            <Icone nom="copier" />
            Copier le lien
          </Bouton>
          {partage && (
            <Bouton
              variante="secondaire"
              onClick={() => void navigator.share({ title: `Billet ${titre}`, text: message }).catch(() => undefined)}
            >
              Partager
            </Bouton>
          )}
          <a
            className={s.lienAide}
            href={`https://wa.me/?text=${encodeURIComponent(message)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            Envoyer par WhatsApp
          </a>
        </div>
        <p aria-live="polite" className={s.note}>
          {copie === 'copie' ? 'Lien copié.' : copie === 'echec' ? 'Copie impossible : sélectionnez le lien à la main.' : ''}
        </p>
        <p className={s.note}>
          Ce lien donne accès au billet : envoyez-le seulement à son destinataire. Il reste disponible ici, depuis votre
          commande, si vous devez le renvoyer.
        </p>
      </Panneau>
    </>
  );
}
