package com.billetplus.orders.api;

import com.billetplus.orders.api.dto.CommandeCreeeResponse;
import com.billetplus.orders.api.dto.CommandeDto;
import com.billetplus.orders.api.dto.CreateOrderRequest;
import com.billetplus.orders.application.usecase.ConsulterCommandeUseCase;
import com.billetplus.orders.application.usecase.CreateOrderUseCase;
import com.billetplus.orders.domain.Order;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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

    public OrderController(CreateOrderUseCase createOrder, ConsulterCommandeUseCase consulterCommande) {
        this.createOrder = createOrder;
        this.consulterCommande = consulterCommande;
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
}
