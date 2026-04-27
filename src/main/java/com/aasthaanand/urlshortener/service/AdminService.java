package com.urlshortener.service;

import com.urlshortener.dto.BlockDomainRequest;
import com.urlshortener.exception.BadRequestException;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.model.AuditLog;
import com.urlshortener.model.BlockedDomain;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.model.User;
import com.urlshortener.repository.AuditLogRepository;
import com.urlshortener.repository.BlockedDomainRepository;
import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final BlockedDomainRepository blockedDomainRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final CacheService cacheService;

    @Transactional
    public BlockedDomain blockDomain(BlockDomainRequest request, Long adminId) {
        String domain = request.getDomain().toLowerCase().trim();
        if (blockedDomainRepository.existsByDomainAndActiveTrue(domain)) {
            throw new BadRequestException("Domain already blocked: " + domain);
        }

        BlockedDomain bd = blockedDomainRepository.findByDomain(domain)
                .map(existing -> { existing.setActive(true); existing.setReason(request.getReason()); return existing; })
                .orElse(BlockedDomain.builder().domain(domain).reason(request.getReason()).active(true).build());

        bd = blockedDomainRepository.save(bd);
        audit(adminId, "BLOCK_DOMAIN", bd.getId(), "BlockedDomain", "Blocked: " + domain);
        log.info("Admin {} blocked domain: {}", adminId, domain);
        return bd;
    }

    @Transactional
    public void unblockDomain(Long id, Long adminId) {
        BlockedDomain bd = blockedDomainRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blocked domain not found"));
        bd.setActive(false);
        blockedDomainRepository.save(bd);
        audit(adminId, "UNBLOCK_DOMAIN", id, "BlockedDomain", "Unblocked: " + bd.getDomain());
    }

    public List<BlockedDomain> listBlockedDomains() {
        return blockedDomainRepository.findByActiveTrue();
    }

    @Transactional
    public void deactivateUrl(Long urlId, Long adminId) {
        ShortUrl shortUrl = shortUrlRepository.findById(urlId)
                .orElseThrow(() -> new ResourceNotFoundException("URL not found"));
        shortUrl.setStatus(ShortUrl.Status.INACTIVE);
        shortUrlRepository.save(shortUrl);
        cacheService.evict(shortUrl.getShortCode());
        audit(adminId, "DEACTIVATE_URL", urlId, "ShortUrl", "Deactivated: " + shortUrl.getShortCode());
        log.info("Admin {} deactivated URL: {}", adminId, shortUrl.getShortCode());
    }

    @Transactional
    public void banUser(Long userId, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(User.Status.BANNED);
        userRepository.save(user);
        audit(adminId, "BAN_USER", userId, "User", "Banned user: " + user.getEmail());
    }

    private void audit(Long actorId, String action, Long targetId, String targetType, String details) {
        auditLogRepository.save(AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .targetId(targetId)
                .targetType(targetType)
                .details(details)
                .build());
    }
}
