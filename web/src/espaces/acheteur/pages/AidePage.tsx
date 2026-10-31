import type { ReactNode } from 'react';
import { BoutonLien, Cadre, Icone, type NomIcone } from '@/ui';
import s from './pages.module.css';

interface Question {
  question: string;
  reponse: ReactNode;
}

interface Rubrique {
  titre: string;
  icone: NomIcone;
  questions: ReadonlyArray<Question>;
}

/*
 * Réponses fidèles au fonctionnement réel : achat sans compte (ADR 0006), paiement par la
 * passerelle (ADR 0007), billets gardés sur l'appareil (ADR 0004), remboursement manuel
 * au MVP (décision B). Aucun délai, contact ni frais inventé.
 */
const RUBRIQUES: ReadonlyArray<Rubrique> = [
  {
    titre: 'Acheter',
    icone: 'billet',
    questions: [
      {
        question: 'Faut-il créer un compte ?',
        reponse: (
          <p>
            Non. Votre numéro de mobile sénégalais suffit : il identifie votre commande. Choisissez vos billets, indiquez
            votre numéro, puis payez.
          </p>
        ),
      },
      {
        question: 'Combien de billets puis-je acheter ?',
        reponse: <p>Jusqu'à 20 billets par catégorie dans une même commande.</p>,
      },
      {
        question: 'Mes places sont-elles réservées pendant que je paie ?',
        reponse: (
          <p>
            Oui, pendant un temps limité, affiché par un compte à rebours sur l'écran de votre commande. Passé ce délai
            sans paiement, les places sont remises en vente et rien n'est prélevé.
          </p>
        ),
      },
    ],
  },
  {
    titre: 'Payer',
    icone: 'carte',
    questions: [
      {
        question: 'Quels moyens de paiement sont acceptés ?',
        reponse: <p>Wave et Orange Money. Vous validez le paiement sur votre téléphone, puis vous revenez sur Billet+.</p>,
      },
      {
        question: 'J’ai payé mais je ne vois pas mes billets',
        reponse: (
          <p>
            La confirmation peut prendre quelques secondes : l'écran de votre commande se met à jour tout seul. Ouvrez
            « Mes billets » sur le téléphone qui a servi à l'achat.
          </p>
        ),
      },
      {
        question: 'Mon paiement a été reçu, mais il n’y avait plus de places',
        reponse: (
          <p>
            Cela peut arriver si le paiement arrive après la fin de la réservation. Aucun billet n'est émis et votre
            commande l'indique : le remboursement est traité à la main par l'équipe Billet+.
          </p>
        ),
      },
    ],
  },
  {
    titre: 'Mes billets',
    icone: 'cadenas',
    questions: [
      {
        question: 'Où sont mes billets ?',
        reponse: (
          <p>
            Sur l'appareil qui a servi à l'achat, dans « Mes billets ». Ils s'affichent même sans connexion. Gardez ce
            navigateur et ne videz pas ses données avant l'événement.
          </p>
        ),
      },
      {
        question: 'Et si je change de téléphone ?',
        reponse: (
          <p>
            Les billets ne se retrouvent pas depuis un autre appareil. Par précaution, téléchargez-les en PDF depuis
            l'écran de votre commande : le PDF s'imprime ou s'ouvre sur n'importe quel téléphone.
          </p>
        ),
      },
      {
        question: 'Puis-je partager mon billet ?',
        reponse: (
          <p>
            Évitez-le : une seule entrée est prévue par billet. Une copie (photo, capture d'écran) présentée avant vous
            serait acceptée à votre place, et vous pourriez être refusé à l'entrée.
          </p>
        ),
      },
    ],
  },
  {
    titre: 'À l’entrée',
    icone: 'bouclier',
    questions: [
      {
        question: 'Que dois-je présenter ?',
        reponse: (
          <p>
            Le QR code de votre billet, à l'écran (luminosité au maximum) ou imprimé. Le code de secours écrit sous le QR
            aide l'équipe en cas de besoin.
          </p>
        ),
      },
      {
        question: 'Un faux billet peut-il passer ?',
        reponse: (
          <p>
            Un QR code fabriqué ou modifié est refusé : chaque billet est signé par Billet+ et sa signature est vérifiée à
            l'entrée, même quand le contrôle se fait sans réseau. Une copie d'un vrai billet n'est pas un faux : d'où le
            conseil de ne pas partager le vôtre.
          </p>
        ),
      },
    ],
  },
];

export function AidePage() {
  return (
    <Cadre>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Questions fréquentes</p>
          <h1 className={s.titrePage}>Aide</h1>
        </div>
        <div className={s.aide}>
          {RUBRIQUES.map((r) => (
            <section key={r.titre} className={s.aideRubrique} aria-labelledby={`aide-${r.icone}`}>
              <h2 id={`aide-${r.icone}`} className={s.aideTitre}>
                <span className={s.infoIcone}>
                  <Icone nom={r.icone} />
                </span>
                {r.titre}
              </h2>
              {r.questions.map((q) => (
                <details key={q.question} className={s.question}>
                  <summary>{q.question}</summary>
                  <div className={s.reponse}>{q.reponse}</div>
                </details>
              ))}
            </section>
          ))}
        </div>
        <section className={s.avantages} aria-label="Vous organisez ou contrôlez un événement ?">
          <div className={s.avantage}>
            <span className={s.infoIcone}>
              <Icone nom="calendrier" />
            </span>
            <div>
              <p className={s.infoTitre}>Vous organisez un événement ?</p>
              <p className={s.infoDetail}>Publiez-le, vendez vos billets et suivez vos ventes.</p>
              <div className={s.lienAide}>
                <BoutonLien to="/organisateur" variante="secondaire">
                  Espace organisateur
                </BoutonLien>
              </div>
            </div>
          </div>
          <div className={s.avantage}>
            <span className={s.infoIcone}>
              <Icone nom="bouclier" />
            </span>
            <div>
              <p className={s.infoTitre}>Vous contrôlez une entrée ?</p>
              <p className={s.infoDetail}>Connectez-vous avec le compte créé par l'organisateur.</p>
              <div className={s.lienAide}>
                <BoutonLien to="/scanner" variante="secondaire">
                  Contrôle d'entrée
                </BoutonLien>
              </div>
            </div>
          </div>
        </section>
      </div>
    </Cadre>
  );
}
