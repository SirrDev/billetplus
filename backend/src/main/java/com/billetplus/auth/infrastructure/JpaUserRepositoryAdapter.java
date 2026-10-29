package com.billetplus.auth.infrastructure;

import com.billetplus.auth.application.UserRepository;
import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springRepo;

    JpaUserRepositoryAdapter(SpringDataUserRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return springRepo.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springRepo.findByEmail(email.toLowerCase().trim());
    }

    @Override
    public User save(User user) {
        return springRepo.save(user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springRepo.existsByEmail(email.toLowerCase().trim());
    }

    @Override
    public List<User> findByCreePar(UUID createurId) {
        return springRepo.findByCreeParOrderByCreeLeAsc(createurId);
    }

    @Override
    public boolean existsByRole(Role role) {
        return springRepo.existsByRole(role);
    }
}
