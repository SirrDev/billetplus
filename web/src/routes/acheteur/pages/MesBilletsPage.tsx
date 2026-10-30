import { useEffect, useState } from 'react';
import {
  BottomNav,
  ButtonLink,
  EmptyState,
  ListRow,
  Notice,
  Screen,
  TopBar,
  buyerNavItems,
  formatEventDate,
} from '@/design-system';
import m from '@/shared/ui/miseEnPage.module.css';
import { listerCommandesLocales, type CommandeLocale } from '../commandesLocales';

/**
 * Commandes passées depuis cet appareil (IndexedDB). Sans compte (ADR 0006), c'est
 * la seule liste possible : chaque ligne ouvre la commande, lue sur le serveur
 * avec son jeton.
 */
export function MesBilletsPage() {
  const [commandes, setCommandes] = useState<CommandeLocale[] | null>(null);

  useEffect(() => {
    listerCommandesLocales()
      .then(setCommandes)
      .catch(() => setCommandes([]));
  }, []);

  return (
    <Screen withNav>
      <div className={m.pile}>
        <TopBar title="Mes billets" large />
        {commandes === null ? <Notice icon="clock">Chargement…</Notice> : null}
        {commandes?.length === 0 ? (
          <EmptyState
            icon="ticket"
            title="Aucun billet pour l'instant"
            description="Les billets achetés apparaîtront ici et resteront disponibles sur cet appareil."
            actions={<ButtonLink to="/">Découvrir les événements</ButtonLink>}
          />
        ) : null}
        <div className={m.grille}>
          {commandes?.map((commande) => (
            <ListRow
              key={commande.id}
              icon="ticket"
              label={commande.titre}
              value={formatEventDate(new Date(commande.debuteLe), { withTime: false })}
              to={`/commandes/${commande.id}`}
            />
          ))}
        </div>
        {commandes && commandes.length > 0 ? (
          <Notice icon="lock">Vos commandes sont enregistrées sur ce téléphone uniquement.</Notice>
        ) : null}
      </div>
      <BottomNav items={buyerNavItems} />
    </Screen>
  );
}
