package uk.gov.justice.digital.hmpps.hmppsworkload.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.event.ContactLoggingMessage
import uk.gov.justice.digital.hmpps.hmppsworkload.service.ContactLoggingService

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class ContactLoggingController(
  private val contactLoggingService: ContactLoggingService,
) {
  @Operation(summary = "Log contact metrics")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "500", description = "Internal server error"),
    ],
  )
  @PreAuthorize("hasAnyRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @PostMapping("allocations/contact/logging")
  suspend fun logAllocationContact(@RequestBody(required = true) message: ContactLoggingMessage): Boolean = contactLoggingService.logContact(message)
}
