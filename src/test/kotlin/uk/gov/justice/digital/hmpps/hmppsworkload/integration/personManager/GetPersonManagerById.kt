package uk.gov.justice.digital.hmpps.hmppsworkload.integration.personManager

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.AllocationReason
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.PersonManagerEntity
import java.util.UUID

class GetPersonManagerById : IntegrationTestBase() {

  @Test
  fun `can get person manager by Id`() {
    val storedPersonManager = PersonManagerEntity(crn = "CRN1", staffCode = "OM1", teamCode = "T1", createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)
    webTestClient.get()
      .uri("/allocation/person/${storedPersonManager.uuid}")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.id")
      .isEqualTo(storedPersonManager.uuid.toString())
      .jsonPath("$.staffCode")
      .isEqualTo(storedPersonManager.staffCode)
      .jsonPath("$.teamCode")
      .isEqualTo(storedPersonManager.teamCode)
      .jsonPath("$.createdDate")
      .exists()
      .jsonPath("$.crn")
      .isEqualTo(storedPersonManager.crn)
  }

  @Test
  fun `not found returned when getting person manager from uuid which does not exist`() {
    webTestClient.get()
      .uri("/allocation/person/${UUID.randomUUID()}")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isNotFound
  }
}
