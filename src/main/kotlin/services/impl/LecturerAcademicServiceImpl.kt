package com.amos_tech_code.services.impl

import com.amos_tech_code.data.repository.impl.LecturerAcademicRepository
import com.amos_tech_code.domain.dtos.requests.AcademicSetupUpRequest
import com.amos_tech_code.domain.dtos.requests.ProgrammeRequest
import com.amos_tech_code.domain.dtos.requests.UnitRequest
import com.amos_tech_code.domain.dtos.response.AcademicSetupResponse
import com.amos_tech_code.utils.AppException
import com.amos_tech_code.utils.InternalServerException
import com.amos_tech_code.utils.ValidationException
import java.util.UUID
import org.jetbrains.exposed.sql.transactions.transaction

class LecturerAcademicServiceImpl(
    private val repository: LecturerAcademicRepository
) {

    fun saveAcademicSetup(lecturerId: UUID, request: AcademicSetupUpRequest): AcademicSetupResponse {
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

                    // Step 5c: Process each unit in the programme
                    for (unitRequest in programmeRequest.units) {
                        // Find or create unit
                        val unitId = repository.findOrCreateUnit(
                            universityId = universityId,
                            unitCode = unitRequest.code,
                            unitName = unitRequest.name
                        )

                        // Link unit to programme
                        repository.linkProgrammeUnit(
                            programmeId = programmeId,
                            unitId = unitId,
                            yearOfStudy = programmeRequest.yearOfStudy
                        )

                        // Create teaching assignment
                        repository.createTeachingAssignment(
                            lecturerId = lecturerId,
                            universityId = universityId,
                            programmeId = programmeId,
                            unitId = unitId,
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
                    else -> throw InternalServerException("Failed to save academic setup: ${ex.message}")
                }
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