// buildSrc — precompiled build logic，自动加入所有 build.gradle.kts 的 classpath。
// 见 openspec/changes/release-preflight-automation/{proposal,design}.md。
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
