package com.billetplus.events.api.dto;

import com.billetplus.events.application.PageEvenements;

import java.util.List;

/**
 * Une page de la liste publique. {@code total} et {@code pages} permettent au
 * client d'afficher la pagination sans deviner s'il reste des résultats.
 */
public record PageEvenementsPublicsDto(
    List<EvenementPublicDto> contenu,
    int page,
    int taille,
    long total,
    int pages
) {
    public static PageEvenementsPublicsDto from(PageEvenements page) {
        int pages = (int) ((page.total() + page.taille() - 1) / page.taille());
        return new PageEvenementsPublicsDto(
            page.contenu().stream().map(EvenementPublicDto::from).toList(),
            page.page(), page.taille(), page.total(), pages
        );
    }
}
