import { BottomNav, ListRow, Notice, Screen, TopBar, buyerNavItems } from '@/design-system';
import m from '@/shared/ui/miseEnPage.module.css';

/** Achat sans compte (ADR 0006) : cet écran explique où sont les billets, sans connexion à proposer. */
export function ComptePage() {
  return (
    <Screen withNav>
      <div className={m.pile}>
        <TopBar title="Compte" large />
        <Notice icon="shield">
          Pas besoin de compte pour acheter : votre numéro de téléphone suffit, et vos billets sont enregistrés sur cet
          appareil.
        </Notice>
        <ListRow icon="ticket" label="Mes billets" to="/billets" />
        <ListRow icon="help" label="Organisateur ? Accédez à votre espace" to="/organisateur" />
      </div>
      <BottomNav items={buyerNavItems} />
    </Screen>
  );
}
