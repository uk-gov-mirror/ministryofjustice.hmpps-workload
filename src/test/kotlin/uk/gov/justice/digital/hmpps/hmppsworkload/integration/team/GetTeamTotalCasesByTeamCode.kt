package uk.gov.justice.digital.hmpps.hmppsworkload.integration.team

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.client.dto.TeamOverview
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.HmppsProbationEstateApiExtension.Companion.hmppsProbationEstate
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.WorkforceAllocationsToDeliusExtension.Companion.workforceAllocationsToDelius

class GetTeamTotalCasesByTeamCode : IntegrationTestBase() {
  val teamCode = "T1"

  @Test
  fun `can get cases by team code`() {
    // Active team member
    setupCasesForTeamMember("OM1", teamCode)

    // Inactive team member
    setupCasesForTeamMember("OM2", teamCode)

    setupReportData()

    workforceAllocationsToDelius.getTeamsResponse(listOf(teamCode))
    hmppsProbationEstate.getTeamsResponse(listOf(TeamOverview(teamCode, "Team 1")))

    webTestClient.get()
      .uri("/team/workloadcases?teams=$teamCode")
      .headers { it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE")) }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.[0].teamCode")
      .isEqualTo("T1")
      .jsonPath("$.[0].totalCases")
      .isEqualTo(4)
  }

  @Test
  fun `must return forbidden when auth token does not contain correct role`() {
    webTestClient.get()
      .uri("/team/workloadcases?teams=$teamCode")
      .headers { it.authToken(roles = listOf("ROLE_RANDOM_ROLE")) }
      .exchange()
      .expectStatus()
      .isForbidden
  }
}
