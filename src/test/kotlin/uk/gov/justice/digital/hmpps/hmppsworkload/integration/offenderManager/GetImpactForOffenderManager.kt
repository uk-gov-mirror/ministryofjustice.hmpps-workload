package uk.gov.justice.digital.hmpps.hmppsworkload.integration.offenderManager

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.Tier
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.WorkforceAllocationsToDeliusExtension.Companion.workforceAllocationsToDelius
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.CaseDetailsEntity

class GetImpactForOffenderManager : IntegrationTestBase() {

  @Test
  fun `can get impact for an offender manager with workload`() {
    val crn = "CRN1"
    val staffCode = "OM1"
    val teamCode = "T1"
    workforceAllocationsToDelius.getImpactResponse(crn, staffCode)
    setupCurrentWmtStaff(staffCode, teamCode)
    val caseDetailsEntity = CaseDetailsEntity(crn, Tier.B, false, CaseType.CUSTODY, "Jane", "Doe")
    caseDetailsRepository.save(caseDetailsEntity)

    webTestClient.get()
      .uri("/team/$teamCode/offenderManager/$staffCode/impact/person/$crn")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.capacity")
      .isEqualTo(50)
      .jsonPath("$.potentialCapacity")
      .isEqualTo(50)
      .jsonPath("$.tier")
      .isEqualTo(caseDetailsEntity.tier.name)
      .jsonPath("$.name.forename")
      .isEqualTo("Jonathon")
      .jsonPath("$.name.surname")
      .isEqualTo("Jones")
      .jsonPath("$.name.combinedName")
      .isEqualTo("Jonathon Jones")
      .jsonPath("$.staff.code")
      .isEqualTo(staffCode)
      .jsonPath("$.staff.name.forename")
      .isEqualTo("Sheila")
      .jsonPath("$.staff.name.surname")
      .isEqualTo("Hancock")
      .jsonPath("$.staff.name.combinedName")
      .isEqualTo("Sheila Hancock")
      .jsonPath("$.staff.grade")
      .isEqualTo("PO")
  }

  @Test
  fun `must not change capacity if case already allocated to the officer and is classified the same`() {
    val crn = "CRN2222"
    val staffCode = "OM1"
    val teamCode = "T1"
    workforceAllocationsToDelius.getImpactResponse(crn, staffCode)
    val wmtStaff = setupCurrentWmtStaff(staffCode, teamCode)
    val caseDetails = caseDetailsRepository.save(CaseDetailsEntity(crn, Tier.B, false, CaseType.CUSTODY, "Jane", "Doe"))
    setupWmtManagedCase(wmtStaff, caseDetails.tier, crn, caseDetails.type)

    webTestClient.get()
      .uri("/team/$teamCode/offenderManager/$staffCode/impact/person/$crn")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.capacity")
      .isEqualTo(50)
      .jsonPath("$.potentialCapacity")
      .isEqualTo(50)
  }

  @Test
  fun `can get impact for an offender manager without workload`() {
    val crn = "CRN1"
    val staffCode = "NOWORKLOAD1"
    val teamCode = "T1"
    workforceAllocationsToDelius.getImpactResponse(crn, staffCode)
    caseDetailsRepository.save(CaseDetailsEntity(crn, Tier.B, false, CaseType.CUSTODY, "Jane", "Doe"))
    webTestClient.get()
      .uri("/team/$teamCode/offenderManager/$staffCode/impact/person/$crn")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.capacity")
      .isEqualTo(0)
      .jsonPath("$.potentialCapacity")
      .isEqualTo(0)
  }

  @Test
  fun `can get impact for an offender manager without a grade and a workload`() {
    val crn = "CRN1"
    val staffCode = "NOWORKLOAD1"
    val teamCode = "T1"
    workforceAllocationsToDelius.getImpactNoGradeResponse(crn, staffCode)
    caseDetailsRepository.save(CaseDetailsEntity(crn, Tier.B, false, CaseType.CUSTODY, "Jane", "Doe"))
    webTestClient.get()
      .uri("/team/$teamCode/offenderManager/$staffCode/impact/person/$crn")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.staff.grade")
      .isEqualTo("DMY")
      .jsonPath("$.capacity")
      .isEqualTo(0)
      .jsonPath("$.potentialCapacity")
      .isEqualTo(0)
  }
}
