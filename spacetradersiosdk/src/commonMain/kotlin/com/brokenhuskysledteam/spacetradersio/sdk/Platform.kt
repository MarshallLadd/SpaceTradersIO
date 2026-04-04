package com.brokenhuskysledteam.spacetradersio.sdk

// KMP expect/actual declaration — each platform provides its own implementation.
// Currently used only for identification; may be extended for platform-specific
// behavior (e.g. different cache strategies on Android vs iOS).
expect fun platform(): String
