package uk.gov.justice.digital.hmpps.hmppsworkload.integration.offenderManager

import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.Tier
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.domain.ActiveCasesIntegration
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.jpa.entity.ReductionCategoryEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.jpa.entity.ReductionReasonEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.TierApiExtension.Companion.hmppsTier
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.WorkforceAllocationsToDeliusExtension.Companion.workforceAllocationsToDelius
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.CaseDetailsEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.EventManagerEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.ReductionEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.ReductionStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class GetOverviewForOffenderManager : IntegrationTestBase() {
  @Test
  fun `can get overview for an offender manager`() {
    val teamCode = "T1"
    val offenderManagerCode = "OM1"
    workforceAllocationsToDelius.officerViewResponse(offenderManagerCode)
    workforceAllocationsToDelius.staffActiveCasesResponse(
      offenderManagerCode,
      activeCases = listOf(
        buildActiveCase("X111111"),
        buildActiveCase("X111112"),
        buildActiveCase("X111113"),
        buildActiveCase("X111114"),
        buildActiveCase("X111115"),
        buildActiveCase("X111116"),
        buildActiveCase("X111117"),
        buildActiveCase("X111118"),
        buildActiveCase("X111119"),
      ),
    )

    val wmtStaff = setupCurrentWmtStaff(offenderManagerCode, teamCode)

    setupWmtManagedCase(wmtStaff, Tier.A, "CRN3333", CaseType.COMMUNITY)
    setupWmtManagedCase(wmtStaff, Tier.D, "CRN2222", CaseType.CUSTODY)

    val reductionCategory = reductionCategoryRepository.save(ReductionCategoryEntity())
    val reductionReason = reductionReasonRepository.save(ReductionReasonEntity(reductionCategoryEntity = reductionCategory))
    val reduction = ReductionEntity(
      workloadOwner = wmtStaff.wmtWorkloadOwnerEntity,
      hours = BigDecimal.valueOf(5),
      effectiveFrom = LocalDate.now().minusDays(2).atStartOfDay(
        ZoneId.systemDefault(),
      ),
      effectiveTo = LocalDate.now().plusDays(2).atStartOfDay(ZoneId.systemDefault()),
      status = ReductionStatus.ACTIVE,
      reductionReasonId = reductionReason.id!!,
    )
    reductionsRepository.save(reduction)

    reductionsRepository.save(
      ReductionEntity(
        workloadOwner = wmtStaff.wmtWorkloadOwnerEntity,
        hours = BigDecimal.valueOf(5),
        effectiveFrom = LocalDate.now().minusDays(2).atStartOfDay(
          ZoneId.systemDefault(),
        ),
        effectiveTo = LocalDate.now().plusDays(2).atStartOfDay(ZoneId.systemDefault()),
        status = ReductionStatus.DELETED,
        reductionReasonId = reductionReason.id!!,
      ),
    )

    hmppsTier.bulkTierCalculationResponse(
      mapOf(
        "X111111" to "A",
        "X111112" to "B",
        "X111113" to "C",
        "X111114" to "D",
        "X111115" to "E",
        "X111116" to "F",
        "X111117" to "G",
        "X111118" to "MISSING",
        "X111119" to "NOT_SUPERVISED",
      ),
    )

    webTestClient.get()
      .uri("/team/$teamCode/offenderManagers/$offenderManagerCode")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.forename")
      .isEqualTo("Sheila")
      .jsonPath("$.surname")
      .isEqualTo("Hancock")
      .jsonPath("$.grade")
      .isEqualTo("PO")
      .jsonPath("$.capacity")
      .isEqualTo(50)
      .jsonPath("$.code")
      .isEqualTo("OM1")
      .jsonPath("$.email")
      .isEqualTo("sheila.hancock@test.justice.gov.uk")
      .jsonPath("$.totalCases")
      .isEqualTo(35)
      .jsonPath("$.weeklyHours")
      .isEqualTo(wmtStaff.wmtWorkloadOwnerEntity.contractedHours)
      .jsonPath("$.totalReductionHours")
      .isEqualTo(reduction.hours)
      .jsonPath("$.pointsAvailable")
      .isEqualTo(wmtStaff.workloadPointsCalculation.availablePoints)
      .jsonPath("$.pointsUsed")
      .isEqualTo(wmtStaff.workloadPointsCalculation.totalPoints)
      .jsonPath("$.pointsRemaining")
      .isEqualTo(wmtStaff.workloadPointsCalculation.availablePoints - wmtStaff.workloadPointsCalculation.totalPoints)
      .jsonPath("$.lastUpdatedOn")
      .isEqualTo(
        wmtStaff.workloadPointsCalculation.lastUpdatedOn.format(
          DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        ),
      )
      .jsonPath("$.nextReductionChange")
      .isEqualTo(
        reduction.effectiveTo.withZoneSameInstant(ZoneOffset.UTC).format(
          DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        ),
      )
      .jsonPath("$.caseTotals.a")
      .isEqualTo(1)
      .jsonPath("$.caseTotals.b")
      .isEqualTo(1)
      .jsonPath("$.caseTotals.c")
      .isEqualTo(1)
      .jsonPath("$.caseTotals.d")
      .isEqualTo(1)
      .jsonPath("$.caseTotals.e")
      .isEqualTo(1)
      .jsonPath("$.caseTotals.f")
      .isEqualTo(1)
      .jsonPath("$.caseTotals.g")
      .isEqualTo(1)
      .jsonPath("$.caseTotals.missing")
      .isEqualTo(1)
      .jsonPath("$.caseTotals.notSupervised")
      .isEqualTo(1)
      .jsonPath("$.paroleReportsDue")
      .isEqualTo(wmtStaff.workload.institutionalReportsDueInNextThirtyDays)
      .jsonPath("$.caseEndDue")
      .isEqualTo(1)
      .jsonPath("$.releasesDue")
      .isEqualTo(1)
      .jsonPath("$.lastAllocatedEvent")
      .doesNotExist()
  }

  @Test
  fun `can get overview for an offender manager without any reductions`() {
    val teamCode = "T1"
    val offenderManagerCode = "OM2"
    setupCurrentWmtStaff(offenderManagerCode, teamCode)
    workforceAllocationsToDelius.officerViewResponse(offenderManagerCode)
    workforceAllocationsToDelius.staffActiveCasesResponse(offenderManagerCode)
    webTestClient.get()
      .uri("/team/$teamCode/offenderManagers/$offenderManagerCode")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.totalReductionHours")
      .isEqualTo(0)
      .jsonPath("$.nextReductionChange")
      .doesNotExist()
  }

  @Test
  fun `can get overview for an offender manager without workload`() {
    val teamCode = "T1"
    val offenderManagerCode = "NOWORKLOAD1"
    workforceAllocationsToDelius.officerViewResponse(offenderManagerCode)

    webTestClient.get()
      .uri("/team/$teamCode/offenderManagers/$offenderManagerCode")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.forename")
      .isEqualTo("Sheila")
      .jsonPath("$.surname")
      .isEqualTo("Hancock")
      .jsonPath("$.grade")
      .isEqualTo("PO")
      .jsonPath("$.capacity")
      .isEqualTo(0)
      .jsonPath("$.code")
      .isEqualTo(offenderManagerCode)
      .jsonPath("$.totalCases")
      .isEqualTo(0)
      .jsonPath("$.weeklyHours")
      .isEqualTo(37)
      .jsonPath("$.totalReductionHours")
      .isEqualTo(0)
      .jsonPath("$.pointsAvailable")
      .isEqualTo(2176)
      .jsonPath("$.pointsUsed")
      .isEqualTo(0)
      .jsonPath("$.pointsRemaining")
      .isEqualTo(2176)
      .jsonPath("$.nextReductionChange")
      .doesNotExist()
      .jsonPath("$.caseTotals.a")
      .isEqualTo(0)
      .jsonPath("$.caseTotals.b")
      .isEqualTo(0)
      .jsonPath("$.caseTotals.c")
      .isEqualTo(0)
      .jsonPath("$.caseTotals.d")
      .isEqualTo(0)
      .jsonPath("$.caseTotals.e")
      .isEqualTo(0)
      .jsonPath("$.caseTotals.f")
      .isEqualTo(0)
      .jsonPath("$.caseTotals.g")
      .isEqualTo(0)
      .jsonPath("$.caseTotals.missing")
      .isEqualTo(0)
      .jsonPath("$.caseTotals.notSupervised")
      .isEqualTo(0)
  }

  @Test
  fun `can get overview for an offender manager without email`() {
    val teamCode = "T1"
    val offenderManagerCode = "NOWORKLOAD1"
    workforceAllocationsToDelius.officerViewResponse(offenderManagerCode, email = null)
    webTestClient.get()
      .uri("/team/$teamCode/offenderManagers/$offenderManagerCode")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.forename")
      .isEqualTo("Sheila")
      .jsonPath("$.surname")
      .isEqualTo("Hancock")
      .jsonPath("$.email")
      .doesNotExist()
  }

  @Test
  fun `get last allocated event`() {
    val teamCode = "T1"
    val offenderManagerCode = "NOWORKLOAD1"
    workforceAllocationsToDelius.officerViewResponse(offenderManagerCode)

    val eventManager = eventManagerRepository.save(
      EventManagerEntity(
        crn = "CRN12345",
        staffCode = offenderManagerCode,
        teamCode = teamCode,
        createdBy = "USER1",
        isActive = true,
        eventNumber = 1,
        spoStaffCode = "SP2",
        spoName = "Fred flintstone",
      ),
    )
    val storedEventManager = eventManagerRepository.findByIdOrNull(eventManager.id!!)!!
    val caseDetails = caseDetailsRepository.save(CaseDetailsEntity(storedEventManager.crn, Tier.C, false, CaseType.COMMUNITY, "Jane", "Doe"))
    webTestClient.get()
      .uri("/team/$teamCode/offenderManagers/$offenderManagerCode")
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.lastAllocatedEvent.allocatedOn")
      .isEqualTo(
        storedEventManager.createdDate!!.withZoneSameInstant(ZoneOffset.UTC).format(
          DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        ),
      )
      .jsonPath("$.lastAllocatedEvent.tier")
      .isEqualTo(caseDetails.tier.name)
      .jsonPath("$.lastAllocatedEvent.sentenceType")
      .isEqualTo(caseDetails.type.name)
  }

  private fun buildActiveCase(crn: String): ActiveCasesIntegration = ActiveCasesIntegration(crn, "", "", "")
}
