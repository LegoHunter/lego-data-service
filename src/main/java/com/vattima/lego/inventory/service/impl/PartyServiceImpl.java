package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.api.PartyService;
import com.vattima.lego.inventory.service.dto.ApiValidationError;
import com.vattima.lego.inventory.service.dto.PartyCreateRequest;
import com.vattima.lego.inventory.service.dto.PartyExternalIdentityRequest;
import com.vattima.lego.inventory.service.dto.PartyExternalIdentityResponse;
import com.vattima.lego.inventory.service.dto.PartyResponse;
import com.vattima.lego.inventory.service.exception.DuplicatePartyException;
import com.vattima.lego.inventory.service.exception.NotFoundException;
import com.vattima.lego.inventory.service.exception.ValidationException;
import io.legohunter.data.dao.PartyDao;
import io.legohunter.data.dao.PartyExternalIdentityDao;
import io.legohunter.data.dao.TransactionPlatformDao;
import io.legohunter.data.dto.Party;
import io.legohunter.data.dto.PartyExternalIdentity;
import io.legohunter.data.dto.TransactionPlatform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartyServiceImpl implements PartyService {
    static final int DEFAULT_SEARCH_LIMIT = 50;
    static final int MAX_SEARCH_LIMIT = 200;
    static final String DUPLICATE_PARTY_CODE = "DUPLICATE_PARTY";

    private final PartyDao partyDao;
    private final PartyExternalIdentityDao partyExternalIdentityDao;
    private final TransactionPlatformDao transactionPlatformDao;

    @Override
    @Transactional(readOnly = true)
    public PartyResponse findById(Long partyId) {
        Party party = partyDao.findPartyById(partyId)
                .orElseThrow(() -> new NotFoundException("Party [%s] was not found".formatted(partyId)));
        return toResponse(party, platformNamesById());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyResponse> search(
            String name,
            String email,
            String transactionPlatformName,
            String externalPartyId,
            Integer limit
    ) {
        Integer transactionPlatformId = hasText(transactionPlatformName)
                ? platform(transactionPlatformName.trim()).getTransactionPlatformId()
                : null;
        Map<Integer, String> platformNames = platformNamesById();
        return partyDao.search(name, email, transactionPlatformId, externalPartyId, effectiveLimit(limit)).stream()
                .map(party -> toResponse(party, platformNames))
                .toList();
    }

    @Override
    @Transactional
    public PartyResponse create(PartyCreateRequest request, boolean allowDuplicateName) {
        Party party = Party.builder()
                .partyType(request.getPartyType())
                .partyFirstName(trimToNull(request.getPartyFirstName()))
                .partyMiddleInitial(trimToNull(request.getPartyMiddleInitial()))
                .partyLastName(trimToNull(request.getPartyLastName()))
                .partyAddress1(trimToNull(request.getPartyAddress1()))
                .partyAddress2(trimToNull(request.getPartyAddress2()))
                .partyCity(trimToNull(request.getPartyCity()))
                .partyState(trimToNull(request.getPartyState()))
                .partyPostalCode(trimToNull(request.getPartyPostalCode()))
                .partyCountryCode(trimToNull(request.getPartyCountryCode()))
                .partyCountry(trimToNull(request.getPartyCountry()))
                .partyPhone(trimToNull(request.getPartyPhone()))
                .partyEmail(trimToNull(request.getPartyEmail()))
                .partyActivationDate(LocalDate.now().atStartOfDay())
                .build();
        if (party.getPartyFirstName() == null && party.getPartyLastName() == null) {
            throw new ValidationException("partyFirstName or partyLastName is required");
        }
        List<PartyExternalIdentity> identities = identities(request.getExternalIdentities());

        List<ApiValidationError> matches = duplicates(party, identities, allowDuplicateName);
        if (!matches.isEmpty()) {
            throw new DuplicatePartyException(matches);
        }

        partyDao.insert(party);
        identities.forEach(identity -> {
            identity.setPartyId(party.getPartyId());
            partyExternalIdentityDao.insert(identity);
        });
        return toResponse(party, platformNamesById());
    }

    private List<PartyExternalIdentity> identities(List<PartyExternalIdentityRequest> requests) {
        List<PartyExternalIdentity> identities = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PartyExternalIdentityRequest request : Optional.ofNullable(requests).orElse(List.of())) {
            TransactionPlatform platform = platform(request.getTransactionPlatformName().trim());
            String externalPartyId = request.getExternalPartyId().trim();
            String key = platform.getTransactionPlatformId() + "|" + externalPartyId.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new ValidationException("External identity [%s] on [%s] is listed more than once"
                        .formatted(externalPartyId, platform.getTransactionPlatformName()));
            }
            identities.add(PartyExternalIdentity.builder()
                    .transactionPlatformId(platform.getTransactionPlatformId())
                    .externalPartyId(externalPartyId)
                    .build());
        }
        return identities;
    }

    private List<ApiValidationError> duplicates(
            Party party,
            List<PartyExternalIdentity> identities,
            boolean allowDuplicateName
    ) {
        List<ApiValidationError> matches = new ArrayList<>();
        partyDao.findByEmail(party.getPartyEmail()).forEach(existing -> matches.add(duplicate(
                "partyEmail",
                "Party [%s] already has this email".formatted(existing.getPartyId())
        )));
        identities.forEach(identity -> partyExternalIdentityDao
                .findByPlatformAndExternalPartyId(identity.getTransactionPlatformId(), identity.getExternalPartyId())
                .ifPresent(existing -> matches.add(duplicate(
                        "externalIdentities",
                        "Party [%s] already has the external identity [%s]"
                                .formatted(existing.getPartyId(), identity.getExternalPartyId())
                ))));
        if (!allowDuplicateName) {
            partyDao.findByName(party.getPartyFirstName(), party.getPartyLastName()).forEach(existing -> matches.add(
                    duplicate(
                            "partyName",
                            "Party [%s] already has this name; send allowDuplicateName=true to add another"
                                    .formatted(existing.getPartyId())
                    )));
        }
        return matches;
    }

    private ApiValidationError duplicate(String field, String message) {
        return ApiValidationError.builder().field(field).message(message).code(DUPLICATE_PARTY_CODE).build();
    }

    private TransactionPlatform platform(String name) {
        return transactionPlatformDao.findTransactionPlatformByName(name)
                .orElseThrow(() -> new ValidationException("Transaction Platform [%s] is invalid. Must be one of %s"
                        .formatted(name, transactionPlatformDao.findAll().stream()
                                .map(TransactionPlatform::getTransactionPlatformName)
                                .toList())));
    }

    private Map<Integer, String> platformNamesById() {
        return transactionPlatformDao.findAll().stream().collect(Collectors.toMap(
                TransactionPlatform::getTransactionPlatformId,
                TransactionPlatform::getTransactionPlatformName,
                (first, second) -> first
        ));
    }

    private PartyResponse toResponse(Party party, Map<Integer, String> platformNames) {
        return PartyResponse.builder()
                .partyId(party.getPartyId())
                .partyType(party.getPartyType())
                .partyFirstName(party.getPartyFirstName())
                .partyMiddleInitial(party.getPartyMiddleInitial())
                .partyLastName(party.getPartyLastName())
                .partyAddress1(party.getPartyAddress1())
                .partyAddress2(party.getPartyAddress2())
                .partyCity(party.getPartyCity())
                .partyState(party.getPartyState())
                .partyPostalCode(party.getPartyPostalCode())
                .partyCountryCode(party.getPartyCountryCode())
                .partyCountry(party.getPartyCountry())
                .partyPhone(party.getPartyPhone())
                .partyEmail(party.getPartyEmail())
                .partyActivationDate(Optional.ofNullable(party.getPartyActivationDate())
                        .map(LocalDateTime::toLocalDate)
                        .orElse(null))
                .externalIdentities(partyExternalIdentityDao.findByPartyId(party.getPartyId()).stream()
                        .map(identity -> PartyExternalIdentityResponse.builder()
                                .partyExternalIdentityId(identity.getPartyExternalIdentityId())
                                .transactionPlatformId(identity.getTransactionPlatformId())
                                .transactionPlatformName(platformNames.get(identity.getTransactionPlatformId()))
                                .externalPartyId(identity.getExternalPartyId())
                                .build())
                        .toList())
                .build();
    }

    private static int effectiveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_SEARCH_LIMIT;
        }
        if (limit < 1 || limit > MAX_SEARCH_LIMIT) {
            throw new ValidationException("limit must be between 1 and %s".formatted(MAX_SEARCH_LIMIT));
        }
        return limit;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
