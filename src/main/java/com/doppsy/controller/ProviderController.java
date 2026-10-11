package com.doppsy.controller;

import com.doppsy.dto.DoctorResponse;
import com.doppsy.service.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for healthcare provider discovery and search.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   GET /api/v1/providers              — Search / list providers (optional filters)
 *   GET /api/v1/providers/{id}         — Get a single provider by UUID
 * </pre>
 *
 * <h3>Example Requests</h3>
 * <pre>
 *   GET /api/v1/providers                              — All verified providers
 *   GET /api/v1/providers?specialty=Allopathy          — Allopathic doctors
 *   GET /api/v1/providers?location=Mumbai              — Providers in Mumbai
 *   GET /api/v1/providers?specialty=Veterinary&amp;location=Delhi
 *   GET /api/v1/providers/{uuid}
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    // -----------------------------------------------------------------------
    // GET /api/v1/providers
    // -----------------------------------------------------------------------

    /**
     * Returns a list of verified healthcare providers, optionally filtered
     * by {@code specialty} and/or {@code location}.
     *
     * <p>All query parameters are optional. Omitting both returns every
     * verified provider on the platform.</p>
     *
     * @param specialty optional clinical specialty (e.g., "Allopathy", "Veterinary")
     * @param location  optional location keyword (case-insensitive substring match)
     * @return 200 OK with a (possibly empty) list of {@link DoctorResponse}
     */
    @GetMapping
    public ResponseEntity<List<DoctorResponse>> searchProviders(
        @RequestParam(required = false) String specialty,
        @RequestParam(required = false) String location
    ) {
        log.info("[PROVIDER] Search — specialty={}, location={}", specialty, location);
        List<DoctorResponse> results = providerService.searchProviders(specialty, location);
        return ResponseEntity.ok(results);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/providers/{id}
    // -----------------------------------------------------------------------

    /**
     * Returns the full details of a single healthcare or veterinary provider.
     *
     * @param id the provider's UUID path variable
     * @return 200 OK with a {@link DoctorResponse}, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<DoctorResponse> getProviderById(@PathVariable UUID id) {
        log.info("[PROVIDER] Fetch by id={}", id);
        DoctorResponse response = providerService.getProviderById(id);
        return ResponseEntity.ok(response);
    }
}
