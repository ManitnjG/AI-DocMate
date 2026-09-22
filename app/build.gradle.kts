plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="com.aidocmate.app"; compileSdk=35
 defaultConfig { applicationId="com.aidocmate.app"; minSdk=26; targetSdk=35; versionCode=4; versionName="0.4.0" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 buildFeatures { compose=true; buildConfig=true }
 defaultConfig {
  val serviceUrl = providers.environmentVariable("DOCMATE_API_URL").orElse("").get()
  require(serviceUrl.isEmpty() || (serviceUrl.startsWith("https://") && serviceUrl.matches(Regex("https://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/[A-Za-z0-9/_-]*)?")))) { "DOCMATE_API_URL must be an HTTPS service URL" }
  buildConfigField("String", "DOCMATE_API_URL", "\"$serviceUrl\"")
 }
}
dependencies {
 implementation("com.google.mlkit:text-recognition:16.0.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("com.tom-roush:pdfbox-android:2.0.27.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
