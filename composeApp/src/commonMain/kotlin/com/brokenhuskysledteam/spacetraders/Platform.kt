package com.brokenhuskysledteam.spacetraders

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform