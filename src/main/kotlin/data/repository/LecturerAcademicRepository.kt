package com.amos_tech_code.data.repository

import com.amos_tech_code.data.database.entities.AcademicTermsTable
import com.amos_tech_code.data.database.entities.DepartmentsTable
import com.amos_tech_code.data.database.entities.LecturerTeachingAssignmentsTable
import com.amos_tech_code.data.database.entities.LecturerUniversitiesTable
import com.amos_tech_code.data.database.entities.LecturersTable
import com.amos_tech_code.data.database.entities.ProgrammeUnitsTable
import com.amos_tech_code.data.database.entities.ProgrammesTable
import com.amos_tech_code.data.database.entities.UnitsTable
import com.amos_tech_code.data.database.entities.UniversitiesTable
import com.amos_tech_code.data.database.utils.exposedTransaction
import com.amos_tech_code.domain.dtos.requests.UnitSetupRequest
import com.amos_tech_code.domain.dtos.response.AcademicSetupResponse
import com.amos_tech_code.domain.dtos.response.ProgrammeResponse
import com.amos_tech_code.domain.dtos.response.UnitResponse
import com.amos_tech_code.domain.dtos.response.UniversitySetupResponse
import com.amos_tech_code.domain.models.ResolvedUnit
import com.amos_tech_code.utils.ResourceNotFoundException
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID


class LecturerAcademicRepository() {

    suspend fun findOrCreateUniversity(universityName: String): UUID = exposedTransaction {
        val normalizedName = normalizeName(universityName)

        // Try to find existing university using normalized name
        val existingUniversityRow = UniversitiesTable
            .select(UniversitiesTable.id)
            .where { UniversitiesTable.name eq normalizedName }
            .singleOrNull()

        existingUniversityRow?.get(UniversitiesTable.id) ?: run {
            val universityId = UUID.randomUUID()
            UniversitiesTable.insert {
                it[id] = universityId
                it[name] = normalizedName
            }
            universityId
        }
    }

    suspend fun findOrCreateDepartment(universityId: UUID, departmentName: String): UUID = exposedTransaction {
        val normalizedName = normalizeName(departmentName)

        val existingDepartment = DepartmentsTable
            .select(DepartmentsTable.id).where {
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

    suspend fun findOrCreateProgramme(
        universityId: UUID,
        departmentId: UUID,
        programmeName: String,
    ): UUID = exposedTransaction {
        val normalizedName = normalizeName(programmeName)

        val existingProgramme = ProgrammesTable
            .select(ProgrammesTable.id).where {
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
    suspend fun findOrCreateUnitsBatch(
        universityId: UUID,
        departmentId: UUID,
        unitRequests: List<UnitSetupRequest>
    ): List<ResolvedUnit> = exposedTransaction {
        if (unitRequests.isEmpty()) return@exposedTransaction emptyList()

        val normalizedUnits = unitRequests.associateBy { request ->
            normalizeCode(request.code)
        }

        // Single query to find all existing units
        val existingUnits = UnitsTable
            .selectAll().where {
                (UnitsTable.universityId eq universityId) and
                (UnitsTable.departmentId eq departmentId) and
                (UnitsTable.code inList normalizedUnits.keys)
            }
            .associateBy { it[UnitsTable.code] }

        val resolvedUnits = mutableListOf<ResolvedUnit>()

        // Separate existing units that need updates and new units to create
        normalizedUnits.forEach { (code, request) ->
            val row = existingUnits[code]

            val unitId = if (row != null) {
                // Update name if needed
                if (normalizeName(row[UnitsTable.name]) != normalizeName(request.name)) {
                    UnitsTable.update({ UnitsTable.id eq row[UnitsTable.id] }) {
                        it[name] = normalizeName(request.name)
                    }
                }
                row[UnitsTable.id]
            } else {
                val newId = UUID.randomUUID()
                UnitsTable.insert {
                    it[id] = newId
                    it[UnitsTable.universityId] = universityId
                    it[UnitsTable.departmentId] = departmentId
                    it[UnitsTable.code] = code
                    it[name] = normalizeName(request.name)
                }
                newId
            }

            resolvedUnits += ResolvedUnit(
                unitId = unitId,
                code = code,
                semester = request.semester,
            )
        }

        resolvedUnits
    }

    suspend fun findOrCreateAcademicTerm(
        universityId: UUID,
        academicYear: String,
        semester: Int
    ): UUID = exposedTransaction {
        // 1. Try to find existing term
        AcademicTermsTable
            .select(AcademicTermsTable.id).where {
                (AcademicTermsTable.universityId eq universityId) and
                        (AcademicTermsTable.academicYear eq academicYear) and
                        (AcademicTermsTable.semester eq semester)
            }
            .limit(1)
            .singleOrNull()
            ?.let { return@exposedTransaction it[AcademicTermsTable.id] }

        // 2. Create if missing
        return@exposedTransaction try {
            AcademicTermsTable.insert {
                it[this.universityId] = universityId
                it[this.academicYear] = academicYear
                it[this.semester] = semester
                it[this.isActive] = true
            }.resultedValues?.firstOrNull()?.get(AcademicTermsTable.id) ?: throw IllegalStateException("Failed to insert academic term.")
        } catch (ex: ExposedSQLException) {
            // 3. Handle race condition safely
            AcademicTermsTable
                .select(AcademicTermsTable.id).where {
                    (AcademicTermsTable.universityId eq universityId) and
                            (AcademicTermsTable.academicYear eq academicYear) and
                            (AcademicTermsTable.semester eq semester)
                }
                .limit(1)
                .single()[AcademicTermsTable.id]
        }
    }

    // Batch operation for programme-unit links
    suspend fun linkProgrammeUnitsBatch(
        programmeId: UUID,
        units: List<ResolvedUnit>,
        yearOfStudy: Int
    ) = exposedTransaction {
        if (units.isEmpty()) return@exposedTransaction

        ProgrammeUnitsTable.batchInsert(units, ignore = true) { unit ->
            this[ProgrammeUnitsTable.programmeId] = programmeId
            this[ProgrammeUnitsTable.unitId] = unit.unitId
            this[ProgrammeUnitsTable.yearOfStudy] = yearOfStudy
            this[ProgrammeUnitsTable.semester] = unit.semester
        }

    }


    // Batch operation for teaching assignments
    suspend fun createTeachingAssignmentsBatch(
        lecturerId: UUID,
        universityId: UUID,
        programmeId: UUID,
        units: List<ResolvedUnit>,
        academicTermId: UUID,
        yearOfStudy: Int
    ) = exposedTransaction {
        if (units.isEmpty()) return@exposedTransaction

        val unitIds = units.map { it.unitId }

        // Single query to find existing assignments
        val existingAssignments = LecturerTeachingAssignmentsTable
            .selectAll().where {
                (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                        (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                        (LecturerTeachingAssignmentsTable.programmeId eq programmeId) and
                        (LecturerTeachingAssignmentsTable.unitId inList unitIds) and
                        (LecturerTeachingAssignmentsTable.yearOfStudy eq yearOfStudy)
            }
            .map { it[LecturerTeachingAssignmentsTable.unitId] }
            .toSet()

        // Batch create missing assignments
        val assignmentsToCreate = unitIds.filterNot { it in existingAssignments }

        LecturerTeachingAssignmentsTable.batchInsert(assignmentsToCreate) { unitId ->
            this[LecturerTeachingAssignmentsTable.lecturerId] = lecturerId
            this[LecturerTeachingAssignmentsTable.universityId] = universityId
            this[LecturerTeachingAssignmentsTable.programmeId] = programmeId
            this[LecturerTeachingAssignmentsTable.unitId] = unitId
            this[LecturerTeachingAssignmentsTable.academicTermId] = academicTermId
            this[LecturerTeachingAssignmentsTable.yearOfStudy] = yearOfStudy
        }

    }

    suspend fun linkLecturerToUniversity(lecturerId: UUID, universityId: UUID) = exposedTransaction {
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

    suspend fun markLecturerProfileComplete(lecturerId: UUID) = exposedTransaction {
        LecturersTable.update({ LecturersTable.id eq lecturerId }) {
            it[isProfileComplete] = true
        }
    }

    suspend fun getLecturerAcademicSetup(lecturerId: UUID): AcademicSetupResponse = exposedTransaction {
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

    suspend fun getLecturerUniversities(lecturerId: UUID): List<UniversitySetupResponse> = exposedTransaction {
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

    suspend fun getLecturerUniversity(lecturerId: UUID, universityId: UUID): UniversitySetupResponse? = exposedTransaction {
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