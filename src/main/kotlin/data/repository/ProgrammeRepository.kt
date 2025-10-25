package com.amos_tech_code.data.repository

import com.amos_tech_code.data.database.entities.ProgrammeUnitsTable
import com.amos_tech_code.data.database.entities.ProgrammesTable
import com.amos_tech_code.data.database.entities.StudentProgrammesTable
import com.amos_tech_code.data.database.utils.exposedTransaction
import com.amos_tech_code.domain.models.StudentProgramme
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class ProgrammeRepository() {

    fun getStudentEnrolledProgrammes(studentId: UUID, universityId: UUID): List<StudentProgramme> =
        exposedTransaction {
            StudentProgrammesTable
                .join(ProgrammesTable, JoinType.INNER, StudentProgrammesTable.programmeId, ProgrammesTable.id)
                .selectAll().where {
                    (StudentProgrammesTable.studentId eq studentId) and
                            (StudentProgrammesTable.universityId eq universityId) and
                            (StudentProgrammesTable.isActive eq true)
                }
                .map { row ->
                    StudentProgramme(
                        programmeId = row[StudentProgrammesTable.programmeId],
                        programmeName = row[ProgrammesTable.name],
                        yearOfStudy = row[StudentProgrammesTable.yearOfStudy]
                    )
                }
        }

    fun getStudentActiveProgramme(studentId: UUID, universityId: UUID): StudentProgramme? =
        exposedTransaction {
            StudentProgrammesTable
                .join(ProgrammesTable, JoinType.INNER, StudentProgrammesTable.programmeId, ProgrammesTable.id)
                .selectAll().where {
                    (StudentProgrammesTable.studentId eq studentId) and
                            (StudentProgrammesTable.universityId eq universityId) and
                            (StudentProgrammesTable.isActive eq true)
                }
                .orderBy(StudentProgrammesTable.createdAt to SortOrder.DESC)
                .limit(1)
                .map { row ->
                    StudentProgramme(
                        programmeId = row[StudentProgrammesTable.programmeId],
                        programmeName = row[ProgrammesTable.name],
                        yearOfStudy = row[StudentProgrammesTable.yearOfStudy]
                    )
                }
                .singleOrNull()
        }

    fun linkStudentToProgramme(studentId: UUID, unitId: UUID, programmeId: UUID, universityId: UUID) {
        exposedTransaction {
            val yearOfStudy = ProgrammeUnitsTable
                .select(ProgrammeUnitsTable.yearOfStudy)
                .where {
                    (ProgrammeUnitsTable.programmeId eq programmeId) and
                            (ProgrammeUnitsTable.unitId eq unitId)
                }.single()[ProgrammeUnitsTable.yearOfStudy]

            // Deactivate any existing active programmes for this student at this university
            StudentProgrammesTable.update(
                where = {
                    (StudentProgrammesTable.studentId eq studentId) and
                            (StudentProgrammesTable.universityId eq universityId) and
                            (StudentProgrammesTable.isActive eq true)
                }
            ) {
                it[isActive] = false
            }

            // Create new active programme link
            StudentProgrammesTable.insert {
                it[id] = UUID.randomUUID()
                it[StudentProgrammesTable.studentId] = studentId
                it[StudentProgrammesTable.programmeId] = programmeId
                it[StudentProgrammesTable.universityId] = universityId
                it[StudentProgrammesTable.yearOfStudy] = yearOfStudy
                it[StudentProgrammesTable.academicYear] = null
                it[StudentProgrammesTable.isActive] = true
                it[StudentProgrammesTable.enrolledAt] = LocalDateTime.now()
            }
        }
    }


}