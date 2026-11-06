package com.billetplus.events.infrastructure;

import com.billetplus.events.application.VilleRepository;
import com.billetplus.events.domain.Ville;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Adaptateur du port VilleRepository : 32 lignes, triées en mémoire. */
@Repository
class JpaVilleRepositoryAdapter implements VilleRepository {

    private final SpringDataVilleRepository springRepo;

    JpaVilleRepositoryAdapter(SpringDataVilleRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<Ville> findByCode(String code) {
        return code == null ? Optional.empty() : springRepo.findById(code);
    }

    @Override
    public List<Ville> toutes() {
        return springRepo.findAll().stream()
            .sorted(Comparator.comparing(Ville::getPays).thenComparingInt(Ville::getOrdre))
            .toList();
    }
}
