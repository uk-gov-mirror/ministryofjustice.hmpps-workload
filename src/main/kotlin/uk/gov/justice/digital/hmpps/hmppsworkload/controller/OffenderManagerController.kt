package uk.gov.justice.digital.hmpps.hmppsworkload.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.AllocateCase
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseAllocated
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseReallocated
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.OffenderManagerCases
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.OffenderManagerOverview
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.OffenderManagerPotentialWorkload
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.ReallocateCase
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.StaffIdentifier
import uk.gov.justice.digital.hmpps.hmppsworkload.service.DefaultSaveWorkloadService
import uk.gov.justice.digital.hmpps.hmppsworkload.service.staff.GetOffenderManagerService

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class OffenderManagerController(
  private val getOffenderManagerService: GetOffenderManagerService,
  private val saveWorkloadService: DefaultSaveWorkloadService,
) {

  @Operation(summary = "Retrieves capacity and potential capacity if case were to be allocated")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("/team/{teamCode}/offenderManager/{staffCode}/impact/person/{crn}")
  suspend fun getImpactOfAllocation(@PathVariable(required = true) teamCode: String, @PathVariable(required = true) staffCode: String, @PathVariable crn: String): OffenderManagerPotentialWorkload = getOffenderManagerService.getPotentialWorkload(StaffIdentifier(staffCode, teamCode), crn) ?: throw EntityNotFoundException("Team $teamCode and staff Code $staffCode combination not found")

  @Operation(summary = "Retrieves overview of Offender Manager")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("/team/{teamCode}/offenderManagers/{offenderManagerCode}")
  suspend fun getOverview(@PathVariable(required = true) teamCode: String, @PathVariable(required = true) offenderManagerCode: String): OffenderManagerOverview = getOffenderManagerService.getOverview(StaffIdentifier(offenderManagerCode, teamCode)) ?: run {
    throw EntityNotFoundException("Team $teamCode and offender manager $offenderManagerCode combination not found")
  }

  @Operation(summary = "Allocate Case to an Offender Manager")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @PostMapping("/team/{teamCode}/offenderManager/{staffCode}/case")
  suspend fun allocateCaseToOffenderManager(
    @PathVariable(required = true) teamCode: String,
    @PathVariable(required = true) staffCode: String,
    @RequestBody allocateCase: AllocateCase,
    authentication: Authentication,
  ): CaseAllocated? {
    val username = authentication.name
    log.info("Allocating case for crn ${allocateCase.crn} to staffCode $staffCode in teamCode $teamCode by $username")
    return saveWorkloadService.saveWorkload(StaffIdentifier(staffCode, teamCode), allocateCase, authentication.name)
  }

  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @PostMapping("/team/{teamCode}/offenderManager/{staffCode}/{previousStaffCode}/case")
  suspend fun reallocateCaseToOffenderManager(
    @PathVariable(required = true) teamCode: String,
    @PathVariable(required = true) staffCode: String,
    @PathVariable(required = true) previousStaffCode: String,
    @RequestBody allocateCase: ReallocateCase,
    authentication: Authentication,
  ): CaseReallocated? {
    val username = authentication.name
    log.info("reallocating case for crn ${allocateCase.crn} to staffCode $staffCode in teamCode $teamCode by $username")
    return saveWorkloadService.saveReallocatedWorkLoad(StaffIdentifier(staffCode, teamCode), previousStaffCode, allocateCase, authentication.name)
  }

  @Operation(summary = "Retrieves all cases allocated to an Offender Manager")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK"),
      ApiResponse(responseCode = "404", description = "Result Not Found"),
    ],
  )
  @PreAuthorize("hasRole('ROLE_MANAGE_A_WORKFORCE_ALLOCATE')")
  @GetMapping("/team/{teamCode}/offenderManagers/{offenderManagerCode}/cases")
  suspend fun getCases(@PathVariable(required = true) teamCode: String, @PathVariable(required = true) offenderManagerCode: String): OffenderManagerCases = getOffenderManagerService.getCases(StaffIdentifier(offenderManagerCode, teamCode)) ?: throw EntityNotFoundException("Team $teamCode and offender manager $offenderManagerCode combination not found")

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
