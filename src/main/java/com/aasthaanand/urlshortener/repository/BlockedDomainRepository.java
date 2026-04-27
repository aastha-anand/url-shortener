package com.urlshortener.repository;

import com.urlshortener.model.BlockedDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockedDomainRepository extends JpaRepository<BlockedDomain, Long> {
    boolean existsByDomainAndActiveTrue(String domain);
    Optional<BlockedDomain> findByDomain(String domain);
    List<BlockedDomain> findByActiveTrue();
}
