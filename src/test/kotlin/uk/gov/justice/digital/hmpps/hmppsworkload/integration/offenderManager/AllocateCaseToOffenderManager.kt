package uk.gov.justice.digital.hmpps.hmppsworkload.integration.offenderManager

import com.microsoft.applicationinsights.TelemetryClient
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.AllocateCase
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.AllocationReason
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseAllocated
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.CaseType
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.Tier
import uk.gov.justice.digital.hmpps.hmppsworkload.domain.UpdatedCaseDetails
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.AssessRisksNeedsApiExtension.Companion.assessRisksNeedsApi
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.TierApiExtension.Companion.hmppsTier
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.mockserver.WorkforceAllocationsToDeliusExtension.Companion.workforceAllocationsToDelius
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.request.allocateCase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.request.allocateOldCase
import uk.gov.justice.digital.hmpps.hmppsworkload.integration.responses.emailResponse
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.CaseDetailsEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.EventManagerEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.PersonManagerEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.RequirementManagerEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.jpa.entity.WorkloadCalculationEntity
import uk.gov.justice.digital.hmpps.hmppsworkload.service.AuditData
import uk.gov.justice.digital.hmpps.hmppsworkload.service.NotificationMessageResponse
import uk.gov.justice.digital.hmpps.hmppsworkload.service.NotificationService
import uk.gov.justice.digital.hmpps.hmppsworkload.service.TelemetryEventType
import uk.gov.justice.digital.hmpps.hmppsworkload.service.TelemetryEventType.PERSON_MANAGER_ALLOCATED
import uk.gov.justice.digital.hmpps.hmppsworkload.service.getWmtPeriod
import uk.gov.service.notify.NotificationClientApi
import uk.gov.service.notify.NotificationClientException
import uk.gov.service.notify.SendEmailResponse
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

class AllocateCaseToOffenderManager : IntegrationTestBase() {
  @MockkBean
  private lateinit var notificationClient: NotificationClientApi

  @MockkBean
  private lateinit var telemetryClient: TelemetryClient

  @MockkBean
  private lateinit var notificationService: NotificationService

  private val crn = "CRN1"
  private val staffCode = "OM1"
  private val teamCode = "T1"
  private val eventNumber = 1
  private val requirementId = BigInteger.valueOf(645234212L)
  private val allocatedRequiredmentId = BigInteger.valueOf(645234215L)
  private val unallocatedRequirementIds = listOf(BigInteger.valueOf(645234212L), BigInteger.valueOf(645234222L))
  private val allocatedRequirementIds = listOf(BigInteger.valueOf(645234215L), BigInteger.valueOf(645234221L))
  private val allocatingOfficerUsername = "SOME_USER"

  private val templateId = "test-template"
  private val emailReference = UUID.randomUUID().toString()
  private val emails = setOf("first@email.com", "second@email.com")

  @BeforeEach
  fun setupApiCalls() {
    workforceAllocationsToDelius.allocationResponse(crn, eventNumber, staffCode, allocatingOfficerUsername)
    hmppsTier.tierCalculationResponse(crn)
    assessRisksNeedsApi.riskSummaryErrorResponse(crn)
    assessRisksNeedsApi.riskPredictorV1Response(crn)
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Jane", "Doe", Tier.A, false, CaseType.CUSTODY, crn))
    every { notificationClient.sendEmail(any(), any(), any(), any()) } returns
      SendEmailResponse(emailResponse())
    coEvery { notificationService.notifyAllocation(any(), any(), any()) } returns
      NotificationMessageResponse(templateId, emailReference, emails)
    every { telemetryClient.trackEvent(any(), any(), null) } returns Unit
    every { telemetryClient.context.operation.id } returns "fakeId"
  }

  @Test
  fun `can allocate CRN to Staff member`() = runBlocking {
    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.personManagerId")
      .value(String::class.java) { it.matches("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})".toRegex()) }
      .jsonPath("$.eventManagerId")
      .value(String::class.java) { it.matches("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})".toRegex()) }
      .jsonPath("$.requirementManagerIds[0]")
      .value(String::class.java) { it.matches("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})".toRegex()) }

    expectWorkloadAllocationCompleteMessages(crn)

    await untilCallTo {
      workloadCalculationRepository.count()
    } matches { it == 1L }
    val actualWorkloadCalcEntity: WorkloadCalculationEntity =
      workloadCalculationRepository.findFirstByStaffCodeAndTeamCodeOrderByCalculatedDate(staffCode, teamCode)!!

    Assertions.assertAll(
      { Assertions.assertEquals(staffCode, actualWorkloadCalcEntity.staffCode) },
      { Assertions.assertEquals(teamCode, actualWorkloadCalcEntity.teamCode) },
      { Assertions.assertEquals(LocalDateTime.now().dayOfMonth, actualWorkloadCalcEntity.calculatedDate.dayOfMonth) },
      { Assertions.assertEquals(1, actualWorkloadCalcEntity.breakdownData.caseloadCount) },
    )

    verify(exactly = 1) {
      telemetryClient.trackEvent(
        PERSON_MANAGER_ALLOCATED.eventName,
        mapOf(
          "crn" to crn,
          "teamCode" to teamCode,
          "staffCode" to staffCode,
          "caseType" to CaseType.CUSTODY.name,
          "wmtPeriod" to getWmtPeriod(LocalDateTime.now()),
        ),
        null,
      )
    }
  }

  @Test
  fun `Notify error still keeps entry in db`() {
    every { notificationClient.sendEmail(any(), any(), any(), any()) } throws NotificationClientException("An exception")
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Jane", "Doe", Tier.A, false, CaseType.CUSTODY, crn))

    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk

    val personManager = personManagerRepository.findFirstByCrnOrderByCreatedDateDesc(crn)!!
    Assertions.assertEquals(staffCode, personManager.staffCode)
    Assertions.assertEquals(teamCode, personManager.teamCode)

    val eventManager = eventManagerRepository.findFirstByCrnAndEventNumberOrderByCreatedDateDesc(crn, eventNumber)!!
    Assertions.assertEquals(staffCode, eventManager.staffCode)
    Assertions.assertEquals(teamCode, eventManager.teamCode)

    val requirementManager = requirementManagerRepository.findFirstByCrnAndEventNumberAndRequirementIdOrderByCreatedDateDesc(crn, eventNumber, requirementId)!!
    Assertions.assertEquals(staffCode, requirementManager.staffCode)
    Assertions.assertEquals(teamCode, requirementManager.teamCode)
  }

  @Test
  fun `can allocate an already managed CRN to same staff member`() {
    val createdDate = ZonedDateTime.now()
    val storedPersonManager = PersonManagerEntity(crn = crn, staffCode = staffCode, teamCode = teamCode, createdBy = "USER1", createdDate = createdDate, isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)
    val storedEventManager = EventManagerEntity(
      crn = crn,
      staffCode = staffCode,
      teamCode = teamCode,
      createdBy = "USER1",
      createdDate = createdDate,
      isActive = true,
      eventNumber = eventNumber,
      spoStaffCode = "SP2",
      spoName = "Fred flintstone",
    )
    eventManagerRepository.save(storedEventManager)
    val storedRequirementManager = RequirementManagerEntity(
      crn = crn,
      requirementId = requirementId,
      staffCode = staffCode,
      teamCode = teamCode,
      createdBy = "USER1",
      createdDate = createdDate,
      isActive = true,
      eventNumber = eventNumber,
      allocationReason = AllocationReason.INITIAL_ALLOCATION,
    )
    requirementManagerRepository.save(storedRequirementManager)

    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.personManagerId")
      .isEqualTo(storedPersonManager.uuid.toString())
      .jsonPath("$.eventManagerId")
      .isEqualTo(storedEventManager.uuid.toString())
      .jsonPath("$.requirementManagerIds[0]")
      .isEqualTo(storedRequirementManager.uuid.toString())
  }

  @Test
  fun `can allocate an already managed CRN to different staff member`() {
    val otherPersonManager = PersonManagerEntity(crn = crn, staffCode = "ADIFFERENTCODE", teamCode = "TEAMCODE", createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    workforceAllocationsToDelius.officerViewResponse(otherPersonManager.staffCode)
    val storedPersonManager = personManagerRepository.save(otherPersonManager)
    val storedEventManager = eventManagerRepository.save(
      EventManagerEntity(
        crn = crn,
        staffCode = "ADIFFERENTCODE",
        teamCode = "TEAMCODE",
        createdBy = "USER1",
        isActive = true,
        eventNumber = eventNumber,
        spoStaffCode = "SP2",
        spoName = "Fred flintstone",
      ),
    )

    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.personManagerId")
      .value(String::class.java) { it.matches("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})".toRegex()) }
      .jsonPath("$.personManagerId")
      .value(String::class.java) { it != storedPersonManager.uuid.toString() }

    await untilCallTo {
      workloadCalculationRepository.count()
    } matches { it == 2L }

    val actualWorkloadCalcEntity: WorkloadCalculationEntity? =
      workloadCalculationRepository.findFirstByStaffCodeAndTeamCodeOrderByCalculatedDate(storedPersonManager.staffCode, storedPersonManager.teamCode)

    Assertions.assertAll(
      { Assertions.assertEquals(storedPersonManager.staffCode, actualWorkloadCalcEntity!!.staffCode) },
      { Assertions.assertEquals(storedPersonManager.teamCode, actualWorkloadCalcEntity!!.teamCode) },
      { Assertions.assertEquals(LocalDateTime.now().dayOfMonth, actualWorkloadCalcEntity!!.calculatedDate.dayOfMonth) },
    )

    val previousPersonManager = personManagerRepository.findByIdOrNull(storedPersonManager.id!!)!!
    Assertions.assertFalse(previousPersonManager.isActive)

    val previousEventManager = eventManagerRepository.findByIdOrNull(storedEventManager.id!!)!!
    Assertions.assertFalse(previousEventManager.isActive)
  }

  @Test
  fun `only send the email once when clicking allocate multiple times`() {
    // WFP-2937 we have changed the front end behaviour to disable the button after the first click
    workforceAllocationsToDelius.allocationResponse(crn, eventNumber, staffCode, allocatingOfficerUsername)

    casesDbService.insertCaseDetails(UpdatedCaseDetails("Jane", "Doe", Tier.A, false, CaseType.CUSTODY, crn))

    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk

    clearAllMocks()

    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .is5xxServerError
  }

  @Test
  fun `must emit staff grade to tier allocation telemetry event`() {
    val caseDetailsEntity = CaseDetailsEntity(crn, Tier.A, false, CaseType.CUSTODY, "Jane", "Doe")
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Jane", "Doe", Tier.A, false, CaseType.CUSTODY, crn))
    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk

    verify(exactly = 1) {
      telemetryClient.trackEvent(
        TelemetryEventType.STAFF_GRADE_TIER_ALLOCATED.eventName,
        mapOf(
          "teamCode" to teamCode,
          "staffGrade" to "PO",
          "tier" to caseDetailsEntity.tier.name,
        ),
        null,
      )
    }
  }

  @Test
  fun `must emit staff grade to tier allocation telemetry event without case details`() {
    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk

    verify(exactly = 1) {
      telemetryClient.trackEvent(
        TelemetryEventType.STAFF_GRADE_TIER_ALLOCATED.eventName,
        mapOf(
          "teamCode" to teamCode,
          "staffGrade" to "PO",
          "tier" to "A",
        ),
        null,
      )
    }
  }

  @Test
  fun `can send audit data when allocating`() {
    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk

    await untilCallTo { verifyAuditMessageOnQueue() } matches { it == true }
  }

  @Test
  fun `audit data contain required fields and only unallocated requirement`() {
    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk

    await untilCallTo { verifyAuditMessageOnQueue() } matches { it == true }
    val auditData = AuditData(crn, eventNumber, listOf(requirementId))
    Assertions.assertFalse(auditData.requirementIds.contains(allocatedRequiredmentId))
    Assertions.assertEquals(objectMapper.writeValueAsString(auditData), getAuditMessages().details)
  }

  @Test
  fun `audit data contain required fields and all unallocated requirements`() {
    workforceAllocationsToDelius.reset()
    workforceAllocationsToDelius.allocationRequirementResponse(crn, eventNumber, staffCode, allocatingOfficerUsername)
    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk

    await untilCallTo { verifyAuditMessageOnQueue() } matches { it == true }
    val auditData = AuditData(crn, eventNumber, unallocatedRequirementIds)
    Assertions.assertEquals(objectMapper.writeValueAsString(auditData), getAuditMessages().details)
    allocatedRequirementIds.forEach { Assertions.assertFalse(auditData.requirementIds.contains(it)) }
  }

  @Test
  fun `can send email when selecting a second person to receive email`() = runBlocking {
    casesDbService.insertCaseDetails(UpdatedCaseDetails("Jane", "Doe", Tier.A, false, CaseType.COMMUNITY, crn))
    webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.personManagerId")
      .value(String::class.java) { it.matches("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})".toRegex()) }
      .jsonPath("$.eventManagerId")
      .value(String::class.java) { it.matches("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})".toRegex()) }
      .jsonPath("$.requirementManagerIds[0]")
      .value(String::class.java) { it.matches("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})".toRegex()) }

    expectWorkloadAllocationCompleteMessages(crn)

    await untilCallTo {
      workloadCalculationRepository.count()
    } matches { it == 1L }
    val actualWorkloadCalcEntity: WorkloadCalculationEntity =
      workloadCalculationRepository.findFirstByStaffCodeAndTeamCodeOrderByCalculatedDate(staffCode, teamCode)!!

    Assertions.assertAll(
      { Assertions.assertEquals(staffCode, actualWorkloadCalcEntity.staffCode) },
      { Assertions.assertEquals(teamCode, actualWorkloadCalcEntity.teamCode) },
      { Assertions.assertEquals(LocalDateTime.now().dayOfMonth, actualWorkloadCalcEntity.calculatedDate.dayOfMonth) },
      { Assertions.assertEquals(1, actualWorkloadCalcEntity.breakdownData.caseloadCount) },
    )
    val parameters = slot<AllocateCase>()
    // verify that the additional email got an email
    coVerify(exactly = 1) { notificationService.notifyAllocation(any(), capture(parameters), any()) }
    assertTrue(parameters.captured.emailTo!!.size == 1)
    verify(exactly = 1) {
      telemetryClient.trackEvent(
        PERSON_MANAGER_ALLOCATED.eventName,
        mapOf(
          "crn" to crn,
          "teamCode" to teamCode,
          "caseType" to CaseType.COMMUNITY.name,
          "staffCode" to staffCode,
          "wmtPeriod" to getWmtPeriod(LocalDateTime.now()),
        ),
        null,
      )
    }
  }

  @Test
  fun `must return event number for event manager allocated`() {
    val response = webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(CaseAllocated::class.java)
      .returnResult()
      .responseBody

    val storedEventManager = eventManagerRepository.findByUuid(response!!.eventManagerId)!!
    val eventManagerAllocatedEvent = expectWorkloadAllocationCompleteMessages(crn)["event.manager.allocated"]!!

    webTestClient.get()
      .uri(eventManagerAllocatedEvent.detailUrl.replace("https://localhost:8080", ""))
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
      .isEqualTo(staffCode)
      .jsonPath("$.teamCode")
      .isEqualTo(teamCode)
      .jsonPath("$.createdDate")
      .exists()
      .jsonPath("$.eventNumber")
      .isEqualTo(eventNumber)
  }

  @Test
  fun `must return event number for requirement manager allocated`() {
    val response = webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(CaseAllocated::class.java)
      .returnResult()
      .responseBody

    val storedRequirementManager = requirementManagerRepository.findByUuid(response!!.requirementManagerIds[0])!!
    val requirementManagerAllocatedEvent = expectWorkloadAllocationCompleteMessages(crn)["requirement.manager.allocated"]!!
    webTestClient.get()
      .uri(requirementManagerAllocatedEvent.detailUrl.replace("https://localhost:8080", ""))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("$.id")
      .isEqualTo(storedRequirementManager.uuid.toString())
      .jsonPath("$.staffCode")
      .isEqualTo(staffCode)
      .jsonPath("$.teamCode")
      .isEqualTo(teamCode)
      .jsonPath("$.createdDate")
      .exists()
      .jsonPath("$.requirementId")
      .isEqualTo(storedRequirementManager.requirementId)
      .jsonPath("$.eventNumber")
      .isEqualTo(eventNumber)
  }

  @Test
  fun `must audit event manager allocation when evidence supplied`() {
    val allocationJustificationNotes = "Some evidence"
    val sensitiveNotes = false
    val response = webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateOldCase(crn, eventNumber, true, allocationJustificationNotes, sensitiveNotes))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(CaseAllocated::class.java)
      .returnResult()
      .responseBody

    val eventManager = eventManagerRepository.findByUuid(response!!.eventManagerId)
    val eventManagerAudit = eventManagerAuditRepository.findByEventManager(eventManager!!)

    Assertions.assertEquals(1, eventManagerAudit.size)
    Assertions.assertEquals(allocationJustificationNotes, eventManagerAudit[0].allocationJustificationNotes)
    Assertions.assertEquals(sensitiveNotes, eventManagerAudit[0].sensitiveNotes)
  }

  @Test
  fun `can allocate an already managed CRN to same staff member and changes createdDate when not too recent`() {
    val nowTimeCheck = ZonedDateTime.now()
    val createdDateToTest = ZonedDateTime.now().minusMinutes(20)

    val storedPersonManager = PersonManagerEntity(crn = crn, staffCode = staffCode, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)
    val storedEventManager = EventManagerEntity(
      crn = crn,
      staffCode = staffCode,
      teamCode = teamCode,
      createdBy = "USER1",
      isActive = true,
      eventNumber = eventNumber,
      spoStaffCode = "SP2",
      spoName = "Fred flintstone",
    )

    // update event created date
    val saveEventManager = eventManagerRepository.save(storedEventManager)
    saveEventManager.createdDate = createdDateToTest

    eventManagerRepository.save(saveEventManager)

    val storedRequirementManager = RequirementManagerEntity(
      crn = crn,
      requirementId = requirementId,
      staffCode = staffCode,
      teamCode = teamCode,
      createdBy = "USER1",
      isActive = true,
      eventNumber = eventNumber,
      allocationReason = AllocationReason.INITIAL_ALLOCATION,
    )
    requirementManagerRepository.save(storedRequirementManager)

    val response = webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(CaseAllocated::class.java)
      .returnResult()
      .responseBody

    val eventManager = eventManagerRepository.findByUuid(response!!.eventManagerId)

    assertTrue(eventManager!!.createdDate!!.isAfter(createdDateToTest))
    assertTrue(eventManager!!.createdDate!!.isAfter(nowTimeCheck))
  }

  @Test
  fun `Does not allocate an already managed CRN to same staff member if allocated  too recently`() {
    val createdDateToTest = ZonedDateTime.now().minusMinutes(2)
    val storedPersonManager = PersonManagerEntity(crn = crn, staffCode = staffCode, teamCode = teamCode, createdBy = "USER1", isActive = true, allocationReason = AllocationReason.INITIAL_ALLOCATION)
    personManagerRepository.save(storedPersonManager)
    val storedEventManager = EventManagerEntity(
      crn = crn,
      staffCode = staffCode,
      teamCode = teamCode,
      createdBy = "USER1",
      isActive = true,
      eventNumber = eventNumber,
      spoStaffCode = "SP2",
      spoName = "Fred flintstone",
    )
    eventManagerRepository.save(storedEventManager)
    // update event created date
    val saveEventManager = eventManagerRepository.save(storedEventManager)
    saveEventManager.createdDate = createdDateToTest
    eventManagerRepository.save(saveEventManager)

    val storedRequirementManager = RequirementManagerEntity(
      crn = crn,
      requirementId = requirementId,
      staffCode = staffCode,
      teamCode = teamCode,
      createdBy = "USER1",
      isActive = true,
      eventNumber = eventNumber,
      allocationReason = AllocationReason.INITIAL_ALLOCATION,
    )
    requirementManagerRepository.save(storedRequirementManager)

    val response = webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(CaseAllocated::class.java)
      .returnResult()
      .responseBody

    val eventManager = eventManagerRepository.findByUuid(response!!.eventManagerId)
    assertEquals(createdDateToTest.hour, eventManager!!.createdDate!!.hour)
    assertEquals(createdDateToTest.minute, eventManager!!.createdDate!!.minute)
    assertEquals(createdDateToTest.second, eventManager!!.createdDate!!.second)
    assertEquals(createdDateToTest.dayOfMonth, eventManager!!.createdDate!!.dayOfMonth)
  }

  @Test
  fun `must audit event manager allocation when oversight supplied`() {
    val spoOversightNotes = "my spo notes"
    val sensitiveNotes = false
    val response = webTestClient.post()
      .uri("/team/$teamCode/offenderManager/$staffCode/case")
      .bodyValue(allocateCase(crn, eventNumber, true, spoOversightNotes, sensitiveNotes))
      .headers {
        it.authToken(roles = listOf("ROLE_MANAGE_A_WORKFORCE_ALLOCATE"))
        it.contentType = MediaType.APPLICATION_JSON
      }
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(CaseAllocated::class.java)
      .returnResult()
      .responseBody

    val eventManager = eventManagerRepository.findByUuid(response!!.eventManagerId)
    val eventManagerAudit = eventManagerAuditRepository.findByEventManager(eventManager!!)

    Assertions.assertEquals(1, eventManagerAudit.size)
    Assertions.assertEquals(spoOversightNotes, eventManagerAudit[0].spoOversightNotes)
    Assertions.assertEquals(sensitiveNotes, eventManagerAudit[0].sensitiveOversightNotes)
  }
}
