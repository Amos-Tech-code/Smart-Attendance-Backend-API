package com.amos_tech_code.di

import com.amos_tech_code.data.repository.LecturerRepository
import com.amos_tech_code.data.repository.StudentRepository
import com.amos_tech_code.data.repository.impl.LecturerAcademicRepository
import com.amos_tech_code.data.repository.impl.LecturerRepositoryImpl
import com.amos_tech_code.data.repository.impl.StudentRepositoryImpl
import com.amos_tech_code.services.AuthService
import com.amos_tech_code.services.GoogleAuthService
import com.amos_tech_code.services.impl.AuthServiceImpl
import com.amos_tech_code.services.impl.GoogleAuthServiceImpl
import com.amos_tech_code.services.impl.LecturerAcademicServiceImpl
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
        AuthServiceImpl(
            lecturerRepository = get(),
            studentRepository = get(),
            googleAuthService = get(),
        )
    }

    single { LecturerAcademicServiceImpl(get()) }


    /**
     * Repositories
     */

    single<StudentRepository> { StudentRepositoryImpl() }

    single<LecturerRepository> { LecturerRepositoryImpl() }

    single { LecturerAcademicRepository() }

}