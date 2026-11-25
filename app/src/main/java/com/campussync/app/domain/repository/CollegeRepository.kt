package com.campussync.app.domain.repository

import com.campussync.app.data.model.College

interface CollegeRepository {
    suspend fun getColleges(): List<College>
}
