// writing-with-ai · root build.gradle.kts
// 插件在子模块启用;这里只声明版本 + apply false,真正启用在 app/build.gradle.kts。

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}