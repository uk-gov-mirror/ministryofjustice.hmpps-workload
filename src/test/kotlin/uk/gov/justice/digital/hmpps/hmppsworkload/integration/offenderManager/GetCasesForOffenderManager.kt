package uk.gov.justice.digital.hmpps.hmppsworkload.integration.offenderManager

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.AllocationReason
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.Tier
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.domain.ActiveCasesIntegration
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.WorkforceAllocationsToDeliusExtension.Companion.workforceAllocationsToDelius
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.CaseDetailsEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.PersonManagerEntity

class GetCasesForOffenderManager : IntegrationTestBase() {

  val teamCode = "T1"
  val staffCodeOM = "OM1"
  val staffCodeNO = "NOWORKLOAD1"

  @Test
  fun `get all cases allocated to offender manager`() {
    workforceAllocationsToDelius.staffActiveCasesResponse(
      staffCodeOM,
      activeCases = listOf(
        ActiveCasesIntegration("CRN2222", "Sally", "Smith", "CUSTODY"),
        ActiveCasesIntegration("CRN3333", "John", "Williams", "COMMUNITY"),
        ActiveCasesIntegration("CRN1111", "John", "Doe", "LICENSE"),
      ),
    )

    caseDetailsRepository.saveAll(
      listOf(
        CaseDetailsEntity("CRN2222", Tier.B, false, CaseType.CUSTODY, "Sally", "Smith"),
        CaseDetailsEntity("CRN3333", Tier.C, false, CaseType.COMMUNITY, "John", "Williams"),
        CaseDetailsEntity("CRN1111", Tier.C, false, CaseType.LICENSE, "John", "Doe"),
      ),
    )

    personManagerRepository.saveAll(
      listOf(
        PersonManagerEntity(crn = "CRN2222", teamCode = teamCode, staffCode = staffCodeOM, createdBy = "USER.NAME", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION),
        PersonManagerEntity(crn = "CRN3333", teamCode = teamCode, staffCode = staffCodeOM, createdBy = "USER.NAME", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION),
        PersonManagerEntity(crn = "CRN1111", teamCode = teamCode, staffCode = staffCodeOM, createdBy = "USER.NAME", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION),
      ),
    )

    webTestClient.get()
      .uri("/team/$teamCode/offenderManagers/$staffCodeOM/cases")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.name.forename")
      .isEqualTo("Sheila")
      .jsonPath("$.name.surname")
      .isEqualTo("Hancock")
      .jsonPath("$.grade")
      .isEqualTo("PO")
      .jsonPath("$.code")
      .isEqualTo(staffCodeOM)
      .jsonPath("$.email")
      .isEqualTo("sheila.hancock@test.justice.gov.uk")
      .jsonPath("$.activeCases[?(@.crn == 'CRN2222')].tier")
      .isEqualTo("B")
      .jsonPath("$.activeCases[?(@.crn == 'CRN2222')].provisionalTier")
      .isEqualTo(false)
      .jsonPath("$.activeCases[?(@.crn == 'CRN2222')].type")
      .isEqualTo("CUSTODY")
      .jsonPath("$.activeCases[?(@.crn == 'CRN2222')].name.forename")
      .isEqualTo("Sally")
      .jsonPath("$.activeCases[?(@.crn == 'CRN2222')].name.surname")
      .isEqualTo("Smith")
      .jsonPath("$.activeCases[?(@.crn == 'CRN3333')].tier")
      .isEqualTo("C")
      .jsonPath("$.activeCases[?(@.crn == 'CRN3333')].provisionalTier")
      .isEqualTo(false)
      .jsonPath("$.activeCases[?(@.crn == 'CRN3333')].type")
      .isEqualTo("COMMUNITY")
      .jsonPath("$.activeCases[?(@.crn == 'CRN3333')].name.forename")
      .isEqualTo("John")
      .jsonPath("$.activeCases[?(@.crn == 'CRN3333')].name.surname")
      .isEqualTo("Williams")
      .jsonPath("$.activeCases[?(@.crn == 'CRN1111')].tier")
      .isEqualTo("C")
      .jsonPath("$.activeCases[?(@.crn == 'CRN1111')].provisionalTier")
      .isEqualTo(false)
      .jsonPath("$.activeCases[?(@.crn == 'CRN1111')].type")
      .isEqualTo("LICENSE")
      .jsonPath("$.activeCases[?(@.crn == 'CRN1111')].name.forename")
      .isEqualTo("John")
      .jsonPath("$.activeCases[?(@.crn == 'CRN1111')].name.surname")
      .isEqualTo("Doe")
  }

  @Test
  fun `Get staff member without any email`() {
    workforceAllocationsToDelius.staffActiveCasesResponse(staffCodeNO, email = null)
    webTestClient.get()
      .uri("/team/$teamCode/offenderManagers/$staffCodeNO/cases")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.name.forename")
      .isEqualTo("Sheila")
      .jsonPath("$.name.surname")
      .isEqualTo("Hancock")
      .jsonPath("$.email")
      .doesNotExist()
  }

  @Test
  fun `Get staff member without any WMT active cases`() {
    workforceAllocationsToDelius.staffActiveCasesResponse(staffCodeNO)
    webTestClient.get()
      .uri("/team/$teamCode/offenderManagers/$staffCodeNO/cases")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.name.forename")
      .isEqualTo("Sheila")
      .jsonPath("$.name.surname")
      .isEqualTo("Hancock")
      .jsonPath("$.grade")
      .isEqualTo("PO")
      .jsonPath("$.code")
      .isEqualTo(staffCodeNO)
      .jsonPath("$.activeCases")
      .isEmpty
  }
}
