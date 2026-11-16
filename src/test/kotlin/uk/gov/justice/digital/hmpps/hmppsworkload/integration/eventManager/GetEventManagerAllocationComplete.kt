package uk.gov.justice.digital.hmpps.hmppsworkload.integration.eventManager

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.WorkforceAllocationsToDeliusExtension.Companion.workforceAllocationsToDelius
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.EventManagerEntity

class GetEventManagerAllocationComplete : IntegrationTestBase() {

  @Test
  fun `can get allocation complete details by crn and event number`() {
    val storedEventManager = EventManagerEntity(
      crn = "CRN1",
      staffCode = "OM1",
      teamCode = "T1",
      createdBy = "USER1",
      isActive = true,
      eventNumber = 2,
      spoStaffCode = "SP2",
      spoName = "Fred flintstone",
    )
    eventManagerRepository.save(storedEventManager)

    workforceAllocationsToDelius.allocationCompleteResponse(storedEventManager.crn, storedEventManager.eventNumber.toString(), storedEventManager.staffCode)

    webTestClient.get()
      .uri("/allocation/person/${storedEventManager.crn}/event/${storedEventManager.eventNumber}/complete-details")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.crn")
      .isEqualTo(storedEventManager.crn)
      .jsonPath("$.name.forename")
      .isEqualTo("Shiva")
      .jsonPath("$.name.surname")
      .isEqualTo("Damon")
      .jsonPath("$.type")
      .isEqualTo("COMMUNITY")
      .jsonPath("$.initialAppointment.date")
      .isEqualTo("2023-01-09")
      .jsonPath("$.staff.name.forename")
      .isEqualTo("Jane")
      .jsonPath("$.staff.name.surname")
      .isEqualTo("Doe")
      .jsonPath("$.staff.email")
      .isEqualTo("j.doe@email.co.uk")
      .jsonPath("$.staff.grade")
      .isEqualTo("PO")
  }

  @Test
  fun `not found when no event manager exists`() {
    webTestClient.get()
      .uri("/allocation/person/CRNNOMANAGER/event/1/complete-details")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isNotFound
  }
}
