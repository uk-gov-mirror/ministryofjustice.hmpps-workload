package uk.gov.justice.digital.hmpps.hmppsworkload.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.RequirementManagerDetails
import uk.gov.justice.digital.hmpps.hmppsworkload.service.staff.GetRequirementManager
import java.time.ZonedDateTime
import java.util.UUID

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class RequirementManagerController(private val getRequirementManager: GetRequirementManager) {

  @Operation(summary = "Get Requirement Manager by ID")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("\${requirement.manager.getByIdPath}")
  suspend fun getRequirementManagerById(@PathVariable(required = true) id: UUID): RequirementManagerDetails {
    var requirementManager = getRequirementManager.findById(id)?.let { requirementManagerEntity -> RequirementManagerDetails.from(requirementManagerEntity) } ?: run {
      throw EntityNotFoundException("Event Manager not found for id $id")
    }
    if (requirementManager.createdDate.isBefore(ZonedDateTime.now().minusMinutes(5))) {
      requirementManager.createdDate = ZonedDateTime.now()
    }
    return requirementManager
  }
}
