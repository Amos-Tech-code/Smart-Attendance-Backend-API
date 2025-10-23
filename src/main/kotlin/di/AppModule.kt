package com.amos_tech_code.di

import com.amos_tech_code.data.repository.LecturerRepository
import com.amos_tech_code.data.repository.StudentRepository
import com.amos_tech_code.data.repository.impl.AttendanceSessionRepository
import com.amos_tech_code.data.repository.impl.LecturerAcademicRepository
import com.amos_tech_code.data.repository.impl.LecturerRepositoryImpl
import com.amos_tech_code.data.repository.impl.StudentRepositoryImpl
import com.amos_tech_code.services.AttendanceSessionService
import com.amos_tech_code.services.AuthService
import com.amos_tech_code.services.CloudStorageService
import com.amos_tech_code.services.GoogleAuthService
import com.amos_tech_code.services.LecturerAcademicService
import com.amos_tech_code.services.QRCodeService
import com.amos_tech_code.services.SessionCodeGenerator
import com.amos_tech_code.services.impl.AttendanceSessionServiceImpl
import com.amos_tech_code.services.impl.AuthServiceImpl
import com.amos_tech_code.services.impl.CloudStorageServiceImpl
import com.amos_tech_code.services.impl.GoogleAuthServiceImpl
import com.amos_tech_code.services.impl.LecturerAcademicServiceImpl
import com.amos_tech_code.services.impl.QRCodeServiceImpl
import com.amos_tech_code.services.impl.SessionCodeGeneratorImpl
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.koin.dsl.module

val appModule = module {

    single<HttpClient> {
        HttpClient(CIO)
    }

    /**
     * Services
     */
    single<GoogleAuthService> { GoogleAuthServiceImpl(get()) }

    single<AuthService> {
        AuthServiceImpl(get(), get(), get())
    }

    single<LecturerAcademicService> { LecturerAcademicServiceImpl(get()) }

    single<AttendanceSessionService> {
        AttendanceSessionServiceImpl(get(), get(), get(), get())
    }

    single<QRCodeService> { QRCodeServiceImpl() }

    single<SessionCodeGenerator> { SessionCodeGeneratorImpl() }

    single<CloudStorageService> { CloudStorageServiceImpl() }

    /**
     * Repositories
     */

    single<StudentRepository> { StudentRepositoryImpl() }

    single<LecturerRepository> { LecturerRepositoryImpl() }

    single { LecturerAcademicRepository() }

    single { AttendanceSessionRepository() }

}