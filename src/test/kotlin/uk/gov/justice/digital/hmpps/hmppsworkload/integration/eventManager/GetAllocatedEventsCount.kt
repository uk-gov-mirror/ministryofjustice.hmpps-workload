package uk.gov.justice.digital.hmpps.hmppsworkload.integration.eventManager

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.controller.EventManagerController
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.Tier
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.CaseDetailsEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.EventManagerEntity
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class GetAllocatedEventsCount : IntegrationTestBase() {

  private val loggedInUser = "SOME_USER"
  val teams = EventManagerController.TeamsRequest(listOf("T1"))

  @Test
  fun `can get count of all allocated events by logged in user`() {
    val storedEventManager = eventManagerRepository.save(
      EventManagerEntity(
        crn = "CRN1",
        staffCode = "OM1",
        teamCode = "T1",
        createdBy = loggedInUser,
        isActive = true,
        eventNumber = 2,
        spoStaffCode = "SP2",
        spoName = "Fred flintstone",
      ),
    )

    caseDetailsRepository.save(CaseDetailsEntity(crn = storedEventManager.crn, tier = Tier.B, provisionalTier = false, type = CaseType.COMMUNITY, "", ""))

    webTestClient.post()
      .uri(
        "/allocation/events/teams/count?since=${thirtyDaysInPast()}",
      )
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE", loggedInUser))
      }
      .bodyValue(teams)
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.caseCount")
      .isEqualTo(1)
  }

  @Test
  fun `only get count since`() {
    val oldEventManager = eventManagerRepository.save(
      EventManagerEntity(
        crn = "CRN1",
        staffCode = "OM1",
        teamCode = "T1",
        createdBy = loggedInUser,
        isActive = true,
        eventNumber = 2,
        spoStaffCode = "SP2",
        spoName = "Fred flintstone",
      ),
    )
    oldEventManager.createdDate = ZonedDateTime.now().minusDays(60)
    eventManagerRepository.save(oldEventManager)

    caseDetailsRepository.save(CaseDetailsEntity(crn = oldEventManager.crn, tier = Tier.B, provisionalTier = false, type = CaseType.COMMUNITY, "", ""))

    webTestClient.post()
      .uri(
        "/allocation/events/teams/count?since=${thirtyDaysInPast()}",
      )
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE", loggedInUser))
      }
      .bodyValue(teams)
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.caseCount")
      .isEqualTo(0)
  }

  @Test
  fun `only retrieve active event managers`() {
    eventManagerRepository.save(
      EventManagerEntity(
        crn = "CRN1",
        staffCode = "OM1",
        teamCode = "T1",
        createdBy = loggedInUser,
        isActive = false,
        eventNumber = 2,
        spoStaffCode = "SP2",
        spoName = "Fred flintstone",
      ),
    )

    webTestClient.post()
      .uri(
        "/allocation/events/teams/count?since=${thirtyDaysInPast()}",
      )
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE", loggedInUser))
      }
      .bodyValue(teams)
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.caseCount")
      .isEqualTo(0)
  }

  private fun thirtyDaysInPast(): String? = ZonedDateTime.now().minusDays(30).truncatedTo(ChronoUnit.DAYS).withZoneSameInstant(ZoneOffset.UTC).format(
    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
  )
}
