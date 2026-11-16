package uk.gov.justice.digital.hmpps.hmppsworkload.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsworkload.service.MawAuditService

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class AuditController(
  private val mawAuditService: MawAuditService,
) {

  @Operation(summary = "Post an audit message")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @PostMapping("/audit/data")
  suspend fun auditData(@RequestBody auditData: Any, authentication: Authentication) = mawAuditService.auditData(auditData, authentication.name)
}
