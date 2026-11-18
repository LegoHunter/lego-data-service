package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.PartyService;
import com.vattima.lego.inventory.service.dto.PartyCreateRequest;
import com.vattima.lego.inventory.service.dto.PartyResponse;
import com.vattima.lego.inventory.service.dto.PartyUpdateRequest;
import com.vattima.lego.inventory.service.logging.LogExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Finds, adds and updates parties. Item intake needs the seller and the owner as existing parties, so a new seller is
 * looked up here first and added when there is no match.
 */
@RestController
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
public class PartyController {
    private final PartyService partyService;

    @GetMapping("/{partyId}")
    @LogExecution
    public ResponseEntity<PartyResponse> findById(@PathVariable Long partyId) {
        return ResponseEntity.ok(partyService.findById(partyId));
    }

    @GetMapping
    @LogExecution
    public ResponseEntity<List<PartyResponse>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String transactionPlatformName,
            @RequestParam(required = false) String externalPartyId,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(partyService.search(name, email, transactionPlatformName, externalPartyId, limit));
    }

    @PostMapping
    @LogExecution
    public ResponseEntity<PartyResponse> create(
            @Valid @RequestBody PartyCreateRequest request,
            @RequestParam(defaultValue = "false") boolean allowDuplicateName
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partyService.create(request, allowDuplicateName));
    }

    @PutMapping("/{partyId}")
    @LogExecution
    public ResponseEntity<PartyResponse> update(
            @PathVariable Long partyId,
            @Valid @RequestBody PartyUpdateRequest request,
            @RequestParam(defaultValue = "false") boolean allowDuplicateName
    ) {
        return ResponseEntity.ok(partyService.update(partyId, request, allowDuplicateName));
    }
}
