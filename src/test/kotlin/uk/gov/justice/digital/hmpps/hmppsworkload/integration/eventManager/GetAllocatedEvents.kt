package uk.gov.justice.digital.hmpps.hmppsworkload.integration.eventManager

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.controller.EventManagerController
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.Tier
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.domain.AllocationDetailIntegration
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.WorkforceAllocationsToDeliusExtension.Companion.workforceAllocationsToDelius
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.CaseDetailsEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.EventManagerEntity
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class GetAllocatedEvents : IntegrationTestBase() {

  private val loggedInUser = "SOME_USER"
  private val teams = EventManagerController.TeamsRequest(listOf("T1"))

  @Test
  fun `can get all allocated events by logged in user`() {
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

    val caseDetails = caseDetailsRepository.save(CaseDetailsEntity(crn = storedEventManager.crn, tier = Tier.B, provisionalTier = false, type = CaseType.COMMUNITY, "", ""))

    workforceAllocationsToDelius.allocationDetailsResponse(listOf(AllocationDetailIntegration(storedEventManager.crn, storedEventManager.staffCode)))

    webTestClient.post()
      .uri(
        "/allocation/events/teams?since=${thirtyDaysInPast()}",
      )
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE", loggedInUser))
      }
      .bodyValue(teams)
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.cases[?(@.crn == '${storedEventManager.crn}')].name.forename")
      .isEqualTo("Dylan")
      .jsonPath("$.cases[?(@.crn == '${storedEventManager.crn}')].name.surname")
      .isEqualTo("Armstrong")
      .jsonPath("$.cases[?(@.crn == '${storedEventManager.crn}')].name.combinedName")
      .isEqualTo("Dylan Adam Armstrong")
      .jsonPath("$.cases[?(@.crn == '${storedEventManager.crn}')].staff.name.combinedName")
      .isEqualTo("Sally Smith")
      .jsonPath("$.cases[?(@.crn == '${storedEventManager.crn}')].tier")
      .isEqualTo(caseDetails.tier.name)
      .jsonPath("$.cases[?(@.crn == '${storedEventManager.crn}')].provisionalTier")
      .isEqualTo(caseDetails.provisionalTier)
      .jsonPath("$.cases[?(@.crn == '${storedEventManager.crn}')].allocatedOn")
      .exists()
  }

  @Test
  fun `only get cases since`() {
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

    workforceAllocationsToDelius.allocationDetailsResponse(listOf(AllocationDetailIntegration(oldEventManager.crn, oldEventManager.staffCode)))

    webTestClient.post()
      .uri(
        "/allocation/events/teams?since=${thirtyDaysInPast()}",
      )
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE", loggedInUser))
      }
      .bodyValue(teams)
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.cases[?(@.crn == '${oldEventManager.crn}')]")
      .doesNotExist()
  }

  @Test
  fun `only retrieve active event managers`() {
    val inactiveEventManager = eventManagerRepository.save(
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
        "/allocation/events/teams?since=${thirtyDaysInPast()}",
      )
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE", loggedInUser))
      }
      .bodyValue(teams)
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.cases[?(@.crn == '${inactiveEventManager.crn}')]")
      .doesNotExist()
  }

  @Test
  fun `don't return allocated event managers who have no Delius details`() {
    val noDeliusDetailsEventManager = eventManagerRepository.save(
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

    workforceAllocationsToDelius.allocationDetailsResponse(emptyList())

    caseDetailsRepository.save(CaseDetailsEntity(crn = noDeliusDetailsEventManager.crn, tier = Tier.B, provisionalTier = false, type = CaseType.COMMUNITY, "", ""))

    webTestClient.post()
      .uri(
        "/allocation/events/teams?since=${thirtyDaysInPast()}",
      )
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE", loggedInUser))
      }
      .bodyValue(teams)
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.cases[?(@.crn == '${noDeliusDetailsEventManager.crn}')]")
      .doesNotExist()
  }

  private fun thirtyDaysInPast(): String? = ZonedDateTime.now().minusDays(30).truncatedTo(ChronoUnit.DAYS).withZoneSameInstant(ZoneOffset.UTC).format(
    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
  )
}
