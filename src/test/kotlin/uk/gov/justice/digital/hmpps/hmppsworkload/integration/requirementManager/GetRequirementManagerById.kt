package uk.gov.justice.digital.hmpps.hmppsworkload.integration.requirementManager

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import java.util.UUID

class GetRequirementManagerById : IntegrationTestBase() {

  @Test
  fun `not found returned when getting requirement manager from uuid which does not exist`() {
    webTestClient.get()
      .uri("/allocation/requirements/${UUID.randomUUID()}")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isNotFound
  }
}
