package com.billetplus.orders.api;

import com.billetplus.orders.api.dto.BilletDto;
import com.billetplus.orders.api.dto.CommandeCreeeResponse;
import com.billetplus.orders.api.dto.CommandeDto;
import com.billetplus.orders.api.dto.CreateOrderRequest;
import com.billetplus.orders.api.dto.LienPaiementDto;
import com.billetplus.orders.api.dto.BilletTransfereResponse;
import com.billetplus.orders.api.dto.OuvrirPaiementRequest;
import com.billetplus.orders.api.dto.TransfererBilletRequest;
import com.billetplus.orders.application.usecase.ConsulterBilletsUseCase;
import com.billetplus.orders.application.usecase.ConsulterCommandeUseCase;
import com.billetplus.orders.application.usecase.CreateOrderUseCase;
import com.billetplus.orders.application.usecase.OuvrirPaiementUseCase;
import com.billetplus.orders.application.usecase.TransfererBilletUseCase;
import com.billetplus.orders.domain.Order;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Commandes sans compte (ADR 0006). Les deux routes sont publiques (voir
 * SecurityConfig) : l'identité de l'acheteur est le jeton d'accès de la commande.
 *
 * Cache-Control: no-store sur les deux réponses : elles contiennent le jeton ou
 * le détail d'un achat, qu'aucun cache (navigateur, proxy) ne doit conserver.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** En-tête du jeton : jamais dans l'URL, qui finit dans les journaux et l'historique. */
    public static final String EN_TETE_JETON = "X-Jeton-Commande";

    private final CreateOrderUseCase createOrder;
    private final ConsulterCommandeUseCase consulterCommande;
    private final OuvrirPaiementUseCase ouvrirPaiement;
    private final ConsulterBilletsUseCase consulterBillets;
    private final TransfererBilletUseCase transfererBillet;

    public OrderController(CreateOrderUseCase createOrder, ConsulterCommandeUseCase consulterCommande,
                           OuvrirPaiementUseCase ouvrirPaiement, ConsulterBilletsUseCase consulterBillets,
                           TransfererBilletUseCase transfererBillet) {
        this.createOrder = createOrder;
        this.consulterCommande = consulterCommande;
        this.ouvrirPaiement = ouvrirPaiement;
        this.consulterBillets = consulterBillets;
        this.transfererBillet = transfererBillet;
    }

    @PostMapping
    public ResponseEntity<CommandeCreeeResponse> creer(@Valid @RequestBody CreateOrderRequest request) {
        Order.CommandeCreee creee = createOrder.execute(request.eventId(), request.telephone(),
            request.lignes().stream()
                .map(l -> new CreateOrderUseCase.LigneDemandee(l.categoryId(), l.quantite()))
                .toList());
        return ResponseEntity.created(URI.create("/api/orders/" + creee.commande().getId()))
            .cacheControl(CacheControl.noStore())
            .body(CommandeCreeeResponse.from(creee));
    }

    /** Inconnue ou jeton absent ou faux : 404, sans distinction. */
    @GetMapping("/{id}")
    public ResponseEntity<CommandeDto> consulter(@PathVariable UUID id,
                                                 @RequestHeader(name = EN_TETE_JETON, required = false) String jeton) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(CommandeDto.from(consulterCommande.execute(id, jeton)));
    }

    /**
     * Billets d'une commande payée, avec leur QR (jeton porteur) : 404 sans le bon
     * jeton, 409 tant que la commande n'est pas payée (la PWA réessaie).
     */
    @GetMapping("/{id}/billets")
    public ResponseEntity<List<BilletDto>> billets(@PathVariable UUID id,
                                                   @RequestHeader(name = EN_TETE_JETON, required = false) String jeton) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(consulterBillets.execute(id, jeton).stream().map(BilletDto::from).toList());
    }

    /**
     * Transfère un billet de la commande à un autre mobile (ADR 0008) : définitif. Renvoie
     * le jeton du destinataire, une seule fois. 404 sans le bon jeton de commande ou pour
     * un billet d'une autre commande, 400 pour un mobile invalide, 409 si le billet est
     * déjà transféré, plus valable, ou si l'événement n'est plus en vente.
     */
    @PostMapping("/{id}/billets/{ticketId}/transfert")
    public ResponseEntity<BilletTransfereResponse> transferer(@PathVariable UUID id, @PathVariable UUID ticketId,
                                                              @RequestHeader(name = EN_TETE_JETON, required = false) String jeton,
                                                              @Valid @RequestBody TransfererBilletRequest request) {
        String jetonDestinataire = transfererBillet.execute(id, jeton, ticketId, request.telephone());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(new BilletTransfereResponse(ticketId, jetonDestinataire));
    }

    /**
     * Ouvre le paiement chez la passerelle (ADR 0007) et renvoie le lien à ouvrir.
     * Même jeton que la lecture. Redemander le même moyen renvoie le même lien.
     */
    @PostMapping("/{id}/paiement")
    public ResponseEntity<LienPaiementDto> payer(@PathVariable UUID id,
                                                 @RequestHeader(name = EN_TETE_JETON, required = false) String jeton,
                                                 @Valid @RequestBody OuvrirPaiementRequest request) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(LienPaiementDto.from(ouvrirPaiement.execute(id, jeton, request.moyen())));
    }
}
