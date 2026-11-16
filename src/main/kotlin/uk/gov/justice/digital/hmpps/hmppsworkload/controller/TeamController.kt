package uk.gov.justice.digital.hmpps.hmppsworkload.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.persistence.EntityNotFoundException
import kotlinx.coroutines.flow.Flow
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.PractitionerWithRawWorkloadPoints
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.PractitionerWorkload
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.WorkloadCase
import uk.gov.justice.digital.hmpps.hmppsworkload.service.TeamService

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class TeamController(
  private val teamService: TeamService,
) {

  @Operation(summary = "Retrieve Team summary by Team Code")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("/team/choose-practitioner")
  suspend fun getPractitioners(
    @RequestParam teamCodes: List<String>,
    @RequestParam crn: String,
    @RequestParam grades: List<String>?,
  ): PractitionerWorkload = teamService.getPractitioners(teamCodes, crn, grades)
    ?: throw EntityNotFoundException("Choose practitioner not found for $teamCodes")

  @Operation(summary = "Retrieve Team workload and case count by Team Codes")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("/team/workloadcases")
  suspend fun getTeamWorkloadAndCaseCount(@RequestParam(required = true) teams: List<String>): Flow<WorkloadCase> = teamService.getWorkloadCases(teams)

  @Operation(summary = "Retrieve Team workload and case count by Practitioner")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("/team/practitioner-workloadcases")
  suspend fun getPractitionerWorkloadAndCaseCount(@RequestParam(required = true) teamCode: String, @RequestParam grades: List<String>?): Map<String, Map<String, List<PractitionerWithRawWorkloadPoints>>> {
    val practitioners = teamService.getPractitioners(listOf(teamCode), grades)
      ?: throw EntityNotFoundException("Choose practitioner not found for $teamCode")
    return mapOf(practitioners.keys.first() to mapOf("teams" to practitioners.values.first()))
  }
}
