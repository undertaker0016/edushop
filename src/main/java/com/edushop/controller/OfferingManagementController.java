package com.edushop.controller;

import com.edushop.dto.offering.*;
import com.edushop.entity.OfferingStatus;
import com.edushop.service.OfferingManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor/offerings")
@Tag(name = "Offerings/Sessions")
@SecurityRequirement(name = "bearerAuth")
public class OfferingManagementController {
	@GetMapping("/{id}")
	public java.util.Map<String,Object> detail(@AuthenticationPrincipal Jwt jwt,Authentication auth,@PathVariable long id){
		return service.detail(userId(jwt),isAdmin(auth),id);
	}
	@GetMapping("/{id}/enrollments")
	public java.util.List<java.util.Map<String,Object>> learners(@AuthenticationPrincipal Jwt jwt,Authentication auth,@PathVariable long id){
		return service.learners(userId(jwt),isAdmin(auth),id);
	}
	@PostMapping("/{id}/complete")
	public ManagedOfferingResponse complete(@AuthenticationPrincipal Jwt jwt,Authentication auth,@PathVariable long id){
		return service.changeStatus(userId(jwt),isAdmin(auth),id,OfferingStatus.COMPLETED);
	}
	private final OfferingManagementService service;
	public OfferingManagementController(OfferingManagementService service) { this.service = service; }

	@PostMapping @ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a draft class offering with a VND price")
	public ManagedOfferingResponse create(@AuthenticationPrincipal Jwt jwt, Authentication auth,
			@Valid @RequestBody OfferingWriteRequest request) {
		return service.create(userId(jwt), isAdmin(auth), request);
	}

	@GetMapping @Operation(summary = "List offerings you may manage")
	public List<ManagedOfferingResponse> list(@AuthenticationPrincipal Jwt jwt, Authentication auth) {
		return service.list(userId(jwt), isAdmin(auth));
	}

	@PutMapping("/{id}") @Operation(summary = "Update offering details")
	public ManagedOfferingResponse update(@AuthenticationPrincipal Jwt jwt, Authentication auth,
			@PathVariable long id, @Valid @RequestBody OfferingUpdateRequest request) {
		return service.update(userId(jwt), isAdmin(auth), id, request);
	}

	@PostMapping("/{id}/open") @Operation(summary = "Open a draft offering for sale")
	public ManagedOfferingResponse open(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable long id) {
		return service.changeStatus(userId(jwt), isAdmin(auth), id, OfferingStatus.OPEN);
	}

	@PostMapping("/{id}/close") @Operation(summary = "Close an open offering")
	public ManagedOfferingResponse close(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable long id) {
		return service.changeStatus(userId(jwt), isAdmin(auth), id, OfferingStatus.CLOSED);
	}

	@PostMapping("/{id}/cancel") @Operation(summary = "Cancel an offering without paid orders")
	public ManagedOfferingResponse cancel(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable long id) {
		return service.changeStatus(userId(jwt), isAdmin(auth), id, OfferingStatus.CANCELLED);
	}

	@PostMapping("/{id}/sessions") @ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a class session; meeting details are visible only to authorized managers")
	public ManagedSessionResponse createSession(@AuthenticationPrincipal Jwt jwt, Authentication auth,
			@PathVariable long id, @Valid @RequestBody SessionWriteRequest request) {
		return service.createSession(userId(jwt), isAdmin(auth), id, request);
	}

	@GetMapping("/{id}/sessions") @Operation(summary = "List managed session details including meeting credentials")
	public List<ManagedSessionResponse> sessions(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable long id) {
		return service.listSessions(userId(jwt), isAdmin(auth), id);
	}

	@PutMapping("/{id}/sessions/{number}") @Operation(summary = "Update a session before purchases are completed")
	public ManagedSessionResponse updateSession(@AuthenticationPrincipal Jwt jwt, Authentication auth,
			@PathVariable long id, @PathVariable short number, @Valid @RequestBody SessionWriteRequest request) {
		return service.updateSession(userId(jwt), isAdmin(auth), id, number, request);
	}

	@DeleteMapping("/{id}/sessions/{number}") @Operation(summary = "Cancel a session without deleting its history")
	public ResponseEntity<Void> cancelSession(@AuthenticationPrincipal Jwt jwt, Authentication auth,
			@PathVariable long id, @PathVariable short number) {
		service.cancelSession(userId(jwt), isAdmin(auth), id, number);
		return ResponseEntity.noContent().build();
	}

	private static long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
	private static boolean isAdmin(Authentication auth) {
		return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
	}
}
