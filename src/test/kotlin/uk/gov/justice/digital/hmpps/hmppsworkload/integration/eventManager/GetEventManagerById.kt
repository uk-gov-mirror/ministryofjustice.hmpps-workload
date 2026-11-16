package uk.gov.justice.digital.hmpps.hmppsworkload.integration.eventManager

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.EventManagerAuditEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.EventManagerEntity
import java.util.UUID

class GetEventManagerById : IntegrationTestBase() {

  @Test
  fun `can get event manager by Id`() {
    val storedEventManager = EventManagerEntity(crn = "CRN1", staffCode = "OM1", teamCode = "T1", createdBy = "USER1", eventNumber = 1, isActive = true, spoName = "Fred Flintstone", spoStaffCode = "SP2")
    eventManagerRepository.save(storedEventManager)

    webTestClient.get()
      .uri("/allocation/event/${storedEventManager.uuid}")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.id")
      .isEqualTo(storedEventManager.uuid.toString())
      .jsonPath("$.staffCode")
      .isEqualTo(storedEventManager.staffCode)
      .jsonPath("$.spoName")
      .isEqualTo(storedEventManager.spoName)
      .jsonPath("$.spoStaffCode")
      .isEqualTo(storedEventManager.spoStaffCode)
      .jsonPath("$.createdDate")
      .exists()
      .jsonPath("$.eventNumber")
      .isEqualTo(storedEventManager.eventNumber)
      .jsonPath("$.allocationJustificationNotes")
      .doesNotExist()
      .jsonPath("$.sensitiveNotes")
      .doesNotExist()
  }

  @Test
  fun `must return justification notes when recorded`() {
    val storedEventManager = EventManagerEntity(crn = "CRN1", staffCode = "OM1", teamCode = "T1", createdBy = "USER1", eventNumber = 1, isActive = true, spoName = "Fred Flintstone", spoStaffCode = "SP2")
    eventManagerRepository.save(storedEventManager)

    val eventManagerAudit = eventManagerAuditRepository.save(EventManagerAuditEntity(allocationJustificationNotes = "Justification notes", sensitiveNotes = true, createdBy = storedEventManager.createdBy, spoOversightNotes = "spo oversight notes", sensitiveOversightNotes = true, eventManager = storedEventManager))

    webTestClient.get()
      .uri("/allocation/event/${storedEventManager.uuid}")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.allocationJustificationNotes")
      .isEqualTo(eventManagerAudit.allocationJustificationNotes)
      .jsonPath("$.sensitiveNotes")
      .isEqualTo(eventManagerAudit.sensitiveNotes)
  }

  @Test
  fun `not found returned when getting event manager from uuid which does not exist`() {
    webTestClient.get()
      .uri("/allocation/event/${UUID.randomUUID()}")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isNotFound
  }
}
