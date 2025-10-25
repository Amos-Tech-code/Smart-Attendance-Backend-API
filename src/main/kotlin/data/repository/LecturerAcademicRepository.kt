package com.amos_tech_code.data.repository

import com.amos_tech_code.data.database.entities.DepartmentsTable
import com.amos_tech_code.data.database.entities.LecturerTeachingAssignmentsTable
import com.amos_tech_code.data.database.entities.LecturerUniversitiesTable
import com.amos_tech_code.data.database.entities.LecturersTable
import com.amos_tech_code.data.database.entities.ProgrammeUnitsTable
import com.amos_tech_code.data.database.entities.ProgrammesTable
import com.amos_tech_code.data.database.entities.UnitsTable
import com.amos_tech_code.data.database.entities.UniversitiesTable
import com.amos_tech_code.data.database.utils.exposedTransaction
import com.amos_tech_code.domain.dtos.requests.UnitRequest
import com.amos_tech_code.domain.dtos.response.AcademicSetupResponse
import com.amos_tech_code.domain.dtos.response.ProgrammeResponse
import com.amos_tech_code.domain.dtos.response.UnitResponse
import com.amos_tech_code.domain.dtos.response.UniversitySetupResponse
import com.amos_tech_code.utils.ResourceNotFoundException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID


class LecturerAcademicRepository() {

    fun findOrCreateUniversity(universityName: String): UUID = exposedTransaction {
        val normalizedName = normalizeName(universityName)

        // Try to find existing university using normalized name
        val existingUniversity = UniversitiesTable
            .selectAll().where { UniversitiesTable.name eq normalizedName }
            .singleOrNull()
            ?.get(UniversitiesTable.id)

        existingUniversity ?: run {
            val universityId = UUID.randomUUID()
            UniversitiesTable.insert {
                it[id] = universityId
                it[name] = normalizedName
            }
            universityId
        }
    }

    fun findOrCreateDepartment(universityId: UUID, departmentName: String): UUID = exposedTransaction {
        val normalizedName = normalizeName(departmentName)

        val existingDepartment = DepartmentsTable
            .selectAll().where {
                (DepartmentsTable.universityId eq universityId) and
                        (DepartmentsTable.name eq normalizedName)
            }
            .singleOrNull()
            ?.get(DepartmentsTable.id)

        existingDepartment ?: run {
            val departmentId = UUID.randomUUID()
            DepartmentsTable.insert {
                it[id] = departmentId
                it[DepartmentsTable.universityId] = universityId
                it[name] = normalizedName
            }
            departmentId
        }
    }

    fun findOrCreateProgramme(
        universityId: UUID,
        departmentId: UUID,
        programmeName: String,
        yearOfStudy: Int
    ): UUID = exposedTransaction {
        val normalizedName = normalizeName(programmeName)

        val existingProgramme = ProgrammesTable
            .selectAll().where {
                (ProgrammesTable.universityId eq universityId) and
                        (ProgrammesTable.departmentId eq departmentId) and
                        (ProgrammesTable.name eq normalizedName)
            }
            .singleOrNull()
            ?.get(ProgrammesTable.id)

        existingProgramme ?: run {
            val programmeId = UUID.randomUUID()
            ProgrammesTable.insert {
                it[id] = programmeId
                it[ProgrammesTable.universityId] = universityId
                it[ProgrammesTable.departmentId] = departmentId
                it[name] = normalizedName
            }
            programmeId
        }
    }

    // Batch operation for units
    fun findOrCreateUnitsBatch(universityId: UUID, unitRequests: List<UnitRequest>): Map<String, UUID> = exposedTransaction {
        if (unitRequests.isEmpty()) return@exposedTransaction emptyMap()

        val normalizedUnits = unitRequests.associate { request ->
            normalizeCode(request.code) to normalizeName(request.name)
        }

        // Single query to find all existing units
        val existingUnits = UnitsTable
            .selectAll().where {
                (UnitsTable.universityId eq universityId) and
                        (UnitsTable.code inList normalizedUnits.keys)
            }
            .associate { row ->
                row[UnitsTable.code] to Pair(row[UnitsTable.id], row[UnitsTable.name])
            }

        val result = mutableMapOf<String, UUID>()
        val unitsToUpdate = mutableListOf<Pair<UUID, String>>()
        val unitsToCreate = mutableListOf<Pair<String, String>>()

        // Separate existing units that need updates and new units to create
        normalizedUnits.forEach { (code, normalizedName) ->
            val existingUnit = existingUnits[code]
            if (existingUnit != null) {
                val (unitId, currentName) = existingUnit
                result[code] = unitId

                // Check if name needs update (using normalized comparison)
                if (normalizeName(currentName) != normalizedName) {
                    unitsToUpdate.add(unitId to normalizedName)
                }
            } else {
                unitsToCreate.add(code to normalizedName)
            }
        }

        // Batch update units that need name changes
        if (unitsToUpdate.isNotEmpty()) {
            unitsToUpdate.forEach { (unitId, newName) ->
                UnitsTable.update({ UnitsTable.id eq unitId }) {
                    it[name] = newName
                }
            }
        }

        // Batch create new units
        if (unitsToCreate.isNotEmpty()) {
            unitsToCreate.forEach { (code, name) ->
                val unitId = UUID.randomUUID()
                result[code] = unitId
                UnitsTable.insert {
                    it[id] = unitId
                    it[UnitsTable.universityId] = universityId
                    it[UnitsTable.code] = code
                    it[UnitsTable.name] = name
                }
            }
        }

        result
    }

    // Batch operation for programme-unit links
    fun linkProgrammeUnitsBatch(programmeId: UUID, unitIds: Map<String, UUID>, yearOfStudy: Int) = exposedTransaction {
        if (unitIds.isEmpty()) return@exposedTransaction

        // Single query to find existing links
        val existingLinks = ProgrammeUnitsTable
            .selectAll().where {
                (ProgrammeUnitsTable.programmeId eq programmeId) and
                        (ProgrammeUnitsTable.unitId inList unitIds.values) and
                        (ProgrammeUnitsTable.yearOfStudy eq yearOfStudy)
            }
            .map { it[ProgrammeUnitsTable.unitId] }
            .toSet()

        // Batch create missing links
        val linksToCreate = unitIds.values.filter { it !in existingLinks }
        if (linksToCreate.isNotEmpty()) {
            linksToCreate.forEach { unitId ->
                ProgrammeUnitsTable.insert {
                    it[id] = UUID.randomUUID()
                    it[ProgrammeUnitsTable.programmeId] = programmeId
                    it[ProgrammeUnitsTable.unitId] = unitId
                    it[ProgrammeUnitsTable.yearOfStudy] = yearOfStudy
                }
            }
        }
    }

    // Batch operation for teaching assignments
    fun createTeachingAssignmentsBatch(
        lecturerId: UUID,
        universityId: UUID,
        programmeId: UUID,
        unitIds: Map<String, UUID>,
        yearOfStudy: Int
    ) = exposedTransaction {
        if (unitIds.isEmpty()) return@exposedTransaction

        // Single query to find existing assignments
        val existingAssignments = LecturerTeachingAssignmentsTable
            .selectAll().where {
                (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                        (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                        (LecturerTeachingAssignmentsTable.programmeId eq programmeId) and
                        (LecturerTeachingAssignmentsTable.unitId inList unitIds.values) and
                        (LecturerTeachingAssignmentsTable.yearOfStudy eq yearOfStudy)
            }
            .map { it[LecturerTeachingAssignmentsTable.unitId] }
            .toSet()

        // Batch create missing assignments
        val assignmentsToCreate = unitIds.values.filter { it !in existingAssignments }
        if (assignmentsToCreate.isNotEmpty()) {
            assignmentsToCreate.forEach { unitId ->
                LecturerTeachingAssignmentsTable.insert {
                    it[id] = UUID.randomUUID()
                    it[LecturerTeachingAssignmentsTable.lecturerId] = lecturerId
                    it[LecturerTeachingAssignmentsTable.universityId] = universityId
                    it[LecturerTeachingAssignmentsTable.programmeId] = programmeId
                    it[LecturerTeachingAssignmentsTable.unitId] = unitId
                    it[LecturerTeachingAssignmentsTable.yearOfStudy] = yearOfStudy
                    it[LecturerTeachingAssignmentsTable.academicYear] = null
                }
            }
        }
    }

    fun linkLecturerToUniversity(lecturerId: UUID, universityId: UUID) = exposedTransaction {
        val existingLink = LecturerUniversitiesTable
            .selectAll().where  {
                (LecturerUniversitiesTable.lecturerId eq lecturerId) and
                        (LecturerUniversitiesTable.universityId eq universityId)
            }
            .singleOrNull()

        if (existingLink == null) {
            LecturerUniversitiesTable.insert {
                it[id] = UUID.randomUUID()
                it[LecturerUniversitiesTable.lecturerId] = lecturerId
                it[LecturerUniversitiesTable.universityId] = universityId
            }
        }
    }

    fun markLecturerProfileComplete(lecturerId: UUID) = exposedTransaction {
        LecturersTable.update({ LecturersTable.id eq lecturerId }) {
            it[isProfileComplete] = true
        }
    }

    fun getLecturerAcademicSetup(lecturerId: UUID): AcademicSetupResponse = exposedTransaction {
        // Get the most recent university setup for the lecturer
        val universitySetup = LecturerTeachingAssignmentsTable
            .join(UniversitiesTable, JoinType.INNER, LecturerTeachingAssignmentsTable.universityId, UniversitiesTable.id)
            .selectAll().where { LecturerTeachingAssignmentsTable.lecturerId eq lecturerId }
            .orderBy(LecturerTeachingAssignmentsTable.createdAt to SortOrder.DESC)
            .firstOrNull()

        if (universitySetup == null) {
            throw ResourceNotFoundException("No academic setup found for lecturer")
        }

        val universityId = universitySetup[UniversitiesTable.id]
        val universityName = universitySetup[UniversitiesTable.name]

        // Get all programmes for this university and lecturer
        val programmes = getProgrammesForLecturerUniversity(lecturerId, universityId)

        AcademicSetupResponse(
            universityName = universityName,
            programmes = programmes,
            createdAt = System.currentTimeMillis()
        )
    }

    fun getLecturerUniversities(lecturerId: UUID): List<UniversitySetupResponse> = exposedTransaction {
        // Get all universities associated with the lecturer
        val universities = LecturerUniversitiesTable
            .join(UniversitiesTable, JoinType.INNER, LecturerUniversitiesTable.universityId, UniversitiesTable.id)
            .selectAll().where { LecturerUniversitiesTable.lecturerId eq lecturerId }
            .map { row ->
                val universityId = row[UniversitiesTable.id]
                UniversitySetupResponse(
                    id = universityId.toString(),
                    name = row[UniversitiesTable.name],
                    programmes = getProgrammesForLecturerUniversity(lecturerId, universityId)
                )
            }

        universities
    }

    fun getLecturerUniversity(lecturerId: UUID, universityId: UUID): UniversitySetupResponse? = exposedTransaction {
        // Check if lecturer is associated with this university
        val universityAssociation = LecturerUniversitiesTable
            .join(UniversitiesTable, JoinType.INNER, LecturerUniversitiesTable.universityId, UniversitiesTable.id)
            .selectAll().where {
                (LecturerUniversitiesTable.lecturerId eq lecturerId) and
                        (LecturerUniversitiesTable.universityId eq universityId)
            }
            .singleOrNull()

        universityAssociation?.let { row ->
            UniversitySetupResponse(
                id = universityId.toString(),
                name = row[UniversitiesTable.name],
                programmes = getProgrammesForLecturerUniversity(lecturerId, universityId)
            )
        }
    }

    private fun getProgrammesForLecturerUniversity(lecturerId: UUID, universityId: UUID): List<ProgrammeResponse> {
        return LecturerTeachingAssignmentsTable
            .join(ProgrammesTable, JoinType.INNER, LecturerTeachingAssignmentsTable.programmeId, ProgrammesTable.id)
            .join(DepartmentsTable, JoinType.INNER, ProgrammesTable.departmentId, DepartmentsTable.id)
            .selectAll().where {
                (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                        (LecturerTeachingAssignmentsTable.universityId eq universityId)
            }
            .map { row ->
                ProgrammeResponse(
                    id = row[ProgrammesTable.id].toString(),
                    name = row[ProgrammesTable.name],
                    department = row[DepartmentsTable.name],
                    yearOfStudy = row[LecturerTeachingAssignmentsTable.yearOfStudy],
                    units = getUnitsForLecturerProgramme(
                        lecturerId,
                        universityId,
                        row[ProgrammesTable.id],
                        row[LecturerTeachingAssignmentsTable.yearOfStudy]
                    )
                )
            }
            .distinctBy { Triple(it.id, it.name, it.yearOfStudy) } // Remove duplicates
    }

    private fun getUnitsForLecturerProgramme(
        lecturerId: UUID,
        universityId: UUID,
        programmeId: UUID,
        yearOfStudy: Int
    ): List<UnitResponse> {
        return LecturerTeachingAssignmentsTable
            .join(UnitsTable, JoinType.INNER, LecturerTeachingAssignmentsTable.unitId, UnitsTable.id)
            .selectAll().where {
                (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                        (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                        (LecturerTeachingAssignmentsTable.programmeId eq programmeId) and
                        (LecturerTeachingAssignmentsTable.yearOfStudy eq yearOfStudy)
            }
            .map { row ->
                UnitResponse(
                    id = row[UnitsTable.id].toString(),
                    code = row[UnitsTable.code],
                    name = row[UnitsTable.name]
                )
            }
    }


    // Enhanced normalization functions
    private fun normalizeName(name: String): String {
        return name
            .trim()
            .replace("\\s+".toRegex(), " ") // Replace multiple spaces with single space
            .uppercase()
    }

    private fun normalizeCode(code: String): String {
        return code
            .trim()
            .uppercase()
            .replace("\\s+".toRegex(), "") // Remove all spaces from codes
    }
}