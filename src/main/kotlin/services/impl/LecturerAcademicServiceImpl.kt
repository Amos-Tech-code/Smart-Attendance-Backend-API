package com.amos_tech_code.services.impl

import com.amos_tech_code.data.repository.impl.LecturerAcademicRepository
import com.amos_tech_code.domain.dtos.requests.AcademicSetupUpRequest
import com.amos_tech_code.domain.dtos.requests.ProgrammeRequest
import com.amos_tech_code.domain.dtos.requests.UnitRequest
import com.amos_tech_code.domain.dtos.response.AcademicSetupResponse
import com.amos_tech_code.domain.dtos.response.LecturerUniversitiesResponse
import com.amos_tech_code.services.LecturerAcademicService
import com.amos_tech_code.utils.AppException
import com.amos_tech_code.utils.InternalServerException
import com.amos_tech_code.utils.ResourceNotFoundException
import com.amos_tech_code.utils.ValidationException
import java.util.UUID
import org.jetbrains.exposed.sql.transactions.transaction

class LecturerAcademicServiceImpl(
    private val repository: LecturerAcademicRepository
) : LecturerAcademicService {

    override fun saveAcademicSetup(lecturerId: UUID, request: AcademicSetupUpRequest): AcademicSetupResponse {
        // Step 1: Validate ALL input first (before any database interaction)
        validateAcademicSetupRequest(request)

        // Step 2: Execute everything in a single transaction
        return transaction {
            try {
                // Step 3: Find or create university
                val universityId = repository.findOrCreateUniversity(request.universityName)

                // Step 4: Link lecturer to university
                repository.linkLecturerToUniversity(lecturerId, universityId)

                // Step 5: Process each programme
                for (programmeRequest in request.programmes) {
                    // Step 5a: Find or create department
                    val departmentId = repository.findOrCreateDepartment(universityId, programmeRequest.department)

                    // Step 5b: Find or create programme
                    val programmeId = repository.findOrCreateProgramme(
                        universityId = universityId,
                        departmentId = departmentId,
                        programmeName = programmeRequest.name,
                        yearOfStudy = programmeRequest.yearOfStudy
                    )

                    // Step 5c: Process ALL units in batch for this programme
                    if (programmeRequest.units.isNotEmpty()) {
                        // Batch create/find all units for this programme
                        val unitIds = repository.findOrCreateUnitsBatch(universityId, programmeRequest.units)

                        // Batch link all units to programme
                        repository.linkProgrammeUnitsBatch(programmeId, unitIds, programmeRequest.yearOfStudy)

                        // Batch create teaching assignments
                        repository.createTeachingAssignmentsBatch(
                            lecturerId = lecturerId,
                            universityId = universityId,
                            programmeId = programmeId,
                            unitIds = unitIds,
                            yearOfStudy = programmeRequest.yearOfStudy
                        )
                    }
                }

                // Step 6: Mark lecturer profile as complete
                repository.markLecturerProfileComplete(lecturerId)

                // Step 7: Return the created academic setup
                repository.getLecturerAcademicSetup(lecturerId)

            } catch (ex: Exception) {
                // Rollback will happen automatically
                when (ex) {
                    is AppException -> throw ex
                    else -> throw InternalServerException("Failed to save academic setup")
                }
            }
        }
    }

    override suspend fun getLecturerAcademicSetup(lecturerId: UUID, universityId: String?): LecturerUniversitiesResponse {
        try {
            return if (universityId != null) {
                // Return setup for specific university
                val universityUuid = UUID.fromString(universityId)
                val universitySetup = repository.getLecturerUniversity(lecturerId, universityUuid)
                    ?: throw ResourceNotFoundException("University not found")

                LecturerUniversitiesResponse(universities = listOf(universitySetup))

            } else {
                // Return setup for all universities
                val universities = repository.getLecturerUniversities(lecturerId)
                if (universities.isEmpty()) {
                    throw ResourceNotFoundException("No academic setup found for lecturer")
                }
                LecturerUniversitiesResponse(universities = universities)
            }
        } catch (ex: Exception) {
            when (ex) {
                is AppException -> throw ex
                else -> throw InternalServerException("Failed to get academic setup")
            }
        }
    }

    private fun validateAcademicSetupRequest(request: AcademicSetupUpRequest) {
        if (request.universityName.isBlank()) {
            throw ValidationException("University name is required")
        }
        if (request.programmes.isEmpty()) {
            throw ValidationException("At least one programme is required")
        }

        // Validate all programmes and their units
        request.programmes.forEachIndexed { index, programmeRequest ->
            validateProgrammeRequest(programmeRequest, index)
        }
    }

    private fun validateProgrammeRequest(programmeRequest: ProgrammeRequest, index: Int) {
        if (programmeRequest.name.isBlank()) {
            throw ValidationException("Programme name is required for programme at index $index")
        }
        if (programmeRequest.department.isBlank()) {
            throw ValidationException("Department name is required for programme '${programmeRequest.name}'")
        }
        if (programmeRequest.yearOfStudy <= 0) {
            throw ValidationException("Year of study must be positive for programme '${programmeRequest.name}'")
        }
        if (programmeRequest.units.isEmpty()) {
            throw ValidationException("At least one unit is required for programme '${programmeRequest.name}'")
        }

        // Validate all units in this programme
        programmeRequest.units.forEachIndexed { unitIndex, unitRequest ->
            validateUnitRequest(unitRequest, programmeRequest.name, unitIndex)
        }
    }

    private fun validateUnitRequest(unitRequest: UnitRequest, programmeName: String, index: Int) {
        if (unitRequest.code.isBlank()) {
            throw ValidationException("Unit code is required for unit at index $index in programme '$programmeName'")
        }
        if (unitRequest.name.isBlank()) {
            throw ValidationException("Unit name is required for unit '${unitRequest.code}' in programme '$programmeName'")
        }
    }
}