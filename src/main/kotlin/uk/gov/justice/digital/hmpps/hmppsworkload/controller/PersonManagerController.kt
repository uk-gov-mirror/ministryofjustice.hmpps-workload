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
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.PersonManagerDetails
import uk.gov.justice.digital.hmpps.hmppsworkload.service.staff.GetPersonManager
import java.time.ZonedDateTime
import java.util.UUID

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class PersonManagerController(private val getPersonManager: GetPersonManager) {

  @Operation(summary = "Get Person Manager by ID")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("\${person.manager.getByIdPath}")
  suspend fun getPersonManagerById(@PathVariable(required = true) id: UUID): PersonManagerDetails {
    var personManager = getPersonManager.findById(id) ?: throw EntityNotFoundException("Person Manager not found for id $id")
    if (personManager.createdDate.isBefore(ZonedDateTime.now().minusMinutes(5))) {
      personManager.createdDate = ZonedDateTime.now()
    }
    return personManager
  }
}
