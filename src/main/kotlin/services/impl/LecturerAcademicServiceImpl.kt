package com.amos_tech_code.services.impl

import com.amos_tech_code.data.repository.LecturerAcademicRepository
import com.amos_tech_code.domain.dtos.requests.AcademicSetUpRequest
import com.amos_tech_code.domain.dtos.requests.ProgrammeSetupRequest
import com.amos_tech_code.domain.dtos.requests.UnitSetupRequest
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

    override fun saveAcademicSetup(
        lecturerId: UUID,
        request: AcademicSetUpRequest
    ): AcademicSetupResponse {

        validateAcademicSetupRequest(request)

        return transaction {
            try {
                // 1. Resolve university
                val universityId = if (request.universityId != null) {
                    UUID.fromString(request.universityId)
                } else {
                    repository.findOrCreateUniversity(
                        request.universityName
                            ?: throw ValidationException("University name is required")
                    )
                }

                // 2. Link lecturer to university
                repository.linkLecturerToUniversity(lecturerId, universityId)

                // 3. Resolve academic term
                val academicTermId = repository.findOrCreateAcademicTerm(
                    universityId = universityId,
                    academicYear = request.academicYear,
                    semester = request.semester
                )

                // 4. Process programmes
                request.programmes.forEach { programmeRequest ->

                    // 4a. Resolve department
                    val departmentId = if (programmeRequest.departmentId != null) {
                        UUID.fromString(programmeRequest.departmentId)
                    } else {
                        repository.findOrCreateDepartment(
                            universityId,
                            programmeRequest.departmentName
                        )
                    }

                    // 4b. Resolve programme (NO yearOfStudy here)
                    val programmeId = if (programmeRequest.programmeId != null) {
                        UUID.fromString(programmeRequest.programmeId)
                    } else {
                        repository.findOrCreateProgramme(
                            universityId = universityId,
                            departmentId = departmentId,
                            programmeName = programmeRequest.programmeName
                        )
                    }

                    // 4c. Resolve units
                    val units = repository.findOrCreateUnitsBatch(
                        universityId,
                        departmentId,
                        programmeRequest.units
                    )

                    // 4d. Link programme units WITH year & semester
                    repository.linkProgrammeUnitsBatch(
                        programmeId = programmeId,
                        units = units,
                        yearOfStudy = programmeRequest.yearOfStudy
                    )

                    // 4e. Create teaching assignments (TERM-SCOPED)
                    repository.createTeachingAssignmentsBatch(
                        lecturerId = lecturerId,
                        universityId = universityId,
                        programmeId = programmeId,
                        units = units,
                        academicTermId = academicTermId,
                        yearOfStudy = programmeRequest.yearOfStudy
                    )
                }

                // 5. Mark lecturer profile complete
                repository.markLecturerProfileComplete(lecturerId)

                // 6. Return academic setup
                repository.getLecturerAcademicSetup(lecturerId)

            } catch (ex: Exception) {
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

    private fun validateAcademicSetupRequest(request: AcademicSetUpRequest) {
//        if (request.universityName.isBlank()) {
//            throw ValidationException("University name is required")
//        }
        if (request.academicYear.isBlank()) {
            throw ValidationException("Academic year is required")
        }
        if (request.semester !in listOf(1, 2)) {
            throw ValidationException("Semester must be 1 or 2")
        }
        if (request.programmes.isEmpty()) {
            throw ValidationException("At least one programme is required")
        }
        request.programmes.forEachIndexed { index, programmeRequest -> validateProgrammeRequest(programmeRequest, index) }
    }

    private fun validateProgrammeRequest(programmeRequest: ProgrammeSetupRequest, index: Int) {
        if (programmeRequest.programmeId == null && programmeRequest.programmeName.isBlank()) {
            throw ValidationException("Programme name is required for programme at index $index")
        }
        if (programmeRequest.departmentId == null && programmeRequest.departmentName.isBlank()) {
            throw ValidationException("Department name is required for programme '${programmeRequest.programmeName}'")
        }
        if (programmeRequest.yearOfStudy <= 0) {
            throw ValidationException("Year of study must be positive for programme '${programmeRequest.programmeName}'")
        }
        if (programmeRequest.units.isEmpty()) {
            throw ValidationException("At least one unit is required for programme '${programmeRequest.programmeName}'")
        }

        // Validate all units in this programme
        programmeRequest.units.forEachIndexed { unitIndex, unitRequest ->
            validateUnitRequest(unitRequest, programmeRequest.programmeName, unitIndex)
        }
    }

    private fun validateUnitRequest(
        unitRequest: UnitSetupRequest,
        programmeName: String,
        index: Int
    ) {
        if (unitRequest.code.isBlank()) {
            throw ValidationException("Unit code is required at index $index in $programmeName")
        }
        if (unitRequest.name.isBlank()) {
            throw ValidationException("Unit name is required for ${unitRequest.code}")
        }
        if (unitRequest.semester !in listOf(1, 2)) {
            throw ValidationException("Invalid semester for unit ${unitRequest.code}")
        }
    }

}