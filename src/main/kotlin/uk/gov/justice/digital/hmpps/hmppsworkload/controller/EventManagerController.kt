package uk.gov.justice.digital.hmpps.hmppsworkload.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.persistence.EntityNotFoundException
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsworkload.client.dto.CompleteDetails
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseCount
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseDetails
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CreatedAllocationDetails
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.EventManagerDetails
import uk.gov.justice.digital.hmpps.hmppsworkload.service.staff.JpaBasedGetEventManager
import java.time.ZonedDateTime
import java.util.UUID

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class EventManagerController(private val getEventManager: JpaBasedGetEventManager) {

  @Operation(summary = "Get Event Manager by ID")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("\${event.manager.getByIdPath}")
  suspend fun getEventManagerById(@PathVariable(required = true) id: UUID): EventManagerDetails {
    var eventManager = getEventManager.findById(id) ?: throw EntityNotFoundException("Event Manager not found for id $id")
    if (eventManager.createdDate.isBefore(ZonedDateTime.now().minusMinutes(5))) {
      eventManager.createdDate = ZonedDateTime.now()
    }
    return eventManager
  }

  @Operation(summary = "Get case details of Event Manager by crn and event number")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("/allocation/person/{crn}/event/{eventNumber}/details")
  suspend fun getCaseDetailsForEventManager(
    @PathVariable(required = true) crn: String,
    @PathVariable(required = true) eventNumber: Int,
  ): CaseDetails = getEventManager.findDetailsByCrnAndEventNumber(crn, eventNumber) ?: throw EntityNotFoundException("Case details of event manager not found for crn $crn eventNumber $eventNumber")

  @Operation(summary = "Get Allocation complete details of Event Manager by crn and event number")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("/allocation/person/{crn}/event/{eventNumber}/complete-details")
  suspend fun getCompleteDetailsForEventManager(
    @PathVariable(required = true) crn: String,
    @PathVariable(required = true) eventNumber: Int,
  ): CompleteDetails = getEventManager.findCompleteDetailsByCrnAndEventNumber(crn, eventNumber) ?: throw EntityNotFoundException("Complete details of event manager not found for crn $crn eventNumber $eventNumber")

  @Operation(summary = "Get allocated event count by logged in user's teams")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @PostMapping("/allocation/events/teams/count")
  suspend fun getAllocationCountByLoggedInUsersTeam(
    @RequestParam(required = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    since: ZonedDateTime,
    @RequestBody teams: TeamsRequest,
  ): CaseCount = getEventManager.countAllocationsBy(since, teams.teams)

  @Operation(summary = "Get allocated events created by logged in user's selected teams")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @PostMapping("/allocation/events/teams")
  suspend fun getAllocationsByUsersTeams(
    @RequestParam(required = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    since: ZonedDateTime,
    @RequestBody teams: TeamsRequest,
  ): CreatedAllocationDetails = getEventManager.findAllocationsByTeam(since, teams.teams)

  data class TeamsRequest(val teams: List<String>)
}
