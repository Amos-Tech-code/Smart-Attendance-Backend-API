package com.amos_tech_code.data.repository

import com.amos_tech_code.data.database.entities.ProgrammeUnitsTable
import com.amos_tech_code.data.database.entities.ProgrammesTable
import com.amos_tech_code.data.database.entities.StudentEnrollmentsTable
import com.amos_tech_code.data.database.utils.exposedTransaction
import com.amos_tech_code.domain.models.StudentEnrollmentSource
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

    suspend fun getStudentActiveProgramme(studentId: UUID, universityId: UUID): StudentProgramme? =
        exposedTransaction {
            StudentEnrollmentsTable
                .join(ProgrammesTable, JoinType.INNER, StudentEnrollmentsTable.programmeId, ProgrammesTable.id)
                .selectAll().where {
                    (StudentEnrollmentsTable.studentId eq studentId) and
                            (StudentEnrollmentsTable.universityId eq universityId) and
                            (StudentEnrollmentsTable.isActive eq true)
                }
                .orderBy(StudentEnrollmentsTable.enrollmentDate to SortOrder.DESC)
                .limit(1)
                .map { row ->
                    StudentProgramme(
                        programmeId = row[StudentEnrollmentsTable.programmeId],
                        programmeName = row[ProgrammesTable.name],
                        yearOfStudy = row[StudentEnrollmentsTable.yearOfStudy]
                    )
                }
                .singleOrNull()
        }

    suspend fun linkStudentToProgramme(
        studentId: UUID,
        unitId: UUID,
        programmeId: UUID,
        universityId: UUID,
        academicTermId: UUID,
        enrollmentSource: StudentEnrollmentSource
    ) {
        exposedTransaction {
            val yearOfStudy = ProgrammeUnitsTable
                .select(ProgrammeUnitsTable.yearOfStudy)
                .where {
                    (ProgrammeUnitsTable.programmeId eq programmeId) and
                            (ProgrammeUnitsTable.unitId eq unitId)
                }.single()[ProgrammeUnitsTable.yearOfStudy]

            // Deactivate any existing active programmes for this student at this university
            StudentEnrollmentsTable.update(
                where = {
                    (StudentEnrollmentsTable.studentId eq studentId) and
                            (StudentEnrollmentsTable.universityId eq universityId) and
                            (StudentEnrollmentsTable.isActive eq true)
                }
            ) {
                it[isActive] = false
            }

            // Create new active programme link
            StudentEnrollmentsTable.insert {
                it[id] = UUID.randomUUID()
                it[StudentEnrollmentsTable.studentId] = studentId
                it[StudentEnrollmentsTable.programmeId] = programmeId
                it[StudentEnrollmentsTable.universityId] = universityId
                it[StudentEnrollmentsTable.academicTermId] = academicTermId
                it[StudentEnrollmentsTable.yearOfStudy] = yearOfStudy
                it[StudentEnrollmentsTable.enrollmentSource] = enrollmentSource
                it[StudentEnrollmentsTable.enrollmentDate] = LocalDateTime.now()
                it[StudentEnrollmentsTable.isActive] = true
            }
        }
    }


}