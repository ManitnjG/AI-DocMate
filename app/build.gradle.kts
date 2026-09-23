plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="com.aidocmate.app"; compileSdk=35
 defaultConfig { testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"; applicationId="com.aidocmate.app"; minSdk=26; targetSdk=35; versionCode=13; versionName="0.8.4" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 val releaseKeyPath = providers.environmentVariable("DOCMATE_KEYSTORE").orNull
 if (!releaseKeyPath.isNullOrBlank()) {
  signingConfigs {
   create("production") {
    storeFile = file(releaseKeyPath)
    storePassword = providers.environmentVariable("DOCMATE_STORE_PASSWORD").get()
    keyAlias = providers.environmentVariable("DOCMATE_KEY_ALIAS").get()
    keyPassword = providers.environmentVariable("DOCMATE_KEY_PASSWORD").get()
   }
  }
  buildTypes.getByName("release").signingConfig = signingConfigs.getByName("production")
 }
 buildFeatures { compose=true; buildConfig=true }
 defaultConfig {
  val serviceUrl = providers.environmentVariable("DOCMATE_API_URL").orNull?.takeIf { it.isNotBlank() } ?: "https://ai-docmate-boli.onrender.com"
  require(serviceUrl.isEmpty() || (serviceUrl.startsWith("https://") && serviceUrl.matches(Regex("https://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/[A-Za-z0-9/_-]*)?")))) { "DOCMATE_API_URL must be an HTTPS service URL" }
  buildConfigField("String", "DOCMATE_API_URL", "\"$serviceUrl\"")
 }
}
dependencies {
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
 implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
 implementation("com.google.mlkit:text-recognition:16.0.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("com.tom-roush:pdfbox-android:2.0.27.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.work:work-testing:2.10.0")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 debugImplementation("androidx.compose.ui:ui-tooling")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
