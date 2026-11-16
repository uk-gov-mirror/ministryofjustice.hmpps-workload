package uk.gov.justice.digital.hmpps.hmppsworkload.integration.team

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.client.dto.TeamOverview
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.AllocationReason
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.Tier
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.UpdatedCaseDetails
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.HmppsProbationEstateApiExtension.Companion.hmppsProbationEstate
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.TierApiExtension.Companion.hmppsTier
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.WorkforceAllocationsToDeliusExtension.Companion.workforceAllocationsToDelius
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.PersonManagerEntity
import java.time.ZonedDateTime

class ChoosePractitionersByTeamCodes : IntegrationTestBase() {

  @Test
  fun `can get choose practitioner response`() {
    val teamCode = "T1"
    val teamCode2 = "T2"
    val crn = "CRN1"
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Don", "Cole", Tier.B, false, CaseType.CUSTODY, crn))

    workforceAllocationsToDelius.choosePractitionerByTeamCodesResponse(listOf(teamCode, teamCode2), crn)

    val firstOm = "OM1"
    val secondOm = "OM2"
    val noWorkloadStaffCode = "NOWORKLOAD1"

    val storedPersonManager = PersonManagerEntity(crn = "CRN5", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)

    val movedPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", createdDate = ZonedDateTime.now().minusDays(5L), isActive = false, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(movedPersonManager)

    val newPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = secondOm, teamCode = teamCode2, createdBy = "USER2", createdDate = ZonedDateTime.now().minusDays(2L), isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(newPersonManager)

    val personManagerWithNoWorkload = PersonManagerEntity(crn = "CRN4", staffCode = noWorkloadStaffCode, teamCode = "T1", createdBy = "USER2", createdDate = ZonedDateTime.now().minusDays(2L), isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(personManagerWithNoWorkload)

    setupCasesForTeams()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1"), TeamOverview(teamCode2, "Team 2")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN1" to "A"))

    setupReportData()

    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode,$teamCode2")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.crn")
      .isEqualTo(crn)
      .jsonPath("$.name.forename")
      .isEqualTo("Don")
      .jsonPath("$.name.middleName")
      .isEqualTo("")
      .jsonPath("$.name.surname")
      .isEqualTo("Cole")
      .jsonPath("$.tier")
      .isEqualTo(Tier.B.name)
      .jsonPath("$.probationStatus.status")
      .isEqualTo("PREVIOUSLY_MANAGED")
      .jsonPath("$.probationStatus.description")
      .isEqualTo("Previously managed")
      .jsonPath("$.communityPersonManager.code")
      .isEqualTo("N03A019")
      .jsonPath("$.communityPersonManager.name.forename")
      .isEqualTo("Derek")
      .jsonPath("$.communityPersonManager.name.surname")
      .isEqualTo("Pint")
      .jsonPath("$.communityPersonManager.teamCode")
      .isEqualTo("N03F01")
      .jsonPath("$.communityPersonManager.grade")
      .isEqualTo("PO")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.forename")
      .isEqualTo("Jane")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.surname")
      .isEqualTo("Doe")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].email")
      .isEqualTo("j.doe@email.co.uk")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].grade")
      .isEqualTo("PO")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].casesPastWeek")
      .isEqualTo(4)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].allocatedCasesPastWeek")
      .isEqualTo(4)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].reallocatedCasesPastWeek")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].totalCases")
      .isEqualTo(3)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].communityCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].licenseCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].custodyCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].ispsDueInNext14Days")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].activeCases")
      .isEqualTo(2)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].contactSuspendedCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].custodyReleasesInNext7Days")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].paroleReportsInNext28Days")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].otherReportsInNext14Days")
      .isEqualTo(3)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].tierCaseTotals.a")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].name.forename")
      .isEqualTo("No")
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].name.surname")
      .isEqualTo("Workload")
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].email")
      .isEqualTo("no.workload.email@co.uk")
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].casesPastWeek")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].allocatedCasesPastWeek")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].reallocatedCasesPastWeek")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].totalCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].communityCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].custodyCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].ispsDueInNext14Days")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].activeCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].contactSuspendedCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].custodyReleasesInNext7Days")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].paroleReportsInNext28Days")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].otherReportsInNext14Days")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].tierCaseTotals.a")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode2[?(@.code == '$secondOm')].name.forename")
      .isEqualTo("Mark")
      .jsonPath("$.teams.$teamCode2[?(@.code == '$secondOm')].name.surname")
      .isEqualTo("Reese")
      .jsonPath("$.teams.$teamCode2[?(@.code == '$secondOm')].email")
      .doesNotExist()
      .jsonPath("$.teams.$teamCode2[?(@.code == '$secondOm')].grade")
      .isEqualTo("PQiP")
  }

  @Test
  fun `can get choose practitioner response team only`() {
    val teamCode = "T1"
    val teamCode2 = "T2"
    val crn = "CRN1"
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Don", "Cole", Tier.B, false, CaseType.CUSTODY, crn))

    workforceAllocationsToDelius.choosePractitionerByTeamCodesResponse(listOf(teamCode, teamCode2), crn)

    val firstOm = "OM1"
    val secondOm = "OM2"
    val noWorkloadStaffCode = "NOWORKLOAD1"

    val storedPersonManager = PersonManagerEntity(crn = "CRN5", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)

    val movedPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", createdDate = ZonedDateTime.now().minusDays(5L), isActive = false, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(movedPersonManager)

    val newPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = secondOm, teamCode = teamCode2, createdBy = "USER2", createdDate = ZonedDateTime.now().minusDays(2L), isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(newPersonManager)

    val personManagerWithNoWorkload = PersonManagerEntity(crn = "CRN4", staffCode = noWorkloadStaffCode, teamCode = "T1", createdBy = "USER2", createdDate = ZonedDateTime.now().minusDays(2L), isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(personManagerWithNoWorkload)

    setupCasesForTeams()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1"), TeamOverview(teamCode2, "Team 2")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN1" to "A"))

    setupReportData()

    webTestClient.get()
      .uri("/team/choose-practitioner?&teamCodes=$teamCode,$teamCode2&crn=$crn")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.crn")
      .isEqualTo(crn)
      .jsonPath("$.name.forename")
      .isEqualTo("Don")
      .jsonPath("$.name.middleName")
      .isEqualTo("")
      .jsonPath("$.name.surname")
      .isEqualTo("Cole")
      .jsonPath("$.tier")
      .isEqualTo(Tier.B.name)
      .jsonPath("$.probationStatus.status")
      .isEqualTo("PREVIOUSLY_MANAGED")
      .jsonPath("$.probationStatus.description")
      .isEqualTo("Previously managed")
      .jsonPath("$.communityPersonManager.code")
      .isEqualTo("N03A019")
      .jsonPath("$.communityPersonManager.name.forename")
      .isEqualTo("Derek")
      .jsonPath("$.communityPersonManager.name.surname")
      .isEqualTo("Pint")
      .jsonPath("$.communityPersonManager.teamCode")
      .isEqualTo("N03F01")
      .jsonPath("$.communityPersonManager.grade")
      .isEqualTo("PO")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.forename")
      .isEqualTo("Jane")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.surname")
      .isEqualTo("Doe")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].email")
      .isEqualTo("j.doe@email.co.uk")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].grade")
      .isEqualTo("PO")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].casesPastWeek")
      .isEqualTo(4)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].allocatedCasesPastWeek")
      .isEqualTo(4)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].reallocatedCasesPastWeek")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].totalCases")
      .isEqualTo(3)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].communityCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].licenseCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].custodyCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].ispsDueInNext14Days")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].activeCases")
      .isEqualTo(2)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].contactSuspendedCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].custodyReleasesInNext7Days")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].paroleReportsInNext28Days")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].otherReportsInNext14Days")
      .isEqualTo(3)
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].tierCaseTotals.a")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].name.forename")
      .isEqualTo("No")
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].name.surname")
      .isEqualTo("Workload")
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].email")
      .isEqualTo("no.workload.email@co.uk")
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].casesPastWeek")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].allocatedCasesPastWeek")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].reallocatedCasesPastWeek")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].totalCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].communityCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].custodyCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].ispsDueInNext14Days")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].activeCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].contactSuspendedCases")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].custodyReleasesInNext7Days")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].paroleReportsInNext28Days")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].otherReportsInNext14Days")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')].tierCaseTotals.a")
      .isEqualTo(0)
      .jsonPath("$.teams.$teamCode2[?(@.code == '$secondOm')].name.forename")
      .isEqualTo("Mark")
      .jsonPath("$.teams.$teamCode2[?(@.code == '$secondOm')].name.surname")
      .isEqualTo("Reese")
      .jsonPath("$.teams.$teamCode2[?(@.code == '$secondOm')].email")
      .doesNotExist()
      .jsonPath("$.teams.$teamCode2[?(@.code == '$secondOm')].grade")
      .isEqualTo("PQiP")
  }

  @Test
  fun `can get choose practitioner response team code only`() {
    val teamCode = "T1"

    workforceAllocationsToDelius.getTeamsResponse(listOf(teamCode))

    val firstOm = "OM1"

    val storedPersonManager = PersonManagerEntity(crn = "CRN5", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)

    val movedPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", createdDate = ZonedDateTime.now().minusDays(5L), isActive = false, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(movedPersonManager)

    setupCasesForTeams()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN1" to "A"))

    setupReportData()

    webTestClient.get()
      .uri("/team/practitioner-workloadcases?teamCode=$teamCode")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].name.forename")
      .isEqualTo("Jane")
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].name.surname")
      .isEqualTo("Doe")
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].email")
      .isEqualTo("j.doe@email.co.uk")
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].grade")
      .isEqualTo("PO")
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].casesPastWeek")
      .isEqualTo(4)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].allocatedCasesPastWeek")
      .isEqualTo(4)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].reallocatedCasesPastWeek")
      .isEqualTo(0)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].totalCases")
      .isEqualTo(3)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].communityCases")
      .isEqualTo(1)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].licenseCases")
      .isEqualTo(1)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].custodyCases")
      .isEqualTo(1)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].ispsDueInNext14Days")
      .isEqualTo(1)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].activeCases")
      .isEqualTo(2)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].contactSuspendedCases")
      .isEqualTo(1)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].custodyReleasesInNext7Days")
      .isEqualTo(1)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].paroleReportsInNext28Days")
      .isEqualTo(1)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].otherReportsInNext14Days")
      .isEqualTo(3)
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')].tierCaseTotals.a")
      .isEqualTo(1)
  }

  @Test
  fun `can get choose practitioner response team code only filtered by grade`() {
    val teamCode = "T1"

    workforceAllocationsToDelius.getTeamsResponse(listOf(teamCode))

    val firstOm = "OM1"
    val secondOm = "NOWORKLOAD1"

    setupCasesForTeams()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN1" to "A"))

    webTestClient.get()
      .uri("/team/practitioner-workloadcases?teamCode=$teamCode&grades=PO")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.$teamCode.teams[?(@.code == '$firstOm')]")
      .exists()
      .jsonPath("$.$teamCode.teams[?(@.code == '$secondOm')]")
      .doesNotExist()
  }

  @Test
  fun `throws 404 when crn not in repository`() {
    val teamCode = "T1"
    val teamCode2 = "T2"
    val crn = "NOTFOUND"

    workforceAllocationsToDelius.choosePractitionerByTeamCodesResponse(listOf(teamCode, teamCode2), crn)

    val firstOm = "OM1"
    val secondOm = "OM2"
    val noWorkloadStaffCode = "NOWORKLOAD1"

    val storedPersonManager = PersonManagerEntity(crn = "CRN5", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)

    val movedPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", createdDate = ZonedDateTime.now().minusDays(5L), isActive = false, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(movedPersonManager)

    val newPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = secondOm, teamCode = teamCode2, createdBy = "USER2", createdDate = ZonedDateTime.now().minusDays(2L), isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(newPersonManager)

    val personManagerWithNoWorkload = PersonManagerEntity(crn = "CRN4", staffCode = noWorkloadStaffCode, teamCode = "T1", createdBy = "USER2", createdDate = ZonedDateTime.now().minusDays(2L), isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(personManagerWithNoWorkload)

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1"), TeamOverview(teamCode2, "Team 2")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN5" to "A"))

    setupTeamWithNoWorkCases()

    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode,$teamCode2")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isNotFound
  }

  @Test
  fun `can filter officers by grade`() {
    val teamCode = "T1"
    val teamCode2 = "T2"
    val crn = "CRN1"
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Don", "Cole", Tier.B, false, CaseType.CUSTODY, crn))
    workforceAllocationsToDelius.choosePractitionerByTeamCodesResponse(listOf(teamCode, teamCode2), crn)

    val firstOm = "OM1"
    val noWorkloadStaffCode = "NOWORKLOAD1"
    setupTeamWithNoWorkCases()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1"), TeamOverview(teamCode2, "Team 2")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN5" to "A"))

    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode,$teamCode2&grades=PO,PQiP")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.forename")
      .isEqualTo("Jane")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.surname")
      .isEqualTo("Doe")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].email")
      .isEqualTo("j.doe@email.co.uk")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].grade")
      .isEqualTo("PO")
      .jsonPath("$.teams.$teamCode[?(@.code == '$noWorkloadStaffCode')]")
      .doesNotExist()
  }

  @Test
  fun `must return not found when team code is not matched`() {
    webTestClient.get()
      .uri("/team/choose-practitioner?crn=CRNRANDOM123456&teamCodes=T1")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isNotFound
  }

  @Test
  fun `must return forbidden when auth token does not contain correct role`() {
    val teamCode = "T1"
    val teamCode2 = "T2"
    val crn = "CRN1"
    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode,$teamCode2&grades=PO,PQiP")
      .headers { it.authToken(roles = listOf("ROLE_RANDOM_ROLE")) }
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `can get team overview of offender manager by team code when assigned to another staff member`() {
    val teamCode = "T1"
    val staffCode = "OM1"
    val crn = "CRN1"
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Don", "Cole", Tier.B, false, CaseType.CUSTODY, crn))
    workforceAllocationsToDelius.choosePractitionerByTeamCodesResponse(listOf(teamCode), crn)

    val movedPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = staffCode, teamCode = teamCode, createdBy = "USER1", isActive = false, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(movedPersonManager)

    val newPersonManager = PersonManagerEntity(crn = "CRN3", staffCode = "STAFF2", teamCode = "TEAM2", createdBy = "USER2", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(newPersonManager)

    setupTeamWithNoWorkCases()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1")))

    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.teams.$teamCode[?(@.code == '$staffCode')].allocatedCasesPastWeek")
      .isEqualTo(0)
  }

  @Test
  fun `must get correct workloads when the same offender manager is working in multiple teams`() {
    val teamCode = "T1"
    val teamCode2 = "T2"
    val crn = "CRN1"
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Don", "Cole", Tier.B, false, CaseType.CUSTODY, crn))
    val staffCode = "OM1"

    workforceAllocationsToDelius.choosePractitionerStaffInMultipleTeamsResponse(listOf(teamCode, teamCode2), crn)

    setupCasesForTeams()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1"), TeamOverview(teamCode2, "Team 2")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN5" to "A"))

    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode,$teamCode2")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.teams.$teamCode[?(@.code == '$staffCode')].custodyCases")
      .isEqualTo(1)
      .jsonPath("$.teams.$teamCode2[?(@.code == '$staffCode')].custodyCases")
      .isEqualTo(1)
  }

  @Test
  fun `can get choose practitioner response when practitioner does not have an email field`() {
    val teamCode = "T2"
    val crn = "CRN1"
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Don", "Cole", Tier.B, false, CaseType.CUSTODY, crn))

    workforceAllocationsToDelius.choosePractitionerByTeamCodesResponse(listOf(teamCode), crn)

    val firstOm = "OM3"

    val storedPersonManager = PersonManagerEntity(crn = "CRN1", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)

    setupTeamWithNoWorkCases()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN5" to "A"))

    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.forename")
      .isEqualTo("Mark")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.surname")
      .isEqualTo("NoEmail")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].email")
      .doesNotExist()
  }

  @Test
  fun `can get choose practitioner response when there is no community person manager`() {
    val teamCode = "T2"
    val crn = "CRN1"
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Don", "Cole", Tier.B, false, CaseType.CUSTODY, crn))

    workforceAllocationsToDelius.choosePractitionerByTeamCodesResponseNoCommunityPersonManager(listOf(teamCode), crn)
    val firstWmtStaff = setupCurrentWmtStaff("OM3", teamCode)

    val firstOm = firstWmtStaff.offenderManager.code

    val storedPersonManager = PersonManagerEntity(crn = "CRN1", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)

    setupTeamWithNoWorkCases()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN5" to "A"))

    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.forename")
      .isEqualTo("Mark")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.surname")
      .isEqualTo("NoEmail")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].email")
      .doesNotExist()
      .jsonPath("$.communityPersonManager")
      .doesNotExist()
  }

  @Test
  fun `can get choose practitioner response when unallocated case`() {
    val teamCode = "T2"
    val crn = "CRN1"
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Don", "Cole", Tier.B, false, CaseType.CUSTODY, crn))

    workforceAllocationsToDelius.choosePractitionerByTeamCodesResponseUnallocated(listOf(teamCode), crn)
    val firstWmtStaff = setupCurrentWmtStaff("OM3", teamCode)

    val firstOm = firstWmtStaff.offenderManager.code

    val storedPersonManager = PersonManagerEntity(crn = "CRN1", staffCode = firstOm, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)

    setupTeamWithNoWorkCases()

    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1")))
    hmppsTier.bulkTierCalculationResponse(mapOf("CRN5" to "A"))

    webTestClient.get()
      .uri("/team/choose-practitioner?crn=$crn&teamCodes=$teamCode")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.forename")
      .isEqualTo("Mark")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].name.surname")
      .isEqualTo("NoEmail")
      .jsonPath("$.teams.$teamCode[?(@.code == '$firstOm')].email")
      .doesNotExist()
      .jsonPath("$.communityPersonManager")
      .doesNotExist()
  }
}
