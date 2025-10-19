package com.amos_tech_code.data.repository.impl

import com.amos_tech_code.data.database.entities.DepartmentsTable
import com.amos_tech_code.data.database.entities.LecturerTeachingAssignmentsTable
import com.amos_tech_code.data.database.entities.LecturerUniversitiesTable
import com.amos_tech_code.data.database.entities.LecturersTable
import com.amos_tech_code.data.database.entities.ProgrammeUnitsTable
import com.amos_tech_code.data.database.entities.ProgrammesTable
import com.amos_tech_code.data.database.entities.UnitsTable
import com.amos_tech_code.data.database.entities.UniversitiesTable
import com.amos_tech_code.data.database.utils.exposedTransaction
import com.amos_tech_code.domain.dtos.response.AcademicSetupResponse
import com.amos_tech_code.domain.dtos.response.ProgrammeResponse
import com.amos_tech_code.domain.dtos.response.UnitResponse
import com.amos_tech_code.utils.InternalServerException
import com.amos_tech_code.utils.ResourceNotFoundException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID


class LecturerAcademicRepository {

    fun findOrCreateUniversity(universityName: String): UUID = exposedTransaction {
        val universityNameUpperCase = universityName.uppercase()
        // Try to find existing university
        val existingUniversity = UniversitiesTable
            .selectAll().where { UniversitiesTable.name eq universityNameUpperCase }
            .singleOrNull()
            ?.get(UniversitiesTable.id)

        if (existingUniversity != null) {
            return@exposedTransaction existingUniversity
        }

        // Create new university
        val universityId = UUID.randomUUID()
        UniversitiesTable.insert {
            it[id] = universityId
            it[name] = universityNameUpperCase
        }

        universityId
    }

    fun findOrCreateDepartment(universityId: UUID, departmentName: String): UUID = exposedTransaction {
        // Try to find existing department
        val existingDepartment = DepartmentsTable
            .selectAll().where {
                (DepartmentsTable.universityId eq universityId) and
                        (DepartmentsTable.name eq departmentName.uppercase())
            }
            .singleOrNull()
            ?.get(DepartmentsTable.id)

        if (existingDepartment != null) {
            return@exposedTransaction existingDepartment
        }

        // Create new department
        val departmentId = UUID.randomUUID()
        DepartmentsTable.insert {
            it[id] = departmentId
            it[DepartmentsTable.universityId] = universityId
            it[name] = departmentName.uppercase()
        }

        departmentId
    }

    fun findOrCreateProgramme(
        universityId: UUID,
        departmentId: UUID,
        programmeName: String,
        yearOfStudy: Int
    ): UUID = exposedTransaction {
        // For existing programmes, we need to match by ID if provided, or by name+department+university
        val existingProgramme = ProgrammesTable
            .selectAll().where {
                (ProgrammesTable.universityId eq universityId) and
                        (ProgrammesTable.name eq programmeName) and
                        (ProgrammesTable.departmentId eq departmentId)
            }
            .singleOrNull()
            ?.get(ProgrammesTable.id)

        if (existingProgramme != null) {
            return@exposedTransaction existingProgramme
        }

        // Create new programme
        val programmeId = UUID.randomUUID()
        ProgrammesTable.insert {
            it[id] = programmeId
            it[ProgrammesTable.universityId] = universityId
            it[ProgrammesTable.departmentId] = departmentId
            it[name] = programmeName
        }

        programmeId
    }

    fun findOrCreateUnit(universityId: UUID, unitCode: String, unitName: String): UUID = exposedTransaction {
        // Try to find existing unit by code
        val existingUnit = UnitsTable
            .selectAll().where  {
                (UnitsTable.universityId eq universityId) and
                        (UnitsTable.code eq unitCode)
            }
            .singleOrNull()
            ?.get(UnitsTable.id)

        if (existingUnit != null) {
            // Update unit name if different
            if (UnitsTable
                    .selectAll().where { UnitsTable.id eq existingUnit }
                    .single()[UnitsTable.name] != unitName
            ) {
                UnitsTable.update({ UnitsTable.id eq existingUnit }) {
                    it[name] = unitName
                }
            }
            return@exposedTransaction existingUnit
        }

        // Create new unit
        val unitId = UUID.randomUUID()
        UnitsTable.insert {
            it[id] = unitId
            it[UnitsTable.universityId] = universityId
            it[code] = unitCode
            it[name] = unitName
        }

        unitId
    }

    fun linkProgrammeUnit(programmeId: UUID, unitId: UUID, yearOfStudy: Int) = exposedTransaction {
        // Check if link already exists
        val existingLink = ProgrammeUnitsTable
            .selectAll().where {
                (ProgrammeUnitsTable.programmeId eq programmeId) and
                        (ProgrammeUnitsTable.unitId eq unitId) and
                        (ProgrammeUnitsTable.yearOfStudy eq yearOfStudy)
            }
            .singleOrNull()

        if (existingLink == null) {
            ProgrammeUnitsTable.insert {
                it[id] = UUID.randomUUID()
                it[ProgrammeUnitsTable.programmeId] = programmeId
                it[ProgrammeUnitsTable.unitId] = unitId
                it[ProgrammeUnitsTable.yearOfStudy] = yearOfStudy
            }
        }
    }

    fun createTeachingAssignment(
        lecturerId: UUID,
        universityId: UUID,
        programmeId: UUID,
        unitId: UUID,
        yearOfStudy: Int
    ) = exposedTransaction {

        // Check if assignment already exists
        val existingAssignment = LecturerTeachingAssignmentsTable
            .selectAll().where  {
                (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                        (LecturerTeachingAssignmentsTable.universityId eq universityId) and
                        (LecturerTeachingAssignmentsTable.programmeId eq programmeId) and
                        (LecturerTeachingAssignmentsTable.unitId eq unitId) and
                        (LecturerTeachingAssignmentsTable.yearOfStudy eq yearOfStudy)
                        // and (LecturerTeachingAssignmentsTable.academicYear eq academicYear)
            }
            .singleOrNull()

        if (existingAssignment == null) {
            LecturerTeachingAssignmentsTable.insert {
                it[id] = UUID.randomUUID()
                it[LecturerTeachingAssignmentsTable.lecturerId] = lecturerId
                it[LecturerTeachingAssignmentsTable.universityId] = universityId
                it[LecturerTeachingAssignmentsTable.programmeId] = programmeId
                it[LecturerTeachingAssignmentsTable.unitId] = unitId
                it[LecturerTeachingAssignmentsTable.yearOfStudy] = yearOfStudy
                it[LecturerTeachingAssignmentsTable.academicYear] = academicYear
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
            .selectAll().where  { LecturerTeachingAssignmentsTable.lecturerId eq lecturerId }
            .groupBy(UniversitiesTable.id, UniversitiesTable.name)
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

    private fun getProgrammesForLecturerUniversity(lecturerId: UUID, universityId: UUID): List<ProgrammeResponse> {
        return LecturerTeachingAssignmentsTable
            .join(ProgrammesTable, JoinType.INNER, LecturerTeachingAssignmentsTable.programmeId, ProgrammesTable.id)
            .join(DepartmentsTable, JoinType.INNER, ProgrammesTable.departmentId, DepartmentsTable.id)
            .selectAll().where  {
                (LecturerTeachingAssignmentsTable.lecturerId eq lecturerId) and
                        (LecturerTeachingAssignmentsTable.universityId eq universityId)
            }
            .groupBy(
                ProgrammesTable.id,
                ProgrammesTable.name,
                DepartmentsTable.name,
                LecturerTeachingAssignmentsTable.yearOfStudy
            )
            .map { row ->
                val programmeId = row[ProgrammesTable.id]
                val yearOfStudy = row[LecturerTeachingAssignmentsTable.yearOfStudy]

                ProgrammeResponse(
                    id = programmeId.toString(),
                    name = row[ProgrammesTable.name],
                    department = row[DepartmentsTable.name],
                    yearOfStudy = yearOfStudy,
                    units = getUnitsForLecturerProgramme(lecturerId, universityId, programmeId, yearOfStudy)
                )
            }
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


}